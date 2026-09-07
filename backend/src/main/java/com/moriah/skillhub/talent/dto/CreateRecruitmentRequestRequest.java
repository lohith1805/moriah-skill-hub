package com.moriah.skillhub.talent.dto;

import com.moriah.skillhub.talent.entity.EngagementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/recruitment-requests} (gap B1.9) — a CLIENT asks to recruit a candidate.
 * Lands {@code PENDING} for ADMIN/HR review. */
public record CreateRecruitmentRequestRequest(
        @NotBlank String candidateUuid,
        @NotBlank @Size(max = 150) String roleTitle,
        @NotNull EngagementType engagementType,
        @Size(max = 5000) String message
) {
}
