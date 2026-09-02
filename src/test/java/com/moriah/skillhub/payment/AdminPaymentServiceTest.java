package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.payment.dto.AdminPaymentResponse;
import com.moriah.skillhub.payment.dto.PaymentStatusAggregate;
import com.moriah.skillhub.payment.dto.PaymentSummaryResponse;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.gateway.RazorpayService;
import com.moriah.skillhub.payment.gateway.StripeService;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RazorpayService razorpayService;
    @Mock
    private StripeService stripeService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdminPaymentService service;

    private Payment capturedRazorpay() {
        User u = new User();
        u.setId(5L);
        u.setUuid("user-uuid");
        u.setFullName("Paying User");
        Payment p = new Payment();
        p.setId(100L);
        p.setUser(u);
        p.setPlanId(3L);
        p.setGateway(PaymentGateway.RAZORPAY);
        p.setGatewayOrderId("order_ABC");
        p.setGatewayPaymentId("pay_XYZ");
        p.setAmount(new BigDecimal("14999.00"));
        p.setCurrency("INR");
        p.setStatus(PaymentStatus.CAPTURED);
        return p;
    }

    @Test
    void get_unknownOrderId_throwsNotFound() {
        when(paymentRepository.findByGatewayOrderId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void refund_capturedRazorpay_callsGatewayThenMarksRefundedAndAudits() {
        Payment p = capturedRazorpay();
        when(paymentRepository.findByGatewayOrderId("order_ABC")).thenReturn(Optional.of(p));

        AdminPaymentResponse response = service.refund("order_ABC", "duplicate charge", 9L);

        verify(razorpayService).refund("pay_XYZ", new BigDecimal("14999.00"));
        verifyNoInteractions(stripeService);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(p);
        verify(auditLogService).record(eq(9L), eq("PAYMENT_REFUNDED"), eq("Payment"), eq(100L),
                eq(PaymentStatus.CAPTURED), eq("duplicate charge"));
    }

    @Test
    void refund_stripePayment_routesToStripe() {
        Payment p = capturedRazorpay();
        p.setGateway(PaymentGateway.STRIPE);
        when(paymentRepository.findByGatewayOrderId("order_ABC")).thenReturn(Optional.of(p));

        service.refund("order_ABC", null, 9L);

        verify(stripeService).refund("pay_XYZ", new BigDecimal("14999.00"));
        verifyNoInteractions(razorpayService);
    }

    @Test
    void refund_notCaptured_throwsConflict_andNeverCallsGateway() {
        Payment p = capturedRazorpay();
        p.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findByGatewayOrderId("order_ABC")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.refund("order_ABC", null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_REFUNDABLE);

        verifyNoInteractions(razorpayService, stripeService);
        verify(paymentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refund_capturedButNeverConfirmedByGateway_isNotRefundable() {
        Payment p = capturedRazorpay();
        p.setGatewayPaymentId(null);
        when(paymentRepository.findByGatewayOrderId("order_ABC")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.refund("order_ABC", null, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    void summary_zeroFillsEveryStatusBucket() {
        when(paymentRepository.summarizeByStatus()).thenReturn(List.of(
                aggregate(PaymentStatus.CAPTURED, 3L, new BigDecimal("30000")),
                aggregate(PaymentStatus.REFUNDED, 1L, new BigDecimal("9999"))));

        PaymentSummaryResponse summary = service.summary();

        assertThat(summary.byStatus()).hasSize(PaymentStatus.values().length);
        assertThat(summary.totalCount()).isEqualTo(4L);
        assertThat(summary.totalCaptured()).isEqualByComparingTo("30000");
        assertThat(summary.totalRefunded()).isEqualByComparingTo("9999");
        assertThat(summary.byStatus()).anySatisfy(b -> {
            assertThat(b.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(b.count()).isZero();
            assertThat(b.totalAmount()).isEqualByComparingTo("0");
        });
    }

    private PaymentStatusAggregate aggregate(PaymentStatus status, long count, BigDecimal total) {
        return new PaymentStatusAggregate() {
            public PaymentStatus getStatus() {
                return status;
            }

            public long getCount() {
                return count;
            }

            public BigDecimal getTotalAmount() {
                return total;
            }
        };
    }
}
