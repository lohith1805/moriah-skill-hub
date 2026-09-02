import { mockRequest } from "./apiClient";
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

export async function getTalentPool() {
  // TALENT_POOL (from mockData) is a static, permanently-empty seed. Real
  // candidates come from HR finalizing a graduate's exit clearance (see
  // hrService.publishGraduateToTalentPool), which writes here — this is
  // what makes a cleared, exited graduate actually show up for a Corporate
  // Client to browse and recruit.
  let dynamicPool = [];
  try {
    const raw = localStorage.getItem("msh_talent_pool");
    dynamicPool = raw ? JSON.parse(raw) : [];
  } catch (e) {
    dynamicPool = [];
  }
  return mockRequest([...dynamicPool, ...TALENT_POOL]);
}

export async function requestRecruitment(candidateId) {
  await mockRequest(null, { delay: 700 });
  return { candidateId, status: "Interview Requested" };
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