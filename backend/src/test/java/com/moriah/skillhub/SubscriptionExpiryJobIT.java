package com.moriah.skillhub;

import com.moriah.skillhub.subscription.SubscriptionExpiryService;
import com.moriah.skillhub.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * build-plan.md feature 07: "Without it every subscription is perpetual." Calls {@code
 * runExpiry()} directly on {@link SubscriptionExpiryService} — a separate bean from {@code
 * SubscriptionExpiryJob} (see that service's Javadoc: {@code @Transactional} on a self-invoked
 * method from a {@code @Scheduled} caller does nothing) — rather than waiting on the real {@code
 * @Scheduled} cron trigger (03:00 IST — see the class Javadoc for why) — same reasoning {@code
 * RateLimitFilterIT} uses for testing the mechanism itself, not the trigger.
 */
@Transactional
class SubscriptionExpiryJobIT extends IntegrationTestBase {

    @Autowired
    private SubscriptionExpiryService subscriptionExpiryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Test
    void expiresOnlyActiveSubscriptionsPastEndDate_leavesEverythingElseAlone() {
        long planId = insertPlan();
        long overdueUserId = insertUser();
        long stillActiveUserId = insertUser();
        long alreadyExpiredUserId = insertUser();
        long pendingUserId = insertUser();

        long overdueSubId = insertSubscription(overdueUserId, planId, "ACTIVE", -10, -1); // ended yesterday
        long stillActiveSubId = insertSubscription(stillActiveUserId, planId, "ACTIVE", -10, 10); // ends in the future
        long alreadyExpiredSubId = insertSubscription(alreadyExpiredUserId, planId, "EXPIRED", -30, -20);
        long pendingSubId = insertSubscription(pendingUserId, planId, "PENDING", -10, -1); // never activated

        int expiredCount = subscriptionExpiryService.runExpiry();
        // runExpiry() updates managed entities via dirty checking — saveAll() doesn't force an
        // immediate flush, so the UPDATE isn't necessarily on the wire yet. Reading it back below
        // via raw JdbcTemplate (outside Hibernate's session) would otherwise see stale data, same
        // flush-timing issue CouponService.redeem() already documents.
        userSubscriptionRepository.flush();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(statusOf(overdueSubId)).isEqualTo("EXPIRED");
        assertThat(statusOf(stillActiveSubId)).isEqualTo("ACTIVE");
        assertThat(statusOf(alreadyExpiredSubId)).isEqualTo("EXPIRED");
        assertThat(statusOf(pendingSubId)).isEqualTo("PENDING");
    }

    private String statusOf(long subscriptionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM user_subscriptions WHERE id = ?", String.class, subscriptionId);
    }

    private long insertPlan() {
        // subscription_plans.code is VARCHAR(30) — a full UUID doesn't fit, an 8-char slice does.
        String code = "PLAN-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO subscription_plans (code, name, price_inr, tier_rank, duration_days, is_active)
                VALUES (?, ?, 100.00, 1, 30, TRUE)
                """, code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
    }

    private long insertUser() {
        String email = "expiry-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Expiry Test', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertSubscription(long userId, long planId, String status, int startOffsetDays, int endOffsetDays) {
        jdbcTemplate.update("""
                INSERT INTO user_subscriptions (user_id, plan_id, start_date, end_date, status)
                VALUES (?, ?, DATE_ADD(CURDATE(), INTERVAL ? DAY), DATE_ADD(CURDATE(), INTERVAL ? DAY), ?)
                """, userId, planId, startOffsetDays, endOffsetDays, status);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM user_subscriptions WHERE user_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, userId);
    }
}
