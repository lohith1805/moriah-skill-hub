package com.moriah.skillhub.payment;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.payment.dto.AdminPaymentResponse;
import com.moriah.skillhub.payment.dto.PaymentStatusAggregate;
import com.moriah.skillhub.payment.dto.PaymentSummaryResponse;
import com.moriah.skillhub.payment.entity.Invoice;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentGateway;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.gateway.RazorpayService;
import com.moriah.skillhub.payment.gateway.StripeService;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final Duration INVOICE_LINK_TTL = Duration.ofMinutes(10);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final StorageService storageService;
    private final RazorpayService razorpayService;
    private final StripeService stripeService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> list(PaymentStatus status, PaymentGateway gateway,
                                                    String userUuid, Pageable pageable) {
        var page = paymentRepository.search(status, gateway, userUuid, pageable);
        Map<Long, Invoice> invoices = invoiceRepository
                .findByPaymentIdIn(page.map(Payment::getId).toList())
                .stream().collect(Collectors.toMap(i -> i.getPayment().getId(), Function.identity()));
        Map<Long, SubscriptionPlan> plans = subscriptionPlanRepository
                .findAllById(page.stream().map(Payment::getPlanId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(SubscriptionPlan::getId, Function.identity()));
        return PageResponse.from(page.map(p -> toResponse(p, invoices.get(p.getId()), plans.get(p.getPlanId()))));
    }

    @Transactional(readOnly = true)
    public AdminPaymentResponse get(String gatewayOrderId) {
        Payment payment = requirePayment(gatewayOrderId);
        return toResponse(payment, invoiceRepository.findByPaymentId(payment.getId()).orElse(null), planOf(payment));
    }

    private SubscriptionPlan planOf(Payment payment) {
        return payment.getPlanId() == null ? null
                : subscriptionPlanRepository.findById(payment.getPlanId()).orElse(null);
    }

    /** Presigned GET for one payment's invoice PDF — {@code ADMIN} only (route-gated), and the
     * {@code invoices/} branch in {@code OwnershipGuard} grants an ADMIN any invoice. 404 while
     * the async invoice job hasn't produced the PDF yet. */
    @Transactional(readOnly = true)
    public String invoicePdfUrl(String gatewayOrderId, String callerUuid) {
        Payment payment = requirePayment(gatewayOrderId);
        Invoice invoice = invoiceRepository.findByPaymentId(payment.getId())
                .filter(i -> i.getPdfKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVOICE_NOT_FOUND, gatewayOrderId));
        return storageService.presignedGetUrl(callerUuid, invoice.getPdfKey(), INVOICE_LINK_TTL).toString();
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

        return toResponse(payment, invoiceRepository.findByPaymentId(payment.getId()).orElse(null), planOf(payment));
    }

    private Payment requirePayment(String gatewayOrderId) {
        return paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND, gatewayOrderId));
    }

    private static AdminPaymentResponse toResponse(Payment p, Invoice invoice, SubscriptionPlan plan) {
        String invoiceStatus = invoice != null ? invoice.getStatus().name()
                : (p.getStatus() == PaymentStatus.CAPTURED ? "PROCESSING" : null);
        return new AdminPaymentResponse(
                p.getGatewayOrderId(),
                p.getGatewayPaymentId(),
                p.getUser().getUuid(),
                p.getUser().getFullName(),
                p.getPlanId(),
                plan != null ? plan.getCode() : null,
                plan != null ? plan.getName() : null,
                p.getTrackCode(),
                p.getGateway(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getFailureReason(),
                p.getCapturedAt(),
                p.getCreatedAt(),
                invoice != null ? invoice.getInvoiceNumber() : null,
                invoiceStatus);
    }
}
