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
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
 * {@code auth-requests-per-minute} (10); everything else gets
 * {@code global-requests-per-minute} (60).
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

        String ip = clientIpResolver.resolve(request);
        long window = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:%s:%d".formatted(ip, window);

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

    private void writeRateLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ErrorDetail.of(ErrorCode.RATE_LIMIT_EXCEEDED));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
