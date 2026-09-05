package com.moriah.skillhub.client.entity;

/** build-plan.md feature 21 names the full flow as "DRAFT -> IN_REVIEW -> APPROVED," but this
 * feature's own endpoint list has no "save draft" or "submit for review" action at all — only
 * {@code POST /ba/documents} (create) and {@code PUT /ba/documents/{id}/approve}. {@code
 * RequirementDocumentService#create} sets {@code IN_REVIEW} directly, skipping {@code DRAFT}
 * entirely, so the one approval action this feature actually ships has something to act on
 * immediately. {@code DRAFT} is left in schema for a future edit-in-place workflow with no route
 * yet — the same "a schema state exists that no route in this feature reaches yet, and that's
 * deliberate" reasoning {@link com.moriah.skillhub.hr.entity.PayrollStatus}'s own Javadoc
 * documents for its own {@code DRAFT}/{@code PAID} values. */
public enum RequirementDocumentStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    /** Terminal, like {@code APPROVED} — any one required party (CLIENT/BUSINESS_ANALYST/
     * DEVELOPER, or ADMIN overriding) can reject a document that's still {@code IN_REVIEW},
     * recording why on {@code RequirementDocument.rejectionReason}. A rejected version is never
     * reopened or edited in place — the author resubmits via {@code RequirementDocumentService
     * #create} with the same {@code (clientProjectId, docType)} pair, which lands as a brand new
     * version with its own fresh approval slots (the existing versioning model, unchanged). */
    REJECTED
}
