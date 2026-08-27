package com.moriah.skillhub;

import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.security.Entitlement;
import com.moriah.skillhub.common.security.EntitlementGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * build-plan.md feature 04 verify line ("A STARTER gets 403 on a sprint endpoint, PROJECT_BASED
 * gets 200") tested directly against {@link EntitlementGuard} rather than through a real sprint
 * endpoint — none exists yet (feature 11). This is the meaningful, buildable proof at this
 * stage: the entitlement-check mechanism itself, which every future feature's endpoints will
 * call unchanged.
 * <p>
 * {@code @Transactional} rolls each test method's JDBC fixture rows (users, subscription_plans,
 * user_subscriptions) back automatically — required because the MySQL container is a
 * static-singleton shared across every IT class in the same {@code mvn verify} run
 * (IntegrationTestBase), so uncommitted-then-rolled-back is the only way these fixtures don't
 * leak into a later class's row-count assertions (e.g. SeedIdempotencyIT). Safe here because
 * {@link EntitlementGuard#require} runs in-process on the same test thread; it would NOT be safe
 * for AuthFlowIT, whose real HTTP calls execute on a separate servlet thread with its own
 * connection.
 */
@Transactional
class EntitlementGuardIT extends IntegrationTestBase {

    @Autowired
    private EntitlementGuard entitlementGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void noActiveSubscription_deniesEveryEntitlement() {
        long userId = insertUser("no-sub@example.com");

        assertThatThrownBy(() -> entitlementGuard.require(userId, Entitlement.BATCH))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void activeSubscriptionWithFlagTrue_grantsThatEntitlementOnly() {
        long userId = insertUser("project-based@example.com");
        insertActiveSubscription(userId, "PROJECT_BASED_TEST", true, true, true, false, false, false);

        assertThatCode(() -> entitlementGuard.require(userId, Entitlement.BATCH)).doesNotThrowAnyException();
        assertThatCode(() -> entitlementGuard.require(userId, Entitlement.SPRINTS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> entitlementGuard.require(userId, Entitlement.CLIENT_PROJECT))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void inactiveSubscription_isNotEntitled() {
        long userId = insertUser("expired-sub@example.com");
        long planId = insertPlan("EXPIRED_PLAN_TEST", true, true, true, false, false, false);
        jdbcTemplate.update("""
                INSERT INTO user_subscriptions (user_id, plan_id, start_date, end_date, status)
                VALUES (?, ?, CURDATE(), CURDATE(), 'EXPIRED')
                """, userId, planId);

        assertThatThrownBy(() -> entitlementGuard.require(userId, Entitlement.BATCH))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private long insertUser(String email) {
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status)
                VALUES (UUID(), 'Entitlement Test', ?, 'ACTIVE')
                """, email);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return id;
    }

    private long insertPlan(String code, boolean batch, boolean sprints, boolean pip,
                             boolean mentor, boolean internshipLetter, boolean clientProject) {
        jdbcTemplate.update("""
                INSERT INTO subscription_plans
                    (code, name, price_inr, tier_rank, duration_days, mentor_support,
                     allows_batch, allows_sprints, allows_pip, allows_internship_letter, allows_client_project, is_active)
                VALUES (?, ?, 14999.00, 3, 180, ?, ?, ?, ?, ?, ?, TRUE)
                """, code, code, mentor, batch, sprints, pip, internshipLetter, clientProject);
        return jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
    }

    private void insertActiveSubscription(long userId, String planCode, boolean batch, boolean sprints,
                                           boolean pip, boolean mentor, boolean internshipLetter, boolean clientProject) {
        long planId = insertPlan(planCode, batch, sprints, pip, mentor, internshipLetter, clientProject);
        jdbcTemplate.update("""
                INSERT INTO user_subscriptions (user_id, plan_id, start_date, end_date, status)
                VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 180 DAY), 'ACTIVE')
                """, userId, planId);
    }
}
