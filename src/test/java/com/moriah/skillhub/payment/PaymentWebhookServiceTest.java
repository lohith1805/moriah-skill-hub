package com.moriah.skillhub.payment;

import com.moriah.skillhub.batch.BatchAllocationService;
import com.moriah.skillhub.payment.entity.Payment;
import com.moriah.skillhub.payment.entity.PaymentStatus;
import com.moriah.skillhub.payment.repository.InvoiceRepository;
import com.moriah.skillhub.payment.repository.PaymentRepository;
import com.moriah.skillhub.subscription.entity.SubscriptionPlan;
import com.moriah.skillhub.subscription.entity.SubscriptionStatus;
import com.moriah.skillhub.subscription.entity.UserSubscription;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import com.moriah.skillhub.user.entity.User;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stripe's capture/refund business logic, exercised directly against a mocked {@code Event} —
 * `/architect feature 07` decision: constructing a real, correctly-shaped Stripe {@code Event}/
 * {@code Session} JSON envelope for the SDK to deserialize is a much larger surface to get
 * exactly right than what this actually needs to prove (amount comparison, state transitions).
 * {@code PaymentWebhookFlowIT} covers Razorpay's equivalent end-to-end over real HTTP, plus
 * Stripe's signature-verification and idempotency wiring with a minimal unrecognized-event-type
 * payload that needs no typed deserialization at all.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private BatchAllocationService batchAllocationService;

    @InjectMocks
    private PaymentWebhookService paymentWebhookService;

    @Captor
    private ArgumentCaptor<UserSubscription> subscriptionCaptor;

    private Payment payment;
    private SubscriptionPlan plan;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        plan = new SubscriptionPlan();
        plan.setId(10L);
        plan.setDurationDays(180);

        payment = new Payment();
        payment.setId(100L);
        payment.setUser(user);
        payment.setPlanId(10L);
        payment.setAmount(new BigDecimal("14999.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
    }

    @Test
    void checkoutSessionCompleted_matchingAmount_capturesActivatesAndInvoicesAndPublishesEvent() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
        when(subscriptionPlanRepository.findById(10L)).thenReturn(Optional.of(plan));

        Event event = mockCheckoutSessionCompleted(100L, 1499900L, "pi_test123");

        paymentWebhookService.handleStripeEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getGatewayPaymentId()).isEqualTo("pi_test123");
        verify(userSubscriptionRepository).saveAndFlush(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscriptionCaptor.getValue().getPaymentId()).isEqualTo(100L);
        verify(invoiceRepository).save(any());
        verify(eventPublisher).publishEvent(new PaymentCapturedEvent(100L));
    }

    @Test
    void checkoutSessionCompleted_amountMismatch_marksFailed_neverActivatesOrInvoices() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        // Gateway reports 1 rupee captured when the server computed 14999 — a tampering /
        // integration-bug scenario, not a normal path.
        Event event = mockCheckoutSessionCompleted(100L, 100L, "pi_wrong");

        paymentWebhookService.handleStripeEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(userSubscriptionRepository, never()).saveAndFlush(any());
        verify(invoiceRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void checkoutSessionCompleted_paymentAlreadyCaptured_isANoOp() {
        payment.setStatus(PaymentStatus.CAPTURED);
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        Event event = mockCheckoutSessionCompleted(100L, 1499900L, "pi_test123");

        paymentWebhookService.handleStripeEvent(event);

        verify(userSubscriptionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void chargeRefunded_cancelsTheMatchingActiveSubscription() {
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId("pi_test123");
        when(paymentRepository.findByGatewayPaymentId("pi_test123")).thenReturn(Optional.of(payment));

        UserSubscription activeSub = new UserSubscription();
        activeSub.setStatus(SubscriptionStatus.ACTIVE);
        activeSub.setPaymentId(100L);
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));

        Event event = mockChargeRefunded("pi_test123");

        paymentWebhookService.handleStripeEvent(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(activeSub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(userSubscriptionRepository).save(activeSub);
    }

    @Test
    void chargeRefunded_activeSubscriptionBelongsToADifferentPayment_isLeftAlone() {
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setGatewayPaymentId("pi_test123");
        when(paymentRepository.findByGatewayPaymentId("pi_test123")).thenReturn(Optional.of(payment));

        UserSubscription unrelatedActiveSub = new UserSubscription();
        unrelatedActiveSub.setStatus(SubscriptionStatus.ACTIVE);
        unrelatedActiveSub.setPaymentId(999L); // a different payment funds their current subscription
        when(userSubscriptionRepository.findByUserIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(unrelatedActiveSub));

        paymentWebhookService.handleStripeEvent(mockChargeRefunded("pi_test123"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(unrelatedActiveSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void unrecognizedEventType_touchesNoRepository() {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("invoice.created");

        paymentWebhookService.handleStripeEvent(event);

        verify(paymentRepository, never()).findById(any());
        verify(paymentRepository, never()).findByGatewayPaymentId(any());
    }

    private Event mockCheckoutSessionCompleted(Long paymentId, Long amountTotalCents, String paymentIntentId) {
        Session session = mock(Session.class);
        when(session.getClientReferenceId()).thenReturn(paymentId.toString());
        when(session.getAmountTotal()).thenReturn(amountTotalCents);
        when(session.getPaymentIntent()).thenReturn(paymentIntentId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Event mockChargeRefunded(String paymentIntentId) {
        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn(paymentIntentId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(charge));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.refunded");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }
}
