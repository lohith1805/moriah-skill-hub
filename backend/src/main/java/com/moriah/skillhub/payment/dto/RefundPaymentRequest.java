package com.moriah.skillhub.payment.dto;

import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/admin/payments/{gatewayOrderId}/refund} (gap B1.11). {@code reason} is
 * optional free text recorded on the audit entry; the refund is always for the full captured
 * amount (partial refunds are out of scope for this admin action). */
public record RefundPaymentRequest(
        @Size(max = 255) String reason
) {
}
