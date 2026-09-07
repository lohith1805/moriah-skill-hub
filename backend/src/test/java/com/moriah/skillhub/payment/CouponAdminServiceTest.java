package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.dto.CouponResponse;
import com.moriah.skillhub.payment.dto.CreateCouponRequest;
import com.moriah.skillhub.payment.dto.UpdateCouponRequest;
import com.moriah.skillhub.payment.entity.Coupon;
import com.moriah.skillhub.payment.entity.CouponDiscountType;
import com.moriah.skillhub.payment.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CouponAdminService service;

    private CreateCouponRequest createRequest(String code, LocalDate from, LocalDate until) {
        return new CreateCouponRequest(code, CouponDiscountType.PERCENTAGE, new BigDecimal("20"),
                from, until, 100, true);
    }

    @Test
    void create_persistsCouponAndAudits() {
        when(couponRepository.existsByCode("LAUNCH20")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        CouponResponse response = service.create(
                createRequest("LAUNCH20", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), 9L);

        assertThat(response.code()).isEqualTo("LAUNCH20");
        assertThat(response.discountType()).isEqualTo(CouponDiscountType.PERCENTAGE);
        assertThat(response.active()).isTrue();

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getMaxRedemptions()).isEqualTo(100);
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        when(couponRepository.existsByCode("DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                createRequest("DUP", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_CODE_TAKEN);

        verify(couponRepository, never()).save(any());
    }

    @Test
    void create_windowInverted_throwsBusinessRule() {
        when(couponRepository.existsByCode("BADWIN")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                createRequest("BADWIN", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);

        verify(couponRepository, never()).save(any());
    }

    @Test
    void update_unknownCode_throwsNotFound() {
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("NOPE",
                new UpdateCouponRequest(CouponDiscountType.FLAT, new BigDecimal("500"),
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1), null, true), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    void update_replacesEveryField() {
        Coupon existing = new Coupon();
        existing.setId(3L);
        existing.setCode("SPRING");
        existing.setDiscountType(CouponDiscountType.PERCENTAGE);
        existing.setDiscountValue(new BigDecimal("10"));
        existing.setActive(true);
        when(couponRepository.findByCode("SPRING")).thenReturn(Optional.of(existing));

        CouponResponse response = service.update("SPRING",
                new UpdateCouponRequest(CouponDiscountType.FLAT, new BigDecimal("750"),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), 50, false), 9L);

        assertThat(response.discountType()).isEqualTo(CouponDiscountType.FLAT);
        assertThat(response.discountValue()).isEqualByComparingTo("750");
        assertThat(response.maxRedemptions()).isEqualTo(50);
        assertThat(response.active()).isFalse();
        assertThat(existing.getDiscountType()).isEqualTo(CouponDiscountType.FLAT);
    }

    @Test
    void deactivate_onlyFlipsAnActiveCoupon() {
        Coupon active = new Coupon();
        active.setId(4L);
        active.setCode("OLD");
        active.setActive(true);
        when(couponRepository.findByCode("OLD")).thenReturn(Optional.of(active));

        service.deactivate("OLD", 9L);

        assertThat(active.isActive()).isFalse();
        verify(auditLogService).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deactivate_alreadyInactive_isANoOpSuccess() {
        Coupon inactive = new Coupon();
        inactive.setId(5L);
        inactive.setCode("GONE");
        inactive.setActive(false);
        when(couponRepository.findByCode("GONE")).thenReturn(Optional.of(inactive));

        service.deactivate("GONE", 9L);

        assertThat(inactive.isActive()).isFalse();
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
    }
}
