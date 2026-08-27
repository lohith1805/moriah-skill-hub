package com.moriah.skillhub.common.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * build-plan.md feature 22: "Exports and heavy metrics reads target the read replica, so a
 * 50,000-row XLSX export never touches the primary." architecture.md's {@code mysql-replica}
 * docker-compose service is real async MySQL replication, already provisioned but "not wired to
 * the application... this is a feature 22 concern" per its own comment — this class is that
 * wiring.
 * <p>
 * Deliberately <b>not</b> a full routing {@code DataSource}/{@code EntityManagerFactory} split —
 * this feature's own brief calls that unnecessary complexity, and rightly so: every read this
 * feature does (the admin overview KPIs, the revenue-by-range query, the audit log listing, every
 * export query) is already a raw-SQL {@code JdbcTemplate} read, not entity-mapped, the same
 * reasoning {@code EntitlementFlagsLoader}/{@code OwnershipGuard} already establish for native
 * queries in {@code common/}. A second plain {@code JdbcTemplate} bean is enough.
 * <p>
 * The replica connection pool is deliberately <b>never registered as its own {@code @Bean}</b> —
 * only the {@link JdbcTemplate} wrapping it is. A second Spring-managed bean assignable to {@code
 * DataSource} would trip {@code DataSourceAutoConfiguration}'s own {@code
 * @ConditionalOnMissingBean(DataSource.class)} guard (it matches on type, not name or qualifier)
 * and silently suppress the application's real primary connection pool entirely — including, in
 * the test suite, Testcontainers' {@code @ServiceConnection}/{@code JdbcConnectionDetails} wiring
 * that pool depends on. Keeping the pool a plain, un-registered object sidesteps that failure mode
 * entirely, at the cost of the pool not being closed via Spring's own bean-lifecycle shutdown hook
 * — an acceptable trade-off for a read-only reporting pool (the OS reclaims the sockets on process
 * exit regardless).
 * <p>
 * <b>The primary {@code JdbcTemplate} bean below is a second, exactly analogous trap this class
 * fell into on the first pass, confirmed the hard way</b>: {@code JdbcTemplateAutoConfiguration}'s
 * own bean is guarded by {@code @ConditionalOnMissingBean(JdbcOperations.class)} — a plain,
 * unqualified {@code @Bean JdbcTemplate replicaJdbcTemplate()} (no {@code DataSource} bean
 * involved at all, so the {@code DataSource} guard above never fires) is still itself a {@code
 * JdbcOperations}, so its mere presence made Boot back off its own default {@code jdbcTemplate}
 * bean entirely — leaving the read-only replica pool as the *only* {@code JdbcTemplate} in the
 * whole context, silently handed to every one of the dozens of classes across this codebase that
 * autowire a plain, unqualified {@code JdbcTemplate} (e.g. {@code SchedulingConfig}'s {@code
 * LockProvider}, {@code EntitlementFlagsLoader}, {@code OwnershipGuard}, every test fixture
 * helper). Every one of those calls a write through what was now a read-only connection and failed
 * with "Connection is read-only" — reproduced by a full {@code mvn verify} run, not a hypothetical.
 * The fix mirrors the {@code DataSource} case one level up: redeclare the default explicitly,
 * marked {@code @Primary}, wrapping the same untouched, still-correctly-autoconfigured {@code
 * DataSource} bean (dev/prod's {@code spring.datasource.*}, or the test suite's Testcontainers
 * {@code @ServiceConnection} wiring — this class never touches that bean, only reads it).
 */
@Configuration
@RequiredArgsConstructor
public class ReplicaDataSourceConfig {

    private final ReplicaDataSourceProperties replicaDataSourceProperties;

    @Primary
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("replicaJdbcTemplate")
    JdbcTemplate replicaJdbcTemplate() {
        HikariDataSource replicaDataSource = new HikariDataSource();
        replicaDataSource.setJdbcUrl(replicaDataSourceProperties.url());
        replicaDataSource.setUsername(replicaDataSourceProperties.username());
        replicaDataSource.setPassword(replicaDataSourceProperties.password());
        replicaDataSource.setPoolName("replica-pool");
        replicaDataSource.setMaximumPoolSize(replicaDataSourceProperties.maxPoolSize());
        replicaDataSource.setReadOnly(true);
        return new JdbcTemplate(replicaDataSource);
    }
}
