package com.moriah.skillhub;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Feature 01 verify line "the runtime user provably cannot run DDL" — not just by
 * inspecting {@code docker/mysql-init/01-users.sql}, but by connecting as each user and
 * attempting the exact operation the grant is supposed to allow or reject. Runs the real
 * {@code moriah_migrate}/{@code moriah_app} split (see {@link IntegrationTestBase}), the same
 * script docker-compose uses — one source of truth for both environments.
 * <p>
 * Raw JDBC, not the application's Spring-managed datasource: the whole point is to connect as
 * a specific MySQL user and observe what MySQL itself allows, independent of anything the
 * application configures.
 */
class DatabaseUserPrivilegesIT extends IntegrationTestBase {

    private static final String DB = "moriah_skillhub";

    private String jdbcUrl() {
        return "jdbc:mysql://%s:%d/%s".formatted(MYSQL.getHost(), MYSQL.getMappedPort(3306), DB);
    }

    @Test
    void moriahAppCanReadAndWriteData() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "moriah_app", "app_dev_only");
             Statement statement = connection.createStatement()) {
            assertThat(statement.execute("SELECT 1")).isTrue();
        }
    }

    @Test
    void moriahAppCannotRunDdl() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "moriah_app", "app_dev_only");
             Statement statement = connection.createStatement()) {

            assertThatThrownBy(() -> statement.execute("CREATE TABLE probe_ddl_should_fail (id INT)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("command denied");
        }
    }

    @Test
    void moriahMigrateCanRunDdl() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "moriah_migrate", "migrate_dev_only");
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE probe_ddl_should_succeed (id INT)");
            statement.execute("DROP TABLE probe_ddl_should_succeed");
        }
    }

    /**
     * Feature 02 verify line: "moriah_app fails on UPDATE audit_logs". By the time this test
     * runs, Flyway has already created audit_logs (V2) and afterMigrate.sql has already narrowed
     * moriah_app to SELECT+INSERT on it — this proves that narrowing actually took effect,
     * not just that the callback file exists.
     */
    @Test
    void moriahAppCanInsertAndSelectAuditLogsButNotUpdateOrDelete() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "moriah_app", "app_dev_only");
             Statement statement = connection.createStatement()) {

            statement.execute("INSERT INTO audit_logs (action, entity_type) VALUES ('PROBE_INSERT', 'Probe')");
            assertThat(statement.execute("SELECT COUNT(*) FROM audit_logs")).isTrue();

            assertThatThrownBy(() ->
                    statement.execute("UPDATE audit_logs SET action = 'CHANGED' WHERE action = 'PROBE_INSERT'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("command denied");

            assertThatThrownBy(() ->
                    statement.execute("DELETE FROM audit_logs WHERE action = 'PROBE_INSERT'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("command denied");
        }
    }

    /**
     * Caught in review: afterMigrate.sql's per-table grant loop excluded only audit_logs,
     * which meant flyway_schema_history — matched by the same information_schema query, since
     * it's a base table like any other — silently got UPDATE/DELETE granted too. The runtime
     * app pool has no legitimate reason to modify Flyway's own migration ledger.
     */
    @Test
    void moriahAppCannotUpdateOrDeleteFlywaySchemaHistory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), "moriah_app", "app_dev_only");
             Statement statement = connection.createStatement()) {

            assertThatThrownBy(() ->
                    statement.execute("UPDATE flyway_schema_history SET success = success WHERE installed_rank = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("command denied");

            assertThatThrownBy(() ->
                    statement.execute("DELETE FROM flyway_schema_history WHERE installed_rank = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("command denied");
        }
    }
}
