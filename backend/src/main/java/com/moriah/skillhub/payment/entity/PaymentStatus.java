package com.moriah.skillhub.payment.entity;

/** Mirrors the {@code chk_payments_status} CHECK constraint in {@code V3__subscriptions_payments.sql}. */
public enum PaymentStatus {
    CREATED,
    PENDING,
    CAPTURED,
    FAILED,
    REFUNDED
}
