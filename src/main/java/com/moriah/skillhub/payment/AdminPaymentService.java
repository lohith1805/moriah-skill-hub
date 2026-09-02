package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Admin transactions list + refund (gap B1.11). Read side is straightforward paged/grouped
 * queries. Refund follows {@code CertificateService.issue}'s shape deliberately: the method is
 * <b>not</b> {@code @Transactional}, because the gateway call in the middle is outbound HTTP and
 * AGENTS.md is explicit that an outbound call must never sit inside a transaction — each
 * repository call runs in its own short transaction via Spring Data's per-call proxy.
 * <p>
 * Setting {@code status = REFUNDED} here is optimistic: the gateway also emits a refund webhook
 * ({@code refund.processed} / {@code charge.refunded}) which {@code PaymentWebhookService}
 * already handles, and that handler no-ops when the row is already {@code REFUNDED}. So the two
 * paths converge safely whichever lands first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final RazorpayService razorpayService;
    private final StripeService stripeService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> list(PaymentStatus status, PaymentGateway gateway,
                                                    String userUuid, Pageable pageable) {
        return PageResponse.from(
                paymentRepository.search(status, gateway, userUuid, pageable).map(AdminPaymentService::toResponse));
    }

    @Transactional(readOnly = true)
    public AdminPaymentResponse get(String gatewayOrderId) {
        return toResponse(requirePayment(gatewayOrderId));
    }

    @Transactional(readOnly = true)
    public PaymentSummaryResponse summary() {
        Map<PaymentStatus, PaymentSummaryResponse.StatusBucket> buckets = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus s : PaymentStatus.values()) {
            buckets.put(s, new PaymentSummaryResponse.StatusBucket(s, 0L, BigDecimal.ZERO));
        }
        for (PaymentStatusAggregate row : paymentRepository.summarizeByStatus()) {
            buckets.put(row.getStatus(),
                    new PaymentSummaryResponse.StatusBucket(row.getStatus(), row.getCount(), row.getTotalAmount()));
        }

        long totalCount = buckets.values().stream().mapToLong(PaymentSummaryResponse.StatusBucket::count).sum();
        return new PaymentSummaryResponse(
                List.copyOf(buckets.values()),
                totalCount,
                buckets.get(PaymentStatus.CAPTURED).totalAmount(),
                buckets.get(PaymentStatus.REFUNDED).totalAmount());
    }

    /** Not {@code @Transactional} — see class Javadoc. */
    public AdminPaymentResponse refund(String gatewayOrderId, String reason, Long callerUserId) {
        Payment payment = requirePayment(gatewayOrderId);

        if (payment.getStatus() != PaymentStatus.CAPTURED || payment.getGatewayPaymentId() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }

        switch (payment.getGateway()) {
            case RAZORPAY -> razorpayService.refund(payment.getGatewayPaymentId(), payment.getAmount());
            case STRIPE -> stripeService.refund(payment.getGatewayPaymentId(), payment.getAmount());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        auditLogService.record(callerUserId, "PAYMENT_REFUNDED", "Payment", payment.getId(),
                PaymentStatus.CAPTURED, reason);
        log.info("[admin/payments] refund issued for {} ({})", gatewayOrderId, payment.getGateway());

        return toResponse(payment);
    }

    private Payment requirePayment(String gatewayOrderId) {
        return paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND, gatewayOrderId));
    }

    private static AdminPaymentResponse toResponse(Payment p) {
        return new AdminPaymentResponse(
                p.getGatewayOrderId(),
                p.getGatewayPaymentId(),
                p.getUser().getUuid(),
                p.getUser().getFullName(),
                p.getPlanId(),
                p.getTrackCode(),
                p.getGateway(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getFailureReason(),
                p.getCapturedAt(),
                p.getCreatedAt());
    }
}
