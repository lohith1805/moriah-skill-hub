package com.moriah.skillhub;

import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.pip.PipEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * build-plan.md feature 17: real DB throughout — the whole point of {@link
 * PipEvaluationService#evaluate} is the cohort/open-record/rule-set flat queries plus the actual
 * {@code student_metrics} row a real {@code MetricsRefreshFlowIT}-shaped fixture produces, exactly
 * the part a mocked-repository unit test (see {@code PipEvaluationServiceTest}) can't prove
 * correct on its own. Calls {@code evaluate()} directly rather than waiting on the real 02:00 IST
 * cron trigger, same as every other job IT in this codebase. Fixture rows are inserted straight
 * into {@code student_metrics} (bypassing {@code MetricsRefreshJob} itself, which is Feature 16's
 * own already-covered concern) so each test controls the exact metric values under test.
 */
@Transactional
class PipEvaluationFlowIT extends IntegrationTestBase {

    @Autowired
    private PipEvaluationService pipEvaluationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BatchStudentRepository batchStudentRepository;

    /** {@code BatchService#updatePipStatus}'s {@code setStatus(...)} dirty-checks an
     * already-managed {@code BatchStudent} — unlike the {@code IDENTITY}-generated {@code
     * PipRecord}/{@code PipMilestone} inserts (forced immediate by the generator strategy), that
     * UPDATE stays buffered in the persistence context until a flush. Reading it back via raw
     * {@code JdbcTemplate} — a separate query path from Hibernate's own session — would otherwise
     * see stale data (same flush-timing issue {@code AttendanceFinalisationJobIT}'s own Javadoc
     * documents). */
    private void flush() {
        batchStudentRepository.flush();
    }

    @Test
    void evaluate_attendanceBelowThreshold_triggersRecordAndMilestoneAndFlagsOnPip() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        insertStudentMetric(studentId, batchId, "62.00", 0, null, 0, null, 0, 0);

        int triggered = pipEvaluationService.evaluate();
        flush();

        assertThat(triggered).isEqualTo(1);
        assertThat(pipRecordCount(studentId)).isEqualTo(1);
        assertThat(pipRecordField(studentId, "rule_code")).isEqualTo("ATTENDANCE_LOW");
        assertThat(pipRecordField(studentId, "status")).isEqualTo("TRIGGERED");
        assertThat(pipRecordField(studentId, "blocks_task_pull")).isEqualTo("0");
        assertThat(milestoneCountFor(studentId)).isEqualTo(1);
        assertThat(batchStudentStatus(batchId, studentId)).isEqualTo("ON_PIP");
    }

    @Test
    void evaluate_projectDelayTrigger_setsBlocksTaskPullTrue() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        insertStudentMetric(studentId, batchId, "90.00", 2, "90.00", 0, "90.00", 0, 0);

        int triggered = pipEvaluationService.evaluate();

        assertThat(triggered).isEqualTo(1);
        assertThat(pipRecordField(studentId, "rule_code")).isEqualTo("PROJECT_DELAY");
        assertThat(pipRecordField(studentId, "blocks_task_pull")).isEqualTo("1");
    }

    @Test
    void evaluate_studentWithCleanMetrics_neverTriggers() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        insertStudentMetric(studentId, batchId, "90.00", 0, "90.00", 0, "90.00", 0, 0);

        int triggered = pipEvaluationService.evaluate();
        flush();

        assertThat(triggered).isZero();
        assertThat(pipRecordCount(studentId)).isZero();
        assertThat(batchStudentStatus(batchId, studentId)).isEqualTo("ACTIVE");
    }

    @Test
    void evaluate_rerunAfterAlreadyTriggered_doesNotCreateASecondRecord() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        insertStudentMetric(studentId, batchId, "62.00", 0, null, 0, null, 0, 0);

        int firstRun = pipEvaluationService.evaluate();
        int secondRun = pipEvaluationService.evaluate();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(pipRecordCount(studentId)).isEqualTo(1);
    }

    private long insertUser() {
        String email = "pip-eval-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'PIP Eval Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "PIPE-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private void insertBatchStudent(long batchId, long userId, String status) {
        jdbcTemplate.update("INSERT INTO batch_students (batch_id, user_id, status) VALUES (?, ?, ?)",
                batchId, userId, status);
    }

    private void insertStudentMetric(long userId, long batchId, String attendancePercent, int tasksOverdue48h,
            String taskCompletionPercent, int consecutiveAssignmentsMissed, String quizAveragePercent,
            int unsatisfactoryReviews, int daysSinceLastActivity) {
        jdbcTemplate.update("""
                INSERT INTO student_metrics
                    (user_id, batch_id, computed_at, attendance_present, attendance_total, attendance_percent,
                     tasks_assigned, tasks_completed, tasks_overdue_48h, task_completion_percent,
                     days_since_last_activity, quiz_attempts_count, quiz_average_percent,
                     consecutive_assignments_missed, unsatisfactory_reviews)
                VALUES (?, ?, NOW(6), 0, 0, ?, 0, 0, ?, ?, ?, 0, ?, ?, ?)
                """, userId, batchId, attendancePercent, tasksOverdue48h, taskCompletionPercent,
                daysSinceLastActivity, quizAveragePercent, consecutiveAssignmentsMissed, unsatisfactoryReviews);
    }

    private int pipRecordCount(long studentId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pip_records WHERE user_id = ?", Long.class, studentId);
        return count == null ? 0 : count.intValue();
    }

    private String pipRecordField(long studentId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM pip_records WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, studentId);
    }

    private int milestoneCountFor(long studentId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pip_milestones pm
                  JOIN pip_records pr ON pr.id = pm.pip_record_id
                 WHERE pr.user_id = ?
                """, Long.class, studentId);
        return count == null ? 0 : count.intValue();
    }

    private String batchStudentStatus(long batchId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM batch_students WHERE batch_id = ? AND user_id = ?",
                String.class, batchId, userId);
    }
}
