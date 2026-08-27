package com.moriah.skillhub.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code githubUsername} lives on {@code users}, not {@code user_profiles} — build-plan.md
 * feature 09: "github_username editable here as a fallback for password-registered users" (OAuth2
 * users already have it populated by {@code OAuth2Service}; this is the manual-entry path for
 * everyone else). Every other field is {@code user_profiles}.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String githubUsername,
        @Size(max = 2000) String bio,
        @Size(max = 150) String location,
        @Size(max = 150) String currentTitle,
        @Size(max = 30) String experienceLevel,
        @Min(0) @Max(60) Integer yearsExperience,
        @Size(max = 30) List<@NotBlank @Size(max = 50) String> skills,
        @Size(max = 20) List<@Valid EducationEntry> education,
        @Size(max = 20) List<@Valid WorkExperienceEntry> workExperience
) {
}
