package com.moriah.skillhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moriah.security.rate-limit")
public record RateLimitProperties(
        int authRequestsPerMinute,
        int globalRequestsPerMinute
) {
}
