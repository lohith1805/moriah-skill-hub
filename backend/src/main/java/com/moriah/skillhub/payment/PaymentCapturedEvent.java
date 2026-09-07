package com.moriah.skillhub.payment;

/**
 * Published after a payment webhook's transaction commits (never before — publishing inside the
 * transaction would let {@link InvoiceGenerationJob} start rendering a PDF for a payment that
 * then rolls back). `/architect feature 07` decision: this in-process event, not feature 08's
 * not-yet-built Redis notification queue, is what triggers PDF generation — matches
 * progress-tracker.md's Nightly Job Chain listing this job's schedule as "async."
 */
public record PaymentCapturedEvent(Long paymentId) {
}
