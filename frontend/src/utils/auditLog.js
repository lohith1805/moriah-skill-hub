// Client-side audit trail (localStorage, key `msh_audit_logs`).
//
// This is a COMPLEMENT to the backend's real audit log (GET /api/v1/admin/audit,
// surfaced on /admin/audit-logs) — it records browser-only signals the server
// never sees (a failed 2FA code entry, a login attempt that 401s before a
// session exists, an admin action the UI fired, …). The admin Audit Logs page
// merges both sources. Nothing here is authoritative or tamper-proof.
export const AUDIT_CATEGORIES = {
  SECURITY_LOGIN: "SECURITY_LOGIN",
  ROLE_PERMISSION_CHANGE: "ROLE_PERMISSION_CHANGE",
  FINANCIAL_TXN: "FINANCIAL_TXN",
  GRADE_CHANGE: "GRADE_CHANGE",
  DOCUMENT_GEN: "DOCUMENT_GEN",
  PIP_STATUS_CHANGE: "PIP_STATUS_CHANGE",
};

const STORAGE_KEY = "msh_audit_logs";
// Keep the trail bounded so localStorage doesn't grow without limit in a
// long-running session; oldest entries roll off first.
const MAX_LOGS = 500;

function currentActor() {
  try {
    const raw = localStorage.getItem("msh_user");
    if (raw) {
      const u = JSON.parse(raw);
      return u.name || u.email || "Unknown user";
    }
  } catch (e) {
    // ignore malformed session
  }
  return "System";
}

/**
 * Append one entry to the client-side audit trail.
 * @param {Object} entry
 * @param {string} entry.category - one of AUDIT_CATEGORIES
 * @param {"Info"|"Warning"|"Critical"} [entry.severity]
 * @param {string} [entry.actor] - defaults to the currently signed-in user
 * @param {string} entry.action - short human-readable description
 * @param {string} [entry.target] - resource affected (email, txn id, uuid, …)
 * @param {string} [entry.ip] - defaults to a loopback placeholder (no real
 *   client IP is available in the browser)
 * @returns {Object|null} the written entry, or null if persistence failed
 */
export function logAudit({ category, severity = "Info", actor, action, target, ip }) {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const logs = raw ? JSON.parse(raw) : [];
    const entry = {
      id: `log_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      timestamp: new Date().toISOString(),
      category,
      severity,
      actor: actor || currentActor(),
      action,
      target: target || "—",
      ip: ip || "127.0.0.1",
      source: "local",
    };
    logs.unshift(entry);
    if (logs.length > MAX_LOGS) logs.length = MAX_LOGS;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(logs));
    return entry;
  } catch (e) {
    console.warn("Failed to write audit log:", e);
    return null;
  }
}

/** Read the client-side trail (newest first). Safe on a cleared/absent store. */
export function readLocalAuditLogs() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const logs = raw ? JSON.parse(raw) : [];
    return Array.isArray(logs) ? logs.map((l) => ({ source: "local", ...l })) : [];
  } catch (e) {
    return [];
  }
}
