package com.moriah.skillhub.talent.dto;

import com.moriah.skillhub.talent.entity.EngagementType;
import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;

import java.time.Instant;

/** A recruitment request (gap B1.9). Decision fields are {@code null} while {@code PENDING}. */
public record RecruitmentRequestResponse(
        Long id,
        String candidateUuid,
        String candidateName,
        String requestedByUuid,
        String roleTitle,
        EngagementType engagementType,
        String message,
        RecruitmentRequestStatus status,
        String decisionNote,
        String decidedByUuid,
        Instant decidedAt,
        Instant createdAt
) {
}
