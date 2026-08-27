package com.moriah.skillhub.user.dto;

import java.util.List;

/** {@code GET /api/v1/users/me} and the response of every mutation on it. Never {@code users.id}
 * or {@code resumeKey} directly — {@code hasResume} plus the dedicated
 * {@code GET /api/v1/users/me/resume} presigned-URL endpoint is the safe equivalent (a raw
 * storage key is meaningless to a client anyway; signing it is {@code ResumeService}'s job). */
public record UserProfileResponse(
        String uuid,
        String fullName,
        String email,
        String phone,
        String githubUsername,
        String linkedinUrl,
        String bio,
        String location,
        String currentTitle,
        String experienceLevel,
        Integer yearsExperience,
        List<String> skills,
        List<EducationEntry> education,
        List<WorkExperienceEntry> workExperience,
        boolean hasResume,
        String portfolioSlug,
        boolean isComplete,
        int completionPercent
) {
}
