package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin view of a {@code payments} row (gap B1.11). The public identifier is {@code
 * gatewayOrderId} — {@code payments} has no {@code uuid} and {@code id} never leaves the service
 * layer (architecture.md invariant). {@code gatewayPaymentId} is {@code null} until the capture
 * webhook lands. {@code invoiceNumber}/{@code invoiceStatus} are {@code null} until the async
 * {@code InvoiceGenerationJob} has produced the invoice row for a captured payment; download the
 * PDF via {@code GET /api/v1/admin/payments/{gatewayOrderId}/invoice}.
 */
public record AdminPaymentResponse(
        String gatewayOrderId,
        String gatewayPaymentId,
        String userUuid,
        String userFullName,
        Long planId,
        String planCode,
        String planName,
        String trackCode,
        PaymentGateway gateway,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant capturedAt,
        Instant createdAt,
        String invoiceNumber,
        String invoiceStatus
) {
}
