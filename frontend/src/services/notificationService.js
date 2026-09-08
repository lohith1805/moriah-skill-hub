// In-app notifications feed (backend gap B1.5).
//   GET  /api/v1/notifications            (paginated, ?unreadOnly)
//   GET  /api/v1/notifications/unread-count
//   PUT  /api/v1/notifications/{id}/read
//   PUT  /api/v1/notifications/read-all
//
// Backend rows are { id, templateCode, payload:{...}, read, readAt, createdAt }.
// The UI wants { id, title, body, time, read, tone } — mapped here.
//
// Some backend templates put a ready "title"/"body" in the payload (MEETING_INVITE,
// HR_DOCUMENT_REJECTED, ONBOARDING_COMPLETE, …); others store only structured fields
// (PIP_TRIGGERED -> {ruleCode,severity}, BATCH_ALLOCATED -> {batchName,trackCode}, …).
// TEMPLATE_RENDERERS turns the structured ones into human copy so the bell never shows a
// bare "Pip Triggered" with an empty body.

import { apiClient } from "./apiClient";

function humanize(code) {
  if (!code) return "";
  return String(code)
    .toLowerCase()
    .split(/[_\s]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

const PIP_RULE_LABEL = {
  ATTENDANCE_LOW: "low attendance",
  PROJECT_DELAY: "overdue project tasks",
  ASSIGNMENT_MISSED: "missed assignments",
  QUIZ_FAILURE: "quiz scores below the pass mark",
  REVIEW_FAILED: "an unsatisfactory weekly review",
  TASK_ABANDONED: "no recent activity",
};

// templateCode -> ({payload}) => { title, body, tone }
const TEMPLATE_RENDERERS = {
  PIP_TRIGGERED: (p) => ({
    title: "Performance Improvement Plan",
    body:
      `A PIP was triggered${p.severity ? ` (${String(p.severity).toLowerCase()} severity)` : ""}` +
      `${p.ruleCode ? ` for ${PIP_RULE_LABEL[p.ruleCode] || humanize(p.ruleCode)}` : ""}.`,
    tone: "warning",
  }),
  BATCH_ALLOCATED: (p) => ({
    title: "You've been placed in a batch",
    body:
      `${p.batchName || "Your batch"}${p.trackCode ? ` · ${p.trackCode}` : ""}` +
      `${p.startDate ? ` — starts ${p.startDate}` : ""}.`,
    tone: "success",
  }),
  BATCH_PLACEMENT_PENDING: (p) => ({
    title: "Finding you a batch",
    body: p.message || `We're placing you in a ${p.trackCode || ""} batch and will let you know.`,
    tone: "info",
  }),
  BATCH_ALLOCATION_PENDING: (p) => ({
    title: "Student awaiting batch allocation",
    body: `${p.studentName || "A student"} is waiting for a ${p.trackCode || ""} batch.`,
    tone: "info",
  }),
  BATCH_ALLOCATION_PENDING_PM: (p) => ({
    title: "Student awaiting batch allocation",
    body: `${p.studentName || "A student"} is waiting for a ${p.trackCode || ""} batch.`,
    tone: "info",
  }),
  SUBSCRIPTION_ACTIVATED: (p) => ({
    title: "Subscription active",
    body: `Your ${p.planName || "subscription"} is active${p.startDate ? `, starting ${p.startDate}` : ""}.`,
    tone: "success",
  }),
  PAYSLIP_READY: (p) => ({
    title: "Payslip ready",
    body: `Your payslip for ${p.periodMonth || "the latest period"} is ready` +
      `${p.netAmount ? ` — net ₹${Number(p.netAmount).toLocaleString("en-IN")}` : ""}. ` +
      `Download it from My Payslips.`,
    tone: "success",
  }),
  // Placement pipeline hand-off. The backend sends one code with an `audience`
  // tag ("candidate" | "client" | "hr"); the copy is phrased for the reader.
  PLACEMENT_STAGE_CHANGED: (p) => {
    const cand = p.candidateName || "the candidate";
    const client = p.clientName || "the recruiting company";
    const byStage = {
      TECHNICAL_SCHEDULED: { candidate: ["Technical interview scheduled", `${client} has scheduled your technical interview.`] },
      TECHNICAL_APPROVED: { hr: ["Candidate cleared the technical round", `${cand} passed ${client}'s technical round — schedule the HR round.`] },
      HR_SCHEDULED: { candidate: ["HR interview scheduled", `Your HR interview for the ${client} placement has been scheduled.`] },
      HR_APPROVED: { candidate: ["You cleared the HR round", `${client} cleared you at the HR round — document verification is next.`] },
      DOCUMENT_VERIFICATION: { candidate: ["Upload your documents", `Upload your documents for the ${client} placement so HR can verify them.`] },
      OFFER_CREATED: { client: ["Offer letter ready to sign", `HR has prepared the offer letter for ${cand}. Review and add your signature.`] },
      CLIENT_SIGNED: { candidate: ["Your offer letter is ready", `${client} has signed your offer letter — review it and accept or decline.`] },
      STUDENT_SIGNED: { hr: ["Candidate accepted the offer", `${cand} accepted ${client}'s offer — finalise the placement.`] },
      PLACED: {
        candidate: ["You're placed! 🎉", `Your placement with ${client} is finalised. Congratulations!`],
        client: ["Placement finalised", `${cand}'s placement is finalised.`],
      },
      REJECTED: {
        candidate: ["Placement closed", `Your placement process with ${client} has been closed.`],
        client: ["Placement closed", `The placement process for ${cand} has been closed.`],
        hr: ["Placement rejected", `The placement between ${cand} and ${client} was rejected.`],
      },
    };
    const entry =
      byStage[p.stage]?.[p.audience] ||
      byStage[p.stage]?.candidate ||
      [humanize(p.stage) || "Placement update", `Placement update for ${cand}.`];
    return {
      title: entry[0],
      body: entry[1],
      tone: p.stage === "REJECTED" ? "warning" : p.stage === "PLACED" ? "success" : "info",
    };
  },
};

function render(n) {
  const p = n.payload || {};
  if (p.title || p.body || p.message || p.text || p.subject) {
    return {
      title: p.title || p.subject || humanize(n.templateCode) || "Notification",
      body: p.body || p.message || p.text || "",
      tone: p.tone,
    };
  }
  const r = TEMPLATE_RENDERERS[n.templateCode];
  if (r) return r(p);
  return { title: humanize(n.templateCode) || "Notification", body: "" };
}

function toFeNotification(n) {
  const { title, body, tone } = render(n);
  return {
    id: n.id,
    title,
    body,
    tone: tone || "info",
    time: n.createdAt,
    read: !!n.read,
    templateCode: n.templateCode,
    payload: n.payload || {},
  };
}

export async function getNotifications({ page = 0, size = 30, unreadOnly = false } = {}) {
  const res = await apiClient.get("/notifications", { page, size, unreadOnly });
  return (res && res.content ? res.content : []).map(toFeNotification);
}

export async function getUnreadCount() {
  const res = await apiClient.get("/notifications/unread-count");
  return res ? res.unreadCount ?? 0 : 0;
}

export async function markAsRead(id) {
  const n = await apiClient.put(`/notifications/${id}/read`);
  return toFeNotification(n);
}

export async function markAllAsRead() {
  const res = await apiClient.put("/notifications/read-all");
  return res ? res.markedRead ?? 0 : 0;
}
