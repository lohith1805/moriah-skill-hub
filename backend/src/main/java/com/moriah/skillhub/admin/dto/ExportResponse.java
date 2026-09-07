package com.moriah.skillhub.admin.dto;

import java.time.Instant;

/**
 * {@code POST /admin/exports/{report}}. {@code deliveredInline} reflects the row-count threshold
 * build-plan.md's own wording draws ("delivered by presigned URL above 1,000 rows") — {@code
 * downloadUrl} is a presigned URL either way today (see {@code ExportService}'s own Javadoc for
 * why: no precedent in this codebase for returning raw file bytes in a JSON response, and
 * build-plan.md is silent on what happens at or under the threshold, so always-presign is a
 * documented, permitted simplification). {@code deliveredInline} is kept as an honest signal of
 * which regime applied, and as the seam a future "return bytes directly for small exports" change
 * would hang off without touching the {@code false} branch's callers.
 */
public record ExportResponse(
        String report,
        String format,
        long rowCount,
        boolean deliveredInline,
        String downloadUrl,
        Instant urlExpiresAt
) {
}
