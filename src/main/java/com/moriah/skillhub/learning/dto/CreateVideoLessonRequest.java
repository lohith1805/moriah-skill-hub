package com.moriah.skillhub.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/lessons} (gap B1.4). {@code published} defaults to {@code false} so a
 * curator can stage a lesson before it goes live. */
public record CreateVideoLessonRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 120) String moduleName,
        @NotBlank @Size(max = 1000) String videoUrl,
        @Positive Integer durationSeconds,
        @PositiveOrZero int sortOrder,
        boolean published
) {
}
