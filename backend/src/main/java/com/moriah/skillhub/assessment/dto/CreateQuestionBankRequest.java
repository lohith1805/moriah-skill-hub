package com.moriah.skillhub.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/assessments/banks} (gap B1.15). Starts active. */
public record CreateQuestionBankRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String topic,
        @Size(max = 5000) String description
) {
}
