package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Requires a currently-valid TOTP code, not just an authenticated call — a hijacked session
 * should not be able to silently turn off a victim's second factor (`/architect feature 05`
 * decision).
 */
public record TwoFactorDisableRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String totpCode
) {
}
