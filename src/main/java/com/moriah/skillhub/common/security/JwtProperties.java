package com.moriah.skillhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moriah.jwt")
public record JwtProperties(
        String secret,
        String keyId,
        long accessTokenMinutes,
        long refreshTokenDays
) {
}
