package com.moriah.skillhub;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.CouponService;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `/architect feature 07` decision: full coupon validation, both races closed atomically — the
 * capacity cap ({@code coupons.times_redeemed < max_redemptions}) and the one-per-user rule
 * ({@code uq_coupon_redemptions_coupon_user}) are both enforced by real DB constraints, not
 * application-level pre-checks, so this needs a real database, not mocked repositories.
 * <p>
 * {@code @Transactional} rolls each test's fixture rows back — safe here because {@link
 * CouponService} runs entirely in-process on the test thread, same reasoning as {@code
 * EntitlementGuardIT}.
 */
@Transactional
class CouponServiceIT extends IntegrationTestBase {

    @Autowired
    private CouponService couponService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void percentageCoupon_previewDiscountsCorrectly() {
        String code = insertCoupon("PERCENTAGE", "20.00", 1, true, -1, 1);

        CouponService.CouponPreview preview = couponService.preview(code, new BigDecimal("14999.00"));

        assertThat(preview.discountedAmount()).isEqualByComparingTo("11999.20");
    }

    @Test
    void flatCoupon_previewNeverGoesBelowZero() {
        String code = insertCoupon("FLAT", "999999.00", 1, true, -1, 1);

        CouponService.CouponPreview preview = couponService.preview(code, new BigDecimal("100.00"));

        assertThat(preview.discountedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void redeem_secondUserAfterCapacityExhausted_throwsCouponExhausted() {
        String code = insertCoupon("FLAT", "500.00", 1, true, -1, 1);
        User firstUser = insertUser();
        User secondUser = insertUser();
        Payment firstPayment = insertPayment(firstUser);
        Payment secondPayment = insertPayment(secondUser);

        couponService.redeem(code, firstUser, firstPayment);

        assertThatThrownBy(() -> couponService.redeem(code, secondUser, secondPayment))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_EXHAUSTED));
    }

    @Test
    void redeem_sameUserTwice_throwsAlreadyRedeemed_evenWithCapacityRemaining() {
        // max_redemptions=2 — capacity alone would allow a second redemption; the per-user
        // uniqueness constraint is what must reject this, not the capacity check.
        String code = insertCoupon("FLAT", "500.00", 2, true, -1, 1);
        User user = insertUser();
        Payment firstPayment = insertPayment(user);
        Payment secondPayment = insertPayment(user);

        couponService.redeem(code, user, firstPayment);

        assertThatThrownBy(() -> couponService.redeem(code, user, secondPayment))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_ALREADY_REDEEMED));

        // Capacity was not consumed by the rejected attempt — the insert-first ordering means a
        // failed redemption never burns a unit of capacity it didn't actually grant.
        Integer timesRedeemed = jdbcTemplate.queryForObject(
                "SELECT times_redeemed FROM coupons WHERE code = ?", Integer.class, code);
        assertThat(timesRedeemed).isEqualTo(1);
    }

    @Test
    void redeem_unlimitedCoupon_neverExhausts() {
        String code = insertCoupon("FLAT", "500.00", null, true, -1, 1);
        for (int i = 0; i < 3; i++) {
            User user = insertUser();
            Payment payment = insertPayment(user);
            couponService.redeem(code, user, payment);
        }

        Integer timesRedeemed = jdbcTemplate.queryForObject(
                "SELECT times_redeemed FROM coupons WHERE code = ?", Integer.class, code);
        assertThat(timesRedeemed).isEqualTo(3);
    }

    @Test
    void preview_expiredCoupon_throwsCouponExpired() {
        String code = insertCoupon("FLAT", "500.00", 10, true, -30, -1); // validUntil in the past

        assertThatThrownBy(() -> couponService.preview(code, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_EXPIRED));
    }

    @Test
    void preview_inactiveCoupon_throwsCouponExpired() {
        String code = insertCoupon("FLAT", "500.00", 10, false, -1, 1);

        assertThatThrownBy(() -> couponService.preview(code, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_EXPIRED));
    }

    @Test
    void preview_unknownCode_throwsCouponNotFound() {
        assertThatThrownBy(() -> couponService.preview("DOES-NOT-EXIST", new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND));
    }

    private String insertCoupon(String discountType, String discountValue, Integer maxRedemptions,
                                 boolean active, int validFromOffsetDays, int validUntilOffsetDays) {
        String code = "TEST-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO coupons (code, discount_type, discount_value, valid_from, valid_until,
                                      max_redemptions, is_active)
                VALUES (?, ?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), DATE_ADD(CURDATE(), INTERVAL ? DAY), ?, ?)
                """, code, discountType, discountValue, validFromOffsetDays, validUntilOffsetDays,
                maxRedemptions, active);
        return code;
    }

    private User insertUser() {
        String email = "coupon-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Coupon Test', ?, 'ACTIVE')
                """, email);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return userRepository.findById(id).orElseThrow();
    }

    private Payment insertPayment(User user) {
        String orderId = "order_" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO payments (user_id, plan_id, gateway, gateway_order_id, amount, currency, status)
                VALUES (?, ?, 'RAZORPAY', ?, 100.00, 'INR', 'CREATED')
                """, user.getId(), insertPlan(), orderId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, orderId);
        return paymentRepository.findById(id).orElseThrow();
    }

    /** A fresh plan per payment, not a reused seeded id — same "insert your own fixtures" pattern
     * as {@code EntitlementGuardIT}, rather than assuming which row V5's seed happened to land at. */
    private long insertPlan() {
        // subscription_plans.code is VARCHAR(30) — a full UUID doesn't fit, an 8-char slice does.
        String code = "PLAN-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO subscription_plans (code, name, price_inr, tier_rank, duration_days, is_active)
                VALUES (?, ?, 100.00, 1, 30, TRUE)
                """, code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
    }
}
