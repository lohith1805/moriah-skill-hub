package com.moriah.skillhub.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Makes every {@code @Cacheable} in the app treat Redis as an optimisation, not a hard
 * dependency.
 *
 * <p>Without this, a single unreadable value in Redis — a cache entry written by an older build
 * whose DTO shape has since changed, a {@link SerializationException} from a serializer-config
 * change, or another process sharing the same Redis keyspace — makes the {@code @Cacheable}
 * method itself throw, so e.g. {@code GET /api/v1/plans} 500s until someone runs
 * {@code redis-cli FLUSHALL} by hand. This has already bitten {@code EntitlementService.listActivePlans}
 * more than once.
 *
 * <p>With the handler below a failed cache <em>get</em> evicts the offending key and is reported
 * as a miss, so the method runs against the database and repopulates the cache; a failed
 * <em>put/evict/clear</em> is logged and swallowed. A Redis outage then degrades to
 * "every request does the real work", never to a 500.
 */
@Configuration
@Slf4j
public class CacheErrorHandlingConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler();
    }

    static final class ResilientCacheErrorHandler implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            // A poison value (stale shape / foreign key / serializer mismatch) must not become a
            // 500 — drop it and let the caller fall through to a cache miss.
            if (exception instanceof SerializationException) {
                log.warn("[cache] unreadable value in '{}' for key {} — evicting and treating as a miss",
                        cache.getName(), key);
                safeEvict(cache, key);
            } else {
                log.warn("[cache] get failed for '{}' key {} ({}) — treating as a miss",
                        cache.getName(), key, exception.getMessage());
            }
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("[cache] put failed for '{}' key {} ({}) — value not cached",
                    cache.getName(), key, exception.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("[cache] evict failed for '{}' key {} ({})", cache.getName(), key, exception.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("[cache] clear failed for '{}' ({})", cache.getName(), exception.getMessage());
        }

        private void safeEvict(Cache cache, Object key) {
            try {
                cache.evict(key);
            } catch (RuntimeException evictFailure) {
                log.warn("[cache] follow-up evict of poison key {} in '{}' also failed ({})",
                        key, cache.getName(), evictFailure.getMessage());
            }
        }
    }
}
