package com.moriah.skillhub.certificate.dto;

import com.moriah.skillhub.certificate.entity.CertificateType;

import java.time.Instant;

/** {@code downloadUrl} is a presigned S3 URL re-signed on every response, never stored — the
 * persisted value is {@code pdfKey} (architecture.md "Object Storage": "Store the key ... never a
 * full URL — URLs expire"). {@code userUuid}/{@code batchId} carry no {@code users.id}/internal
 * ambiguity — the public identifier convention every other response in this project follows. */
public record CertificateResponse(
        Long id,
        String certificateNumber,
        String userUuid,
        String userFullName,
        Long batchId,
        String batchName,
        CertificateType certificateType,
        String verificationCode,
        String downloadUrl,
        Instant issuedAt,
        Instant revokedAt,
        String revokeReason
) {
}
