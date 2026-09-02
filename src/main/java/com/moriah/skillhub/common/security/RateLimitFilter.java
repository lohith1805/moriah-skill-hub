package com.moriah.skillhub.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.ErrorDetail;
import com.moriah.skillhub.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed, IP-scoped — key namespace {@code ratelimit:{ip}:{window}} (library-docs.md
 * "Redis"), one-minute fixed windows. Runs before {@link JwtAuthFilter} and applies to every
 * request regardless of authentication status, including {@code permitAll()} endpoints —
 * webhook signature verification is CPU work and is otherwise a free DoS surface
 * (build-plan.md feature 07).
 * <p>
 * Two tiers, feature 03's auth-specific limit and feature 04's global one, in a single filter
 * rather than two separate mechanisms: {@code /api/v1/auth/**} gets the tighter
 * {@code auth-requests-per-minute}; everything else gets {@code global-requests-per-minute}.
 * <p>
 * <b>Audit 2026-08-31 (C4):</b> a request carrying a bearer token is bucketed by a hash of that
 * token, not by client IP. Behind a load balancer that terminates TLS, every request's {@code
 * getRemoteAddr()} is the LB's address (until {@code trusted-proxies} is configured), which would
 * otherwise collapse all authenticated traffic into a single shared bucket. Tokenless requests —
 * which includes the {@code /api/v1/auth/**} brute-force surface — still bucket by IP, so a
 * correct {@code trusted-proxies} configuration remains necessary for login throttling to work
 * per-client (and account-level lockout in {@code AuthService} is the complementary defence).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = path.startsWith("/api/v1/auth/")
                ? properties.authRequestsPerMinute()
                : properties.globalRequestsPerMinute();

        long window = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:%s:%d".formatted(callerIdentity(request), window);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (count != null && count > limit) {
            writeRateLimitExceeded(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * A bearer-token request buckets on {@code tok:<md5(token)>}; everything else on
     * {@code ip:<client ip>}. The hash keeps raw tokens out of Redis keys and logs; MD5 is
     * adequate here (a bucketing discriminator, not a security primitive).
     */
    private String callerIdentity(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) {
                return "tok:" + DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
            }
        }
        return "ip:" + clientIpResolver.resolve(request);
    }

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ErrorDetail.of(ErrorCode.RATE_LIMIT_EXCEEDED));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
