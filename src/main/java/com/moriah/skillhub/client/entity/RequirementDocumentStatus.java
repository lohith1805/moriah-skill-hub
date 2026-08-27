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
    APPROVED
}
