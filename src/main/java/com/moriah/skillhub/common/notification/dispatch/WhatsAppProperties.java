package com.moriah.skillhub.common.notification.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** WhatsApp Cloud API is a plain REST API — no SDK is in code-standards.md's approved dependency
 * list, so {@code WhatsAppDispatcher} calls it directly via a {@code RestClient} bean. No real
 * Meta developer account exists yet, same as every other external integration so far —
 * unit-tested against a mocked {@code RestClient}. {@code appSecret} is Meta's per-app secret
 * used to verify the {@code X-Hub-Signature-256} HMAC on inbound {@code POST
 * /api/v1/webhooks/whatsapp} deliveries (feature 18) — a different credential from {@code
 * accessToken}, which only authenticates outbound calls. */
@ConfigurationProperties(prefix = "moriah.notification.whatsapp")
public record WhatsAppProperties(String apiBaseUrl, String phoneNumberId, String accessToken, String appSecret) {
}
