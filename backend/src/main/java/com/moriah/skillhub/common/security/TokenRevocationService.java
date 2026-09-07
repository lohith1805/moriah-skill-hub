package com.moriah.skillhub.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Owns the {@code jti} denylist (Redis key namespace {@code denylist:jti:{jti}} —
 * library-docs.md "Redis"), populated on explicit {@code /logout}.
 * <p>
 * Deliberately does <b>not</b> also cache {@code token_version} in Redis, despite
 * architecture.md describing that check as "cached in Redis, 60s TTL". {@code JwtAuthFilter}
 * must load the {@code User} row by {@code uuid} on every request regardless — {@code sub} is
 * the uuid, never the numeric id (library-docs.md "JJWT") — so it already holds the freshest
 * {@code token_version} from that same query. Adding a separate 60s-TTL cache on top would be
 * redundant complexity with a strictly worse guarantee: it would let a suspended user's token
 * stay valid for up to 60 seconds instead of failing on the very next request. Revisit only if
 * the per-request user lookup is ever removed from the hot path (e.g. by embedding the numeric
 * id in the token itself, which the documented claim set does not do).
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String DENYLIST_PREFIX = "denylist:jti:";

    private final StringRedisTemplate redisTemplate;

    public boolean isDenylisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(DENYLIST_PREFIX + jti));
    }

    /** {@code ttl} should be the token's remaining lifetime — no reason to outlive the JWT it revokes. */
    public void denylist(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(DENYLIST_PREFIX + jti, "1", ttl);
    }
}
