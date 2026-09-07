package com.moriah.skillhub.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Audit 2026-08-31 (M10): a single place that shouts at startup when a security-relevant setting
 * is left at its unsafe local-dev default in a non-local profile. It only logs — it never fails
 * startup — so a misconfigured deploy is loud in the logs rather than silently insecure.
 * ({@code trusted-proxies} has its own equivalent check in {@code ClientIpResolver}.)
 */
@Component
@Slf4j
public class StartupHardeningWarnings {

    private final Environment environment;
    private final String redisPassword;

    public StartupHardeningWarnings(Environment environment,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        this.environment = environment;
        this.redisPassword = redisPassword;
    }

    @PostConstruct
    void check() {
        if (isLocalProfile()) {
            return;
        }
        if (redisPassword == null || redisPassword.isBlank()) {
            log.warn("spring.data.redis.password is BLANK in profile(s) {} — Redis holds 2FA "
                    + "challenges, the JWT denylist and rate-limit counters. Anyone who can reach "
                    + "the Redis port can forge a 2FA challenge or clear the denylist. Set "
                    + "REDIS_PASSWORD and network-isolate/TLS Redis before serving real traffic.",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }

    private boolean isLocalProfile() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0
                || Arrays.stream(active).anyMatch(p -> p.equals("dev") || p.equals("test") || p.equals("local"));
    }
}
