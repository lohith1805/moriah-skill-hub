package com.moriah.skillhub.payment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Not in architecture.md's package diagram by name (only {@code Payment}/{@code Invoice}/{@code
 * WebhookEvent} are listed for {@code payment/}) — placed here anyway since coupon redemption is
 * exclusively a checkout-time concern, owned by {@code CheckoutController}/{@code
 * CheckoutService}, which live in this package.
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
public class Coupon extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    // columnDefinition matches V3's INT UNSIGNED exactly — see SubscriptionPlan for why.
    @Column(name = "max_redemptions", columnDefinition = "INT UNSIGNED")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false, columnDefinition = "INT UNSIGNED")
    private int timesRedeemed;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
