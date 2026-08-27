package com.moriah.skillhub.certificate.dto;

import com.moriah.skillhub.certificate.entity.CertificateType;

import java.time.Instant;

/** {@code GET /api/v1/certificates/verify/{code}} — public, unauthenticated. build-plan.md
 * feature 20: "returns only holder name, batch, type, issue date, validity. Never email, phone,
 * scores, or PIP history." This record physically cannot carry any of those — same discipline
 * {@code PortfolioResponse} already established for feature 09. A revoked certificate still
 * returns {@code 200} with {@code valid: false} and {@code revokedAt} populated — never a
 * {@code 404}; an unknown code is the only case that 404s (that distinction is made by {@code
 * CertificateService#verify} before this record is ever built). */
public record PublicVerificationResponse(
        String holderFullName,
        String batchName,
        String trackCode,
        CertificateType certificateType,
        Instant issuedAt,
        boolean valid,
        Instant revokedAt
) {
}
