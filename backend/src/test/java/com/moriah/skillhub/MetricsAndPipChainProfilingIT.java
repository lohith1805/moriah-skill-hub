package com.moriah.skillhub;

import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.pip.PipEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 24 (Integration Testing and UAT) — progress-tracker.md's UAT Scenarios row 8 / this
 * feature's own item 7 in the build brief: "Full cohort metrics refresh + PIP evaluation completes
 * in under 90 seconds." {@code PipEvaluationProfilingIT} (feature 23) already profiled {@code
 * PipEvaluationService#evaluate} alone against a synthetic 1,500-row {@code student_metrics}
 * cohort (bypassing {@code MetricsRefreshJob} itself, by that file's own explicit design — it
 * says so in its Javadoc). Read literally, this UAT scenario is about the *combined* nightly
 * pipeline (01:45 refresh -> 02:00 evaluation), not PIP evaluation in isolation — and no existing
 * test measures {@link StudentMetricsService#refresh} at any scale at all. This file closes that
 * gap: both jobs, back to back, against the same 1,500-student cohort, one combined wall-clock
 * number checked against the literal 90-second target.
 * <p>
 * Honesty note, same as {@code PipEvaluationProfilingIT}'s own: there is no real production
 * cohort in this build (no deployment target exists), so this is a synthetic cohort at a scale
 * this local dev environment can genuinely construct and run in one test, not a literal
 * production-scale claim. One {@code standups} row and one {@code attendance} row per student
 * (roughly 1 in 5 marked {@code ABSENT}, everyone else {@code PRESENT}) — enough real underlying
 * data for {@code StudentMetricsService#applyAttendance}'s real join query to do real work across
 * the full cohort, and enough triggering students that {@code PipEvaluationService#evaluate}'s own
 * per-trigger cost (inserts, an audit-log write, notification enqueues) is genuinely exercised
 * too, not just a fast no-op scan over an already-clean cohort.
 */
@Transactional
class MetricsAndPipChainProfilingIT extends IntegrationTestBase {

    private static final int COHORT_SIZE = 1500;

    /** build-plan.md/progress-tracker.md's own literal target for the combined pipeline. */
    private static final long BUDGET_MILLIS = 90_000;

    @Autowired
    private StudentMetricsService studentMetricsService;
    @Autowired
    private PipEvaluationService pipEvaluationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refreshThenEvaluate_fifteenHundredStudentCohort_completesWithinNinetySeconds() {
        long pmId = insertUser("chain-scale-pm");
        long batchId = insertBatch(pmId);
        List<Long> studentIds = insertStudents(COHORT_SIZE, "chain-scale-student");
        insertBatchStudents(batchId, studentIds);
        long standupId = insertPastDueStandup(batchId);
        insertAttendance(standupId, studentIds);

        long startedAt = System.nanoTime();
        int refreshedRows = studentMetricsService.refresh();
        long afterRefreshNanos = System.nanoTime();
        int triggeredCount = pipEvaluationService.evaluate();
        long finishedAt = System.nanoTime();

        long refreshMillis = (afterRefreshNanos - startedAt) / 1_000_000;
        long evaluateMillis = (finishedAt - afterRefreshNanos) / 1_000_000;
        long combinedMillis = (finishedAt - startedAt) / 1_000_000;

        assertThat(refreshedRows).isEqualTo(COHORT_SIZE);
        // Roughly 1 in 5 seeded ABSENT (0% attendance) — proves real per-trigger work happened,
        // not just a fast scan over an already-clean cohort.
        assertThat(triggeredCount).isBetween(COHORT_SIZE / 10, COHORT_SIZE / 3);
        assertThat(combinedMillis)
                .as("StudentMetricsService.refresh() + PipEvaluationService.evaluate() combined "
                        + "wall-clock time for a %d-student cohort", COHORT_SIZE)
                .isLessThan(BUDGET_MILLIS);

        System.out.printf(
                "[metrics-pip-chain-profiling] cohort=%d refreshedRows=%d triggered=%d "
                        + "refreshMillis=%d evaluateMillis=%d combinedMillis=%d budgetMillis=%d%n",
                COHORT_SIZE, refreshedRows, triggeredCount, refreshMillis, evaluateMillis, combinedMillis, BUDGET_MILLIS);
    }

    private long insertUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Chain Scale Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "CHAINSCALE-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, ?, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId, COHORT_SIZE + 10);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private List<Long> insertStudents(int count, String prefix) {
        String batchMarker = UUID.randomUUID().toString().substring(0, 8);
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] { prefix + "-" + batchMarker + "-" + i + "@example.com" });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Chain Scale Test Student', ?, 'ACTIVE')
                """, rows);
        return jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE ? ORDER BY id",
                Long.class, prefix + "-" + batchMarker + "-%@example.com");
    }

    private void insertBatchStudents(long batchId, List<Long> studentIds) {
        List<Object[]> rows = studentIds.stream()
                .map(id -> new Object[] { batchId, id })
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO batch_students (batch_id, user_id, status) VALUES (?, ?, 'ACTIVE')", rows);
    }

    /** One standup, already past its late cutoff — {@code StudentMetricsService#applyAttendance}
     * reads {@code attendance} joined to {@code standups} directly, not through {@code
     * AttendanceFinalisationJob}, so the standup's own status doesn't matter here (unlike {@code
     * NightlyChainUatFlowIT}, which proves the finalisation leg itself). */
    private long insertPastDueStandup(long batchId) {
        Timestamp utcTimestamp = Timestamp.valueOf(LocalDateTime.ofInstant(
                java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS), ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO standups (batch_id, scheduled_at, late_cutoff_minutes, status)
                VALUES (?, ?, 15, 'CONDUCTED')
                """, batchId, utcTimestamp);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM standups WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    /** Roughly 1 in 5 students ABSENT (0% attendance, well under the seeded 75% rule) — everyone
     * else PRESENT (100%, comfortably clean). */
    private void insertAttendance(long standupId, List<Long> studentIds) {
        List<Object[]> rows = new ArrayList<>(studentIds.size());
        for (int i = 0; i < studentIds.size(); i++) {
            String status = i % 5 == 0 ? "ABSENT" : "PRESENT";
            rows.add(new Object[] { standupId, studentIds.get(i), status });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO attendance (standup_id, user_id, status, checked_in_at, is_auto_marked)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), FALSE)
                """, rows);
    }
}
