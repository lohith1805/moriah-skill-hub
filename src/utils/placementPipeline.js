// Single source of truth for the frontend Placement workflow.
// Shared by client/TalentPool.jsx, student/Interviews.jsx and hr/Documents.jsx
// so every screen reads/writes the exact same recruitment record shape and
// never re-implements the stage machine differently.
//
// Workflow (in order):
//   Shortlisted (Client reviews the candidate's resume and shortlists) ->
//   Technical Round Scheduled -> Technical Round Completed ->
//   Technical Round Approved (Client/Interviewer) ->
//   HR Round Scheduled (HR schedules once technical is approved) ->
//   HR Round Completed -> HR Round Approved (HR) ->
//   Document Verification (HR, gated on the student's own document uploads) ->
//   Documents Verified -> Placement Confirmed (HR) ->
//   Offer Letter Created/Uploaded (HR) -> Client Review & Signature ->
//   Student Approval/Reject -> Student Digital Signature ->
//   Placement Completed/Placed (System)
//
// A candidate can be Rejected at the "Technical Round Completed" step (by
// the Client/Interviewer), at the "HR Round Completed" step (by HR), or at
// the "Student Approval" step (by the student). Rejection is terminal and
// stored as stage "Rejected" with `rejectedAt` recording which step it was
// rejected from.

export const RECRUITMENTS_KEY = "msh_client_recruitments";
export const DOCS_KEY = "msh_hr_documents";
export const OTHER_DOCS_KEY = "msh_hr_other_documents";

// Ordered pipeline — index position drives progress %, and "never show
// Placed immediately after interview approval" is enforced structurally:
// there is no transition anywhere in this file from an approval stage
// straight to PLACED. Every candidate must pass through both interview
// rounds and Document Verification first.
export const STAGES = [
  "Shortlisted",
  "Technical Round Scheduled",
  "Technical Round Completed",
  "Technical Round Approved",
  "HR Round Scheduled",
  "HR Round Completed",
  "HR Round Approved",
  "Document Verification",
  "Documents Verified",
  "Placement Confirmed",
  "Offer Letter Created",
  "Client Review & Signature",
  "Student Approval",
  "Student Signature",
  "Placed",
];

export const REJECTED = "Rejected";

// Legacy stage names, kept only so older localStorage data (saved before
// the two-round Technical/HR split existed) still loads and lands on a
// valid, sensible stage instead of breaking. See loadRecruitments() below —
// every read goes through this migration so every screen sees the new
// stage names consistently.
const STAGE_MIGRATION = {
  "Interview Scheduled": "Technical Round Scheduled",
  "Interview Completed": "Technical Round Completed",
  // Under the old single-round flow this meant "Client approved, handed to
  // HR for document verification" — the HR round didn't exist yet, so the
  // closest honest equivalent is to drop the candidate straight into
  // Document Verification rather than invent a fake HR round approval.
  "Interview Approved": "Document Verification",
};

export function migrateStage(stage) {
  return STAGE_MIGRATION[stage] || stage;
}

export function stageIndex(stage) {
  return STAGES.indexOf(stage);
}

// 0–100, used to drive the ProgressBar on pipeline cards.
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
  if (["Document Verification", "Documents Verified"].includes(stage)) return "warning";
  return "primary";
}

// Required documents checked off during the HR "Document Verification" step.
// `uploadedBy` marks who is responsible for attaching the file: the student
// uploads their own resume / ID / education certificates from "My
// Interviews" before HR is allowed to verify them; the interview feedback
// form is HR/interviewer-authored, so it isn't gated on a student upload.
export const REQUIRED_DOCUMENTS = [
  { key: "resume", label: "Resume / CV", uploadedBy: "student" },
  { key: "id_proof", label: "Government ID Proof", uploadedBy: "student" },
  { key: "education", label: "Educational Certificates", uploadedBy: "student" },
  { key: "interview_feedback", label: "Interview Feedback Form", uploadedBy: "hr" },
];

export function freshDocumentChecklist() {
  return REQUIRED_DOCUMENTS.map((d) => ({ ...d, verified: false, studentUploaded: false, fileName: null, fileData: null, fileType: null, uploadedAt: null, docStatus: null }));
}

// Converts an uploaded File to a base64 data URL so a student's submitted
// document (resume, ID proof, etc.) can be persisted in localStorage and
// actually previewed/downloaded by HR later — mirrors the same pattern
// already used for resumes (studentService) and BA requirement docs.
export function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// Turns a stored data URL back into a Blob so it can be opened in a new
// tab / downloaded via an object URL instead of navigating to a (possibly
// very long, and in some browsers blocked) data: URL directly.
export function dataURLToBlob(dataURL) {
  const [header, base64] = dataURL.split(",");
  const mimeMatch = header.match(/data:(.*?);base64/);
  const mime = mimeMatch ? mimeMatch[1] : "application/octet-stream";
  const binary = atob(base64);
  const array = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
  return new Blob([array], { type: mime });
}

// Other required documents tracked separately from the verification
// checklist above — general HR paperwork (not gating the pipeline stage)
// shown in the "Other Documents" tab of Letters & Certifications.
export const OTHER_REQUIRED_DOCUMENTS = [
  { key: "address_proof", label: "Address Proof" },
  { key: "bank_details", label: "Bank Account Details" },
  { key: "marksheets", label: "Educational Marksheets" },
];

export function freshOtherDocumentChecklist() {
  return OTHER_REQUIRED_DOCUMENTS.map((d) => ({ ...d, uploaded: false, fileName: null }));
}

export function loadRecruitments() {
  try {
    const raw = localStorage.getItem(RECRUITMENTS_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    // Run every record's stage through the legacy migration on read so
    // every screen (client/HR/student) always sees the current stage names,
    // regardless of when the record was originally created.
    return parsed.map((r) => (r.stage ? { ...r, stage: migrateStage(r.stage) } : r));
  } catch (e) {
    return [];
  }
}

export function saveRecruitments(list) {
  localStorage.setItem(RECRUITMENTS_KEY, JSON.stringify(list));
}

export function loadDocs() {
  try {
    const raw = localStorage.getItem(DOCS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

export function saveDocs(list) {
  localStorage.setItem(DOCS_KEY, JSON.stringify(list));
}

// Human-friendly one-line status the Student / Client / HR see for a given stage.
export function stageMessage(stage, viewer) {
  switch (stage) {
    case "Shortlisted":
      return viewer === "client"
        ? "Shortlisted — schedule the technical round"
        : viewer === "student"
        ? "You've been shortlisted by a client! Interview scheduling coming soon."
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
      return "Both interview rounds cleared — moving to document verification";
    case "Document Verification":
      return viewer === "student" ? "Upload your documents for HR verification" : "HR is verifying documents";
    case "Documents Verified":
      return "Documents verified — placement pending";
    case "Placement Confirmed":
      return "Placement confirmed — offer letter pending";
    case "Offer Letter Created":
      return "Offer letter created — sending for client review";
    case "Client Review & Signature":
      return viewer === "client" ? "Review & sign the offer letter" : "With client for review & signature";
    case "Student Approval":
      return viewer === "student" ? "Review & approve your offer" : "Awaiting student approval";
    case "Student Signature":
      return viewer === "student" ? "Sign your offer letter" : "Awaiting student signature";
    case "Placed":
      return "Placement completed 🎉";
    case REJECTED:
      return "Not selected this time";
    default:
      return stage;
  }
}