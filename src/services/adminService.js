import { mockRequest } from "./apiClient";
import { TRANSACTIONS } from "./mockData";
import { SUBSCRIPTION_PLANS, ROLES, ACCOUNT_STATUS } from "../utils/constants";

const STATUS_LABELS = {
  [ACCOUNT_STATUS.ACTIVE]: "Active",
  [ACCOUNT_STATUS.PENDING_APPROVAL]: "Pending Approval",
  [ACCOUNT_STATUS.REJECTED]: "Rejected",
  [ACCOUNT_STATUS.INVITED]: "Invited",
};

const ADMIN_DEFAULT_PERMISSIONS = [
  "read_dashboard", "manage_users", "billing_plans", "view_audit",
  "export_reports", "manage_projects", "manage_payroll", "approve_graduation", "pip_oversight"
];

const DEFAULT_AUDIT_LOGS = [];

export async function getExecutiveMetrics() {
  let activeStudents = 0;
  let mrr = 0;
  let crmConversion = 0;
  let batchPassRate = 0;
  let pipRatio = 0;

  try {
    const rawUsers = localStorage.getItem("mORIAH_REGISTERED_USERS");
    if (rawUsers) {
      const users = JSON.parse(rawUsers);
      activeStudents = users.filter((u) => u.role === "student" && u.accountStatus === "active").length;
    }

    const rawTx = localStorage.getItem("msh_transactions");
    const txs = rawTx ? JSON.parse(rawTx) : [];
    const validTxs = txs.filter((t) => t.status === "Success");
    if (validTxs.length > 0) {
      mrr = validTxs.reduce((sum, t) => sum + (t.amount || 0), 0);
    }

    const rawLeads = localStorage.getItem("msh_crm_leads");
    if (rawLeads) {
      const leads = JSON.parse(rawLeads);
      const won = leads.filter((l) => l.stage === "Won / Enrolled" || l.stage === "Enrolled");
      if (leads.length > 0) {
        crmConversion = Math.round((won.length / leads.length) * 100);
      }
    }

    const rawPip = localStorage.getItem("msh_pip_records");
    if (rawPip && activeStudents > 0) {
      const pipRecords = JSON.parse(rawPip);
      const activePip = pipRecords.filter((p) => p.status !== "Resolved");
      pipRatio = Math.round((activePip.length / activeStudents) * 1000) / 10;
    }
  } catch (e) {}

  const arr = mrr * 12;

  return mockRequest({
    mrr,
    arr,
    activeStudents,
    batchPassRate,
    pipRatio,
    crmConversion,
    serverStatus: "Operational",
  });
}

// The Admin Users table's source of truth for WHO exists is the same
// "mORIAH_REGISTERED_USERS" list that Login/Register/Invite/Accept-Invite
// all read and write (see authService.js) — so a newly invited staff
// member, a student who just registered, or someone who just activated
// their invite link shows up here immediately, without any separate sync
// step. Per-user admin customizations (granular permission grants, a
// manually toggled Suspended/Muted status) are layered on top from
// "msh_users_list", keyed by user id, so an admin's edits survive even
// though identity/status fields keep coming from the registered-user list.
export async function getAllUsers() {
  let registered = [];
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    if (raw) registered = JSON.parse(raw);
  } catch (e) {}

  let overridesById = {};
  try {
    const raw = localStorage.getItem("msh_users_list");
    if (raw) {
      JSON.parse(raw).forEach((u) => {
        overridesById[u.id] = u;
      });
    }
  } catch (e) {}

  const merged = registered.map((u) => {
    const override = overridesById[u.id];
    const defaultPermissions = u.role === ROLES.ADMIN ? ADMIN_DEFAULT_PERMISSIONS : ["read_dashboard"];
    return {
      id: u.id,
      name: u.name,
      email: u.email,
      role: u.role,
      company: u.company,
      avatarColor: u.avatarColor,
      // A manual admin override (e.g. Suspended/Muted) always wins; otherwise
      // fall back to the account's real lifecycle status.
      status: override?.status || STATUS_LABELS[u.accountStatus] || "Active",
      permissions: override?.permissions || defaultPermissions,
    };
  });

  return mockRequest(merged);
}

export async function updateUserStatus(userId, status) {
  return { userId, status };
}

// Edits from Admin's "Edit Account" modal touch identity fields (name,
// email, role) that live on the real registered-user record, not on the
// admin-only override — otherwise they'd be silently discarded the next
// time getAllUsers() refreshes from the registered-user list.
export async function updateUserRecord(userId, changes) {
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const list = raw ? JSON.parse(raw) : [];
    const updated = list.map((u) => (u.id === userId ? { ...u, ...changes } : u));
    localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(updated));
  } catch (e) {}
  return { userId, ...changes };
}

// Permanently removes a user from the registered-user list (the source of
// truth getAllUsers() reads from) plus any admin override row keyed by
// their id, so a deleted account doesn't reappear on the next refresh.
export async function deleteUserRecord(userId) {
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const list = raw ? JSON.parse(raw) : [];
    localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list.filter((u) => u.id !== userId)));
  } catch (e) {}

  try {
    const raw = localStorage.getItem("msh_users_list");
    const list = raw ? JSON.parse(raw) : [];
    localStorage.setItem("msh_users_list", JSON.stringify(list.filter((u) => u.id !== userId)));
  } catch (e) {}

  return { userId };
}

export async function getPlans() {
  try {
    const raw = localStorage.getItem("msh_subscription_plans");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  return mockRequest(SUBSCRIPTION_PLANS);
}

export async function getTransactions() {
  try {
    const raw = localStorage.getItem("msh_transactions");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  return mockRequest(TRANSACTIONS);
}

export async function getAuditLogs() {
  try {
    const raw = localStorage.getItem("msh_audit_logs");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_audit_logs", JSON.stringify(DEFAULT_AUDIT_LOGS));
  return mockRequest(DEFAULT_AUDIT_LOGS);
}

export async function saveAuditLogs(logs) {
  localStorage.setItem("msh_audit_logs", JSON.stringify(logs));
}

export async function exportReport(format) {
  await mockRequest(null, { delay: 400 });
  return { format, generatedAt: new Date().toISOString() };
}