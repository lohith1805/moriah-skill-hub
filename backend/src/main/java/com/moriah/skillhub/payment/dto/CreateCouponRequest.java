package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.CouponDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code POST /api/v1/admin/coupons} (gap B1.12). {@code code} is the coupon's stable public
 * identifier — checkout looks it up by {@code code}, so it is set once here and never editable
 * afterwards (mirrors {@code UpdatePlanRequest} excluding {@code code}). Upper-cased and
 * whitespace-free so the value the customer types at checkout always matches.
 * <p>
 * {@code discountValue} for {@code PERCENTAGE} is a percent (e.g. {@code 20} = 20% off); for
 * {@code FLAT} it is a rupee amount. {@code maxRedemptions} {@code null} = unlimited, matching
 * {@code Coupon.maxRedemptions} / {@code CouponRepository.tryReserveRedemption}.
 */
public record CreateCouponRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]*$",
                message = "code must be upper-case letters, digits, hyphen or underscore") String code,
        @NotNull CouponDiscountType discountType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal discountValue,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validUntil,
        @PositiveOrZero Integer maxRedemptions,
        boolean active
) {
}
