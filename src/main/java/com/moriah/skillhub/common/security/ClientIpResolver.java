package com.moriah.skillhub.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single place that decides "what is this request's client IP" — used by both {@link
 * RateLimitFilter} (per-IP rate limiting) and {@code AuthController} (audit-log IP on failed
 * logins etc.). Both need the same answer, and both were getting it wrong the same way before
 * this existed: {@code request.getRemoteAddr()} alone is the immediate TCP peer, which behind
 * any real reverse proxy or load balancer is the proxy itself, not the client — silently
 * collapsing every request into one rate-limit bucket and recording the proxy's IP in every
 * audit row.
 * <p>
 * {@code X-Forwarded-For} is only trusted when {@code getRemoteAddr()} itself is a configured
 * trusted proxy ({@link TrustedProxyProperties}) — never unconditionally. An untrusted direct
 * caller can set any {@code X-Forwarded-For} value it wants; believing it from anyone but a
 * known proxy hop would let a client spoof its own rate-limit identity and audit-log IP.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final TrustedProxyProperties trustedProxyProperties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxyProperties.trustedProxies().contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        // "client, proxy1, proxy2, ..." — the leftmost entry is the original client; everything
        // after it is the chain of proxies the request passed through. Only the leftmost value
        // is meaningful here, and even it is proxy-supplied rather than independently verified.
        String client = forwardedFor.split(",")[0].trim();
        return client.isEmpty() ? remoteAddr : client;
    }
}
