package com.moriah.skillhub.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.payment.gateway.RazorpayService;
import com.moriah.skillhub.payment.gateway.StripeService;
import com.stripe.model.Event;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * architecture.md's Payment Webhook data flow, verbatim: raw body captured before deserialization
 * -> HMAC verified (reject 400 if invalid) -> {@code WebhookIdempotencyService.claim()} -> a
 * duplicate returns 200 immediately and does nothing -> the real state transition -> 200,
 * typically well under 1s. {@code @RequestBody String} is the raw body capture itself — no
 * {@code ContentCachingRequestWrapper} needed, Jackson never touches these bytes before the
 * signature check.
 * <p>
 * Public at the filter level ({@code SecurityConfig}, architecture.md "Public endpoints") —
 * gateways cannot present a JWT (library-docs.md) — secured entirely by signature verification
 * instead. Still behind the global rate-limit tier ({@code RateLimitFilter}), since signature
 * verification is real CPU work and would otherwise be a free DoS surface (build-plan.md feature
 * 07).
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks")
public class PaymentWebhookController {

    private final RazorpayService razorpayService;
    private final StripeService stripeService;
    private final WebhookIdempotencyService webhookIdempotencyService;
    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/razorpay")
    @Operation(summary = "Razorpay payment/refund webhook — signature-verified, not token-verified")
    public ResponseEntity<Void> razorpay(
            @RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        if (!razorpayService.verifySignature(rawBody, signature)) {
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        JsonNode payload = parseJson(rawBody);
        String eventType = payload.path("event").asText();
        String primaryEntityId = firstNonBlank(
                payload.path("payload").path("payment").path("entity").path("id").asText(null),
                payload.path("payload").path("refund").path("entity").path("id").asText(null));
        // Not a dedicated top-level webhook-delivery id — Razorpay's payload doesn't reliably
        // carry one across every event type — so the dedup key is derived from the event type
        // plus the underlying entity's own id, which is stable across Razorpay's retry redeliveries
        // of the exact same event.
        String eventId = eventType + ":" + primaryEntityId;

        if (webhookIdempotencyService.claim("RAZORPAY", eventId, eventType, rawBody)) {
            processClaimed("RAZORPAY", eventId, () -> paymentWebhookService.handleRazorpayEvent(payload));
        }

        // 200 for a successfully processed event or a duplicate. A handler failure rethrows from
        // processClaimed() → a 5xx, which is exactly what makes Razorpay redeliver (the claim is
        // released first so the redelivery is treated as fresh work). library-docs.md "Razorpay".
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stripe")
    @Operation(summary = "Stripe checkout/refund webhook — signature-verified, not token-verified")
    public ResponseEntity<Void> stripe(
            @RequestBody String rawBody,
            @RequestHeader("Stripe-Signature") String signature) {
        Optional<Event> maybeEvent = stripeService.verifyAndParseEvent(rawBody, signature);
        if (maybeEvent.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        Event event = maybeEvent.get();

        if (webhookIdempotencyService.claim("STRIPE", event.getId(), event.getType(), rawBody)) {
            processClaimed("STRIPE", event.getId(), () -> paymentWebhookService.handleStripeEvent(event));
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Runs a just-claimed event's handler, marking the claim {@code PROCESSED} in the same
     * transaction on success. On failure the claim is released (so the gateway's redelivery
     * re-claims it) and the exception is rethrown so the response is a 5xx.
     */
    private void processClaimed(String gateway, String eventId, Runnable handler) {
        try {
            webhookIdempotencyService.runAndMarkProcessed(gateway, eventId, handler);
        } catch (RuntimeException e) {
            webhookIdempotencyService.releaseClaim(gateway, eventId);
            log.error("[webhook/{}] handler failed for event {}; claim released for redelivery",
                    gateway.toLowerCase(), eventId, e);
            throw e;
        }
    }

    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("[webhook/razorpay] malformed JSON body despite a valid signature", e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
