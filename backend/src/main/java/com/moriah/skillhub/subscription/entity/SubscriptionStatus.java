package com.moriah.skillhub.subscription.entity;

/** Mirrors the {@code chk_user_subscriptions_status} CHECK constraint in {@code V3__subscriptions_payments.sql}. */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
