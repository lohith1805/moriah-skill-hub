package com.moriah.skillhub.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Loads {@code db/testdata/dev-seed.sql} once per database, on startup, in the {@code dev}
 * profile only.
 * <p>
 * This is the "dev-profile runner" architecture.md prescribes for sample data:
 * <blockquote>Sample users, batches, and leads live in {@code db/testdata/} and are loaded by a
 * dev-profile runner, never by Flyway.</blockquote>
 * Doing it as a Flyway migration instead would run in {@code mvn verify} (polluting every
 * Testcontainers-backed integration test) and in production. This bean is never created under
 * the {@code test} or {@code prod} profiles.
 * <p>
 * The script itself is fully idempotent ({@code INSERT IGNORE} / {@code INSERT ... WHERE NOT
 * EXISTS}); the {@code admin@moriah.test} pre-check here just avoids re-parsing it on every
 * restart. To reload from scratch: {@code docker compose down -v && docker compose up -d}, then
 * restart the app (Flyway re-migrates, this re-seeds).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataLoader implements ApplicationRunner {

    private static final String SCRIPT = "db/testdata/dev-seed.sql";
    private static final String SENTINEL_EMAIL = "admin@moriah.test";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, SENTINEL_EMAIL);
            if (existing != null && existing > 0) {
                log.info("[dev-seed] already present ({} found), skipping {}", SENTINEL_EMAIL, SCRIPT);
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
            populator.setContinueOnError(false);
            DatabasePopulatorUtils.execute(populator, dataSource);

            log.info("[dev-seed] loaded {} — accounts: admin@ / pm@ / dev@ / sales@ / hr@ / ba@ / "
                    + "client@ / student1@ / student2@ / student3@ moriah.test, password \"Password123!\"", SCRIPT);
        } catch (Exception e) {
            // Dev convenience only — never fail app startup because the seed didn't load.
            log.warn("[dev-seed] failed to load {} — the app will still start. Reason: {}", SCRIPT, e.getMessage());
        }
    }
}
