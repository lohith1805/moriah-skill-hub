package com.moriah.skillhub.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /api/v1/assessments/banks/{id}} (gap B1.15). Full-field replace; {@code active}
 * lets a curator hide a bank, {@code DELETE} is the shortcut for {@code active = false}. */
public record UpdateQuestionBankRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String topic,
        @Size(max = 5000) String description,
        boolean active
) {
}
