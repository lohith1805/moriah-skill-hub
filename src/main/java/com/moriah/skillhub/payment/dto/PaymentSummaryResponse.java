package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/v1/admin/payments/summary} (gap B1.11) — dashboard tiles for the admin
 * transactions screen. {@code byStatus} always carries a bucket for every {@link PaymentStatus},
 * zero-filled where there are no rows, so the FE never has to guess which keys are present.
 * {@code totalCaptured} / {@code totalRefunded} are the two figures the refund UI leads with.
 */
public record PaymentSummaryResponse(
        List<StatusBucket> byStatus,
        long totalCount,
        BigDecimal totalCaptured,
        BigDecimal totalRefunded
) {

    public record StatusBucket(PaymentStatus status, long count, BigDecimal totalAmount) {
    }
}
