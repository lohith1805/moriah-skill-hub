import { mockRequest, apiClient } from "./apiClient";
import { CLIENT_PROJECTS, TALENT_POOL, REQUIREMENT_DOCS } from "./mockData";
import { getPersistedUser } from "./authService";

// Converts an uploaded File to a base64 data URL so it can be persisted in
// localStorage — this is the same trick src/pages/ba/Documents.jsx uses so
// its own uploads survive a reload. Client-submitted requirements need it
// too, otherwise the file only exists as an in-memory File for the current
// browser tab and BA's "Download" always sees no fileData.
function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export async function getClientProjects() {
  const user = getPersistedUser();
  let all = CLIENT_PROJECTS;
  try {
    const raw = localStorage.getItem("msh_client_projects");
    if (raw) all = JSON.parse(raw);
  } catch (e) {
    all = CLIENT_PROJECTS;
  }
  // Scope to this client's own company where we can tell, so one client
  // doesn't see another's project cards.
  const mine = user?.company
    ? all.filter((p) => p.client?.toLowerCase() === user.company.toLowerCase())
    : all;
  return mockRequest(mine);
}

// Writes straight into "msh_ba_documents" — the same localStorage cache the
// BA Documents page reads from (see src/pages/ba/Documents.jsx) — so a
// client-submitted requirement shows up there for BA to pick up. Mirrors
// what a real "submit requirement" endpoint + BA inbox would do.
//
// IMPORTANT: whatever the client attaches here is a raw brief/wireframe —
// NOT a BRD/SRS/FRS. Those three are formal specs the BA authors themselves
// (MSH-FR-BA-01) after reading this brief. So client files are always
// tagged "CLIENT_DOC", a type distinct from BRD/SRS/FRS, so BA's Documents
// page can tell "client attached this" apart from "BA authored this" and
// never mistakes a client's brief for a finished BRD.
export async function submitProjectRequirement(payload) {
  const user = getPersistedUser();
  const files = payload.files || [];
  const fileType = "CLIENT_DOC";
  // Persist the actual file content (base64), not just its name/size, so
  // the BA's Download button on this requirement has something to fetch.
  const fileData = files[0] ? { [fileType]: await fileToDataURL(files[0]) } : {};
  const doc = {
    id: `d${Date.now()}`,
    title: `${user?.company || "Client"} — ${payload.title || "Untitled Requirement"}`,
    type: fileType,
    client: user?.company || user?.name || "Unknown Client",
    clientUserId: user?.id || null,
    version: "0.1",
    status: "Under Review",
    updatedAt: new Date().toISOString().slice(0, 10),
    summary: payload.scope || payload.description || payload.summary || "",
    files: files.map((f) => ({ name: f.name, size: f.size, type: fileType })),
    fileData,
  };

  try {
    const raw = localStorage.getItem("msh_ba_documents");
    let docs = [];
    if (raw) {
      docs = JSON.parse(raw);
    } else {
      // Seed from the same defaults BA's page would seed from, so we don't
      // clobber the BA portal's initial list the first time a client submits
      // before any BA has opened their Documents page yet.
      docs = REQUIREMENT_DOCS.map((d) => ({ ...d, files: [] }));
    }
    docs.unshift(doc);
    localStorage.setItem("msh_ba_documents", JSON.stringify(docs));
  } catch (err) {
    console.warn("[clientService] Could not persist requirement doc:", err.message);
  }

  await mockRequest(null, { delay: 800 });
  return { requirementId: doc.id, doc };
}

// Reads the same shared "msh_ba_documents" store, filtered down to just
// this client's own submissions (by clientUserId, falling back to matching
// on company name for docs BA authored directly against a client). This is
// what powers the client's "My Project Requirements" page, so BA's status
// changes / uploaded BRD-SRS-FRS files are visible back to the client who
// submitted them.
export async function getMyRequirements() {
  const user = getPersistedUser();
  let docs = [];
  try {
    const raw = localStorage.getItem("msh_ba_documents");
    docs = raw ? JSON.parse(raw) : REQUIREMENT_DOCS.map((d) => ({ ...d, files: [] }));
  } catch (err) {
    docs = REQUIREMENT_DOCS.map((d) => ({ ...d, files: [] }));
  }

  const mine = docs.filter((d) => {
    if (user?.id && d.clientUserId === user.id) return true;
    if (user?.company && d.client && d.client.toLowerCase() === user.company.toLowerCase()) return true;
    return false;
  });

  return mockRequest(mine);
}

// Client edits are limited to title/scope while still "Under Review" — once
// BA has started reviewing/approving, status/version/files are BA-owned.
export async function updateMyRequirement(id, changes) {
  // If the edit included a freshly-picked File with real content (not the
  // placeholder empty File this page reconstructs from saved metadata),
  // re-derive fileData so the new upload is downloadable too.
  const newFile = (changes.files || []).find((f) => f instanceof File && f.size > 0);
  const newFileData = newFile ? await fileToDataURL(newFile) : null;

  try {
    const raw = localStorage.getItem("msh_ba_documents");
    const docs = raw ? JSON.parse(raw) : [];
    const updated = docs.map((d) => {
      if (d.id !== id) return d;
      // Client edits only ever touch their own CLIENT_DOC attachment, never
      // the BA-authored BRD/SRS/FRS files that may already live on this doc.
      // Must merge, not replace — replacing the whole `files` array would
      // wipe out any BRD/SRS/FRS the BA already uploaded onto this same
      // requirement while it was still "Under Review".
      const fileType = "CLIENT_DOC";
      const next = { ...d, ...changes, updatedAt: new Date().toISOString().slice(0, 10) };
      if (Array.isArray(changes.files)) {
        const otherFiles = (d.files || []).filter((f) => (f.type || "CLIENT_DOC") !== fileType);
        const clientFiles = changes.files.map((f) => ({ name: f.name, size: f.size, type: fileType }));
        next.files = [...otherFiles, ...clientFiles];
      }
      if (newFileData) {
        const existingFileData = typeof d.fileData === "string" ? { [d.type || fileType]: d.fileData } : (d.fileData || {});
        next.fileData = { ...existingFileData, [fileType]: newFileData };
      }
      return next;
    });
    localStorage.setItem("msh_ba_documents", JSON.stringify(updated));
  } catch (err) {
    console.warn("[clientService] Could not update requirement doc:", err.message);
  }
  await mockRequest(null, { delay: 500 });
  return { id, ...changes };
}

export async function deleteMyRequirement(id) {
  try {
    const raw = localStorage.getItem("msh_ba_documents");
    const docs = raw ? JSON.parse(raw) : [];
    localStorage.setItem("msh_ba_documents", JSON.stringify(docs.filter((d) => d.id !== id)));
  } catch (err) {
    console.warn("[clientService] Could not delete requirement doc:", err.message);
  }
  await mockRequest(null, { delay: 400 });
  return { id };
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