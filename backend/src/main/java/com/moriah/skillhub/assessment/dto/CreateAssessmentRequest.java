package com.moriah.skillhub.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code batchId}/{@code projectId} are both optional (V8: {@code quizzes.batch_id}/{@code
 * project_id} are both nullable) — a quiz can be batch-scoped, project-scoped, or neither.
 * {@code passPercentage}/{@code maxAttempts} default to {@code Constants.QUIZ_PASS_PERCENTAGE}/
 * {@code QUIZ_DEFAULT_MAX_ATTEMPTS} in {@code QuizService} when omitted, matching V8's own column
 * defaults. */
public record CreateAssessmentRequest(
        Long batchId,
        Long projectId,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Min(1) @Max(300) Integer durationMinutes,
        @Min(1) @Max(100) Integer passPercentage,
        @Min(1) @Max(20) Integer maxAttempts,
        @NotEmpty @Valid List<CreateQuestionRequest> questions
) {
}
