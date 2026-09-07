package com.moriah.skillhub.crm.webhook;

import com.moriah.skillhub.common.notification.dispatch.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Meta's WhatsApp Cloud API webhook signature: {@code X-Hub-Signature-256: sha256=<hex hmac>},
 * HMAC-SHA256 of the raw request body keyed by the app secret — no SDK for this on
 * code-standards.md's approved dependency list, unlike Razorpay's {@code Utils.verifyWebhookSignature}.
 * <p>
 * Audit 2026-08-31 (L1): the computed and presented digests are compared with {@link
 * MessageDigest#isEqual} on the decoded bytes, which is constant-time — Razorpay's and Stripe's
 * own verifiers do the same, and it costs nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WhatsAppProperties props;

    public boolean verify(String rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String expectedHex = signatureHeader.substring("sha256=".length());
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(expectedHex);
        } catch (IllegalArgumentException e) {
            return false; // not valid hex — can't be a real signature
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(props.appSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(computed, expected);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[webhook/whatsapp] signature verification failed unexpectedly", e);
            return false;
        }
    }
}
