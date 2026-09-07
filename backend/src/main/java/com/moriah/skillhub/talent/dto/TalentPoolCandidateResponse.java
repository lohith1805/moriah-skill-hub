package com.moriah.skillhub.talent.dto;

import java.util.List;

/**
 * One candidate in the talent pool (gap B1.9). A profile-only view — name, headline, skills and
 * the public portfolio slug. No contact details and no resume link (see
 * {@code V27__recruitment_requests.sql}'s header for why the resume is deliberately out of scope
 * here). The full public portfolio is still available at {@code GET /api/v1/portfolio/{slug}}.
 */
public record TalentPoolCandidateResponse(
        String uuid,
        String fullName,
        String currentTitle,
        String location,
        String experienceLevel,
        Integer yearsExperience,
        List<String> skills,
        String portfolioSlug,
        String bio
) {
}
