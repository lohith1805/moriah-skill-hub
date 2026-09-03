package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the caller's own billing history ({@code GET /api/v1/subscriptions/me/invoices}).
 * Built from a {@code payments} row left-joined to its {@code invoices} row: the payment always
 * exists once captured, the invoice row + PDF are produced asynchronously
 * ({@code InvoiceGenerationJob}) a moment later, so {@code invoiceNumber}/{@code pdfUrl} are null
 * and {@code invoiceStatus} is {@code "PROCESSING"} in the brief window before that job runs (or
 * indefinitely, if it failed — visible as {@code "FAILED"} once it does).
 *
 * <p>{@code pdfUrl} is a short-lived pre-signed GET, regenerated on every call — never stored.
 */
public record InvoiceResponse(
        String invoiceNumber,
        String planCode,
        String planName,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus,
        String invoiceStatus,
        Instant paidAt,
        String pdfUrl,
        String reference
) {
}
