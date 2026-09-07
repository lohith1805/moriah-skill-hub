package com.moriah.skillhub.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/v1/lessons/{id}/quiz/questions} (gap B1.4). MCQ only. {@code correctIndex}
 * must point at a real option — validated in the compact constructor.
 */
public record AddLessonQuizQuestionRequest(
        @NotBlank @Size(max = 2000) String questionText,
        @NotNull @Size(min = 2, max = 6) List<@NotBlank @Size(max = 500) String> options,
        @NotNull @PositiveOrZero Integer correctIndex,
        @Size(max = 2000) String explanation
) {
    public AddLessonQuizQuestionRequest {
        if (options != null && correctIndex != null && correctIndex >= options.size()) {
            throw new IllegalArgumentException("correctIndex must reference a valid option.");
        }
    }
}
