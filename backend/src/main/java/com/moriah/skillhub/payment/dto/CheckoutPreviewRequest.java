package com.moriah.skillhub.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/subscriptions/checkout/preview} — "what would I pay for this plan
 * with this coupon?", answered without creating a {@code payments} row or reserving coupon
 * capacity. {@code couponCode} is optional. */
public record CheckoutPreviewRequest(
        @NotBlank String planCode,
        @Size(max = 40) String couponCode
) {
}
