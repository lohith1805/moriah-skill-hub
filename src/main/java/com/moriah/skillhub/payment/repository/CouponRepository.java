package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    /**
     * Conditional atomic update — the same "no {@code @Version}, no retry loop" pattern
     * code-standards.md's "Transactions" section mandates for {@code batches.enrolled_count}
     * (a hot counter under launch-day load would thrash on retries). {@code NULL max_redemptions}
     * is unlimited, so the {@code OR} branch skips the cap entirely for those coupons. Returns 0
     * when the coupon is already at capacity — {@code CouponService} treats that as {@code
     * COUPON_EXHAUSTED}, never a silent no-op.
     */
    @Modifying
    @Query("""
            UPDATE Coupon c SET c.timesRedeemed = c.timesRedeemed + 1
             WHERE c.id = :couponId AND (c.maxRedemptions IS NULL OR c.timesRedeemed < c.maxRedemptions)
            """)
    int tryReserveRedemption(@Param("couponId") Long couponId);
}
