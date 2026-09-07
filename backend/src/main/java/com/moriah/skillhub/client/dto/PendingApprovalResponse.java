package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.RequirementDocumentType;

/** {@code GET /api/v1/requirement-documents/pending-my-approval} — one row per document where the
 * caller's own sign-off slot (see {@code RequirementDocumentApprovalService#resolveCallerSlot})
 * is still open. Powers the cross-dashboard "Client Project Documents" inbox on the Client,
 * Developer, and BA portals alike — same shape regardless of which role is asking. */
public record PendingApprovalResponse(
        Long documentId,
        Long clientProjectId,
        String clientProjectTitle,
        RequirementDocumentType docType,
        String title,
        int version,
        String approverRole,
        String authoredByFullName
) {
}
