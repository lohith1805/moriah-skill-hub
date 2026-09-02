package com.moriah.skillhub.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/v1/lessons/{id}} (gap B1.4). Full-field replace, matching every other
 * {@code Update*Request}. {@code published} here is the same toggle {@code DELETE} flips to
 * {@code false}. */
public record UpdateVideoLessonRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 120) String moduleName,
        @NotBlank @Size(max = 1000) String videoUrl,
        @Positive Integer durationSeconds,
        @PositiveOrZero int sortOrder,
        boolean published
) {
}
