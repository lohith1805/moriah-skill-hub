package com.moriah.skillhub.submission.dto;

import com.moriah.skillhub.submission.entity.WeeklyRating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code userUuid}, never a raw {@code users.id} — matches every other request boundary in this
 * project. An upsert by design: a second {@code POST} for the same student+week updates the
 * existing row instead of failing on V7's {@code uq_weekly_reviews_user_week} — no dedicated
 * {@code PUT} endpoint exists for a PM correcting a rating, and build-plan.md's endpoint list
 * only ever shows {@code POST /reviews/weekly}. */
public record CreateWeeklyReviewRequest(
        @NotBlank String userUuid,
        @NotNull Long batchId,
        @NotNull Long sprintId,
        @NotNull LocalDate weekStart,
        @NotNull WeeklyRating rating,
        @Size(max = 5000) String notes
) {
}
