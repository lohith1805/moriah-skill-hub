package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ChallengeSubmissionStatus;

import java.time.Instant;

/**
 * One challenge submission. {@code solutionCode} is included on the detail / review views and on
 * the student's own list (they wrote it); {@code studentName} / {@code studentUuid} are populated
 * only on the staff review view.
 */
public record ChallengeSubmissionResponse(
        Long id,
        Long challengeId,
        String challengeTitle,
        Long projectId,
        String projectTitle,
        String studentUuid,
        String studentName,
        String solutionCode,
        String notes,
        ChallengeSubmissionStatus status,
        String reviewerFeedback,
        Integer score,
        Instant submittedAt,
        Instant reviewedAt
) {
}
