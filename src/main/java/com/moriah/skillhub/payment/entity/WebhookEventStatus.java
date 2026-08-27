package com.moriah.skillhub.payment.entity;

/** Mirrors the {@code chk_webhook_events_status} CHECK constraint in {@code V3__subscriptions_payments.sql}. */
public enum WebhookEventStatus {
    RECEIVED,
    PROCESSED,
    FAILED
}
