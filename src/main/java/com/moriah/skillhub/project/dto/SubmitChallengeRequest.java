package com.moriah.skillhub.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A student's rewritten fix for a bug-fix challenge. {@code solutionCode} is the whole rewritten
 * file / snippet, pasted in the browser; {@code notes} is an optional "what I changed and why". */
public record SubmitChallengeRequest(
        @NotBlank @Size(max = 60_000) String solutionCode,
        @Size(max = 4_000) String notes
) {
}
