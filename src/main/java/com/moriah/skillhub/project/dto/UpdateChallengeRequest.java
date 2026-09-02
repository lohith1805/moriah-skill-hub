package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/challenges/{id}} (gap B1.15). Edits the challenge's text fields only —
 * {@code title}, {@code expectedBehaviour}, {@code difficulty}. Replacing the broken-code or
 * test-script archive is a separate multipart concern and is out of scope for this JSON edit;
 * re-create the challenge for that.
 */
public record UpdateChallengeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String expectedBehaviour,
        ProjectDifficulty difficulty
) {
}
