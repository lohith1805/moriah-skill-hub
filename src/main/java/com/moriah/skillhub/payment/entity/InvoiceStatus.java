package com.moriah.skillhub.payment.entity;

/** Mirrors the {@code chk_invoices_status} CHECK constraint in {@code V3__subscriptions_payments.sql}. */
public enum InvoiceStatus {
    PENDING,
    ISSUED,
    FAILED
}
