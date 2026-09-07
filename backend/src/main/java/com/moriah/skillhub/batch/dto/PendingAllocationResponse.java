package com.moriah.skillhub.batch.dto;

import java.time.Instant;

/**
 * One row of the "assignment pending" queue — {@code GET /api/v1/batches/pending-allocations}.
 * A student who paid for a batch-eligible plan on {@code trackCode} while no matching batch
 * existed. Cleared automatically when a batch for that track is created, or when a PM adds them
 * to a batch by hand. PM/ADMIN only, so the email is safe to include.
 */
public record PendingAllocationResponse(
        String studentUuid,
        String studentName,
        String studentEmail,
        String trackCode,
        String planCode,
        String reason,
        Instant requestedAt
) {
}
