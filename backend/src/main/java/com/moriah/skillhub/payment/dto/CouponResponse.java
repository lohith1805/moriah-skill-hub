package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.CouponDiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Admin view of a coupon (gap B1.12). {@code timesRedeemed} is read-only telemetry;
 * {@code maxRedemptions} {@code null} means unlimited. */
public record CouponResponse(
        String code,
        CouponDiscountType discountType,
        BigDecimal discountValue,
        LocalDate validFrom,
        LocalDate validUntil,
        Integer maxRedemptions,
        int timesRedeemed,
        boolean active,
        Instant createdAt
) {
}
