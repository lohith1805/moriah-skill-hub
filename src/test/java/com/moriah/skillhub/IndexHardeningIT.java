package com.moriah.skillhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 23 (Audit, Hardening and Performance) — proves every index/CHECK constraint added by
 * {@code V16__indexes_constraints.sql} actually applies to the real query pattern it was written
 * for, the same {@code EXPLAIN}-against-{@code possible_keys} technique {@code LearningSchemaIT}
 * already established for {@code idx_tasks_sprint_status_due} (see that class's own Javadoc for
 * why {@code possible_keys}, not {@code key}: on a handful of rows MySQL's optimizer will often
 * correctly prefer a full scan over an index seek, which would make {@code key} null regardless of
 * whether the index is genuinely usable — {@code possible_keys} is independent of row count,
 * proven purely by whether the index's leading columns match the predicate). No fixture rows are
 * inserted for the {@code EXPLAIN} assertions for exactly that reason — an empty table still
 * proves the index applies to the shape of the query.
 * <p>
 * {@code @Transactional} — same reasoning as {@code LearningSchemaIT}: every call here runs
 * in-process on the test thread via plain {@code JdbcTemplate}, so per-method rollback is safe.
 */
@Transactional
class IndexHardeningIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void notificationReaperQuery_hasStatusCreatedIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM notifications
                 WHERE status = 'QUEUED' AND created_at < NOW()
                """, "idx_notifications_status_created");
    }

    @Test
    void refundLookupQuery_hasGatewayPaymentIdIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM payments WHERE gateway_payment_id = 'pay_test123'
                """, "idx_payments_gateway_payment_id");
    }

    @Test
    void projectSearchQuery_hasStatusIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM projects WHERE status = 'PUBLISHED' ORDER BY created_at DESC
                """, "idx_projects_status");
    }

    @Test
    void attendanceFinalisationQuery_hasFinalisationIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM standups
                 WHERE status <> 'CANCELLED' AND finalised_at IS NULL AND scheduled_at < NOW()
                """, "idx_standups_finalisation");
    }

    @Test
    void reviewQueueQuery_hasStatusDueIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM tasks WHERE status = 'IN_REVIEW' ORDER BY due_at ASC
                """, "idx_tasks_status_due");
    }

    @Test
    void subscriptionExpiryQuery_hasStatusEndIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM user_subscriptions
                 WHERE status = 'ACTIVE' AND end_date < CURDATE()
                """, "idx_user_subscriptions_status_end");
    }

    @Test
    void batchAllocationCandidatesQuery_hasTrackStatusIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM batches
                 WHERE status IN ('PLANNED', 'ACTIVE') AND track_code = 'FULLSTACK'
                """, "idx_batches_track_status");
    }

    @Test
    void payrollByPeriodMonthQuery_hasPeriodMonthIndexAvailable() {
        assertPossibleKey("""
                EXPLAIN SELECT id FROM payroll_records WHERE period_month = '2026-01-01'
                """, "idx_payroll_records_period_month");
    }

    @Test
    void roles_rejectsACodeOutsideTheEightSeededRoles() {
        // MySQL error 3819 ("Check constraint ... is violated") — Spring's default SQL-state/
        // error-code translation doesn't map this one specifically to the narrower
        // DataIntegrityViolationException (confirmed the hard way: it surfaces as
        // UncategorizedSQLException instead), so this asserts against the common
        // DataAccessException superclass both extend, plus the actual constraint name in the
        // driver's own message — proving the real CHECK fired, not just "some exception".
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO roles (code, name) VALUES ('NOT_A_REAL_ROLE', 'Bogus Role')
                """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_roles_code");
    }

    private void assertPossibleKey(String explainSql, String expectedIndexName) {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(explainSql);

        assertThat(plan).hasSize(1);
        Object possibleKeys = plan.get(0).get("possible_keys");
        assertThat(possibleKeys).as("EXPLAIN's possible_keys for: " + explainSql).asString()
                .contains(expectedIndexName);
    }
}
