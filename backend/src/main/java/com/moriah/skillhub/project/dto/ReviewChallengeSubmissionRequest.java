package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ChallengeSubmissionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A developer / trainer's verdict on a challenge submission. {@code status} must be a terminal
 * value ({@code ACCEPTED} / {@code NEEDS_WORK}); {@code score} is optional 0-100. */
public record ReviewChallengeSubmissionRequest(
        @NotNull ChallengeSubmissionStatus status,
        @Size(max = 4_000) String feedback,
        @Min(0) @Max(100) Integer score
) {
}
