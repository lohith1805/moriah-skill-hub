package com.moriah.skillhub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Frontend routes for the two links {@code AuthService} emails out — a {@code {token}}
 * placeholder, substituted with the raw (unhashed) token. Same per-purpose full-URL treatment as
 * {@code STRIPE_SUCCESS_URL}/{@code STRIPE_CANCEL_URL} (feature 07), not a single shared
 * frontend-base-URL config (`/architect feature 08` decision).
 */
@ConfigurationProperties(prefix = "moriah.auth-links")
public record AuthLinkProperties(String emailVerificationUrlTemplate, String passwordResetUrlTemplate) {
}
