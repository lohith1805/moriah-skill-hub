// Admin console, wired to the Spring Boot backend.
//   GET  /api/v1/admin/metrics/overview
//   GET  /api/v1/admin/users            (paginated; ?role &status)
//   GET  /api/v1/admin/users/{uuid}
//   PUT  /api/v1/admin/users/{uuid}            (profile: name/phone/github/linkedin)  [B1.17]
//   PUT  /api/v1/admin/users/{uuid}/status     ({status})
//   PUT  /api/v1/admin/users/{uuid}/roles      ({roles:[RoleCode]})
//   GET  /api/v1/plans
//   POST/PUT/DELETE /api/v1/admin/plans        [B1.13]
//   GET  /api/v1/admin/payments  + /summary + /{gatewayOrderId} + POST /{id}/refund  [B1.11]
//   GET  /api/v1/admin/audit
//   POST /api/v1/admin/exports/{report}
//   GET/POST/PUT/DELETE /api/v1/admin/coupons  [B1.12]

import { apiClient } from "./apiClient";
import {
  BACKEND_ROLE_TO_FE,
  BACKEND_STATUS_TO_FE,
  FE_ROLE_TO_BACKEND,
  PLAN_CODE_TO_FE,
} from "../utils/constants";
import { logAudit, AUDIT_CATEGORIES } from "../utils/auditLog";

// ---- metrics ---------------------------------------------------------

export async function getExecutiveMetrics() {
  const o = await apiClient.get("/admin/metrics/overview");
  const recent = o.recentRevenue || [];
  const lastMonth = recent.length ? recent[recent.length - 1] : null;
  const mrr = lastMonth ? Number(lastMonth.total ?? lastMonth.amount ?? 0) : 0;

  const funnel = o.leadFunnel || [];
  const funnelTotal = funnel.reduce((s, f) => s + Number(f.count || 0), 0);
  const enrolled = funnel
    .filter((f) => /ENROLLED/i.test(f.stage || f.status || ""))
    .reduce((s, f) => s + Number(f.count || 0), 0);

  return {
    mrr,
    arr: mrr * 12,
    activeStudents: Number(o.activeStudentCount || 0),
    avgAttendance: Number(o.avgAttendancePercent || 0),
    avgTaskCompletion: Number(o.avgTaskCompletionPercent || 0),
    avgQuizAverage: Number(o.avgQuizAveragePercent || 0),
    batchPassRate: Number(o.avgTaskCompletionPercent || 0),
    // The overview endpoint doesn't expose an on-PIP ratio; left at 0 until a
    // dedicated metric exists (or Dashboard.jsx drops the tile).
    pipRatio: 0,
    crmConversion: funnelTotal ? Math.round((enrolled / funnelTotal) * 100) : 0,
    velocityRatio: Number(o.overallVelocityRatio || 0),
    recentRevenue: recent,
    leadFunnel: funnel,
    serverStatus: "Operational",
  };
}

// ---- users ----------------------------------------------------------

function toFeUserRow(u) {
  return {
    id: u.uuid,
    uuid: u.uuid,
    name: u.fullName,
    email: u.email,
    phone: u.phone || "",
    githubUsername: u.githubUsername || "",
    linkedinUrl: u.linkedinUrl || "",
    role: (u.roles || []).map((r) => BACKEND_ROLE_TO_FE[r]).find(Boolean) || "student",
    roles: u.roles || [],
    status: BACKEND_STATUS_TO_FE[u.status] || "active",
    backendStatus: u.status,
    twoFactorEnabled: !!u.twoFactorEnabled,
    createdAt: u.createdAt,
  };
}

export async function getAllUsers({ page = 0, size = 50, role, status } = {}) {
  const res = await apiClient.get("/admin/users", {
    page,
    size,
    role: role ? FE_ROLE_TO_BACKEND[role] || role : undefined,
    status: status || undefined,
  });
  return (res && res.content ? res.content : []).map(toFeUserRow);
}

export async function getUser(uuid) {
  return toFeUserRow(await apiClient.get(`/admin/users/${uuid}`));
}

// Accepts the FE status label ("active"/"suspended"/...) or a backend UserStatus.
export async function updateUserStatus(uuid, status) {
  const backend =
    { active: "ACTIVE", suspended: "SUSPENDED", terminated: "TERMINATED" }[
      String(status).toLowerCase()
    ] || status;
  const updated = await apiClient.put(`/admin/users/${uuid}/status`, { status: backend });
  logAudit({
    category: AUDIT_CATEGORIES.ROLE_PERMISSION_CHANGE,
    severity: backend === "ACTIVE" ? "Info" : "Warning",
    action: `User status changed to ${backend}`,
    target: uuid,
  });
  return toFeUserRow(updated);
}

export async function updateUserRoles(uuid, feRoles) {
  const roles = (feRoles || []).map((r) => FE_ROLE_TO_BACKEND[r] || r);
  const updated = toFeUserRow(await apiClient.put(`/admin/users/${uuid}/roles`, { roles }));
  logAudit({
    category: AUDIT_CATEGORIES.ROLE_PERMISSION_CHANGE,
    action: `User roles set to [${roles.join(", ")}]`,
    target: uuid,
  });
  return updated;
}

// B1.17 — profile fields only (name / phone / github / linkedin).
export async function updateUserRecord(uuid, changes) {
  const body = {
    fullName: changes.name || changes.fullName,
    phone: changes.phone ?? "",
    githubUsername: changes.githubUsername ?? "",
    linkedinUrl: changes.linkedinUrl ?? "",
  };
  return toFeUserRow(await apiClient.put(`/admin/users/${uuid}`, body));
}

// No hard delete on the backend — an account is retired to TERMINATED.
export async function deleteUserRecord(uuid) {
  await apiClient.put(`/admin/users/${uuid}/status`, { status: "TERMINATED" });
  return { uuid };
}

// ---- plans (B1.13) -------------------------------------------------

function toFePlan(p) {
  return {
    code: PLAN_CODE_TO_FE[p.code] || String(p.code || "").toLowerCase(),
    backendCode: p.code,
    name: p.name,
    price: Number(p.priceInr ?? p.price ?? 0),
    tierRank: p.tierRank,
    durationDays: p.durationDays,
    mentorSupport: !!p.mentorSupport,
    allowsBatch: !!p.allowsBatch,
    allowsSprints: !!p.allowsSprints,
    allowsPip: !!p.allowsPip,
    allowsInternshipLetter: !!p.allowsInternshipLetter,
    allowsClientProject: !!p.allowsClientProject,
  };
}

export async function getPlans() {
  const list = await apiClient.get("/plans");
  return (Array.isArray(list) ? list : []).map(toFePlan);
}

export async function createPlan(payload) {
  return toFePlan(await apiClient.post("/admin/plans", payload));
}

export async function updatePlan(id, payload) {
  return toFePlan(await apiClient.put(`/admin/plans/${id}`, payload));
}

export async function deletePlan(id) {
  await apiClient.del(`/admin/plans/${id}`);
  return { id };
}

// ---- transactions + refunds (B1.11) ------------------------------

function toFeTransaction(p) {
  return {
    id: p.gatewayOrderId,
    gatewayOrderId: p.gatewayOrderId,
    gatewayPaymentId: p.gatewayPaymentId,
    student: p.userFullName,
    userUuid: p.userUuid,
    planId: p.planId,
    planCode: p.planCode || null,
    planName: p.planName || null,
    plan: p.planName || p.planCode || "—",
    track: p.trackCode,
    amount: Number(p.amount || 0),
    currency: p.currency,
    gateway: p.gateway,
    status: p.status,
    failureReason: p.failureReason,
    date: (p.capturedAt || p.createdAt || "").slice(0, 10),
    capturedAt: p.capturedAt,
    createdAt: p.createdAt,
    invoiceNumber: p.invoiceNumber || null,
    // ISSUED | PENDING | FAILED | PROCESSING | null
    invoiceStatus: p.invoiceStatus || null,
  };
}

export async function getTransactions({ page = 0, size = 50, status, gateway, userUuid } = {}) {
  const res = await apiClient.get("/admin/payments", { page, size, status, gateway, userUuid });
  return (res && res.content ? res.content : []).map(toFeTransaction);
}

// GET /api/v1/admin/payments/{gatewayOrderId}/invoice -> { url } (short-lived
// pre-signed link to the server-generated invoice PDF). 404 while the async
// invoice job hasn't produced it yet.
export async function getInvoicePdfUrl(gatewayOrderId) {
  const res = await apiClient.get(`/admin/payments/${gatewayOrderId}/invoice`);
  return res?.url || null;
}

export async function getTransactionSummary() {
  return apiClient.get("/admin/payments/summary");
}

export async function refundTransaction(gatewayOrderId, reason) {
  const out = toFeTransaction(
    await apiClient.post(`/admin/payments/${gatewayOrderId}/refund`, reason ? { reason } : undefined)
  );
  logAudit({
    category: AUDIT_CATEGORIES.FINANCIAL_TXN,
    severity: "Warning",
    action: `Refund issued${reason ? ` — ${reason}` : ""}`,
    target: gatewayOrderId,
  });
  return out;
}

// ---- audit --------------------------------------------------------

// The backend has no "category" or "severity" — bucket its action codes into
// the same six categories the client-side trail (utils/auditLog.js) uses so the
// admin page can filter both sources uniformly.
function categoryForAuditAction(action = "") {
  const a = String(action).toUpperCase();
  if (/LOGIN|LOGOUT|PASSWORD_RESET|REFRESH_TOKEN|STAFF_INVITE/.test(a)) return "SECURITY_LOGIN";
  if (/USER_ROLES|USER_STATUS|STAFF_INVITED|PORTAL_USER_PROVISIONED|REGISTRATION_(APPROVED|REJECTED)/.test(a))
    return "ROLE_PERMISSION_CHANGE";
  if (/PAYMENT|REFUND|COUPON|PAYROLL|INVOICE/.test(a)) return "FINANCIAL_TXN";
  if (/GRADUAT|CERTIFICATE|QUIZ|CODE_REVIEW|GRADE/.test(a)) return "GRADE_CHANGE";
  if (/PIP_/.test(a)) return "PIP_STATUS_CHANGE";
  if (/LETTER|DOCUMENT|REQUIREMENT_DOCUMENT/.test(a)) return "DOCUMENT_GEN";
  return "";
}

// Backend AuditLogResponse -> the flat row shape /admin/audit-logs renders.
function toFeAuditRow(r) {
  return {
    id: r.id,
    timestamp: r.createdAt,
    category: categoryForAuditAction(r.action),
    severity: "Info",
    actor: r.userUuid || "System",
    action: (r.action || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()),
    target: r.entityUuid || (r.entityType ? `${r.entityType}#${r.entityId ?? "?"}` : "—"),
    ip: r.ipAddress || "—",
    source: "server",
  };
}

export async function getAuditLogs({ page = 0, size = 50, action, entityType, userUuid } = {}) {
  const res = await apiClient.get("/admin/audit", { page, size, action, entityType, userUuid });
  const rows = res && res.content ? res.content : [];
  return rows.map(toFeAuditRow);
}

// ---- exports -----------------------------------------------------

export async function exportReport(report) {
  // Returns { downloadUrl, ... } from the backend.
  return apiClient.post(`/admin/exports/${report}`);
}

// ---- coupons (B1.12) -------------------------------------------

export async function getCoupons({ page = 0, size = 50 } = {}) {
  const res = await apiClient.get("/admin/coupons", { page, size });
  return res && res.content ? res.content : [];
}
export async function createCoupon(payload) {
  return apiClient.post("/admin/coupons", payload);
}
export async function updateCoupon(code, payload) {
  return apiClient.put(`/admin/coupons/${code}`, payload);
}
export async function deleteCoupon(code) {
  await apiClient.del(`/admin/coupons/${code}`);
  return { code };
}
