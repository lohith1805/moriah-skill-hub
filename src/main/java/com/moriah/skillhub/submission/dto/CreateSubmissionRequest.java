package com.moriah.skillhub.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** build-plan.md feature 12: "Student submits { taskId, prUrl, videoUrl?, notes? }." No {@code
 * attemptNumber} field — always server-computed (never client-supplied), see {@code
 * SubmissionService.create}. */
public record CreateSubmissionRequest(
        @NotNull Long taskId,
        @NotBlank @Size(max = 500) String prUrl,
        @Size(max = 500) String videoUrl,
        @Size(max = 5000) String notes
) {
}
