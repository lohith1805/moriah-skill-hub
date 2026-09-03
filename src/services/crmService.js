import { mockRequest, apiClient } from "./apiClient";

const DEFAULT_LEADS = [];

// ---------------------------------------------------------------------------
// Lead campaigns — WIRED to the backend (B1.7: GET/POST/PUT/DELETE
// /api/v1/leads/campaigns, LEAD_GEN / ADMIN). DELETE deactivates (status =
// CANCELLED), it never row-deletes. The rest of this file (lead pipeline,
// targets, interaction logging) is still the localStorage mock — the backend
// lead endpoints exist but expose no per-lead detail / activity-list / delete
// yet, so the Pipeline page can't be fully migrated without a Part B add.
// ---------------------------------------------------------------------------

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

export function readLeadsFromStorage() {
  try {
    const raw = localStorage.getItem("msh_crm_leads");
    if (raw) return JSON.parse(raw);
  } catch (e) {
    // fallback
  }
  localStorage.setItem("msh_crm_leads", JSON.stringify(DEFAULT_LEADS));
  return DEFAULT_LEADS;
}

export function saveLeadsToStorage(leads) {
  localStorage.setItem("msh_crm_leads", JSON.stringify(leads));
}

export async function getLeads() {
  const leads = readLeadsFromStorage();
  return mockRequest(leads);
}

export function checkDuplicateLead(identifier) {
  const leads = readLeadsFromStorage();
  const phoneClean = identifier.phone ? identifier.phone.replace(/[^0-9]/g, "") : "";
  const emailClean = identifier.email ? identifier.email.trim().toLowerCase() : "";

  return leads.find((l) => {
    const lPhone = (l.phone || "").replace(/[^0-9]/g, "");
    const lEmail = (l.email || "").trim().toLowerCase();
    return (phoneClean && lPhone && lPhone === phoneClean) || (emailClean && lEmail && lEmail === emailClean);
  });
}

export async function createLead(payload) {
  const leads = readLeadsFromStorage();
  const duplicate = checkDuplicateLead({ phone: payload.phone, email: payload.email });
  if (duplicate) {
    throw new Error(`Duplicate Lead found: "${duplicate.name}" (${duplicate.phone || duplicate.email}) already exists in stage "${duplicate.stage}".`);
  }

  const newLead = {
    id: `l_${Date.now()}`,
    name: payload.name,
    phone: payload.phone,
    email: payload.email || "",
    type: payload.type || "Student (B2C)",
    source: payload.source || "Landing Page",
    stage: payload.stage || "New Lead",
    assignedAgent: payload.assignedAgent || "Unassigned",
    dealValue: payload.dealValue ? Number(payload.dealValue) : null,
    createdAt: new Date().toISOString().slice(0, 10),
    updatedAt: new Date().toISOString().slice(0, 10),
    followUpDate: payload.followUpDate || null,
    interactions: []
  };

  const updated = [newLead, ...leads];
  saveLeadsToStorage(updated);
  return mockRequest(newLead, { delay: 400 });
}

export async function bulkImportLeads(leadList) {
  const existing = readLeadsFromStorage();
  let added = 0;
  let skipped = 0;
  const newItems = [];

  for (const item of leadList) {
    const isDup = existing.some((l) => {
      const p1 = (l.phone || "").replace(/[^0-9]/g, "");
      const p2 = (item.phone || "").replace(/[^0-9]/g, "");
      const e1 = (l.email || "").trim().toLowerCase();
      const e2 = (item.email || "").trim().toLowerCase();
      return (p1 && p2 && p1 === p2) || (e1 && e2 && e1 === e2);
    });

    if (isDup) {
      skipped++;
    } else {
      const lead = {
        id: `l_${Date.now()}_${Math.random().toString(36).substr(2, 4)}`,
        name: item.name,
        phone: item.phone,
        email: item.email || "",
        type: item.type || "Student (B2C)",
        source: item.source || "Bulk CSV Ingestion",
        stage: "New Lead",
        assignedAgent: "Unassigned",
        dealValue: item.dealValue ? Number(item.dealValue) : null,
        createdAt: new Date().toISOString().slice(0, 10),
        updatedAt: new Date().toISOString().slice(0, 10),
        followUpDate: null,
        interactions: []
      };
      newItems.push(lead);
      existing.unshift(lead);
      added++;
    }
  }

  saveLeadsToStorage(existing);
  return mockRequest({ added, skipped, total: existing.length });
}

export async function updateLead(leadId, updates) {
  const leads = readLeadsFromStorage();
  const idx = leads.findIndex((l) => l.id === leadId);
  if (idx === -1) throw new Error("Lead not found");

  leads[idx] = {
    ...leads[idx],
    ...updates,
    updatedAt: new Date().toISOString().slice(0, 10)
  };

  saveLeadsToStorage(leads);
  return mockRequest(leads[idx]);
}

export async function updateLeadStage(leadId, stage) {
  return updateLead(leadId, { stage });
}

export async function deleteLead(leadId) {
  const leads = readLeadsFromStorage();
  const updated = leads.filter((l) => l.id !== leadId);
  saveLeadsToStorage(updated);
  return mockRequest(true);
}

export async function logInteraction(leadId, interaction) {
  const leads = readLeadsFromStorage();
  const idx = leads.findIndex((l) => l.id === leadId);
  if (idx === -1) throw new Error("Lead not found");

  const logEntry = {
    id: `i_${Date.now()}`,
    channel: interaction.channel, // Call, WhatsApp, Zoom Demo, Email, Note
    outcome: interaction.outcome || "Logged",
    notes: interaction.notes || "",
    timestamp: new Date().toISOString()
  };

  const currentInteractions = leads[idx].interactions || [];
  leads[idx] = {
    ...leads[idx],
    interactions: [logEntry, ...currentInteractions],
    updatedAt: new Date().toISOString().slice(0, 10),
    ...(interaction.followUpDate !== undefined ? { followUpDate: interaction.followUpDate } : {})
  };

  saveLeadsToStorage(leads);
  return mockRequest(leads[idx]);
}

export async function getTargets() {
  const leads = readLeadsFromStorage();
  const wonLeads = leads.filter((l) => l.stage === "Won / Enrolled" || l.stage === "Enrolled");

  // Compute tier-based commission: 5% up to 1L, 8% between 1-3L, 10% above 3L
  const commissionFor = (revenue) => {
    if (revenue <= 100000) return revenue * 0.05;
    if (revenue <= 300000) return 100000 * 0.05 + (revenue - 100000) * 0.08;
    return 100000 * 0.05 + 200000 * 0.08 + (revenue - 300000) * 0.10;
  };

  // Group leads by their real assigned agent so the leaderboard reflects
  // actual data rather than fabricated names.
  const agentNames = Array.from(new Set(leads.map((l) => l.assignedAgent).filter(Boolean)));

  const targets = agentNames.map((agent) => {
    const agentLeads = leads.filter((l) => l.assignedAgent === agent);
    const agentWon = agentLeads.filter((l) => l.stage === "Won / Enrolled" || l.stage === "Enrolled");
    const revenue = agentWon.reduce((acc, l) => acc + (l.dealValue || 0), 0);

    let totalDays = 0;
    agentWon.forEach((l) => {
      const created = new Date(l.createdAt || new Date()).getTime();
      const updated = new Date(l.updatedAt || new Date()).getTime();
      const diffDays = Math.max(1, Math.round((updated - created) / (1000 * 60 * 60 * 24)));
      totalDays += diffDays;
    });
    const avgVelocityDays = agentWon.length > 0 ? (totalDays / agentWon.length).toFixed(1) : "0";

    return {
      agent,
      role: "Lead Generator",
      targetRevenue: 0,
      revenue,
      targetEnrolled: 0,
      closed: agentWon.length,
      commissionEarned: Math.round(commissionFor(revenue)),
      callsDone: 0,
      callsQuota: 0,
      whatsappDone: 0,
      whatsappQuota: 0,
      demosDone: 0,
      demosQuota: 0,
      conversionVelocity: `${avgVelocityDays} days`
    };
  });

  return mockRequest(targets);
}