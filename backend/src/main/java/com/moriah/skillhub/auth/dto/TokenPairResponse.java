package com.moriah.skillhub.auth.dto;

public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
