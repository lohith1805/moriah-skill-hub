package com.moriah.skillhub.auth.dto;

/** No tokens issued — the account is {@code PENDING_VERIFICATION} until the email link is used. */
public record RegisterResponse(String uuid, String fullName, String email) {
}
