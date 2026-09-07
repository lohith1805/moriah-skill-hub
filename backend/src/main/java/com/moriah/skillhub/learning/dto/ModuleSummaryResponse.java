package com.moriah.skillhub.learning.dto;

/** {@code GET /api/v1/lessons/modules} — one entry per distinct published module, with its
 * lesson count, so the FE can render the module navigation before loading any lessons. */
public record ModuleSummaryResponse(
        String moduleName,
        long lessonCount
) {
}
