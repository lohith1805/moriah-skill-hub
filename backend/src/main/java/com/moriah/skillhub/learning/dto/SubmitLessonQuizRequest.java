package com.moriah.skillhub.learning.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code POST /api/v1/lessons/{id}/quiz/submit} (gap B1.4). {@code answers[i]} is the chosen
 * 0-based option index for question {@code i}, in the order {@code GET /lessons/{id}/quiz}
 * returned them. A missing / out-of-range answer counts as wrong. */
public record SubmitLessonQuizRequest(
        @NotNull List<Integer> answers
) {
}
