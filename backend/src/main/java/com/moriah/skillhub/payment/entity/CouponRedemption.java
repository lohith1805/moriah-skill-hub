package com.moriah.skillhub.payment.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * {@code coupon} and {@code payment} are real {@code @ManyToOne}s — both {@link Coupon} and
 * {@link Payment} are in this same {@code payment/} package. {@code uq_coupon_redemptions_coupon_user}
 * (V3) is what actually enforces "one redemption per user per coupon" — {@code CouponService}
 * relies on that unique-constraint violation the same way {@code WebhookIdempotencyService} relies
 * on {@code webhook_events.event_id}'s, never a {@code SELECT} then {@code INSERT}.
 */
@Entity
@Table(name = "coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class CouponRedemption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();
}
