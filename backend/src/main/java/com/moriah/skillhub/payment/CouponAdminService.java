package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.dto.CouponResponse;
import com.moriah.skillhub.payment.dto.CreateCouponRequest;
import com.moriah.skillhub.payment.dto.UpdateCouponRequest;
import com.moriah.skillhub.payment.entity.Coupon;
import com.moriah.skillhub.payment.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD for {@code coupons} (gap B1.12). Kept separate from {@link CouponService}, which is
 * the checkout-time validate-and-redeem path — this class never touches {@code
 * coupon_redemptions} or the atomic reservation counter, only the catalogue rows themselves.
 * Lives in {@code payment/} because {@code Coupon}/{@code CouponRepository} are this package's
 * internals; the HTTP surface is {@code admin/CouponAdminController}, which calls in the same way
 * {@code AdminPlanController} calls {@code subscription/EntitlementService}.
 * <p>
 * There is no hard delete: a coupon may already be referenced by {@code coupon_redemptions}
 * rows, and dropping it would orphan a customer's redemption history. "Delete" deactivates
 * ({@code active = false}) — the same "never actually deletes" stance {@code
 * BatchController.removeStudent} takes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponAdminService {

    private final CouponRepository couponRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(Pageable pageable) {
        return PageResponse.from(couponRepository.findAll(pageable).map(CouponAdminService::toResponse));
    }

    @Transactional
    public CouponResponse create(CreateCouponRequest request, Long callerUserId) {
        if (couponRepository.existsByCode(request.code())) {
            throw new BusinessException(ErrorCode.COUPON_CODE_TAKEN);
        }
        requireValidWindow(request.validFrom(), request.validUntil());

        Coupon coupon = new Coupon();
        coupon.setCode(request.code());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setMaxRedemptions(request.maxRedemptions());
        coupon.setActive(request.active());
        couponRepository.save(coupon);

        auditLogService.record(callerUserId, "COUPON_CREATED", "Coupon", coupon.getId(), null, coupon.getCode());
        log.info("[admin/coupons] created {}", coupon.getCode());
        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse update(String code, UpdateCouponRequest request, Long callerUserId) {
        Coupon coupon = requireCoupon(code);
        requireValidWindow(request.validFrom(), request.validUntil());

        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setMaxRedemptions(request.maxRedemptions());
        coupon.setActive(request.active());

        auditLogService.record(callerUserId, "COUPON_UPDATED", "Coupon", coupon.getId(), null, coupon.getCode());
        log.info("[admin/coupons] updated {}", coupon.getCode());
        return toResponse(coupon);
    }

    /** Deactivate, never row-delete — see class Javadoc. Idempotent: deactivating an already
     * inactive coupon is a no-op success. */
    @Transactional
    public void deactivate(String code, Long callerUserId) {
        Coupon coupon = requireCoupon(code);
        if (coupon.isActive()) {
            coupon.setActive(false);
            auditLogService.record(callerUserId, "COUPON_DEACTIVATED", "Coupon", coupon.getId(), null, coupon.getCode());
            log.info("[admin/coupons] deactivated {}", coupon.getCode());
        }
    }

    private Coupon requireCoupon(String code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
    }

    private void requireValidWindow(java.time.LocalDate from, java.time.LocalDate until) {
        if (until.isBefore(from)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "validUntil must not be before validFrom.");
        }
    }

    private static CouponResponse toResponse(Coupon c) {
        return new CouponResponse(
                c.getCode(),
                c.getDiscountType(),
                c.getDiscountValue(),
                c.getValidFrom(),
                c.getValidUntil(),
                c.getMaxRedemptions(),
                c.getTimesRedeemed(),
                c.isActive(),
                c.getCreatedAt());
    }
}
