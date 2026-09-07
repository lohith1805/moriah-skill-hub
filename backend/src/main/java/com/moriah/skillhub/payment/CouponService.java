package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.entity.Coupon;
import com.moriah.skillhub.payment.entity.CouponRedemption;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.repository.CouponRedemptionRepository;
import com.moriah.skillhub.payment.repository.CouponRepository;
import com.moriah.skillhub.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * `/architect feature 07` decision: full validation (active, in date range, under
 * max_redemptions, one per user), both races closed atomically — never a SELECT-then-check.
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    public record CouponPreview(Coupon coupon, BigDecimal discountedAmount) {
    }

    /** Read-only preview at checkout time — does not reserve capacity or record a redemption.
     * {@code CheckoutService} calls {@link #redeem} separately, only after the {@code payments}
     * row this redemption will reference actually exists.
     *
     * <p>Deliberately NOT {@code @Transactional}: it only reads a couple of non-lazy columns off
     * {@code coupons}, and an invalid/expired coupon throws {@link BusinessException}. When this
     * ran inside the caller's transaction ({@code CheckoutService.previewCheckout}, which is
     * {@code readOnly}), that throw marked the shared transaction rollback-only — so even though
     * {@code previewCheckout} catches it and returns a clean {@code couponApplied=false} body, the
     * commit afterward failed with {@code UnexpectedRollbackException} and the client saw a
     * generic 500 instead of "coupon not valid". Running outside a transaction lets the exception
     * propagate to the caller's {@code catch} with nothing to roll back. {@link #redeem} stays
     * transactional — there a bad coupon MUST roll the redemption insert back. */
    public CouponPreview preview(String code, BigDecimal originalAmount) {
        Coupon coupon = requireValidCoupon(code);
        return new CouponPreview(coupon, applyDiscount(coupon, originalAmount));
    }

    /**
     * Redemption-row-insert happens before the capacity increment, not after — reversing that
     * order would let an "already redeemed" failure permanently burn one unit of capacity that
     * this user never actually got (the insert failing after capacity was already reserved has
     * no way to hand that unit back without a second write). Inserting first means an "already
     * redeemed" rejection touches nothing; a capacity-exhausted rejection thrown afterward rolls
     * the whole transaction back, including the redemption insert — so nothing is left over
     * either way.
     */
    @Transactional
    public void redeem(String code, User user, Payment payment) {
        Coupon coupon = requireValidCoupon(code);

        try {
            CouponRedemption redemption = new CouponRedemption();
            redemption.setCoupon(coupon);
            redemption.setUser(user);
            redemption.setPayment(payment);
            couponRedemptionRepository.save(redemption);
            couponRedemptionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_REDEEMED);
        }

        int reserved = couponRepository.tryReserveRedemption(coupon.getId());
        if (reserved == 0) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }
    }

    private Coupon requireValidCoupon(String code) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

        LocalDate today = LocalDate.now();
        if (!coupon.isActive() || today.isBefore(coupon.getValidFrom()) || today.isAfter(coupon.getValidUntil())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        return coupon;
    }

    private BigDecimal applyDiscount(Coupon coupon, BigDecimal amount) {
        BigDecimal discounted = switch (coupon.getDiscountType()) {
            case PERCENTAGE -> amount.subtract(
                    amount.multiply(coupon.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            case FLAT -> amount.subtract(coupon.getDiscountValue());
        };
        return discounted.max(BigDecimal.ZERO);
    }
}
