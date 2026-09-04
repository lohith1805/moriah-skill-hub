package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One of the calling student's own attempts, for the student assessment list
 * ({@code GET /api/v1/assessments/attempts/me}). Lets the UI show "Completed —
 * Passed 82%" / "Failed" / "In progress" per assessment instead of always
 * offering "Start Assessment". {@code percentage}/{@code passed} are {@code null}
 * while an attempt is still {@code IN_PROGRESS} or {@code PENDING_MANUAL_GRADING}.
 */
public record MyAssessmentAttemptRow(
        Long attemptId,
        Long assessmentId,
        String assessmentTitle,
        Long batchId,
        Integer attemptNumber,
        AttemptStatus status,
        BigDecimal percentage,
        Boolean passed,
        Integer passMark,
        Instant submittedAt
) {
}
