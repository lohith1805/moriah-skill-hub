package com.moriah.skillhub.auth.dto;

/**
 * {@code POST /2fa/verify} does double duty (see {@link TwoFactorVerifyRequest}'s Javadoc) —
 * this wraps that same ambiguity the way {@link LoginResponse} wraps "2FA required or not": one
 * declared response shape for OpenAPI/Swagger, with the actual branch expressed in the payload.
 * {@code tokens} is null for a setup-confirmation call, populated for a login-completion call.
 */
public record TwoFactorVerifyResponse(TokenPairResponse tokens) {

    public static TwoFactorVerifyResponse setupConfirmed() {
        return new TwoFactorVerifyResponse(null);
    }

    public static TwoFactorVerifyResponse loginCompleted(TokenPairResponse tokens) {
        return new TwoFactorVerifyResponse(tokens);
    }
}
