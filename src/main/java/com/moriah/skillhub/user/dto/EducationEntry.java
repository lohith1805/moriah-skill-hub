package com.moriah.skillhub.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One entry in {@code UserProfile.education} — round-tripped through JSON text by
 * {@code ProfileService}, never persisted as its own table. */
public record EducationEntry(
        @NotBlank @Size(max = 150) String institution,
        @NotBlank @Size(max = 100) String degree,
        @Size(max = 100) String fieldOfStudy,
        @Min(1900) @Max(2100) Integer startYear,
        @Min(1900) @Max(2100) Integer endYear
) {
}
