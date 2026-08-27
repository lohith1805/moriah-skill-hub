package com.moriah.skillhub.assessment.dto;

import jakarta.validation.Valid;

import java.util.List;

/** {@code answers} may legally be empty (a student submitting early having answered nothing yet
 * still gets a scored, terminal attempt rather than being blocked from submitting) — no
 * {@code @NotEmpty} here, unlike {@code CreateAssessmentRequest.questions}. */
public record SubmitAttemptRequest(
        @Valid List<SubmitAnswerRequest> answers
) {
}
