import { mockRequest, apiClient } from "./apiClient";

const DEFAULT_REQUIREMENT_DOCS = [];

const DEFAULT_RESOURCE_PLANS = [];

// ---------------------------------------------------------------------------
// Requirement docs + sprint resource plans are STILL the localStorage mock:
// the backend BA-documents API takes plain-text `content` only (no file
// attachments), and there is no resource-plan endpoint yet — both need a
// Part B pass before their pages can migrate.
// BA meetings (below) ARE wired to the backend (B1.14).
// ---------------------------------------------------------------------------

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
function toInstant(dateStr, timeStr) {
  if (!dateStr) return new Date().toISOString();
  let hh = 15;
  let mm = 0;
  const m = /^(\d{1,2}):(\d{2})\s*(AM|PM)?$/i.exec((timeStr || "").trim());
  if (m) {
    hh = Number(m[1]) % 12;
    mm = Number(m[2]);
    if (/pm/i.test(m[3] || "")) hh += 12;
  }
  const d = new Date(`${dateStr}T00:00:00`);
  d.setHours(hh, mm, 0, 0);
  return d.toISOString();
}

function splitInstant(iso) {
  if (!iso) return { date: "", time: "" };
  const d = new Date(iso);
  const date = d.toISOString().slice(0, 10);
  let h = d.getHours();
  const ap = h >= 12 ? "PM" : "AM";
  h = h % 12 || 12;
  const time = `${String(h).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")} ${ap}`;
  return { date, time };
}

function toFeMeeting(m) {
  if (!m) return null;
  const { date, time } = splitInstant(m.scheduledAt);
  const meta = readMeta()[m.id] || {};
  return {
    id: m.id,
    title: m.title,
    agenda: m.agenda || "",
    clientProjectId: m.clientProjectId ?? null,
    date,
    time,
    scheduledAt: m.scheduledAt,
    durationMinutes: m.durationMinutes ?? null,
    meetLink: m.location || "",
    status: m.status || "SCHEDULED",
    momNotes: m.minutes || "",
    // UI-only sidecar fields
    type: meta.type || "Sprint Demo",
    client: meta.client || "",
    attendees: meta.attendees || [],
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
  };
  if (includeStatus) {
    body.status = v.status || "SCHEDULED";
    body.minutes = v.momNotes ? v.momNotes : null;
  }
  return body;
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
    attendees: Array.isArray(input.attendees)
      ? input.attendees
      : String(input.attendees || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
  });
  return toFeMeeting(created);
}

export async function updateMeeting(id, input) {
  const updated = await apiClient.put(`/ba/meetings/${id}`, toMeetingRequest(input, { includeStatus: true }));
  if (input.type !== undefined || input.client !== undefined || input.attendees !== undefined) {
    writeMeta(id, {
      ...(input.type !== undefined ? { type: input.type } : {}),
      ...(input.client !== undefined ? { client: input.client } : {}),
      ...(input.attendees !== undefined
        ? {
            attendees: Array.isArray(input.attendees)
              ? input.attendees
              : String(input.attendees || "")
                  .split(",")
                  .map((s) => s.trim())
                  .filter(Boolean),
          }
        : {}),
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
