package com.moriah.skillhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-cache Redis TTLs, externalized the same way {@code JwtProperties}/{@code
 * RateLimitProperties} externalize their own thresholds — a hardcoded {@code Duration} literal in
 * {@code RedisConfig} would be exactly the kind of inline magic value code-standards.md's
 * "Constants" rule forbids, and every other spec'd numeric threshold in this feature set already
 * lives in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "moriah.cache")
public record CacheProperties(
        int entitlementsTtlSeconds,
        int defaultTtlMinutes,
        int githubPrTtlMinutes
) {
}
