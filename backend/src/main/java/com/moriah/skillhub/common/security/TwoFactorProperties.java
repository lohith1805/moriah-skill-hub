package com.moriah.skillhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code challengeTtlMinutes} — how long a {@link ChallengeTokenService} challenge token stays
 * valid. Externalized rather than a hardcoded constant, matching the `/review` precedent set for
 * every other Redis-backed timing value in this feature set (`JwtProperties`,
 * `RateLimitProperties`, `CacheProperties`) — a production deployment may reasonably want a
 * different window than local dev's default.
 */
@ConfigurationProperties(prefix = "moriah.security.two-factor")
public record TwoFactorProperties(int challengeTtlMinutes) {
}
