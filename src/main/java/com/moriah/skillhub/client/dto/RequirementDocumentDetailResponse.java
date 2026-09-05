package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;

import java.time.Instant;
import java.util.List;

/**
 * The single-document view (gap B1.16) — everything {@link RequirementDocumentResponse} carries
 * plus {@code content}, the actual requirement text a developer needs to read. Kept separate
 * from the list DTO so a page of list rows never ships every document's full LONGTEXT.
 */
public record RequirementDocumentDetailResponse(
        Long id,
        Long clientProjectId,
        RequirementDocumentType docType,
        String title,
        int version,
        RequirementDocumentStatus status,
        String content,
        String authoredByUuid,
        String authoredByFullName,
        String approvedByUuid,
        String approvedByFullName,
        String devReviewedByUuid,
        String devReviewedByFullName,
        Instant devReviewedAt,
        List<RequirementDocumentApprovalResponse> approvals
) {
}
