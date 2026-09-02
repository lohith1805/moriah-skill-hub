package com.moriah.skillhub.common.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

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
 * <p>
 * <b>Audit 2026-08-31 (C4):</b> {@code trusted-proxies} entries may now be either exact IPs or
 * CIDR ranges ({@code 10.0.0.0/8}), so a deployment behind a load balancer with a dynamic egress
 * IP can actually be configured. A prominent startup warning fires if the list is empty in a
 * non-local profile, where an empty list means every request's client IP is the LB's.
 */
@Component
@Slf4j
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final Environment environment;
    private final List<String> configuredProxies;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpResolver(TrustedProxyProperties trustedProxyProperties, Environment environment) {
        this.environment = environment;
        this.configuredProxies = trustedProxyProperties.trustedProxies().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
        this.trustedProxyMatchers = this.configuredProxies.stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    @PostConstruct
    void warnIfUnconfiguredBehindProxy() {
        boolean localProfile = environment.getActiveProfiles().length == 0
                || Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> p.equals("dev") || p.equals("test") || p.equals("local"));
        if (trustedProxyMatchers.isEmpty() && !localProfile) {
            log.warn("moriah.security.trusted-proxies is EMPTY in profile(s) {} — X-Forwarded-For is "
                    + "ignored, so every request's client IP resolves to the load balancer's address. "
                    + "Rate-limit buckets and audit-log IPs will all collapse to one value. Set "
                    + "TRUSTED_PROXIES to the proxy IP(s) or CIDR range(s) before serving real traffic.",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
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

    private boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // e.g. an IPv4 matcher tested against an IPv6 remote addr — not a match, not fatal.
            }
        }
        return false;
    }
}
