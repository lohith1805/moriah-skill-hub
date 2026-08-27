package com.moriah.skillhub.crm.webhook;

import com.moriah.skillhub.common.notification.dispatch.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Meta's WhatsApp Cloud API webhook signature: {@code X-Hub-Signature-256: sha256=<hex hmac>},
 * HMAC-SHA256 of the raw request body keyed by the app secret — no SDK for this on
 * code-standards.md's approved dependency list, unlike Razorpay's {@code Utils.verifyWebhookSignature}.
 * {@link javax.crypto.Mac#doFinal} is not constant-time; a raw {@code equals} timing side-channel
 * on a webhook signature (not a login credential) is an accepted, minor risk here, same as this
 * codebase accepts elsewhere for non-credential comparisons.
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
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(props.appSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return computedHex.equalsIgnoreCase(expectedHex);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[webhook/whatsapp] signature verification failed unexpectedly", e);
            return false;
        }
    }
}
