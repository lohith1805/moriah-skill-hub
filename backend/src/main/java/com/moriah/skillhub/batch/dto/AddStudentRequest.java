package com.moriah.skillhub.batch.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code userUuid}, never a raw {@code users.id} — matches the public-identifier convention
 * used at every other request boundary in this project. */
public record AddStudentRequest(
        @NotBlank String userUuid
) {
}
