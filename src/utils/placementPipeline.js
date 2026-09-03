// Display helpers for the Placement pipeline. Persistence moved to
// services/placementService.js (backend /api/v1/placements); this file now
// only holds the stage vocabulary + presentation logic shared by
// client/TalentPool.jsx, student/Interviews.jsx and hr/Documents.jsx.
//
// The 13 stages mirror the backend PlacementStage enum 1:1.

export const DOCS_KEY = "msh_hr_documents";       // offer-letter artifacts (no backend)
export const OTHER_DOCS_KEY = "msh_hr_other_documents";

// FE label <-> backend enum. Keep both in sync with PlacementStage.java.
export const BACKEND_TO_FE_STAGE = {
  SHORTLISTED: "Shortlisted",
  TECHNICAL_SCHEDULED: "Technical Round Scheduled",
  TECHNICAL_COMPLETED: "Technical Round Completed",
  TECHNICAL_APPROVED: "Technical Round Approved",
  HR_SCHEDULED: "HR Round Scheduled",
  HR_COMPLETED: "HR Round Completed",
  HR_APPROVED: "HR Round Approved",
  DOCUMENT_VERIFICATION: "Document Verification",
  OFFER_CREATED: "Offer Letter Created",
  CLIENT_SIGNED: "Client Signed",
  STUDENT_SIGNED: "Student Signed",
  PLACED: "Placed",
  REJECTED: "Rejected",
};
export const FE_TO_BACKEND_STAGE = Object.fromEntries(
  Object.entries(BACKEND_TO_FE_STAGE).map(([be, fe]) => [fe, be])
);

export const STAGES = [
  "Shortlisted",
  "Technical Round Scheduled",
  "Technical Round Completed",
  "Technical Round Approved",
  "HR Round Scheduled",
  "HR Round Completed",
  "HR Round Approved",
  "Document Verification",
  "Offer Letter Created",
  "Client Signed",
  "Student Signed",
  "Placed",
];

export const REJECTED = "Rejected";

export function stageIndex(stage) {
  return STAGES.indexOf(stage);
}

// 0–100 for the ProgressBar on pipeline cards.
export function stageProgress(stage) {
  if (stage === REJECTED) return 100;
  const i = stageIndex(stage);
  if (i < 0) return 0;
  return Math.round((i / (STAGES.length - 1)) * 100);
}

export function stageTone(stage) {
  if (stage === REJECTED) return "error";
  if (stage === "Placed") return "gold";
  if (stage === "Shortlisted") return "gold";
  if (["Technical Round Scheduled", "HR Round Scheduled"].includes(stage)) return "primary";
  if (["Technical Round Completed", "Technical Round Approved", "HR Round Completed", "HR Round Approved"].includes(stage)) return "info";
  if (["Document Verification", "Offer Letter Created"].includes(stage)) return "warning";
  if (["Client Signed", "Student Signed"].includes(stage)) return "success";
  return "primary";
}

// Documents checked off during "Document Verification". The student uploads
// their own resume / ID / education certs; the interview feedback form is
// HR-authored.
export const REQUIRED_DOCUMENTS = [
  { key: "resume", label: "Resume / CV", uploadedBy: "student" },
  { key: "id_proof", label: "Government ID Proof", uploadedBy: "student" },
  { key: "education", label: "Educational Certificates", uploadedBy: "student" },
  { key: "interview_feedback", label: "Interview Feedback Form", uploadedBy: "hr" },
];

export function freshDocumentChecklist() {
  return REQUIRED_DOCUMENTS.map((d) => ({
    ...d, verified: false, studentUploaded: false, fileName: null, fileData: null, fileType: null, uploadedAt: null, docStatus: null,
  }));
}

export function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export function dataURLToBlob(dataURL) {
  const [header, base64] = dataURL.split(",");
  const mimeMatch = header.match(/data:(.*?);base64/);
  const mime = mimeMatch ? mimeMatch[1] : "application/octet-stream";
  const binary = atob(base64);
  const array = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) array[i] = binary.charCodeAt(i);
  return new Blob([array], { type: mime });
}

export const OTHER_REQUIRED_DOCUMENTS = [
  { key: "address_proof", label: "Address Proof" },
  { key: "bank_details", label: "Bank Account Details" },
  { key: "marksheets", label: "Educational Marksheets" },
];

export function freshOtherDocumentChecklist() {
  return OTHER_REQUIRED_DOCUMENTS.map((d) => ({ ...d, uploaded: false, fileName: null }));
}

// --- Recruitment store (backend-backed adapter) --------------------
// The 3 pipeline pages still call loadRecruitments() / saveRecruitments(list).
// loadRecruitments now fetches /api/v1/placements; saveRecruitments diffs the
// list against the last fetch and PUTs each changed record's stage + details.
// Creating a record client-side is not supported — a placement is born when HR
// approves a recruitment request.
import { getRecruitments as _getRecruitments, advancePlacement as _advancePlacement } from "../services/placementService";

const META_KEYS = new Set([
  "id", "recruitmentRequestId", "candidateId", "candidateUuid", "candidateName",
  "clientUuid", "clientName", "stage", "backendStage", "createdAt", "updatedAt",
]);
let _snapshot = [];

function detailKeys(rec) {
  return Object.keys(rec).filter((k) => !META_KEYS.has(k));
}

export async function loadRecruitments() {
  try {
    _snapshot = await _getRecruitments();
  } catch {
    _snapshot = [];
  }
  return _snapshot.map((r) => ({ ...r }));
}

export async function saveRecruitments(list) {
  const byId = new Map(_snapshot.map((r) => [r.id, r]));
  await Promise.all(
    (list || []).map(async (r) => {
      const prev = byId.get(r.id);
      if (!prev) return; // no client-side create
      const patch = {};
      for (const k of detailKeys(r)) {
        if (JSON.stringify(r[k]) !== JSON.stringify(prev[k])) patch[k] = r[k];
      }
      if (r.stage !== prev.stage || Object.keys(patch).length) {
        try {
          await _advancePlacement(r.id, r.stage, patch);
        } catch (e) {
          // surfaced by the page's own reload; keep going for the rest
          console.warn("[placement] update failed", r.id, e?.message);
        }
      }
    })
  );
  _snapshot = (list || []).map((r) => ({ ...r }));
}

// Offer-letter document artifacts stay client-local (the backend stores the
// offer *text* in placement details, not a rendered letter object).
export function loadDocs() {
  try {
    const raw = localStorage.getItem(DOCS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}
export function saveDocs(list) {
  localStorage.setItem(DOCS_KEY, JSON.stringify(list));
}

export function stageMessage(stage, viewer) {
  switch (stage) {
    case "Shortlisted":
      return viewer === "client" ? "Shortlisted — schedule the technical round"
        : viewer === "student" ? "You've been shortlisted by a client!"
        : "Shortlisted — pending interview scheduling";
    case "Technical Round Scheduled":
      return "Technical interview scheduled";
    case "Technical Round Completed":
      return viewer === "client" ? "Awaiting your decision" : "Awaiting client's decision on the technical round";
    case "Technical Round Approved":
      return viewer === "hr" ? "Technical round cleared — schedule the HR round" : "Technical round cleared — HR round pending";
    case "HR Round Scheduled":
      return "HR round scheduled";
    case "HR Round Completed":
      return viewer === "hr" ? "Awaiting your decision" : "Awaiting HR's decision on the HR round";
    case "HR Round Approved":
      return "Both rounds cleared — moving to document verification";
    case "Document Verification":
      return viewer === "student" ? "Upload your documents for HR verification" : "HR is verifying documents";
    case "Offer Letter Created":
      return viewer === "client" ? "Review & sign the offer letter" : "Offer letter with the client for signature";
    case "Client Signed":
      return viewer === "student" ? "Review & sign your offer" : "Awaiting student signature";
    case "Student Signed":
      return "Both parties signed — HR to finalise";
    case "Placed":
      return "Placement completed 🎉";
    case REJECTED:
      return "Not selected this time";
    default:
      return stage;
  }
}
