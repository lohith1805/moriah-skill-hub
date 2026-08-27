package com.moriah.skillhub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * build-plan.md feature 22: "Exports and heavy metrics reads target the read replica." Deliberately
 * its own small property set (url/username/password), not a reuse of {@code spring.datasource.*}
 * or {@code DB_HOST}/{@code DB_NAME}/{@code DB_USERNAME}/{@code DB_PASSWORD} — those are consumed
 * indirectly via Spring Boot's {@code JdbcConnectionDetails} abstraction for the primary pool
 * (including, in the test suite, Testcontainers' {@code @ServiceConnection} wiring), which a plain
 * property re-read can't see. See {@link ReplicaDataSourceConfig}'s Javadoc for the full reasoning.
 */
@ConfigurationProperties(prefix = "moriah.datasource.replica")
public record ReplicaDataSourceProperties(
        String url,
        String username,
        String password,
        @DefaultValue("10") int maxPoolSize
) {
}
