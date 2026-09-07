package com.moriah.skillhub.crm.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
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

/**
 * build-plan.md feature 18's {@code POST /api/v1/webhooks/whatsapp}. Same raw-body-then-signature
 * shape as {@link com.moriah.skillhub.payment.PaymentWebhookController}: the body is captured as
 * a plain {@code String} before Jackson ever touches it, so the signature check runs against
 * exactly the bytes Meta signed. Public at the filter level ({@code /api/v1/webhooks/**} in
 * {@code SecurityConfig}) — Meta cannot present a JWT, same reasoning as Razorpay/Stripe.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks")
public class WhatsAppWebhookController {

    private final WhatsAppSignatureVerifier signatureVerifier;
    private final WhatsAppWebhookService whatsAppWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/whatsapp")
    @Operation(summary = "WhatsApp Cloud API inbound message webhook — signature-verified, not token-verified")
    public ResponseEntity<Void> whatsapp(
            @RequestBody String rawBody,
            @RequestHeader("X-Hub-Signature-256") String signature) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        JsonNode payload = parseJson(rawBody);
        whatsAppWebhookService.handleInbound(payload, rawBody);

        // 200 regardless of what was inside — a non-200 makes Meta retry the whole delivery
        // (same reasoning PaymentWebhookController documents for Razorpay).
        return ResponseEntity.ok().build();
    }

    private JsonNode parseJson(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("[webhook/whatsapp] malformed JSON body despite a valid signature", e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
