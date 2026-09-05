import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Client project submissions — WIRED to the backend (feature 21 / this pass):
//   POST /api/v1/clients/projects              (CLIENT submits a scope)
//   GET  /api/v1/clients/projects              (CLIENT sees own; BA/ADMIN see all)
//   GET  /api/v1/clients/projects/{id}/progress
// The backend record is title + scope text + budget range — no file upload,
// no client-side edit/delete (status is SUBMITTED until staff allocate a
// batch). A BA authors the formal BRD/SRS/FRS separately from the brief.
// ---------------------------------------------------------------------------

const CP_STATUS_TO_FE = { SUBMITTED: "Submitted", IN_PROGRESS: "In Progress", COMPLETED: "Completed" };

function toFeClientProject(p) {
  return {
    id: p.id,
    clientId: p.clientId,
    clientName: p.clientName || "",
    title: p.title,
    scope: p.scopeDescription || "",
    budgetRange: p.budgetRange || "",
    additionalNotes: p.additionalNotes || "",
    targetBatchId: p.targetBatchId ?? null,
    allocated: p.targetBatchId != null,
    status: CP_STATUS_TO_FE[p.status] || p.status,
    // Round-robin routing (auto-assigned; admin can override from the
    // "Client Project Assignments" screen) — null until an active BA/
    // developer exists to pick up the work.
    assignedBaUuid: p.assignedBaUuid || null,
    assignedBaName: p.assignedBaName || "",
    assignedDeveloperUuid: p.assignedDeveloperUuid || null,
    assignedDeveloperName: p.assignedDeveloperName || "",
    submittedAt: p.submittedAt ? String(p.submittedAt).slice(0, 10) : "",
  };
}

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

export async function getClientProjects({ status, allProjects } = {}) {
  const res = await apiClient.get("/clients/projects", {
    status: status || undefined,
    allProjects: allProjects || undefined,
    size: 100,
  });
  return asRows(res).map(toFeClientProject);
}

// The client's own submissions (same endpoint, server auto-scopes a CLIENT).
export async function getMyRequirements() {
  return getClientProjects();
}

// POST /api/v1/clients/projects — { title, scopeDescription, budgetRange?, additionalNotes? }.
// Text only — no file upload.
export async function submitProjectRequirement({ title, scope, description, budgetRange, additionalNotes }) {
  const res = await apiClient.post("/clients/projects", {
    title: (title || "").trim(),
    scopeDescription: (scope || description || "").trim(),
    budgetRange: budgetRange || undefined,
    additionalNotes: additionalNotes ? additionalNotes.trim() : undefined,
  });
  return toFeClientProject(res);
}

// GET /api/v1/clients/projects/{id}/progress — burndown + milestone completion
// once staff have allocated a batch (zeroed shape until then).
export async function getClientProjectProgress(id) {
  const p = await apiClient.get(`/clients/projects/${id}/progress`);
  return {
    clientProjectId: p.clientProjectId,
    title: p.title,
    targetBatchId: p.targetBatchId ?? null,
    milestoneCompletion: Math.round((p.milestoneCompletionFraction || 0) * 100),
    burndown: (p.burndown || []).map((s) => ({
      sprintId: s.sprintId,
      sprintNumber: s.sprintNumber,
      status: s.sprintStatus,
      plannedPoints: s.plannedPoints ?? 0,
      completedPoints: s.completedPoints ?? 0,
    })),
  };
}

// ---------------------------------------------------------------------------
// Talent pool + recruitment requests — WIRED to the backend (B1.9):
//   GET  /api/v1/talent-pool            (CLIENT / ADMIN / HR_MANAGER / LEAD_GEN)
//   POST /api/v1/recruitment-requests   (CLIENT)  -> lands PENDING
//   GET  /api/v1/recruitment-requests   (CLIENT sees own, ADMIN/HR see all)
// The multi-stage placement pipeline on the Talent Pool page (shortlist ->
// schedule -> offer -> sign -> placed) has NO backend and stays on
// localStorage via utils/placementPipeline.js.
// ---------------------------------------------------------------------------

// Backend TalentPoolCandidateResponse -> the shape the Talent Pool page reads.
function toFeCandidate(c) {
  const years = c.yearsExperience ?? 0;
  return {
    id: c.uuid,
    uuid: c.uuid,
    name: c.fullName,
    track: c.currentTitle || c.experienceLevel || "—",
    experienceLevel: c.experienceLevel || null,
    yearsExperience: years,
    location: c.location || "",
    availability: "now",
    skills: Array.isArray(c.skills) ? c.skills : [],
    portfolioSlug: c.portfolioSlug || null,
    bio: c.bio || "",
    // The page's ProgressBar wants a number; approximate from experience
    // until the backend exposes a real performance score.
    score: Math.min(95, Math.max(45, 55 + years * 8)),
  };
}

export async function getTalentPool({ search, skill } = {}) {
  const params = {};
  if (search) params.search = search;
  if (skill) params.skill = skill;
  const res = await apiClient.get("/talent-pool", Object.keys(params).length ? params : undefined);
  const rows = Array.isArray(res) ? res : res?.content ?? [];
  return rows.map(toFeCandidate);
}

const ENGAGEMENT_TYPES = ["FULL_TIME", "CONTRACT", "INTERNSHIP"];

// candidateUuidOrObj: a uuid string or a candidate object ({uuid}/{id}).
export async function requestRecruitment(candidateUuidOrObj, opts = {}) {
  const candidateUuid =
    typeof candidateUuidOrObj === "string"
      ? candidateUuidOrObj
      : candidateUuidOrObj?.uuid || candidateUuidOrObj?.id;
  const body = {
    candidateUuid,
    roleTitle: opts.roleTitle || "Software Engineer",
    engagementType: ENGAGEMENT_TYPES.includes(opts.engagementType) ? opts.engagementType : "FULL_TIME",
    message: opts.message || null,
  };
  const res = await apiClient.post("/recruitment-requests", body);
  return { candidateUuid, id: res?.id, status: res?.status || "PENDING", raw: res };
}

export async function getRecruitmentRequests({ status } = {}) {
  const res = await apiClient.get("/recruitment-requests", status ? { status } : undefined);
  const rows = Array.isArray(res) ? res : res?.content ?? [];
  return rows.map((r) => ({
    id: r.id,
    candidateUuid: r.candidateUuid,
    candidateName: r.candidateName,
    roleTitle: r.roleTitle,
    engagementType: r.engagementType,
    message: r.message || "",
    status: r.status,
    decisionNote: r.decisionNote || "",
    decidedAt: r.decidedAt || null,
    createdAt: r.createdAt || null,
  }));
}

// Looks up the resume a student uploaded via studentService.saveResumeFile
// (persisted on their registered-user record) so a Corporate Client can
// actually open a Talent Pool candidate's resume before shortlisting them.
// Only candidates HR has already published to the Talent Pool reach this —
// see hrService.trySyncGraduateToTalentPool — so a resume is expected here,
// but this stays defensive in case the underlying record was cleared.
export function getCandidateResume(candidateName) {
  if (!candidateName) return null;
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const list = raw ? JSON.parse(raw) : [];
    const match = list.find((u) => u.name?.toLowerCase() === candidateName.toLowerCase());
    return match?.profileDetails?.resume || null;
  } catch (e) {
    return null;
  }
}