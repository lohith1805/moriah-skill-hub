package com.moriah.skillhub.talent.dto;

import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/recruitment-requests/{id}/status} (gap B1.9) — ADMIN/HR_MANAGER decides a
 * {@code PENDING} request. {@code status} must be {@code APPROVED} or {@code REJECTED} — the
 * compact constructor rejects any other value.
 */
public record DecideRecruitmentRequestRequest(
        @NotNull RecruitmentRequestStatus status,
        @Size(max = 500) String decisionNote
) {
    public DecideRecruitmentRequestRequest {
        if (status != null && status != RecruitmentRequestStatus.APPROVED
                && status != RecruitmentRequestStatus.REJECTED) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
    }
}
