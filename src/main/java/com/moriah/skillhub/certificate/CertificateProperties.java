package com.moriah.skillhub.certificate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The public-facing URL a certificate's QR code encodes — a {@code {code}} placeholder,
 * substituted with {@code verification_code}. build-plan.md feature 20 describes this as
 * {@code {APP_BASE_URL}/verify/{verification_code}}, and this feature's brief asks to reuse
 * "whatever config property already carries the public app/frontend base URL" from feature 03's
 * email-verification/password-reset links — but {@code AuthLinkProperties}' own Javadoc records
 * that no such shared base-URL config exists in this build (`/architect feature 08` decision: a
 * separate full-URL-with-placeholder template per purpose, same treatment as {@code
 * STRIPE_SUCCESS_URL}/{@code STRIPE_CANCEL_URL}). This follows that same established pattern —
 * {@code moriah.auth-links}'s sibling for this feature — rather than introducing the single
 * shared base-URL concept this project has twice already deliberately avoided.
 */
@ConfigurationProperties(prefix = "moriah.certificates")
public record CertificateProperties(String verifyUrlTemplate) {

    public String verifyUrl(String verificationCode) {
        return verifyUrlTemplate.replace("{code}", verificationCode);
    }
}
