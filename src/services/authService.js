// Auth against the Spring Boot backend (/api/v1/auth/**, /api/v1/users/me,
// /api/v1/admin/client-requests, /api/v1/admin/users).
//
// The FE `user` object the app's components expect is composed from:
//   - GET /users/me         -> profile fields (name, email, phone, ...)
//   - the access-token JWT   -> `roles` (claim) + `sub` (uuid)
// The token carries the authoritative role list; /users/me does not return it.

import { apiClient, tokenStore } from "./apiClient";
import {
  primaryFeRole,
  FE_ROLE_TO_BACKEND,
  ACCOUNT_STATUS,
} from "../utils/constants";
import { logAudit, AUDIT_CATEGORIES } from "../utils/auditLog";

const USER_KEY = "msh_user";
const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";

// ---- JWT (decode only — verification is the backend's job) ----------------

function decodeJwt(token) {
  try {
    const part = token.split(".")[1];
    const json = atob(part.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decodeURIComponent(escape(json)));
  } catch {
    return {};
  }
}

// ---- FE user shape ------------------------------------------------------

function toFeUser(me, accessToken) {
  const claims = decodeJwt(accessToken || tokenStore.access);
  const backendRoles = Array.isArray(claims.roles) ? claims.roles : [];
  return {
    id: me.uuid,
    uuid: me.uuid,
    name: me.fullName,
    email: me.email,
    phone: me.phone || "",
    githubUsername: me.githubUsername || "",
    linkedinUrl: me.linkedinUrl || "",
    // Decision D3: one primary role for routing/nav/guards.
    role: primaryFeRole(backendRoles),
    roles: backendRoles, // raw backend codes — kept for a future role switcher
    // If we are holding a valid token the account is ACTIVE; the backend login
    // gate rejects every other status before issuing tokens. A mid-session
    // suspension surfaces as the next request 401-ing.
    accountStatus: ACCOUNT_STATUS.ACTIVE,
    bio: me.bio,
    location: me.location,
    currentTitle: me.currentTitle,
    experienceLevel: me.experienceLevel,
    yearsExperience: me.yearsExperience,
    skills: me.skills || [],
    education: me.education || [],
    workExperience: me.workExperience || [],
    hasResume: !!me.hasResume,
    portfolioSlug: me.portfolioSlug,
    profileComplete: !!me.isComplete,
    completionPercent: me.completionPercent ?? 0,
    twoFactorEnabled: !!(me.twoFactorEnabled ?? me.mfaEnabled),
  };
}

// ---- session persistence ---------------------------------------------------

export function persistSession(user /*, token (legacy, unused) */) {
  persistUser(user);
}

function persistUser(user) {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  } catch {
    /* private mode / quota */
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(USER_KEY);
  } catch {
    /* noop */
  }
  tokenStore.clear();
}

export function getPersistedUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !tokenStore.hasSession) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

// ---- core auth flows -----------------------------------------------------

export async function getMe() {
  const me = await apiClient.get("/users/me");
  const user = toFeUser(me);
  persistUser(user);
  return user;
}

// PUT /api/v1/users/me/profile — self-service profile fields only. NOTE: name,
// email and phone are NOT editable here (name/phone go through admin's
// PUT /admin/users/{uuid}, B1.17); this endpoint takes bio / location /
// currentTitle / githubUsername / experienceLevel / yearsExperience / skills /
// education / workExperience. Returns the refreshed FE user.
export async function updateProfile(patch = {}) {
  const body = {
    githubUsername: patch.githubUsername ?? undefined,
    bio: patch.bio ?? undefined,
    location: patch.location ?? undefined,
    currentTitle: patch.currentTitle ?? undefined,
    experienceLevel: patch.experienceLevel ?? undefined,
    yearsExperience:
      patch.yearsExperience === "" || patch.yearsExperience == null
        ? undefined
        : Number(patch.yearsExperience),
    skills: patch.skills ?? undefined,
    education: patch.education ?? undefined,
    workExperience: patch.workExperience ?? undefined,
  };
  const me = await apiClient.put("/users/me/profile", body);
  const user = toFeUser(me);
  persistUser(user);
  return user;
}

// Returns either { user } (logged in) or { twoFactorRequired, twoFactorSetupRequired,
// challengeToken } (caller must complete 2FA before a session exists).
export async function login({ email, identifier, password }) {
  const who = (email || identifier || "").trim();
  let res;
  try {
    res = await apiClient.post("/auth/login", { email: who, password });
  } catch (err) {
    logAudit({
      category: AUDIT_CATEGORIES.SECURITY_LOGIN,
      severity: "Warning",
      actor: who || "unknown",
      action: "Login failed",
      target: who || "—",
    });
    throw err;
  }
  const result = await finishAuth(res);
  logAudit({
    category: AUDIT_CATEGORIES.SECURITY_LOGIN,
    actor: result.user?.name || who,
    action: result.twoFactorRequired ? "Login challenge issued — 2FA required" : "Login succeeded",
    target: who || "—",
  });
  return result;
}

export async function acceptInvite(token, password) {
  const res = await apiClient.post("/auth/accept-invite", { token, password });
  return finishAuth(res);
}

async function finishAuth(loginResponse) {
  if (loginResponse && loginResponse.tokens) {
    tokenStore.set(loginResponse.tokens);
    const user = await getMe();
    return { user, twoFactorRequired: false };
  }
  return {
    user: null,
    twoFactorRequired: true,
    twoFactorSetupRequired: !!(loginResponse && loginResponse.twoFactorSetupRequired),
    challengeToken: loginResponse ? loginResponse.challengeToken : null,
  };
}

// Complete a 2FA-gated login: exchange the challenge token + TOTP code for a session.
export async function verifyTwoFactor({ challengeToken, totpCode }) {
  let res;
  try {
    res = await apiClient.post("/auth/2fa/verify", { challengeToken, totpCode });
  } catch (err) {
    logAudit({
      category: AUDIT_CATEGORIES.SECURITY_LOGIN,
      severity: "Warning",
      action: "2FA verification failed — invalid code",
      target: "self",
    });
    throw err;
  }
  const tokens = res && res.tokens ? res.tokens : res;
  tokenStore.set(tokens);
  const user = await getMe();
  logAudit({
    category: AUDIT_CATEGORIES.SECURITY_LOGIN,
    actor: user?.name || user?.email,
    action: "2FA verification succeeded",
    target: user?.email || "self",
  });
  return user;
}

// Start mandatory-2FA setup (LoginResponse.twoFactorSetupRequired). Returns
// { secret, provisioningUri } to render a QR; confirm with verifyTwoFactor().
export async function beginTwoFactorSetup(challengeToken) {
  return apiClient.post("/auth/2fa/enable", challengeToken ? { challengeToken } : {});
}

// --- Voluntary 2FA management (already-logged-in caller, Account Settings) ---

// POST /auth/2fa/enable {} -> { secret, provisioningUri }. Not yet in effect
// until confirmTwoFactorSetup() is called with a code from that secret.
export async function startTwoFactorSetup() {
  return apiClient.post("/auth/2fa/enable", {});
}

// POST /auth/2fa/verify { totpCode } (no challengeToken) -> setup confirmed.
export async function confirmTwoFactorSetup(totpCode) {
  await apiClient.post("/auth/2fa/verify", { totpCode });
  return getMe();
}

// POST /auth/2fa/disable { totpCode } — needs a currently-valid code.
export async function disableTwoFactor(totpCode) {
  await apiClient.post("/auth/2fa/disable", { totpCode });
  return getMe();
}

export async function refreshSession() {
  await apiClient.refresh();
  return getMe();
}

export async function logout() {
  const refreshToken = tokenStore.refresh;
  try {
    if (refreshToken) await apiClient.post("/auth/logout", { refreshToken });
  } catch {
    /* best effort — clear locally regardless */
  }
  logAudit({ category: AUDIT_CATEGORIES.SECURITY_LOGIN, action: "User logged out" });
  clearSession();
}

// OAuth: the app does a full-page redirect here; the backend's success handler
// redirects back with the same LoginResponse JSON envelope, which the callback
// route reads.
export function oauthAuthorizeUrl(provider) {
  const base = API_BASE.startsWith("http") ? API_BASE : window.location.origin + API_BASE;
  return `${base.replace(/\/$/, "")}/auth/oauth2/authorize/${provider}`;
}

// ---- registration (Decision D2 "Hybrid": register -> verify email -> login -> checkout) ----

export async function registerStudent(payload) {
  const res = await apiClient.post("/auth/register", {
    fullName: payload.name || payload.fullName,
    email: payload.email,
    phone: payload.phone || undefined,
    password: payload.password,
    githubUsername: payload.githubUsername || undefined,
  });
  return { registered: true, uuid: res.uuid, email: res.email, needsEmailVerification: true };
}

export async function verifyEmail(token) {
  await apiClient.post("/auth/verify-email", { token });
  return { verified: true };
}

// Re-send the verification link. The backend responds identically whether or not the
// address maps to an unverified account (no account-enumeration), so the UI can only
// ever say "if that account needs verifying, a new link is on its way".
export async function resendVerificationEmail(email) {
  await apiClient.post("/auth/resend-verification", { email });
  return { sent: true };
}

export async function registerClient(payload) {
  const res = await apiClient.post("/auth/register/client", {
    fullName: payload.name || payload.fullName,
    email: payload.email,
    phone: payload.phone,
    password: payload.password,
    companyName: payload.companyName || payload.company || payload.name,
    industry: payload.industry || undefined,
  });
  return { registered: true, uuid: res.uuid, pendingApproval: true };
}

// ---- password reset ---------------------------------------------------

export async function requestPasswordReset(email) {
  await apiClient.post("/auth/password/forgot", { email });
  return { message: `If an account exists for ${email}, a reset link has been sent.` };
}

export async function resetPassword(token, newPassword) {
  await apiClient.post("/auth/password/reset", { token, newPassword });
  return { reset: true };
}

// ---- admin: client approval queue -----------------------------------

export async function getPendingClients(status = "PENDING_APPROVAL") {
  const page = await apiClient.get("/admin/client-requests", { status });
  return page && page.content ? page.content : [];
}

export async function setClientApproval(uuid, approve, reason) {
  const path = `/admin/client-requests/${uuid}/${approve ? "approve" : "reject"}`;
  return apiClient.post(path, approve ? undefined : { reason: reason || "Not approved" });
}

// ---- admin: staff invite ------------------------------------------------

export async function inviteStaffMember({ name, fullName, email, phone, role, roles }) {
  const backendRoles =
    roles && roles.length
      ? roles
      : [FE_ROLE_TO_BACKEND[role]].filter(Boolean);
  const created = await apiClient.post("/admin/users", {
    fullName: fullName || name,
    email,
    phone: phone || undefined,
    roles: backendRoles,
  });
  return { user: created };
}

export async function resendStaffInvite(userUuid) {
  return apiClient.post(`/admin/users/${userUuid}/resend-invite`);
}
