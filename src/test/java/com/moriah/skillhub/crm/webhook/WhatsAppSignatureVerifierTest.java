package com.moriah.skillhub.crm.webhook;

import com.moriah.skillhub.common.notification.dispatch.WhatsAppProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppSignatureVerifierTest {

    private final WhatsAppProperties props = new WhatsAppProperties(
            "https://graph.facebook.com/v18.0", "test-phone-number-id", "test-token", "test-app-secret");
    private final WhatsAppSignatureVerifier verifier = new WhatsAppSignatureVerifier(props);

    @Test
    void verify_correctSignature_returnsTrue() throws Exception {
        String rawBody = "{\"object\":\"whatsapp_business_account\"}";
        String signature = "sha256=" + hmacHex(rawBody, "test-app-secret");

        assertThat(verifier.verify(rawBody, signature)).isTrue();
    }

    @Test
    void verify_wrongSecret_returnsFalse() throws Exception {
        String rawBody = "{\"object\":\"whatsapp_business_account\"}";
        String signature = "sha256=" + hmacHex(rawBody, "wrong-secret");

        assertThat(verifier.verify(rawBody, signature)).isFalse();
    }

    @Test
    void verify_tamperedBody_returnsFalse() throws Exception {
        String signedBody = "{\"object\":\"whatsapp_business_account\"}";
        String signature = "sha256=" + hmacHex(signedBody, "test-app-secret");

        assertThat(verifier.verify("{\"object\":\"tampered\"}", signature)).isFalse();
    }

    @Test
    void verify_missingShaPrefix_returnsFalse() {
        assertThat(verifier.verify("body", "not-a-valid-signature")).isFalse();
    }

    @Test
    void verify_nullSignature_returnsFalse() {
        assertThat(verifier.verify("body", null)).isFalse();
    }

    private String hmacHex(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
