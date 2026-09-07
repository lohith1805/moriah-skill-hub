package com.moriah.skillhub.payment.entity;

/** Mirrors the {@code chk_payments_gateway} CHECK constraint in {@code V3__subscriptions_payments.sql}. */
public enum PaymentGateway {
    RAZORPAY,
    STRIPE
}
