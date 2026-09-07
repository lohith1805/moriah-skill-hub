package com.moriah.skillhub.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A separate bean from {@link EntitlementGuard} deliberately: {@code @Cacheable} is proxy-based
 * AOP, and a method calling another {@code @Cacheable} method on {@code this} within the same
 * class bypasses the proxy entirely — the cache would silently never be hit. Cross-bean calls
 * (guard → loader) go through the proxy correctly.
 * <p>
 * Native query, not a repository over a {@code UserSubscription} entity: {@code common/} never
 * imports a feature package (architecture.md invariant), and {@code subscription/} — which owns
 * that entity — doesn't exist until feature 07. The tables already exist (V3, feature 02), so a
 * read-only projection needs nothing from that future package.
 */
@Component
@RequiredArgsConstructor
public class EntitlementFlagsLoader {

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(value = "entitlements", key = "#userId")
    public EntitlementFlags load(Long userId) {
        List<EntitlementFlags> rows = jdbcTemplate.query("""
                SELECT sp.allows_batch, sp.allows_sprints, sp.allows_pip, sp.mentor_support,
                       sp.allows_internship_letter, sp.allows_client_project, sp.tier_rank
                  FROM user_subscriptions us
                  JOIN subscription_plans sp ON sp.id = us.plan_id
                 WHERE us.user_id = ? AND us.status = 'ACTIVE'
                """,
                (rs, rowNum) -> new EntitlementFlags(
                        rs.getBoolean("allows_batch"),
                        rs.getBoolean("allows_sprints"),
                        rs.getBoolean("allows_pip"),
                        rs.getBoolean("mentor_support"),
                        rs.getBoolean("allows_internship_letter"),
                        rs.getBoolean("allows_client_project"),
                        rs.getInt("tier_rank")),
                userId);

        return rows.isEmpty() ? EntitlementFlags.NONE : rows.get(0);
    }

    /** `/architect feature 10`: {@code BatchAllocationService.allocate} runs synchronously in
     * the same transaction that just activated a subscription — the up-to-60s staleness this
     * cache's TTL otherwise tolerates (fine for a subsequent HTTP request re-checking
     * entitlements) isn't acceptable for a webhook-triggered action that needs the plan it was
     * just paid for, immediately. A stale hit here would silently use the caller's *previous*
     * entitlements — e.g. a re-subscribing user's expired {@code NONE} — for an allocation
     * decision. Called right before {@link #load} so every {@code allocate()} call sees a fresh
     * read regardless of what any other caller cached in the last 60 seconds. */
    @CacheEvict(value = "entitlements", key = "#userId")
    public void evict(Long userId) {
    }
}
