package com.moriah.skillhub.certificate.dto;

import com.moriah.skillhub.certificate.entity.CertificateType;

import java.time.Instant;

/** {@code UserService#getPortfolio}'s {@code issuedCertificates} field — the cross-package
 * projection {@code CertificateService#issuedCertificatesFor} returns, reused directly the same
 * way {@code batch.dto.ActiveMemberProjection}/{@code metrics.dto.StudentMetricProjection} already
 * cross a module boundary as plain read-only shapes rather than full response DTOs. Deliberately
 * excludes {@code id}/{@code pdfKey}/{@code batchId} — a portfolio visitor gets an achievement
 * badge, not a certificate management view. */
public record CertificateSummaryProjection(
        CertificateType certificateType,
        Instant issuedAt,
        String verificationCode
) {
}
