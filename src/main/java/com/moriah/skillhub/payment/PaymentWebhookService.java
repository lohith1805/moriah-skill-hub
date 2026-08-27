package com.moriah.skillhub.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.moriah.skillhub.batch.BatchAllocationService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.payment.entity.Invoice;
import com.moriah.skillhub.payment.entity.InvoiceStatus;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The actual state-transition logic behind both webhook endpoints — architecture.md's Payment
 * Webhook data-flow diagram, verbatim: "payment CAPTURED → subscription ACTIVE → invoice row
 * (status PENDING)", all inside one transaction; PDF rendering happens after commit
 * ({@link PaymentCapturedEvent}), never inline (the gateway's ~5s timeout).
 * <p>
 * {@code BatchAllocationService.allocate()} — cleared at feature 10 (progress-tracker.md Open
 * Stubs table). Wired in below, at the spot this class used to mark with a comment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BatchAllocationService batchAllocationService;

    @Transactional
    public void handleRazorpayEvent(JsonNode payload) {
        String event = payload.path("event").asText();
        switch (event) {
            case "payment.captured" -> {
                JsonNode entity = payload.path("payload").path("payment").path("entity");
                String orderId = entity.path("order_id").asText();
                String gatewayPaymentId = entity.path("id").asText();
                // Razorpay amounts are in paise (library-docs.md) — divide back to rupees for
                // the DB-amount comparison below.
                BigDecimal capturedAmount = BigDecimal.valueOf(entity.path("amount").asLong())
                        .divide(BigDecimal.valueOf(100));
                capturePayment(orderId, gatewayPaymentId, capturedAmount);
            }
            case "refund.processed" -> {
                JsonNode entity = payload.path("payload").path("payment").path("entity");
                refundPayment(entity.path("id").asText());
            }
            default -> log.info("[webhook/razorpay] ignored event type {}", event);
        }
    }

    @Transactional
    public void handleStripeEvent(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" -> {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                Session session = (Session) deserializer.getObject().orElseThrow();
                BigDecimal capturedAmount = session.getAmountTotal() == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));
                Long paymentId = Long.valueOf(session.getClientReferenceId());
                capturePaymentById(paymentId, session.getPaymentIntent(), capturedAmount);
            }
            case "charge.refunded" -> {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                Charge charge = (Charge) deserializer.getObject().orElseThrow();
                refundPayment(charge.getPaymentIntent());
            }
            default -> log.info("[webhook/stripe] ignored event type {}", event.getType());
        }
    }

    private void capturePayment(String gatewayOrderId, String gatewayPaymentId, BigDecimal capturedAmount) {
        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
        if (payment == null) {
            log.error("[webhook] payment.captured for unknown gatewayOrderId {}", gatewayOrderId);
            return;
        }
        capturePayment(payment, gatewayPaymentId, capturedAmount);
    }

    private void capturePaymentById(Long paymentId, String gatewayPaymentId, BigDecimal capturedAmount) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.error("[webhook] checkout.session.completed for unknown payment id {}", paymentId);
            return;
        }
        capturePayment(payment, gatewayPaymentId, capturedAmount);
    }

    /** build-plan.md feature 07: "Never trust the amount in the webhook payload. Re-read the
     * plan price from the DB and compare." {@code payment.amount} was already computed
     * server-side at checkout time (coupon-discounted, never client-supplied) — the gateway's
     * reported amount must match *that*, not the other way around. */
    private void capturePayment(Payment payment, String gatewayPaymentId, BigDecimal capturedAmount) {
        if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PENDING) {
            log.info("[webhook] payment {} already in status {}, ignoring duplicate capture",
                    payment.getId(), payment.getStatus());
            return;
        }
        if (payment.getAmount().compareTo(capturedAmount) != 0) {
            log.error("[webhook] amount mismatch for payment {}: expected {}, gateway reported {}",
                    payment.getId(), payment.getAmount(), capturedAmount);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway-reported amount did not match the server-computed amount.");
            paymentRepository.save(payment);
            return;
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        activateSubscription(payment);
        createPendingInvoice(payment);

        // build-plan.md feature 10: "finds an ACTIVE/PLANNED batch matching track and minimum
        // tier with free capacity... No matching batch -> pending queue, PM notified." Pure DB
        // work (no outbound HTTP), safe to run inside this transaction (code-standards.md
        // "Transactions").
        batchAllocationService.allocate(payment.getUser().getId(), payment.getTrackCode(), payment.getPlanId());

        eventPublisher.publishEvent(new PaymentCapturedEvent(payment.getId()));
    }

    private void activateSubscription(Payment payment) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(payment.getPlanId()).orElseThrow();

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(payment.getUser());
        subscription.setPlan(plan);
        subscription.setPaymentId(payment.getId());
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusDays(plan.getDurationDays()));
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        try {
            userSubscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            // uq_one_active_subscription — this user already has an ACTIVE row. Renewal/upgrade
            // handling (transitioning the old row out first) isn't specified for this feature;
            // the payment itself is still genuinely captured, so it stays CAPTURED — just without
            // a second active subscription the schema's own constraint refuses to allow.
            log.warn("[webhook] payment {} captured but user {} already has an active subscription — "
                    + "not activating a second one", payment.getId(), payment.getUser().getId());
        }
    }

    private void createPendingInvoice(Payment payment) {
        Invoice invoice = new Invoice();
        invoice.setPayment(payment);
        invoice.setInvoiceNumber(Constants.INVOICE_PREFIX + "-" + String.format("%06d", payment.getId()));
        invoice.setAmount(payment.getAmount());
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(payment.getAmount());
        invoice.setStatus(InvoiceStatus.PENDING);
        invoiceRepository.save(invoice);
    }

    private void refundPayment(String gatewayPaymentId) {
        Optional<Payment> maybePayment = paymentRepository.findByGatewayPaymentId(gatewayPaymentId);
        if (maybePayment.isEmpty()) {
            log.error("[webhook] refund event for unknown gatewayPaymentId {}", gatewayPaymentId);
            return;
        }
        Payment payment = maybePayment.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("[webhook] payment {} already refunded, ignoring duplicate refund event", payment.getId());
            return;
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        userSubscriptionRepository.findByUserIdAndStatus(payment.getUser().getId(), SubscriptionStatus.ACTIVE)
                .filter(sub -> payment.getId().equals(sub.getPaymentId()))
                .ifPresent(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    userSubscriptionRepository.save(sub);
                    // build-plan.md feature 07: "de-allocate the student from their batch." A
                    // no-op if allocate() never placed them (STARTER/PROFESSIONAL, or they're
                    // still pending) — BatchAllocationService.deallocate handles that internally.
                    batchAllocationService.deallocate(payment.getUser().getId());
                });
    }
}
