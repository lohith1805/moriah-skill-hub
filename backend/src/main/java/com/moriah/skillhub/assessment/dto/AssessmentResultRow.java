package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One student's attempt at an assessment, for the author-facing results screen
 * ({@code GET /api/v1/assessments/results}). {@code percentage}/{@code passed} are the
 * server-graded outcome; {@code null} while an attempt is {@code PENDING_MANUAL_GRADING} (it has
 * ungraded CODE answers). {@code track} / {@code batchName} come from the quiz's batch, so the
 * caller can filter by cohort.
 */
public record AssessmentResultRow(
        Long attemptId,
        Long assessmentId,
        String assessmentTitle,
        Long batchId,
        String batchName,
        String track,
        String studentUuid,
        String studentName,
        Integer attemptNumber,
        AttemptStatus status,
        BigDecimal percentage,
        Boolean passed,
        Integer passMark,
        Instant submittedAt
) {
}
