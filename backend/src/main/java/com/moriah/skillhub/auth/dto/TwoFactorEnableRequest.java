package com.moriah.skillhub.auth.dto;

/**
 * {@code challengeToken} is null for the voluntary path (an already-logged-in caller turning 2FA
 * on, identified by their access token) and set for the mandatory-2FA path (an ADMIN/HR_MANAGER
 * with {@code LoginResponse.twoFactorSetupRequired = true}, who has no access token yet — see
 * {@code TwoFactorService.enable}). The request body itself is optional at the HTTP layer
 * ({@code AuthController} accepts a missing body as the voluntary path with no challenge token).
 */
public record TwoFactorEnableRequest(String challengeToken) {
}
