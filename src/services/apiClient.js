// Central HTTP client. Every service module funnels requests through here so
// swapping the mock layer for the real Laravel/Spring Boot backend (per SRS §5.1)
// only requires changing this file's implementation, not any calling code.

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api/v1";
const USE_MOCKS = true; // flip to false once the backend above is live

export class ApiError extends Error {
  constructor(message, status, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

async function request(path, { method = "GET", body, params } = {}) {
  const url = new URL(BASE_URL + path, window.location.origin);
  if (params) Object.entries(params).forEach(([k, v]) => v !== undefined && url.searchParams.set(k, v));

  const token = localStorage.getItem("msh_token");
  const res = await fetch(url.toString(), {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    const details = await res.json().catch(() => ({}));
    throw new ApiError(details.message || res.statusText, res.status, details);
  }
  return res.json();
}

export const apiClient = { request };

// ---- Mock helper used by every service module during frontend-only development ----
export function mockRequest(data, { delay = 450, failRate = 0 } = {}) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (failRate && Math.random() < failRate) {
        reject(new ApiError("Something went wrong. Please try again.", 500));
      } else {
        resolve(structuredClone(data));
      }
    }, delay);
  });
}

export const isMockMode = USE_MOCKS;
