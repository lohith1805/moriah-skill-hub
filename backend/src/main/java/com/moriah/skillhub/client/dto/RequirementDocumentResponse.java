package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;

import java.time.Instant;
import java.util.List;

/** {@code approvedByUuid}/{@code approvedByFullName} name whoever completed the LAST still-open
 * slot (the one that flipped {@code status} to {@code APPROVED}) — {@code approvals} carries the
 * full multi-party picture (one row per required {@code CLIENT}/{@code BUSINESS_ANALYST}/{@code
 * DEVELOPER} slot; see {@code RequirementDocumentApprovalService}), so a dashboard can render
 * "Client ✅ · BA ✅ · Developer ⏳" instead of a single approve/pending flag. Both are {@code null}
 * until every required slot is filled. {@code devReviewedByUuid}/{@code devReviewedAt} are
 * {@code null} until a developer acknowledges the requirement (gap B1.16) — orthogonal to sign-off. */
public record RequirementDocumentResponse(
        Long id,
        Long clientProjectId,
        RequirementDocumentType docType,
        String title,
        int version,
        RequirementDocumentStatus status,
        String authoredByUuid,
        String authoredByFullName,
        String approvedByUuid,
        String approvedByFullName,
        String devReviewedByUuid,
        String devReviewedByFullName,
        Instant devReviewedAt,
        String rejectedByUuid,
        String rejectedByFullName,
        Instant rejectedAt,
        String rejectionReason,
        List<RequirementDocumentApprovalResponse> approvals
) {
}
