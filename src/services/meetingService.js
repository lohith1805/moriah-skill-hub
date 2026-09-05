import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// "Client Pre-Project Discussions" — the invitee's own read-only view, shared
// across whichever portal an invited person happens to be in (Developer,
// Admin — BA already has full scheduling access under /ba/meetings).
//   GET /api/v1/meetings/my
// The scheduler decides who's invited (see baService.js's role -> employee
// picker); this just lists whatever the caller has been checked into.
// ---------------------------------------------------------------------------

function toInstant(iso) {
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
  const { date, time } = toInstant(m.scheduledAt);
  return {
    id: m.id,
    title: m.title,
    agenda: m.agenda || "",
    clientProjectId: m.clientProjectId ?? null,
    date,
    time,
    meetLink: m.location || "",
    status: m.status,
    momNotes: m.minutes || "",
    createdByUuid: m.createdByUuid || "",
    attendees: (m.attendees || []).map((a) => ({ uuid: a.uuid, fullName: a.fullName, email: a.email })),
  };
}

export async function getMyMeetings() {
  const res = await apiClient.get("/meetings/my", { size: 100 });
  const rows = Array.isArray(res) ? res : res?.content ?? [];
  return rows.map(toFeMeeting);
}
