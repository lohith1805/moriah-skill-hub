package com.moriah.skillhub.user.dto;

import java.util.List;

/**
 * {@code GET /api/v1/portfolio/{slug}} — public, unauthenticated. build-plan.md feature 09:
 * "name, title, skills, completed projects, issued certificates only. Never email, phone,
 * scores, or PIP status." {@code completedProjects}/{@code issuedCertificates} are the
 * tracked Open Stub in {@code progress-tracker.md} — {@code project}/{@code certificate} don't
 * exist as features yet, so there's nothing to query; adding a guessed shape now would only mean
 * a breaking change later. Everything else below is real.
 */
public record PortfolioResponse(
        String fullName,
        String currentTitle,
        String bio,
        String location,
        List<String> skills
) {
}
