package com.moriah.skillhub.payment.dto;

import java.math.BigDecimal;

/**
 * Result of {@code POST /api/v1/subscriptions/checkout/preview}. {@code payableAmount} is what
 * {@code POST /checkout} would charge for the same plan + coupon — the discount is computed by
 * the same {@code CouponService.preview} the real checkout uses, so the number shown here and
 * the number charged agree. An invalid / expired / exhausted coupon is not an error here:
 * {@code couponApplied} is {@code false}, {@code payableAmount == originalAmount}, and
 * {@code couponMessage} says why.
 */
public record CheckoutPreviewResponse(
        String planCode,
        String planName,
        BigDecimal originalAmount,
        BigDecimal payableAmount,
        String currency,
        boolean couponApplied,
        String couponMessage
) {
}
