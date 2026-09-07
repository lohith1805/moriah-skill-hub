package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.CouponDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code PUT /api/v1/admin/coupons/{code}} (gap B1.12). A full-field replace, not a partial
 * patch — same convention as every other {@code Update*Request} in this codebase. {@code code}
 * and {@code timesRedeemed} are deliberately not here: {@code code} is the immutable identifier
 * and {@code timesRedeemed} is a runtime counter owned by the redemption flow, never set by an
 * admin.
 */
public record UpdateCouponRequest(
        @NotNull CouponDiscountType discountType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal discountValue,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @PositiveOrZero Integer maxRedemptions,
        boolean active
) {
}
