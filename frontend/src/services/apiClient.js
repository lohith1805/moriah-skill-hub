// Central HTTP client for the Spring Boot backend.
//
// Contract (see backend docs/API-Documentation.md):
//   - Every response is an envelope: { success, data, error, timestamp }.
//     `request()` unwraps it and returns `data`; on `success:false` it throws
//     an ApiError carrying `error.code` / `error.message`.
//   - Lists are `PageResponse`: { content, page, size, totalElements, totalPages, last }.
//     That whole object is returned as `data` — callers read `.content` etc.
//   - Auth: access token in `Authorization: Bearer`, plus a rotating refresh
//     token. A 401 triggers one transparent `POST /auth/refresh` + retry.

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";

// Flip to true only to run the old mockData layer again (kept for reference /
// gradual migration of the remaining *Service.js files).
export const USE_MOCKS = false;
export const isMockMode = USE_MOCKS;

// ---- token storage -------------------------------------------------------

const ACCESS_KEY = "msh_access_token";
const REFRESH_KEY = "msh_refresh_token";
const EXPIRY_KEY = "msh_token_expiry"; // epoch ms

export const tokenStore = {
  get access() {
    try {
      return localStorage.getItem(ACCESS_KEY);
    } catch {
      return null;
    }
  },
  get refresh() {
    try {
      return localStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  },
  get expiresAt() {
    try {
      return Number(localStorage.getItem(EXPIRY_KEY)) || 0;
    } catch {
      return 0;
    }
  },
  // Accepts the backend `tokens` object: { accessToken, refreshToken, expiresInSeconds }.
  set(tokens) {
    if (!tokens) return;
    try {
      if (tokens.accessToken) localStorage.setItem(ACCESS_KEY, tokens.accessToken);
      if (tokens.refreshToken) localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
      if (tokens.expiresInSeconds) {
        localStorage.setItem(EXPIRY_KEY, String(Date.now() + tokens.expiresInSeconds * 1000));
      }
    } catch {
      /* private mode / quota — session just won't persist */
    }
  },
  clear() {
    try {
      localStorage.removeItem(ACCESS_KEY);
      localStorage.removeItem(REFRESH_KEY);
      localStorage.removeItem(EXPIRY_KEY);
    } catch {
      /* noop */
    }
  },
  get hasSession() {
    return !!this.access;
  },
};

// ---- errors ------------------------------------------------------------

export class ApiError extends Error {
  constructor(message, status, code, details) {
    super(message || "Request failed");
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

// ---- core --------------------------------------------------------------

function buildUrl(path, params) {
  const base = BASE_URL.startsWith("http") ? BASE_URL : window.location.origin + BASE_URL;
  const url = new URL(base.replace(/\/$/, "") + path);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v === undefined || v === null || v === "") return;
      if (Array.isArray(v)) v.forEach((item) => url.searchParams.append(k, item));
      else url.searchParams.set(k, v);
    });
  }
  return url.toString();
}

async function parseEnvelope(res) {
  let body = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  if (!res.ok) {
    const err = body && body.error ? body.error : {};
    throw new ApiError(
      err.message || body?.message || res.statusText,
      res.status,
      err.code,
      body
    );
  }
  // Enveloped success -> return the payload; otherwise return the raw body.
  if (body && typeof body === "object" && "success" in body && "data" in body) {
    return body.data;
  }
  return body;
}

// Single-flight refresh: many parallel requests hitting 401 share one call.
let refreshInFlight = null;

async function doRefresh() {
  const refreshToken = tokenStore.refresh;
  if (!refreshToken) throw new ApiError("Session expired. Please sign in again.", 401, "NO_REFRESH_TOKEN");

  const res = await fetch(buildUrl("/auth/refresh"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  const data = await parseEnvelope(res).catch((e) => {
    // A failed refresh means the whole chain is dead — clear and bubble up.
    tokenStore.clear();
    throw e;
  });
  const tokens = data?.tokens || data;
  tokenStore.set(tokens);
  return tokens;
}

async function ensureFreshToken() {
  // Proactive refresh ~30s before expiry so a request never races a 401.
  const exp = tokenStore.expiresAt;
  if (tokenStore.refresh && exp && Date.now() > exp - 30_000) {
    if (!refreshInFlight) {
      refreshInFlight = doRefresh().finally(() => {
        refreshInFlight = null;
      });
    }
    try {
      await refreshInFlight;
    } catch {
      /* fall through — the request will 401 and be handled or rejected */
    }
  }
}

async function rawRequest(path, { method, headers, body, params, isForm } = {}) {
  await ensureFreshToken();

  const finalHeaders = { ...(headers || {}) };
  const token = tokenStore.access;
  if (token) finalHeaders.Authorization = `Bearer ${token}`;
  if (!isForm && body !== undefined) finalHeaders["Content-Type"] = "application/json";

  return fetch(buildUrl(path, params), {
    method: method || "GET",
    headers: finalHeaders,
    body: isForm ? body : body !== undefined ? JSON.stringify(body) : undefined,
  });
}

async function request(path, opts = {}) {
  let res = await rawRequest(path, opts);

  if (res.status === 401 && tokenStore.refresh && !opts._retried && !path.startsWith("/auth/")) {
    try {
      if (!refreshInFlight) {
        refreshInFlight = doRefresh().finally(() => {
          refreshInFlight = null;
        });
      }
      await refreshInFlight;
      res = await rawRequest(path, { ...opts, _retried: true });
    } catch {
      tokenStore.clear();
      // Let the caller (AuthContext) see the 401 and bounce to /login.
      throw new ApiError("Your session has expired. Please sign in again.", 401, "SESSION_EXPIRED");
    }
  }

  return parseEnvelope(res);
}

// multipart/form-data — for resume + project asset uploads. `fields` may hold
// scalar values and `files` maps a field name to a File (or [File]).
async function requestMultipart(path, { method = "POST", fields = {}, files = {}, params } = {}) {
  const fd = new FormData();
  Object.entries(fields).forEach(([k, v]) => {
    if (v !== undefined && v !== null) fd.append(k, v);
  });
  Object.entries(files).forEach(([k, v]) => {
    if (Array.isArray(v)) v.forEach((f) => f && fd.append(k, f));
    else if (v) fd.append(k, v);
  });
  return request(path, { method, body: fd, isForm: true, params });
}

export const apiClient = {
  request,
  requestMultipart,
  get: (path, params) => request(path, { method: "GET", params }),
  post: (path, body, params) => request(path, { method: "POST", body, params }),
  put: (path, body, params) => request(path, { method: "PUT", body, params }),
  patch: (path, body, params) => request(path, { method: "PATCH", body, params }),
  del: (path, params) => request(path, { method: "DELETE", params }),
  refresh: doRefresh,
};

// ---- legacy mock helper ------------------------------------------------
// Still imported by the not-yet-migrated *Service.js modules. Harmless once
// USE_MOCKS is false and those services are rewritten to call apiClient.
export function mockRequest(data, { delay = 300 } = {}) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delay));
}
