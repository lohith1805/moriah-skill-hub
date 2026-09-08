package com.moriah.skillhub.user.dto;

import java.net.URL;
import java.time.Instant;

/**
 * {@code GET /api/v1/users/me/signature} — a fresh presigned URL for the caller's saved
 * signature image, or {@code SIGNATURE_NOT_FOUND} (404) when none is on file. Mirrors
 * {@code ResumeDownloadResponse}.
 */
public record SignatureResponse(
        URL downloadUrl,
        Instant expiresAt
) {
}
