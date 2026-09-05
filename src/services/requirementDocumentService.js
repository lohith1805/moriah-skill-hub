import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// The shared "Client Project Documents" surface — CLIENT / BUSINESS_ANALYST /
// DEVELOPER / ADMIN all read and act through the same three endpoints, so one
// service (and one page component, see pages/shared/ClientProjectDocuments)
// backs all three portals:
//   GET  /api/v1/requirement-documents/pending-my-approval
//   GET  /api/v1/requirement-documents?clientProjectId=&status=
//   GET  /api/v1/requirement-documents/{id}
//   POST /api/v1/requirement-documents/{id}/approve
// The backend infers which role slot the caller may fill (the project's own
// client contact, its assigned developer, any BA other than the document's
// author, or ADMIN fast-tracking everything still pending) — the frontend
// never has to know or send that itself.
// ---------------------------------------------------------------------------

const REQ_DOC_STATUS_TO_FE = { DRAFT: "Draft", IN_REVIEW: "In Review", APPROVED: "Approved", REJECTED: "Rejected" };
const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

function toFeApproval(a) {
  return {
    role: a.approverRole,
    approvedByUuid: a.approvedByUuid || null,
    approvedByName: a.approvedByFullName || "",
    approvedAt: a.approvedAt || null,
    pending: !a.approvedByUuid,
  };
}

function toFeDocument(d) {
  return {
    id: d.id,
    clientProjectId: d.clientProjectId,
    docType: d.docType,
    title: d.title,
    version: d.version,
    status: d.status,
    statusLabel: REQ_DOC_STATUS_TO_FE[d.status] || d.status,
    authoredByUuid: d.authoredByUuid || "",
    authoredByName: d.authoredByFullName || "",
    approvedByName: d.approvedByFullName || "",
    devReviewedByName: d.devReviewedByFullName || "",
    devReviewedAt: d.devReviewedAt || null,
    rejectedByName: d.rejectedByFullName || "",
    rejectedAt: d.rejectedAt || null,
    rejectionReason: d.rejectionReason || "",
    approvals: (d.approvals || []).map(toFeApproval),
  };
}

// One row per document where the caller's own sign-off slot is still open —
// the "needs your approval" inbox.
export async function getPendingMyApprovals() {
  const res = await apiClient.get("/requirement-documents/pending-my-approval");
  const rows = Array.isArray(res) ? res : [];
  return rows.map((r) => ({
    documentId: r.documentId,
    clientProjectId: r.clientProjectId,
    clientProjectTitle: r.clientProjectTitle || "",
    docType: r.docType,
    title: r.title,
    version: r.version,
    approverRole: r.approverRole,
    authoredByName: r.authoredByFullName || "",
  }));
}

export async function getClientProjectDocuments({ clientProjectId, status } = {}) {
  const res = await apiClient.get("/requirement-documents", {
    clientProjectId,
    status: status || undefined,
    size: 100,
  });
  return asRows(res).map(toFeDocument);
}

export async function getClientProjectDocumentDetail(id) {
  const d = await apiClient.get(`/requirement-documents/${id}`);
  return { ...toFeDocument(d), content: d.content || "" };
}

// Fills whichever slot the backend decides belongs to the caller —
// CLIENT/DEVELOPER/BUSINESS_ANALYST each get exactly one shot per document;
// ADMIN fast-tracks every remaining slot in this one call.
export async function approveClientProjectDocument(id) {
  const d = await apiClient.post(`/requirement-documents/${id}/approve`);
  return toFeDocument(d);
}

// Any one required party rejecting kills the whole document version immediately — no need for
// every slot to weigh in first. reason is required; the author resubmits as a new version rather
// than editing this one in place.
export async function rejectClientProjectDocument(id, reason) {
  const d = await apiClient.post(`/requirement-documents/${id}/reject`, { reason });
  return toFeDocument(d);
}
