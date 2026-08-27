package com.moriah.skillhub.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bare env vars, same treatment as every other gateway/API credential in this project
 * (`JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, the OAuth2 client secrets) — no secret store exists yet. */
@ConfigurationProperties(prefix = "moriah.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret) {
}
