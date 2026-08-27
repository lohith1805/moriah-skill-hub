package com.moriah.skillhub.submission.dto;

import com.moriah.skillhub.submission.entity.WeeklyRating;

import java.time.Instant;
import java.time.LocalDate;

public record WeeklyReviewResponse(
        Long id,
        String studentUuid,
        String studentName,
        Long batchId,
        Long sprintId,
        LocalDate weekStart,
        WeeklyRating rating,
        String notes,
        String reviewedByUuid,
        Instant reviewedAt
) {
}
