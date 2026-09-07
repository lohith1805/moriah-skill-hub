package com.moriah.skillhub.project.dto;

/** {@code UserService#getPortfolio}'s {@code completedProjects} field — {@code
 * ProjectService#findTitlesAndSlugs}'s return shape, reused directly by the portfolio response
 * (same cross-package-projection pattern as {@code certificate.dto.CertificateSummaryProjection}).
 * Deliberately just title/slug — a portfolio visitor gets "built X", not the full catalogue entry
 * (difficulty, tech stack, assets) {@code ProjectResponse} exposes to the portal itself. */
public record CompletedProjectProjection(String title, String slug) {
}
