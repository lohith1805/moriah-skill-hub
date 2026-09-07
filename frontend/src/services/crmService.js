import Papa from "papaparse";
import * as XLSX from "xlsx";
import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// CRM — fully WIRED to the backend crm/ module.
//   Campaigns : GET/POST/PUT/DELETE /api/v1/leads/campaigns   (DELETE = deactivate)
//   Leads     : GET/POST /api/v1/leads, GET/PUT/DELETE /api/v1/leads/{id},
//               PUT /api/v1/leads/{id}/status, GET/POST /api/v1/leads/{id}/activities
//               (DELETE = soft archive; the row + its history stay)
//   Targets   : GET /api/v1/leads/targets/me, GET /api/v1/leads/targets/leaderboard
// All lead endpoints are LEAD_GEN / ADMIN only. The public landing-page capture
// form on pages/public/Home.jsx still writes localStorage — there is no
// unauthenticated inbound-lead endpoint yet.
// ---------------------------------------------------------------------------

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

// Backend LeadCampaignChannel — the UI shows the label, stores the enum.
export const CAMPAIGN_CHANNELS = [
  { value: "EMAIL", label: "Email" },
  { value: "SOCIAL", label: "Social" },
  { value: "EVENT", label: "Event" },
  { value: "REFERRAL", label: "Referral" },
  { value: "PAID_ADS", label: "Paid Ads" },
  { value: "WEBINAR", label: "Webinar" },
];
export const CAMPAIGN_STATUSES = ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"];

const CHANNEL_LABEL = Object.fromEntries(CAMPAIGN_CHANNELS.map((c) => [c.value, c.label]));

function toFeCampaign(c) {
  if (!c) return null;
  return {
    id: c.id,
    name: c.name,
    channel: c.channel,
    channelLabel: CHANNEL_LABEL[c.channel] || c.channel,
    description: c.description || "",
    startDate: c.startDate || "",
    endDate: c.endDate || "",
    budget: c.budget != null ? Number(c.budget) : null,
    targetLeads: c.targetLeads ?? null,
    status: c.status || "PLANNED",
    leadIds: Array.isArray(c.leadIds) ? c.leadIds : [],
    createdByUuid: c.createdByUuid || null,
    createdAt: c.createdAt || null,
    updatedAt: c.updatedAt || null,
  };
}

// FE form values -> backend request body. `startDate` is required by the API;
// default it to today so a minimal "just a name + channel" create still works.
function toCampaignRequest(v, { includeStatus = false } = {}) {
  const body = {
    name: (v.name || "").trim(),
    channel: v.channel,
    description: v.description ? v.description.trim() : null,
    startDate: v.startDate || new Date().toISOString().slice(0, 10),
    endDate: v.endDate || null,
    budget: v.budget === "" || v.budget == null ? null : Number(v.budget),
    targetLeads: v.targetLeads === "" || v.targetLeads == null ? null : Number(v.targetLeads),
    // The audience picked at creation (or re-picked on edit) — always sent as an explicit
    // array, even empty, so an edit that didn't touch the audience field just re-sends the
    // same set rather than silently clearing it.
    leadIds: Array.isArray(v.leadIds) ? v.leadIds.map(Number) : [],
  };
  if (includeStatus) body.status = v.status || "PLANNED";
  return body;
}

export async function getCampaigns({ status } = {}) {
  const res = await apiClient.get("/leads/campaigns", status ? { status } : undefined);
  const rows = Array.isArray(res) ? res : res?.content ?? [];
  return rows.map(toFeCampaign);
}

export async function createCampaign(input) {
  return toFeCampaign(await apiClient.post("/leads/campaigns", toCampaignRequest(input)));
}

export async function updateCampaign(id, input) {
  return toFeCampaign(
    await apiClient.put(`/leads/campaigns/${id}`, toCampaignRequest(input, { includeStatus: true }))
  );
}

// DELETE = deactivate (status -> CANCELLED) on the backend.
export async function deleteCampaign(id) {
  await apiClient.del(`/leads/campaigns/${id}`);
  return true;
}

export const PREAPPROVED_WHATSAPP_TEMPLATES = [
  {
    id: "tpl_intro",
    name: "Welcome & Course Intro Pitch",
    category: "Introductory",
    message: "Hi {{name}}! 👋 Welcome to Moriah Skill Hub. We noticed your interest in our project-based software engineering tracks. Would you be open for a quick 5-min chat to discuss your career goals?"
  },
  {
    id: "tpl_demo",
    name: "Live Sprint Demo Invite",
    category: "Demo",
    message: "Hello {{name}}, you're invited to join our exclusive Live Sprint Demo session! 🚀 See real students build production applications in real-time. Let me know if you can make it today!"
  },
  {
    id: "tpl_discount",
    name: "Special Early-Bird Fee Discount",
    category: "Promotional",
    message: "Exciting news {{name}}! 🎉 We're offering a limited-period 25% Early-Bird scholarship on the {{type}} track if you register this week. Reply 'YES' to claim your voucher code!"
  },
  {
    id: "tpl_syllabus",
    name: "Curriculum & Syllabus Brochure",
    category: "Curriculum",
    message: "Hi {{name}}, here is the complete curriculum syllabus and live industry challenge roadmap for Moriah Skill Hub: {{link}}. Let me know if you have any questions!"
  },
  {
    id: "tpl_payment",
    name: "Enrollment & Payment Link",
    category: "Closing",
    message: "Hi {{name}}, your seat has been reserved! 🎓 Complete your enrollment via our secure portal here: {{link}} to join the upcoming batch orientation."
  }
];

// ---- FE <-> backend enum mapping -----------------------------------------
// The Kanban board's stage labels and the backend LeadStatus enum.
const STAGE_TO_STATUS = {
  "New Lead": "NEW",
  Contacted: "CONTACTED",
  "Demo Scheduled": "DEMO_SCHEDULED",
  "Plan Selected": "COUNSELLING_DONE",
  "Payment Pending": "PAYMENT_PENDING",
  "Won / Enrolled": "ENROLLED",
  Lost: "LOST",
};
const STATUS_TO_STAGE = {
  NEW: "New Lead",
  CONTACTED: "Contacted",
  DEMO_SCHEDULED: "Demo Scheduled",
  COUNSELLING_DONE: "Plan Selected",
  PAYMENT_PENDING: "Payment Pending",
  ENROLLED: "Won / Enrolled",
  LOST: "Lost",
};
// Ordinal order used to detect a backward pipeline move (which the backend
// requires a reason for).
const STAGE_ORDER = ["New Lead", "Contacted", "Demo Scheduled", "Plan Selected", "Payment Pending", "Won / Enrolled"];

const FE_SOURCE_TO_ENUM = {
  "Landing Page": "LANDING_PAGE",
  "College Outreach": "COLLEGE",
  "Corporate Inquiry": "CORPORATE",
  Referral: "REFERRAL",
  "Walk-in": "WALK_IN",
};
const ENUM_TO_FE_SOURCE = {
  LANDING_PAGE: "Landing Page",
  COLLEGE: "College Outreach",
  CORPORATE: "Corporate Inquiry",
  REFERRAL: "Referral",
  WALK_IN: "Walk-in",
};
export const LEAD_SOURCES = Object.keys(FE_SOURCE_TO_ENUM).map((s) => ({ value: s, label: s }));

const FE_CHANNEL_TO_ACT = {
  Call: "CALL",
  Email: "EMAIL",
  WhatsApp: "WHATSAPP",
  "Zoom Demo": "MEETING",
  Meeting: "MEETING",
  "In-Person Meeting": "MEETING",
  Note: "NOTE",
};
const ACT_TO_FE_CHANNEL = {
  CALL: "Call",
  EMAIL: "Email",
  WHATSAPP: "WhatsApp",
  MEETING: "Meeting",
  NOTE: "Note",
  STATUS_CHANGE: "Status Change",
  WHATSAPP_INBOUND: "WhatsApp (inbound)",
};

// Backend LeadResponse -> the flat shape every leadgen page already reads.
function toFeLead(r) {
  return {
    id: r.id,
    name: r.name,
    email: r.email || "",
    phone: r.phone || "",
    type: r.leadType || "B2C",
    source: ENUM_TO_FE_SOURCE[r.source] || r.source,
    stage: STATUS_TO_STAGE[r.status] || "New Lead",
    status: r.status,
    assignedAgent: r.assignedAgentName || "Unassigned",
    assignedAgentUuid: r.assignedAgentUuid || null,
    dealValue: r.dealValue != null ? Number(r.dealValue) : null,
    lostReason: r.lostReason || "",
    convertedUserUuid: r.convertedUserUuid || null,
    followUpDate: r.nextFollowUpAt ? r.nextFollowUpAt.slice(0, 10) : null,
    createdAt: r.createdAt ? r.createdAt.slice(0, 10) : "",
    updatedAt: r.updatedAt ? r.updatedAt.slice(0, 10) : "",
    interactions: [], // hydrated on demand via getLeadActivities()
  };
}

function toFeActivity(a) {
  return {
    id: a.id,
    channel: ACT_TO_FE_CHANNEL[a.activityType] || a.activityType,
    outcome: a.outcome || "",
    notes: a.notes || "",
    timestamp: a.occurredAt,
    followUpDate: a.nextFollowUpAt ? a.nextFollowUpAt.slice(0, 10) : null,
    agent: a.agentName || "System",
  };
}

// A small cache of the last list, so the create form's live duplicate hint can
// stay synchronous (the backend also dedupes on POST — this is just UX).
let _leadCache = [];

export async function getLeads({ stage, source, agentUuid, page = 0, size = 100 } = {}) {
  const params = { page, size };
  if (stage && STAGE_TO_STATUS[stage]) params.status = STAGE_TO_STATUS[stage];
  if (source && FE_SOURCE_TO_ENUM[source]) params.source = FE_SOURCE_TO_ENUM[source];
  if (agentUuid) params.agentUuid = agentUuid;
  const rows = asRows(await apiClient.get("/leads", params)).map(toFeLead);
  _leadCache = rows;
  return rows;
}

export async function getLead(id) {
  return toFeLead(await apiClient.get(`/leads/${id}`));
}

export async function getLeadActivities(id) {
  return asRows(await apiClient.get(`/leads/${id}/activities`, { size: 100 })).map(toFeActivity);
}

// Synchronous pre-submit hint. `identifier` is { phone } or { email }.
export function checkDuplicateLead(identifier) {
  const phoneClean = identifier.phone ? identifier.phone.replace(/[^0-9]/g, "") : "";
  const emailClean = identifier.email ? identifier.email.trim().toLowerCase() : "";
  return _leadCache.find((l) => {
    const lPhone = (l.phone || "").replace(/[^0-9]/g, "");
    const lEmail = (l.email || "").trim().toLowerCase();
    return (phoneClean && lPhone && lPhone === phoneClean) || (emailClean && lEmail && lEmail === emailClean);
  });
}

export async function createLead(payload) {
  const email = (payload.email || "").trim();
  if (!email) throw new Error("An email address is required to capture a lead.");
  const body = {
    name: payload.name,
    email,
    phone: payload.phone,
    source: FE_SOURCE_TO_ENUM[payload.source] || "LANDING_PAGE",
    leadType: (payload.type || "B2C").slice(0, 50),
    institution: payload.institution || payload.college || null,
    dealValue: payload.dealValue === "" || payload.dealValue == null ? null : Number(payload.dealValue),
  };
  // POST /leads upserts on the email+phone dedupe hash — a second submit for the
  // same person updates that lead in place, it never creates a duplicate.
  return toFeLead(await apiClient.post("/leads", body));
}

// ---- Bulk lead ingestion: template download + multi-format file parsing ---
// Previously the only way in was hand-typing "Name, Phone, Email, Type" lines
// into a textarea. Columns: Name, Phone, Email, Type, Source (Source optional
// — defaults to "Bulk Import" if blank).

const BULK_LEAD_HEADERS = ["Name", "Phone", "Email", "Type", "Source"];
const BULK_LEAD_EXAMPLE_ROWS = [
  ["Arun Kumar", "9876543299", "arun@gmail.com", "Student (B2C)", "Landing Page"],
  ["Prof. Meenakshi", "9845112233", "hod@svce.edu.in", "College Tie-up", "College Outreach"],
];

// Downloads a ready-to-fill .csv — opens fine in Excel/Sheets, and is one of
// the formats parseLeadImportFile itself accepts back.
export function downloadLeadImportTemplate() {
  const esc = (c) => `"${String(c).replace(/"/g, '""')}"`;
  const lines = [BULK_LEAD_HEADERS, ...BULK_LEAD_EXAMPLE_ROWS].map((row) => row.map(esc).join(","));
  const blob = new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "moriah_lead_import_template.csv";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function normalizeLeadRows(rows) {
  const parsed = [];
  const problems = [];
  rows.forEach((row, i) => {
    if (!row || !row.length || row[0] == null || !String(row[0]).trim()) return;
    // Skip an optional header row.
    if (i === 0 && /^name$/i.test(String(row[0]).trim())) return;

    const cells = row.map((v) => (v == null ? "" : String(v).trim()));
    const [name, phone, email, type, source] = cells;
    if (!name || !phone) {
      problems.push(`Row ${i + 1}: needs at least a Name and Phone.`);
      return;
    }
    parsed.push({
      name,
      phone,
      email: email || "",
      type: type || "Student (B2C)",
      source: source || "Bulk Import",
    });
  });
  return { rows: parsed, problems };
}

// Accepts .csv, .xlsx, .xls, or .txt — all parsed into the same
// { rows: [{name, phone, email, type, source}], problems: [] } shape the
// preview + bulkImportLeads both consume.
export async function parseLeadImportFile(file) {
  const name = file.name.toLowerCase();

  if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: "array" });
    const sheet = wb.Sheets[wb.SheetNames[0]];
    if (!sheet) return { rows: [], problems: ["The spreadsheet has no sheets."] };
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, blankrows: false, defval: "" });
    return normalizeLeadRows(rows);
  }

  // .csv and .txt both parse as delimited text — PapaParse handles either.
  const text = await file.text();
  const result = Papa.parse(text.trim(), { skipEmptyLines: true });
  return normalizeLeadRows(result.data);
}

export async function bulkImportLeads(leadList) {
  let added = 0;
  let skipped = 0;
  for (const item of leadList) {
    try {
      if (!item.email) {
        skipped++;
        continue;
      }
      await createLead(item);
      added++;
    } catch {
      skipped++;
    }
  }
  return { added, skipped, total: added + skipped };
}

// Field edit (name / type / institution / dealValue) via PUT /leads/{id};
// a `stage` change is routed to the status endpoint. `source` and contact
// details are not editable server-side and are ignored here.
export async function updateLead(leadId, changes) {
  if (changes.stage) {
    await updateLeadStage(leadId, changes.stage, {
      reason: changes.reason,
      lostReason: changes.lostReason,
      convertedUserUuid: changes.convertedUserUuid,
      convertedUserEmail: changes.convertedUserEmail,
    });
  }
  const body = {};
  if (changes.name != null) body.name = changes.name;
  if (changes.type != null) body.leadType = String(changes.type).slice(0, 50);
  if (changes.institution != null) body.institution = changes.institution;
  if (changes.dealValue !== undefined) {
    body.dealValue = changes.dealValue === "" || changes.dealValue == null ? null : Number(changes.dealValue);
  }
  if (Object.keys(body).length === 0) return getLead(leadId);
  return toFeLead(await apiClient.put(`/leads/${leadId}`, body));
}

// opts: { reason, lostReason, convertedUserUuid, convertedUserEmail }
export async function updateLeadStage(leadId, feStage, opts = {}) {
  const newStatus = STAGE_TO_STATUS[feStage];
  if (!newStatus) throw new Error(`Unknown pipeline stage: ${feStage}`);
  const body = { newStatus };
  if (opts.reason) body.reason = opts.reason;
  if (newStatus === "LOST") body.lostReason = opts.lostReason || opts.reason || "Marked lost";
  if (newStatus === "ENROLLED") {
    if (!opts.convertedUserUuid && !opts.convertedUserEmail) {
      throw new Error(
        "Marking a lead as enrolled needs the student's account. Enter the email they registered with when prompted."
      );
    }
    if (opts.convertedUserUuid) body.convertedUserUuid = opts.convertedUserUuid;
    if (opts.convertedUserEmail) body.convertedUserEmail = opts.convertedUserEmail;
  }
  return toFeLead(await apiClient.put(`/leads/${leadId}/status`, body));
}

// True forward/backward detection so a backward drag can carry the reason the
// backend requires.
export function isBackwardStage(fromStage, toStage) {
  const a = STAGE_ORDER.indexOf(fromStage);
  const b = STAGE_ORDER.indexOf(toStage);
  return a > -1 && b > -1 && b < a;
}

// DELETE = soft archive server-side.
export async function deleteLead(leadId) {
  await apiClient.del(`/leads/${leadId}`);
  return true;
}

export async function logInteraction(leadId, interaction) {
  const activityType = FE_CHANNEL_TO_ACT[interaction.channel] || "NOTE";
  const body = {
    activityType,
    outcome: interaction.outcome || null,
    notes: interaction.notes || null,
    nextFollowUpAt: interaction.followUpDate
      ? new Date(`${interaction.followUpDate}T09:00:00`).toISOString()
      : null,
    occurredAt: new Date().toISOString(),
    templateCode:
      activityType === "WHATSAPP"
        ? interaction.templateCode || interaction.templateId || "generic_followup"
        : null,
  };
  return toFeActivity(await apiClient.post(`/leads/${leadId}/activities`, body));
}

export async function getTargets() {
  const rows = await apiClient.get("/leads/targets/leaderboard");

  // Tier-based commission on realised pipeline value: 5% up to 1L, 8% 1-3L, 10% above.
  const commissionFor = (revenue) => {
    if (revenue <= 100000) return revenue * 0.05;
    if (revenue <= 300000) return 100000 * 0.05 + (revenue - 100000) * 0.08;
    return 100000 * 0.05 + 200000 * 0.08 + (revenue - 300000) * 0.1;
  };

  return asRows(rows).map((r) => {
    const revenue = Number(r.pipelineValue || 0);
    return {
      agent: r.agentName || "—",
      role: "Lead Generator",
      targetRevenue: r.revenueTarget != null ? Number(r.revenueTarget) : 0,
      revenue,
      targetEnrolled: r.conversionsTarget ?? 0,
      closed: Number(r.converted || 0),
      totalLeads: Number(r.totalLeads || 0),
      commissionEarned: Math.round(commissionFor(revenue)),
      callsDone: r.callsMade ?? 0,
      callsQuota: r.callsTarget ?? 0,
      whatsappDone: 0,
      whatsappQuota: 0,
      demosDone: 0,
      demosQuota: 0,
      conversionVelocity: "—",
    };
  });
}

export async function getMyTarget() {
  return apiClient.get("/leads/targets/me");
}