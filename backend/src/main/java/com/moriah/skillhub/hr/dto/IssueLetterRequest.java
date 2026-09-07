package com.moriah.skillhub.hr.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueLetterRequest(@NotBlank String userUuid) {
}
