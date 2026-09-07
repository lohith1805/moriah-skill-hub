package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Used for both {@code /logout} and {@code /logout-all} — identifies the caller and their
 * session from the refresh token itself, since {@code /api/v1/auth/**} is public at the filter
 * level (architecture.md "Public endpoints") and needs no separately validated access token to
 * act safely: only someone who already holds this specific refresh token can revoke it.
 */
public record LogoutRequest(@NotBlank String refreshToken) {
}
