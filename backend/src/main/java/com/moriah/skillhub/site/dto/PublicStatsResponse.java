package com.moriah.skillhub.site.dto;

/**
 * {@code GET /api/v1/public/stats} — a handful of safe aggregate counts for the marketing
 * landing page's hero band. No per-person data, no financials; just totals anyone could infer
 * from public certificate verification anyway.
 */
public record PublicStatsResponse(
        long graduates,
        long activeLearners,
        long activeBatches,
        long placements,
        long certificatesIssued,
        long hiringPartners
) {
}
