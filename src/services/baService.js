import { mockRequest } from "./apiClient";

const DEFAULT_REQUIREMENT_DOCS = [];

const DEFAULT_RESOURCE_PLANS = [];

const DEFAULT_MEETINGS = [];

export async function getDocuments() {
  try {
    const raw = localStorage.getItem("msh_ba_documents");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_ba_documents", JSON.stringify(DEFAULT_REQUIREMENT_DOCS));
  return mockRequest(DEFAULT_REQUIREMENT_DOCS);
}

export async function saveDocuments(docs) {
  localStorage.setItem("msh_ba_documents", JSON.stringify(docs));
}

export async function getSprintResourcePlan() {
  try {
    const raw = localStorage.getItem("msh_ba_resource_plans");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_ba_resource_plans", JSON.stringify(DEFAULT_RESOURCE_PLANS));
  return mockRequest(DEFAULT_RESOURCE_PLANS);
}

export async function saveSprintResourcePlan(plans) {
  localStorage.setItem("msh_ba_resource_plans", JSON.stringify(plans));
}

// ---------------------------------------------------------------------------
// Roster pickers for Resource Planning — lets a BA pick real, named students
// and developers (not just a headcount number) when staffing a plan. Reads
// the same "mORIAH_REGISTERED_USERS" list Admin's User Management and
// Registration write to, filtered by role, so it's always the live roster
// of actual accounts on the platform.
// ---------------------------------------------------------------------------
export async function getAssignableStudents() {
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const users = raw ? JSON.parse(raw) : [];
    return mockRequest(users.filter((u) => u.role === "student").map((u) => ({ id: u.id, name: u.name, batch: u.batch || "Unassigned" })));
  } catch (e) {
    return mockRequest([]);
  }
}

export async function getAssignableDevelopers() {
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const users = raw ? JSON.parse(raw) : [];
    return mockRequest(users.filter((u) => u.role === "developer").map((u) => ({ id: u.id, name: u.name })));
  } catch (e) {
    return mockRequest([]);
  }
}

export async function getMeetings() {
  try {
    const raw = localStorage.getItem("msh_ba_meetings");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_ba_meetings", JSON.stringify(DEFAULT_MEETINGS));
  return mockRequest(DEFAULT_MEETINGS);
}

export async function saveMeetings(meetings) {
  localStorage.setItem("msh_ba_meetings", JSON.stringify(meetings));
}
