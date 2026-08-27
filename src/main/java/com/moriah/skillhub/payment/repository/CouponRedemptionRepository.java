package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

/** No custom finder for "has this user already redeemed this coupon" — {@code CouponService}
 * relies on {@code uq_coupon_redemptions_coupon_user}'s constraint violation (a {@code save()}
 * that throws {@code DataIntegrityViolationException}), the same never-SELECT-then-INSERT
 * discipline as {@code WebhookEventRepository}. */
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
}
