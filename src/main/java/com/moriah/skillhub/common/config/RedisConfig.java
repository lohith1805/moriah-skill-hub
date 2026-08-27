package com.moriah.skillhub.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * {@code StringRedisTemplate} itself needs no bean here — Spring Boot autoconfigures one from
 * {@code spring.data.redis.*} the moment {@code spring-boot-starter-data-redis} is on the
 * classpath. This class only adds the cache manager, since the default JDK serializer produces
 * unreadable keys (library-docs.md "Redis") and the per-cache TTLs matter: only genuinely
 * read-heavy, rarely-changing data may be cached (never anything student-scoped, financial, or
 * PIP-status related — library-docs.md "Redis" rules).
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final CacheProperties cacheProperties;

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(cacheProperties.defaultTtlMinutes()))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // entitlements: EntitlementGuard reads the active subscription per check, cached 60s by
        // default (architecture.md "Authentication" / build-plan.md feature 04) — an upgrade
        // must take effect within a minute, not stay stale for the longer default TTL. The
        // number itself lives in application.yml (moriah.cache.entitlements-ttl-seconds), not
        // here, matching every other spec'd threshold in this feature set (JwtProperties,
        // RateLimitProperties).
        // githubPr: build-plan.md feature 12 — "Cache verification results in Redis 5 min keyed
        // owner/repo/pr — a batch refreshing must not burn the 5,000/hr limit."
        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                "entitlements", defaultConfig.entryTtl(Duration.ofSeconds(cacheProperties.entitlementsTtlSeconds())),
                "githubPr", defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.githubPrTtlMinutes())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
