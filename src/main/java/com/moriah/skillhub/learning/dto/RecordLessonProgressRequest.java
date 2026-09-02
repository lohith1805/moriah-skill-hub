package com.moriah.skillhub.learning.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * {@code POST /api/v1/lessons/{id}/progress} (gap B1.4). The player posts its current position
 * ({@code watchedSeconds}) as the user watches, and sets {@code completed} true when the video
 * ends. {@code watchedSeconds} never moves backwards server-side — the stored value is the max
 * of the current and incoming figure, so scrubbing back does not lose the resume point.
 */
public record RecordLessonProgressRequest(
        @PositiveOrZero int watchedSeconds,
        boolean completed
) {
}
