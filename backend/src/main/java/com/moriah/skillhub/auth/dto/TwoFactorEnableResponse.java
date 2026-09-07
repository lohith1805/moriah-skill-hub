package com.moriah.skillhub.auth.dto;

/**
 * {@code secret} is the raw Base32 value for manual entry when a user can't scan a QR code
 * (every mainstream authenticator app's setup screen offers this as a fallback) —
 * {@code provisioningUri} is the same secret, packaged as an {@code otpauth://} URI a QR-code
 * generator can render directly. Returned exactly once, right after generation; never re-returned,
 * never logged (code-standards.md "Security Rules") — {@code users.two_factor_secret} stores only
 * the AES-GCM-encrypted form from this point on.
 */
public record TwoFactorEnableResponse(
        String secret,
        String provisioningUri
) {
}
