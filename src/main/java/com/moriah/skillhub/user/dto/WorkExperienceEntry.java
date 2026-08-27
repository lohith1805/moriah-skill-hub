package com.moriah.skillhub.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One entry in {@code UserProfile.workExperience} — round-tripped through JSON text by
 * {@code ProfileService}, never persisted as its own table. {@code endDate} null means "current
 * role", the same convention {@code build-plan.md}'s other date-range fields use. */
public record WorkExperienceEntry(
        @NotBlank @Size(max = 150) String company,
        @NotBlank @Size(max = 150) String title,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 1000) String description
) {
}
