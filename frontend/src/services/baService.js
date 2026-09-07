import { mockRequest, apiClient } from "./apiClient";

const DEFAULT_REQUIREMENT_DOCS = [];

const DEFAULT_RESOURCE_PLANS = [];

// ---------------------------------------------------------------------------
// Sprint resource plans are STILL the localStorage mock — there is no
// resource-plan endpoint yet. BA meetings ARE wired (B1.14). Requirement
// documents (BRD/SRS/FRS/USER_STORY, below) are ALSO now wired to the real
// backend — GET/POST /api/v1/ba/documents, GET /ba/documents/{id},
// PUT /ba/documents/{id}/approve. The legacy getDocuments()/saveDocuments()
// mock right below is kept only because ba/Dashboard.jsx and
// ba/ResourcePlanning.jsx still read it for their own unrelated project
// pickers — not used by the Requirements Authoring Studio anymore.
// ---------------------------------------------------------------------------

const REQ_DOC_STATUS_TO_FE = { IN_REVIEW: "In Review", APPROVED: "Approved", REJECTED: "Rejected" };
const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

function toFeRequirementDoc(d) {
  return {
    id: d.id,
    clientProjectId: d.clientProjectId,
    docType: d.docType, // BRD | SRS | FRS | USER_STORY
    title: d.title,
    version: d.version,
    status: d.status,
    statusLabel: REQ_DOC_STATUS_TO_FE[d.status] || d.status,
    authoredByUuid: d.authoredByUuid || "",
    authoredByName: d.authoredByFullName || "",
    approvedByUuid: d.approvedByUuid || "",
    approvedByName: d.approvedByFullName || "",
    devReviewedByName: d.devReviewedByFullName || "",
    devReviewedAt: d.devReviewedAt || null,
    rejectedByName: d.rejectedByFullName || "",
    rejectedAt: d.rejectedAt || null,
    rejectionReason: d.rejectionReason || "",
    // One required sign-off slot per role this docType needs (CLIENT/BUSINESS_ANALYST/
    // DEVELOPER for BRD/FRS; BUSINESS_ANALYST/DEVELOPER only for SRS/USER_STORY) —
    // see RequirementDocumentApprovalService on the backend.
    approvals: (d.approvals || []).map((a) => ({
      role: a.approverRole,
      approvedByUuid: a.approvedByUuid || null,
      approvedByName: a.approvedByFullName || "",
      approvedAt: a.approvedAt || null,
      pending: !a.approvedByUuid,
    })),
  };
}

// GET /api/v1/ba/documents — optional clientProjectId / status ("IN_REVIEW" | "APPROVED") filters.
export async function getRequirementDocuments({ clientProjectId, status } = {}) {
  const res = await apiClient.get("/ba/documents", {
    clientProjectId: clientProjectId || undefined,
    status: status || undefined,
    size: 100,
  });
  return asRows(res).map(toFeRequirementDoc);
}

// GET /api/v1/ba/documents/{id} — full content, for re-opening a document already authored.
export async function getRequirementDocumentDetail(id) {
  const d = await apiClient.get(`/ba/documents/${id}`);
  return { ...toFeRequirementDoc(d), content: d.content || "" };
}

// POST /api/v1/ba/documents — always lands IN_REVIEW; version is server-computed
// (max existing version for this clientProjectId+docType, +1).
export async function createRequirementDocument({ clientProjectId, docType, title, content }) {
  const d = await apiClient.post("/ba/documents", {
    clientProjectId: Number(clientProjectId),
    docType,
    title,
    content,
  });
  return toFeRequirementDoc(d);
}

// PUT /api/v1/ba/documents/{id}/approve — IN_REVIEW -> APPROVED.
export async function approveRequirementDocument(id) {
  const d = await apiClient.put(`/ba/documents/${id}/approve`, {});
  return toFeRequirementDoc(d);
}

// PUT /api/v1/ba/documents/{id}/reject — IN_REVIEW -> REJECTED. reason is required; kills the
// whole document immediately regardless of any other slot already signed off. The author
// resubmits as a new version via POST /ba/documents — this one is never edited in place.
export async function rejectRequirementDocument(id, reason) {
  const d = await apiClient.put(`/ba/documents/${id}/reject`, { reason });
  return toFeRequirementDoc(d);
}

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

// ---------------------------------------------------------------------------
// BA meetings — WIRED to the backend (B1.14: GET/POST/PUT/DELETE
// /api/v1/ba/meetings, BUSINESS_ANALYST / ADMIN). DELETE -> status CANCELLED.
// The backend record has no `type` / `client name` / `attendees` fields, so
// those three UI-only bits are kept in a localStorage sidecar keyed by id.
// ---------------------------------------------------------------------------

const META_KEY = "msh_ba_meeting_meta";
const readMeta = () => {
  try {
    return JSON.parse(localStorage.getItem(META_KEY) || "{}");
  } catch {
    return {};
  }
};
const writeMeta = (id, patch) => {
  const all = readMeta();
  all[id] = { ...(all[id] || {}), ...patch };
  localStorage.setItem(META_KEY, JSON.stringify(all));
};

// "2026-09-10" + "03:00 PM" -> ISO instant. Falls back to midnight / now.
// `timeStr` comes from a native <input type="time"> now — plain 24-hour "HH:mm", no AM/PM — but
// this still tolerates the old "hh:mm AM/PM" shape too, so a value round-tripped from an older
// meeting (or saveMeetingMinutes, which reuses whatever splitInstant last produced) parses either
// way instead of silently landing at the wrong hour.
function toInstant(dateStr, timeStr) {
  if (!dateStr) return new Date().toISOString();
  let hh = 15;
  let mm = 0;
  const raw = (timeStr || "").trim();
  const ampm = /^(\d{1,2}):(\d{2})\s*(AM|PM)$/i.exec(raw);
  const plain = /^(\d{1,2}):(\d{2})$/.exec(raw);
  if (ampm) {
    hh = Number(ampm[1]) % 12;
    mm = Number(ampm[2]);
    if (/pm/i.test(ampm[3])) hh += 12;
  } else if (plain) {
    hh = Number(plain[1]);
    mm = Number(plain[2]);
  }
  const d = new Date(`${dateStr}T00:00:00`);
  d.setHours(hh, mm, 0, 0);
  return d.toISOString();
}

// Returns time as plain 24-hour "HH:mm" — what <input type="time"> both accepts and displays.
function splitInstant(iso) {
  if (!iso) return { date: "", time: "" };
  const d = new Date(iso);
  const date = d.toISOString().slice(0, 10);
  const time = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  return { date, time };
}

// "15:00" -> "3:00 PM", for read-only display only — the underlying data stays 24-hour.
function formatTimeDisplay(time24) {
  const m = /^(\d{1,2}):(\d{2})$/.exec((time24 || "").trim());
  if (!m) return time24 || "";
  let h = Number(m[1]);
  const ap = h >= 12 ? "PM" : "AM";
  h = h % 12 || 12;
  return `${h}:${m[2]} ${ap}`;
}

function toFeMeeting(m) {
  if (!m) return null;
  const { date, time } = splitInstant(m.scheduledAt);
  const meta = readMeta()[m.id] || {};
  const namedAttendees = (m.attendees || []).map((a) => a.fullName);
  return {
    id: m.id,
    title: m.title,
    agenda: m.agenda || "",
    clientProjectId: m.clientProjectId ?? null,
    date,
    time,
    timeDisplay: formatTimeDisplay(time),
    scheduledAt: m.scheduledAt,
    durationMinutes: m.durationMinutes ?? null,
    meetLink: m.location || "",
    status: m.status || "SCHEDULED",
    momNotes: m.minutes || "",
    // Real, emailed invitees (B1.14 redesign) — a role -> employee checkbox pick, not free text.
    attendeeList: (m.attendees || []).map((a) => ({ uuid: a.uuid, fullName: a.fullName, email: a.email })),
    // UI-only sidecar fields (cosmetic ceremony type / free-text client label the backend has no
    // column for)
    type: meta.type || "Sprint Demo",
    client: meta.client || "",
    attendees: namedAttendees.length ? namedAttendees : meta.attendees || [],
  };
}

function toMeetingRequest(v, { includeStatus = false } = {}) {
  const body = {
    title: (v.title || "").trim(),
    agenda: v.agenda ? v.agenda.trim() : null,
    clientProjectId: v.clientProjectId ?? null,
    scheduledAt: v.scheduledAt || toInstant(v.date, v.time),
    durationMinutes: v.durationMinutes ? Number(v.durationMinutes) : 60,
    location: v.meetLink || null,
    // undefined (not sent) would still serialize as omitted key in JSON.stringify, but explicit
    // null here means "the caller passed no attendee list" -> backend leaves invites untouched
    // on update; a real (possibly empty) array means "replace wholesale."
    attendeeUuids: Array.isArray(v.attendeeUuids) ? v.attendeeUuids : undefined,
  };
  if (includeStatus) {
    body.status = v.status || "SCHEDULED";
    body.minutes = v.momNotes ? v.momNotes : null;
  }
  return body;
}

// GET /api/v1/ba/meetings/staff-directory?role= — the role -> employee picker's second dropdown.
// role must be BUSINESS_ANALYST, DEVELOPER, or ADMIN.
export async function getMeetingStaffDirectory(role) {
  const res = await apiClient.get("/ba/meetings/staff-directory", { role });
  return Array.isArray(res) ? res : [];
}

export async function getMeetings() {
  const res = await apiClient.get("/ba/meetings");
  const rows = Array.isArray(res) ? res : res?.content ?? [];
  return rows.map(toFeMeeting);
}

export async function createMeeting(input) {
  const created = await apiClient.post("/ba/meetings", toMeetingRequest(input));
  writeMeta(created.id, {
    type: input.type || "Sprint Demo",
    client: input.client || "",
  });
  return toFeMeeting(created);
}

export async function updateMeeting(id, input) {
  const updated = await apiClient.put(`/ba/meetings/${id}`, toMeetingRequest(input, { includeStatus: true }));
  if (input.type !== undefined || input.client !== undefined) {
    writeMeta(id, {
      ...(input.type !== undefined ? { type: input.type } : {}),
      ...(input.client !== undefined ? { client: input.client } : {}),
    });
  }
  return toFeMeeting(updated);
}

export async function saveMeetingMinutes(id, minutes, current) {
  return updateMeeting(id, { ...current, momNotes: minutes });
}

// DELETE = cancel (status -> CANCELLED) on the backend.
export async function deleteMeeting(id) {
  await apiClient.del(`/ba/meetings/${id}`);
  return true;
}

// POST /api/v1/meetings/{id}/note — any named attendee (or the scheduler), not just this BA,
// may add one once the meeting is COMPLETED. Shared with the invitee-side page
// (pages/shared/ClientPreProjectDiscussions.jsx via meetingService.js) — same endpoint either way.
export async function addMeetingNote(id, note) {
  const updated = await apiClient.post(`/meetings/${id}/note`, { note });
  return toFeMeeting(updated);
}
