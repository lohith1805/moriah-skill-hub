package com.moriah.skillhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * IPs allowed to set {@code X-Forwarded-For} and be believed — the load balancer / reverse
 * proxy fronting this service in a real deployment, identified by {@link
 * jakarta.servlet.http.HttpServletRequest#getRemoteAddr()} (the one value in this exchange that
 * cannot be forged by the caller). Empty by default: no deployment target is chosen yet
 * (progress-tracker.md), so trusting nothing and falling back to {@code getRemoteAddr()}
 * everywhere is the safe default until a real proxy's IP is known and set here per-environment.
 */
@ConfigurationProperties(prefix = "moriah.security")
public record TrustedProxyProperties(List<String> trustedProxies) {

    public TrustedProxyProperties {
        if (trustedProxies == null) {
            trustedProxies = List.of();
        }
    }
}
