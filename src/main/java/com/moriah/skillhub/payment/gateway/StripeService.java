package com.moriah.skillhub.payment.gateway;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * library-docs.md "Stripe". Same isolation reasoning as {@link RazorpayService} — no real
 * sandbox credentials exist yet (`/architect feature 07`), so {@code CheckoutService}'s tests
 * mock this class rather than exercising a real network call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final StripeProperties props;

    @PostConstruct
    void configureApiKey() {
        Stripe.apiKey = props.secretKey();
    }

    /** Amount is in rupees in; Stripe wants the smallest currency unit as a {@code Long} — same
     * "never a fractional unit" discipline as {@link RazorpayService#createOrder}.
     * {@code clientReferenceId} carries {@code payments.id} as a string — "that is how the
     * webhook finds the record" (library-docs.md). */
    public Session createCheckoutSession(BigDecimal amountInr, String currency, String planName, Long paymentId) {
        try {
            long unitAmount = amountInr.multiply(BigDecimal.valueOf(100)).longValueExact();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(props.successUrl())
                    .setCancelUrl(props.cancelUrl())
                    .setClientReferenceId(paymentId.toString())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount(unitAmount)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(planName)
                                            .build())
                                    .build())
                            .build())
                    .build();

            return Session.create(params);
        } catch (StripeException e) {
            log.error("[stripe/checkout] session creation failed", e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    /**
     * Admin-initiated refund (gap B1.11) against the Stripe {@code PaymentIntent} id — i.e.
     * {@code payments.gateway_payment_id}, set only by the capture webhook. Rupees in, smallest
     * unit out as a {@code Long}, {@code longValueExact()} so a fractional unit fails loudly —
     * same discipline as {@link #createCheckoutSession}. Stripe then emits {@code charge.refunded},
     * already handled by {@code PaymentWebhookService}; this call does not itself flip
     * {@code payments.status}.
     */
    public void refund(String paymentIntentId, BigDecimal amountInr) {
        try {
            long unitAmount = amountInr.multiply(BigDecimal.valueOf(100)).longValueExact();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(unitAmount)
                    .build();
            Refund.create(params);
        } catch (StripeException e) {
            log.error("[stripe/refund] refund failed for {}", paymentIntentId, e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }

    /** {@code Webhook.constructEvent} both verifies and parses — "never parse the body yourself
     * first" (library-docs.md). Empty on a bad signature; the controller turns that into a 400,
     * never a 500 — a tampered request is a client problem, not a server one. */
    public Optional<Event> verifyAndParseEvent(String rawBody, String signatureHeader) {
        try {
            return Optional.of(Webhook.constructEvent(rawBody, signatureHeader, props.webhookSecret()));
        } catch (SignatureVerificationException e) {
            log.warn("[stripe/webhook] signature verification failed");
            return Optional.empty();
        }
    }
}
