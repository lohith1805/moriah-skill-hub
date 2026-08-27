package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentGateway;

import java.math.BigDecimal;

/** Gateway-discriminated by {@code gateway} — Razorpay's client-side Checkout.js needs {@code
 * razorpayOrderId}/{@code razorpayKeyId} (never the key *secret*) to render its own payment
 * modal; Stripe's hosted Checkout just needs a URL to redirect the browser to. Only the fields
 * for the chosen gateway are populated, the other gateway's fields stay null
 * (`/architect feature 07` decision). */
public record CheckoutResponse(
        PaymentGateway gateway,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String razorpayOrderId,
        String razorpayKeyId,
        String stripeCheckoutUrl
) {
}
