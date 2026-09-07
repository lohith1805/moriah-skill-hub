package com.moriah.skillhub.user.dto;

import com.moriah.skillhub.certificate.dto.CertificateSummaryProjection;
import com.moriah.skillhub.project.dto.CompletedProjectProjection;

import java.util.List;

/**
 * {@code GET /api/v1/portfolio/{slug}} — public, unauthenticated. build-plan.md feature 09:
 * "name, title, skills, completed projects, issued certificates only. Never email, phone,
 * scores, or PIP status."
 * <p>
 * {@code completedProjects}/{@code issuedCertificates} were the tracked Open Stub in
 * {@code progress-tracker.md} — {@code project}/{@code certificate} didn't exist as features yet
 * when this record was first written, so there was nothing to query. Cleared at feature 20:
 * {@code issuedCertificates} is the caller's own non-revoked certificates only (a revoked one is
 * never shown as an achievement — {@code CertificateService#issuedCertificatesFor}'s own Javadoc);
 * {@code completedProjects} is derived from {@code Task.projectId} + {@code TaskStatus.COMPLETED}
 * (there is no per-student "project completion" table in this schema — see {@code
 * TaskService#completedProjectIdsFor}'s Javadoc for the full reasoning). Both reuse their owning
 * module's cross-package projection type directly rather than a portfolio-specific duplicate,
 * matching the {@code ActiveMemberProjection}/{@code StudentMetricProjection} precedent.
 */
public record PortfolioResponse(
        String fullName,
        String currentTitle,
        String bio,
        String location,
        List<String> skills,
        List<CompletedProjectProjection> completedProjects,
        List<CertificateSummaryProjection> issuedCertificates
) {
}
