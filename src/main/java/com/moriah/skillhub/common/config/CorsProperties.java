package com.moriah.skillhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS allow-list, bound from configuration — {@code code-standards.md} forbids {@code *}.
 * Populated per-environment in {@code application-dev.yml} / {@code application-prod.yml}.
 */
@ConfigurationProperties(prefix = "moriah.security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
