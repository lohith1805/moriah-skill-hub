package com.moriah.skillhub.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.moriah.skillhub.batch.BatchAllocationService;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
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
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    /** Audit 2026-08-31 (M7): subscription start/end dates resolved in the jobs' zone, not the
     * JVM default (a UTC host would date them a day early for late-evening-UTC captures). */
    @Value("${moriah.jobs.zone}")
    private String jobsZone;

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
        PaymentStatus previousStatus = payment.getStatus();
        if (payment.getAmount().compareTo(capturedAmount) != 0) {
            log.error("[webhook] amount mismatch for payment {}: expected {}, gateway reported {}",
                    payment.getId(), payment.getAmount(), capturedAmount);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway-reported amount did not match the server-computed amount.");
            paymentRepository.save(payment);
            // Feature 23 hardening: AGENTS.md "AuditLogService wired into every financial ...
            // mutation" — a failed-capture-due-to-amount-mismatch is exactly the kind of financial
            // event that must leave a trail, not just a log line. No human caller (a gateway
            // webhook), same null-actor precedent PipEvaluationService.fire() already established
            // for a system-triggered write.
            auditLogService.record(null, "PAYMENT_CAPTURE_FAILED", "Payment", payment.getId(),
                    previousStatus, PaymentStatus.FAILED);
            return;
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);
        auditLogService.record(null, "PAYMENT_CAPTURED", "Payment", payment.getId(),
                previousStatus, PaymentStatus.CAPTURED);

        UserSubscription subscription = activateSubscription(payment);
        if (subscription == null) {
            // Audit 2026-08-31 (H6): the user already has an ACTIVE subscription, so no second one
            // could be created. Do NOT go on to issue an invoice, allocate a batch seat, or fire
            // PaymentCapturedEvent (which would try to render an invoice PDF for a row that does
            // not exist) — the money is captured with nothing delivered, which needs a manual
            // refund, not more downstream side effects. A distinct audit action makes this
            // findable without log-grepping.
            auditLogService.record(null, "PAYMENT_CAPTURED_NO_SUBSCRIPTION", "Payment", payment.getId(),
                    previousStatus, PaymentStatus.CAPTURED);
            log.error("[webhook] payment {} captured for user {} but no subscription was created "
                    + "(user already has an ACTIVE subscription); invoice + batch allocation SKIPPED — "
                    + "this payment requires a manual refund or a renewal flow", payment.getId(),
                    payment.getUser().getId());
            return;
        }
        createPendingInvoice(payment);

        // build-plan.md feature 10: "finds an ACTIVE/PLANNED batch matching track and minimum
        // tier with free capacity... No matching batch -> pending queue, PM notified." Pure DB
        // work (no outbound HTTP), safe to run inside this transaction (code-standards.md
        // "Transactions").
        batchAllocationService.allocate(payment.getUser().getId(), payment.getTrackCode(), payment.getPlanId());

        notifySubscriber(payment, subscription);
        eventPublisher.publishEvent(new PaymentCapturedEvent(payment.getId()));
    }

    /** Tell the student their payment landed — an in-app row (so it reaches the bell, not just a
     * transient toast) plus an email. Fires after this transaction commits; a dispatch failure
     * never rolls the payment back ({@code enqueueAfterCommit}). */
    private void notifySubscriber(Payment payment, UserSubscription subscription) {
        Long userId = payment.getUser().getId();
        String planName = Objects.toString(subscription.getPlan().getName(), "your subscription");
        String endDate = Objects.toString(subscription.getEndDate(), "");

        notificationService.enqueueAfterCommit(userId, NotificationChannel.IN_APP, "SUBSCRIPTION_ACTIVATED", Map.of(
                "planName", planName,
                "startDate", Objects.toString(subscription.getStartDate(), ""),
                "endDate", endDate,
                "amount", Objects.toString(payment.getAmount(), ""),
                "currency", Objects.toString(payment.getCurrency(), "")));

        String email = payment.getUser().getEmail();
        if (email != null && !email.isBlank()) {
            notificationService.enqueueAfterCommit(userId, NotificationChannel.EMAIL, "SUBSCRIPTION_ACTIVATED", Map.of(
                    "to", email,
                    "subject", "Your Moriah Skill Hub subscription is active",
                    "body", "Your " + planName + " plan is now active until " + endDate
                            + ". Sign in to see your dashboard, and if your plan includes a batch you'll be "
                            + "placed into one automatically — we'll email you the details."));
        }
    }

    /** @return the newly-created ACTIVE subscription, or {@code null} if the user already had one
     *          — see {@link #capturePayment} for how the caller handles {@code null}.
     *          <p>Audit 2026-08-31 (H6): checks for an existing ACTIVE row up front rather than
     *          catching {@code uq_one_active_subscription} from a {@code saveAndFlush} — a flush
     *          failure poisons the Hibernate session and would roll back the whole webhook
     *          transaction (payment never marked CAPTURED), leaving the gateway to retry forever.
     *          The DB constraint still backstops the rare concurrent-double-capture race. */
    private UserSubscription activateSubscription(Payment payment) {
        boolean alreadyActive = userSubscriptionRepository
                .findByUserIdAndStatus(payment.getUser().getId(), SubscriptionStatus.ACTIVE)
                .isPresent();
        if (alreadyActive) {
            return null;
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(payment.getPlanId()).orElseThrow();

        LocalDate startDate = LocalDate.now(ZoneId.of(jobsZone));
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(payment.getUser());
        subscription.setPlan(plan);
        subscription.setPaymentId(payment.getId());
        subscription.setStartDate(startDate);
        subscription.setEndDate(startDate.plusDays(plan.getDurationDays()));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        userSubscriptionRepository.save(subscription);
        return subscription;
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

        PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        auditLogService.record(null, "PAYMENT_REFUNDED", "Payment", payment.getId(),
                previousStatus, PaymentStatus.REFUNDED);

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
