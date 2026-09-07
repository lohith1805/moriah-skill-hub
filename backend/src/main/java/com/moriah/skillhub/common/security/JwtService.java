package com.moriah.skillhub.common.security;

import com.moriah.skillhub.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and parses access tokens only — refresh tokens are a separate, opaque, SHA-256-hashed
 * value (library-docs.md "JJWT"), never a JWT.
 * <p>
 * JJWT 0.13.x API: {@code signWith(key, Jwts.SIG.HS512)}, {@code parseSignedClaims()}. The older
 * {@code setSubject}/{@code parseClaimsJws} builders are deprecated — never use them
 * (library-docs.md "JJWT" rules).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    public String generateAccessToken(User user, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUuid())
                .claim("roles", roles)
                .claim("tv", user.getTokenVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTokenMinutes(), ChronoUnit.MINUTES)))
                .header().keyId(props.keyId()).and()
                .signWith(key(), Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Throws an unchecked {@code io.jsonwebtoken.JwtException} subtype (expired, malformed, bad
     * signature) on any invalid token — {@code JwtAuthFilter} treats any such failure as
     * unauthenticated rather than distinguishing the exact cause to the caller.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
    }

    /**
     * One shared secret today, so {@code kid} in the header is bookkeeping for a future rotation
     * scheme rather than selecting between multiple active keys — the capability the header
     * enables, not a fully implemented multi-key lookup. Revisit if/when a second concurrent
     * secret is introduced.
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }
}
