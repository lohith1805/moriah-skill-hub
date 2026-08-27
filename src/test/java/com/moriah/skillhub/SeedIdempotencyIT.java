package com.moriah.skillhub;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 02 verify line: "Re-running seeders changes nothing." Uses the app's own
 * Spring-managed {@link Flyway} bean — the exact instance and configuration the application
 * boots with — rather than re-implementing the INSERT ... ON DUPLICATE KEY UPDATE logic by hand.
 */
class SeedIdempotencyIT extends IntegrationTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reRunningMigrateAppliesNothingNewAndSeedRowCountsAreUnchanged() {
        Integer rolesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        Integer plansBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_plans", Integer.class);

        // The context already migrated once on startup; this is the re-run.
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class))
                .isEqualTo(rolesBefore).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subscription_plans", Integer.class))
                .isEqualTo(plansBefore).isEqualTo(5);
    }
}
