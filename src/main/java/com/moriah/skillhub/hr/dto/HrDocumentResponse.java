package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.HrDocumentStatus;

import java.time.Instant;

/**
 * {@code downloadUrl} is a presigned S3 GET URL (15-minute TTL), re-signed on every read and never
 * stored — same pattern as {@code CertificateResponse}. It is populated on {@code GET
 * /api/v1/hr/documents}; {@code POST} (upload) and {@code PUT /{id}/verify} return {@code null}
 * for it (re-list to get a fresh link).
 */
public record HrDocumentResponse(
        Long id,
        String userUuid,
        String userFullName,
        String documentType,
        HrDocumentStatus verificationStatus,
        String verifiedByUuid,
        Instant verifiedAt,
        String rejectionReason,
        String downloadUrl,
        Instant createdAt
) {
}
