package com.moriah.skillhub.user.dto;

import java.net.URL;
import java.time.Instant;

/** {@code GET /api/v1/users/me/resume} — a fresh presigned URL, signed on every call rather than
 * cached (architecture.md "Object Storage": 15-minute TTL, {@code Constants.PRESIGNED_URL_TTL_MINUTES}). */
public record ResumeDownloadResponse(
        URL downloadUrl,
        Instant expiresAt
) {
}
