package com.moriah.skillhub;

import com.moriah.skillhub.pip.PipEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 23 (Audit, Hardening and Performance) — build-plan.md: "PIP pipeline profiled end to
 * end against a full cohort." Honesty note, read before citing this test's numbers as a
 * production claim: there is no real "full production cohort" anywhere in this build (no
 * deployment exists yet), so this seeds a synthetic cohort at a scale this local dev environment
 * can genuinely construct and run in one test — {@link #COHORT_SIZE} students, one batch, one
 * {@code student_metrics} row each, batch-inserted directly via JDBC (bypassing {@code
 * MetricsRefreshJob} itself, {@code PipEvaluationFlowIT}'s own established precedent) — not a
 * literal production-scale claim. The real, load-bearing verification this proves: {@link
 * PipEvaluationService#evaluate}'s own Javadoc guarantee ("one flat query for the whole cohort,
 * one for every open record, one for the active rule set... nothing inside the per-student loop
 * is a repository read") holds under a cohort two-plus orders of magnitude larger than every
 * other IT in this codebase uses, and the wall-clock time that guarantee buys stays a small
 * fraction of the real nightly budget AGENTS.md documents (01:45 metrics refresh -> 02:00 PIP
 * evaluation is a 15-minute window this job shares with nothing else).
 * <p>
 * The mix of triggering/clean metrics (roughly 1 in 5 below the attendance threshold, everyone
 * else clean) is deliberate, not incidental — a cohort where nothing ever triggers would never
 * exercise the per-trigger work {@code fire()} does (two more inserts, a {@code
 * batch_students} update, an audit-log write, three notification enqueues), which is exactly the
 * per-row cost that would compound into a real N+1 if it were reintroduced.
 */
@Transactional
class PipEvaluationProfilingIT extends IntegrationTestBase {

    /** "Hundreds to low thousands," per this feature's own brief — not a literal production
     * cohort (none exists), but large enough that a reintroduced per-student repository call
     * would show up as a real, measurable wall-clock regression, not noise. */
    private static final int COHORT_SIZE = 1500;

    /** The real nightly gap this job actually has (AGENTS.md: 01:45 metrics refresh -> 02:00 PIP
     * evaluation) is 15 minutes. This assertion budgets a small, generous fraction of that — the
     * point is proving "well within the window," not chasing the window's literal edge. */
    private static final long BUDGET_MILLIS = 60_000;

    @Autowired
    private PipEvaluationService pipEvaluationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void evaluate_fifteenHundredStudentCohort_completesWellWithinTheNightlyBudget() {
        long pmId = insertUser("pip-scale-pm");
        long batchId = insertBatch(pmId);
        List<Long> studentIds = insertStudents(COHORT_SIZE, "pip-scale-student");
        insertBatchStudents(batchId, studentIds);
        insertStudentMetrics(batchId, studentIds);

        long startedAt = System.nanoTime();
        int triggered = pipEvaluationService.evaluate();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // Roughly 1 in 5 seeded below the ATTENDANCE_LOW threshold (see insertStudentMetrics) —
        // proves the run actually did the per-trigger work, not just a fast no-op scan.
        assertThat(triggered).isBetween(COHORT_SIZE / 10, COHORT_SIZE / 3);
        assertThat(elapsedMillis)
                .as("PipEvaluationService.evaluate() wall-clock time for a %d-student cohort", COHORT_SIZE)
                .isLessThan(BUDGET_MILLIS);

        System.out.printf(
                "[pip-profiling] cohort=%d triggered=%d elapsedMillis=%d budgetMillis=%d%n",
                COHORT_SIZE, triggered, elapsedMillis, BUDGET_MILLIS);
    }

    private long insertUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Pip Scale Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "PIPSCALE-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, ?, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId, COHORT_SIZE + 10);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private List<Long> insertStudents(int count, String prefix) {
        String batchMarker = UUID.randomUUID().toString().substring(0, 8);
        List<Object[]> rows = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] { prefix + "-" + batchMarker + "-" + i + "@example.com" });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Pip Scale Test Student', ?, 'ACTIVE')
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

    /** Roughly 1 in 5 students below the ATTENDANCE_LOW threshold (62% < the seeded rule's 75%
     * default) — everyone else comfortably clean on every rule this cohort's metrics touch. */
    private void insertStudentMetrics(long batchId, List<Long> studentIds) {
        List<Object[]> rows = new java.util.ArrayList<>(studentIds.size());
        for (int i = 0; i < studentIds.size(); i++) {
            boolean triggersAttendance = i % 5 == 0;
            rows.add(new Object[] {
                    studentIds.get(i), batchId,
                    triggersAttendance ? "62.00" : "95.00",
                    0, "95.00", 0, "90.00", 0, 0
            });
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO student_metrics
                    (user_id, batch_id, computed_at, attendance_present, attendance_total, attendance_percent,
                     tasks_assigned, tasks_completed, tasks_overdue_48h, task_completion_percent,
                     days_since_last_activity, quiz_attempts_count, quiz_average_percent,
                     consecutive_assignments_missed, unsatisfactory_reviews)
                VALUES (?, ?, NOW(6), 0, 0, ?, 0, 0, ?, ?, ?, 0, ?, ?, ?)
                """, rows);
    }
}
