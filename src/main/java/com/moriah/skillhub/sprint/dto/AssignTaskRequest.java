package com.moriah.skillhub.sprint.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code userUuid}, not {@code userId} — same "never expose/accept users.id" convention
 * {@code BatchController.removeStudent} already documents for its own path variable. */
public record AssignTaskRequest(
        @NotBlank String userUuid
) {
}
