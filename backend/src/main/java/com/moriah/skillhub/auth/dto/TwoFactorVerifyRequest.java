package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Serves two distinct purposes, distinguished by whether {@code challengeToken} is present
 * (`/architect feature 05` decision — build-plan.md lists only one {@code /2fa/verify}
 * endpoint for both):
 * <ul>
 *   <li>{@code challengeToken} null — confirming setup. Called with a normal access token right
 *       after {@code /2fa/enable}; the caller's identity comes from {@code @CurrentUser}.</li>
 *   <li>{@code challengeToken} set — completing a 2FA-gated login. The caller has no access token
 *       yet; the challenge token (from {@code LoginResponse}) identifies who is logging in.</li>
 * </ul>
 */
public record TwoFactorVerifyRequest(
        String challengeToken,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String totpCode
) {
}
