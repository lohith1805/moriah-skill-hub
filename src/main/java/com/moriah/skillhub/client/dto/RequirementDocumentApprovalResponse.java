package com.moriah.skillhub.client.dto;

import java.time.Instant;

/** One row of {@link RequirementDocumentResponse#approvals()} / {@link
 * RequirementDocumentDetailResponse#approvals()} — one required sign-off slot for the document
 * (see {@code RequirementDocumentApproval}). {@code approverRole} is one of {@code CLIENT},
 * {@code BUSINESS_ANALYST}, {@code DEVELOPER} depending on the document's {@code docType}.
 * {@code approvedByUuid}/{@code approvedByFullName}/{@code approvedAt} are all {@code null} while
 * the slot is still pending. */
public record RequirementDocumentApprovalResponse(
        String approverRole,
        String approvedByUuid,
        String approvedByFullName,
        Instant approvedAt
) {
}
