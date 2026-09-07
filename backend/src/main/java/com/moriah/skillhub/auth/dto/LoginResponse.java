package com.moriah.skillhub.auth.dto;

/**
 * Returned by {@code POST /api/v1/auth/login} and the OAuth2 callback — one shape for both,
 * whether or not 2FA is involved (`/architect feature 05` decision). Credentials that check out
 * always mean {@code success: true} at the {@link com.moriah.skillhub.common.dto.ApiResponse}
 * envelope level; the ambiguity between "fully logged in" and "2FA still required" lives in
 * which of these fields is populated, not in the envelope.
 * <p>
 * Exactly one of {@code challengeToken} / {@code tokens} is non-null:
 * {@code twoFactorRequired = true} → {@code challengeToken} set, exchange it at
 * {@code POST /2fa/verify} for the real pair. {@code twoFactorRequired = false} →
 * {@code tokens} set, login is complete.
 * <p>
 * {@code twoFactorSetupRequired} is only meaningful when {@code twoFactorRequired} is true: it
 * distinguishes "you already have 2FA enabled, enter your existing code" (false) from "your role
 * requires 2FA and you don't have it set up yet" (true — build-plan.md feature 05's "mandatory
 * for ADMIN and HR_MANAGER"). In the setup case, the same {@code challengeToken} also authorizes
 * {@code POST /2fa/enable} — there is no access token yet to authenticate that call with, so the
 * challenge token stands in for one until setup is confirmed (see {@code TwoFactorService}).
 */
public record LoginResponse(
        boolean twoFactorRequired,
        boolean twoFactorSetupRequired,
        String challengeToken,
        TokenPairResponse tokens
) {

    public static LoginResponse completed(TokenPairResponse tokens) {
        return new LoginResponse(false, false, null, tokens);
    }

    public static LoginResponse twoFactorChallenge(String challengeToken, boolean setupRequired) {
        return new LoginResponse(true, setupRequired, challengeToken, null);
    }
}
