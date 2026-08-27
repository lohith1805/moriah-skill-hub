package com.moriah.skillhub.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * The intermediate "credentials were valid, 2FA still required" state between {@code /login} (or
 * the OAuth2 callback) and {@code /2fa/verify} — `/architect feature 05` decision: an opaque
 * 512-bit token (the same {@link OpaqueTokenGenerator} pattern as refresh/verification/reset
 * tokens), but held in Redis with a short TTL rather than a new SQL table. A mid-login state like
 * this has no audit value once it expires or is consumed, so a table (and the migration-numbering
 * shift a new one would cause) buys nothing a Redis key doesn't already give. The TTL itself is
 * externalized to {@link TwoFactorProperties} (`/review` follow-up), not hardcoded here.
 * <p>
 * {@link #peekUserId} deliberately does not delete the key — a user who mistypes their TOTP code
 * must be able to retry within the same challenge window without restarting login from
 * credentials. Only {@link #consume} removes it, called once the code is verified and the real
 * token pair is about to be issued, so the challenge token can never be reused afterward.
 */
@Service
@RequiredArgsConstructor
public class ChallengeTokenService {

    private static final String KEY_PREFIX = "2fa:challenge:";

    private final StringRedisTemplate redisTemplate;
    private final TwoFactorProperties twoFactorProperties;

    /** Returns the raw token — callers return this to the client once, never persist or log it
     * (code-standards.md "Security Rules": never log a token), same discipline as every other
     * opaque token in this system. */
    public String issue(Long userId) {
        String raw = OpaqueTokenGenerator.generate();
        Duration ttl = Duration.ofMinutes(twoFactorProperties.challengeTtlMinutes());
        redisTemplate.opsForValue().set(
                KEY_PREFIX + OpaqueTokenGenerator.sha256Hex(raw), userId.toString(), ttl);
        return raw;
    }

    public Optional<Long> peekUserId(String rawToken) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + OpaqueTokenGenerator.sha256Hex(rawToken));
        return Optional.ofNullable(value).map(Long::valueOf);
    }

    public void consume(String rawToken) {
        redisTemplate.delete(KEY_PREFIX + OpaqueTokenGenerator.sha256Hex(rawToken));
    }
}
