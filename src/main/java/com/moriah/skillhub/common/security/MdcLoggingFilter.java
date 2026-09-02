package com.moriah.skillhub.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a per-request correlation id into the SLF4J {@link MDC} so every log line of one request
 * shares an id, and clears the MDC afterwards. Ordered ahead of the Spring Security filter chain
 * so the id is present even for requests Security rejects; {@code JwtAuthFilter} adds
 * {@code userId} to the same MDC once a token is validated, and the {@code finally} block here
 * clears both.
 * <p>
 * Structured log output ({@code logging.structured.format.*}) emits every MDC key automatically.
 * For the plain text pattern, add {@code [%X{requestId:-}]} to {@code logging.pattern.console}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
