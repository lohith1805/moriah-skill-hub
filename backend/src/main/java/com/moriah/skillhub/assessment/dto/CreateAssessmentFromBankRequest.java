package com.moriah.skillhub.assessment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/assessments/from-bank} — publish a reusable question bank as a live,
 * batch-scoped assessment students can take. The bank's questions (with their answer keys) are
 * <em>snapshotted</em> server-side into the new quiz, so editing the bank afterwards doesn't
 * change an assessment students may already have started. {@code batchId} is required — a quiz
 * with no batch never appears in any student's list. {@code title} defaults to the bank name;
 * {@code passPercentage}/{@code maxAttempts} to the platform defaults. {@code questionCount} is
 * the user's own ask — a bank might hold 200 questions with only, say, 30 meant to go into any one
 * attempt; {@code null} publishes every question in the bank (the old, only behavior), otherwise
 * {@code QuizService#createFromBank} snapshots a random {@code questionCount}-sized sample instead
 * of the whole bank — validated there (not here) since it depends on the bank's own current size.
 */
public record CreateAssessmentFromBankRequest(
        @NotNull Long bankId,
        @NotNull Long batchId,
        Long projectId,
        @Size(max = 200) String title,
        @NotNull @Min(1) @Max(300) Integer durationMinutes,
        @Min(1) @Max(100) Integer passPercentage,
        @Min(1) @Max(20) Integer maxAttempts,
        @Min(1) Integer questionCount
) {
}
