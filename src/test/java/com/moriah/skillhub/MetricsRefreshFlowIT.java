package com.moriah.skillhub;

import com.moriah.skillhub.metrics.StudentMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * build-plan.md feature 16: real DB throughout, never a mocked repository — the whole point of
 * {@link StudentMetricsService#refresh} is a handful of multi-table aggregate joins plus one
 * native upsert, exactly the part a unit test with mocked repositories can't prove correct on its
 * own (same reasoning {@code AttendanceFinalisationJobIT}'s own Javadoc gives for the identical
 * choice). Calls {@code refresh()} directly rather than waiting on the real 01:45 IST cron
 * trigger, same as every other job IT in this codebase.
 * <p>
 * Every fixture timestamp below is a literal SQL expression ({@code NOW(6) - INTERVAL n <unit>},
 * {@code CURRENT_DATE - INTERVAL n DAY}) written directly into the INSERT, never a bound Java
 * {@code Instant}/{@code Timestamp} parameter — {@code AttendanceFinalisationJobIT}'s own Javadoc
 * documents the 5.5-hour offset bug that binding one produces against this driver/Hibernate
 * combination; a server-computed relative expression sidesteps the question entirely instead of
 * reproducing the workaround for every new temporal fixture value this test needs.
 */
@Transactional
class MetricsRefreshFlowIT extends IntegrationTestBase {

    @Autowired
    private StudentMetricsService studentMetricsService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refresh_activeStudentWithFullHistory_computesEveryColumn() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE", "NOW(6) - INTERVAL 30 DAY");
        long sprintId = insertSprint(batchId);

        // Attendance: one PRESENT + one ABSENT inside the 14-day window, one PRESENT outside it —
        // present=1, total=2, percent=50.00 (the out-of-window standup must not shift this).
        long recentPresentStandup = insertStandup(batchId, "NOW(6) - INTERVAL 2 DAY");
        insertAttendance(recentPresentStandup, studentId, "PRESENT");
        long recentAbsentStandup = insertStandup(batchId, "NOW(6) - INTERVAL 1 DAY");
        insertAttendance(recentAbsentStandup, studentId, "ABSENT");
        long staleStandup = insertStandup(batchId, "NOW(6) - INTERVAL 20 DAY");
        insertAttendance(staleStandup, studentId, "PRESENT");

        // Tasks: 3 assigned, 1 completed, 1 overdue STORY (>48h past due, not completed) — a
        // non-STORY overdue task must NOT count toward tasks_overdue_48h.
        insertTask(sprintId, studentId, "STORY", "COMPLETED", null);
        insertTask(sprintId, studentId, "STORY", "IN_PROGRESS", "NOW(6) - INTERVAL 72 HOUR");
        insertTask(sprintId, studentId, "DAILY", "IN_PROGRESS", "NOW(6) - INTERVAL 72 HOUR");

        // Quizzes: one graded SUBMITTED attempt and one graded EXPIRED attempt both count (an
        // expired attempt is auto-graded on whatever was answered before time ran out —
        // QuizAttemptExpiryService always calls gradingService.applyResult); one
        // PENDING_MANUAL_GRADING attempt (no percentage yet) must be excluded from both the count
        // and the average.
        long quizId = insertQuiz(batchId, pmId);
        insertQuizAttempt(quizId, studentId, 1, "SUBMITTED", new BigDecimal("80.00"));
        insertQuizAttempt(quizId, studentId, 2, "EXPIRED", new BigDecimal("40.00"));
        insertQuizAttempt(quizId, studentId, 3, "PENDING_MANUAL_GRADING", null);

        // Assignment windows: most recent week has no submission, the week before it does — the
        // walk must stop at the first submitted week, so consecutive_assignments_missed = 1, not 2.
        long olderTaskId = insertTask(sprintId, studentId, "ASSIGNMENT", "COMPLETED", null);
        long olderWindowId = insertAssignmentWindow(batchId, "CURRENT_DATE - INTERVAL 14 DAY", olderTaskId);
        insertTaskSubmission(olderTaskId, studentId);
        long newerTaskId = insertTask(sprintId, studentId, "ASSIGNMENT", "BACKLOG", null);
        insertAssignmentWindow(batchId, "CURRENT_DATE - INTERVAL 7 DAY", newerTaskId);

        insertWeeklyReview(batchId, studentId, sprintId, pmId, "UNSATISFACTORY");

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isEqualTo(1);
        assertThat(attendancePresent(studentId, batchId)).isEqualTo(1);
        assertThat(attendanceTotal(studentId, batchId)).isEqualTo(2);
        assertThat(attendancePercent(studentId, batchId)).isEqualByComparingTo("50.00");
        assertThat(tasksAssigned(studentId, batchId)).isEqualTo(5);
        assertThat(tasksCompleted(studentId, batchId)).isEqualTo(2);
        assertThat(tasksOverdue48h(studentId, batchId)).isEqualTo(1);
        assertThat(quizAttemptsCount(studentId, batchId)).isEqualTo(2);
        assertThat(quizAveragePercent(studentId, batchId)).isEqualByComparingTo("60.00");
        assertThat(consecutiveAssignmentsMissed(studentId, batchId)).isEqualTo(1);
        assertThat(unsatisfactoryReviews(studentId, batchId)).isEqualTo(1);
    }

    @Test
    void refresh_studentWithNoHistoryYet_getsZeroedRowNotNullDenominators() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isEqualTo(1);
        assertThat(attendanceTotal(studentId, batchId)).isZero();
        assertThat(attendancePercentIsNull(studentId, batchId)).isTrue();
        assertThat(tasksAssigned(studentId, batchId)).isZero();
        assertThat(taskCompletionPercentIsNull(studentId, batchId)).isTrue();
        assertThat(daysSinceLastActivity(studentId, batchId)).isZero();
    }

    @Test
    void refresh_reassignedStudent_isExcludedFromCohort() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "REASSIGNED");

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isZero();
        assertThat(metricsRowExists(studentId, batchId)).isFalse();
    }

    /** A student already flagged still needs live metrics every night — PipEvaluationJob's
     * clearance check (feature 17) reads task-completion/review outcomes from AFTER the flag was
     * raised, not a snapshot frozen the moment they were placed on PIP. */
    @Test
    void refresh_onPipStudent_stillGetsCohortMembershipAndMetrics() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isEqualTo(1);
        assertThat(metricsRowExists(studentId, batchId)).isTrue();
    }

    /** A window whose deadline hasn't passed yet is still open, not missed — counting it would
     * flag a student before they've actually had the chance to submit. */
    @Test
    void refresh_assignmentWindowNotYetDue_doesNotCountAsMiss() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        long sprintId = insertSprint(batchId);

        long futureTaskId = insertTask(sprintId, studentId, "ASSIGNMENT", "BACKLOG", null);
        insertAssignmentWindow(batchId, "CURRENT_DATE + INTERVAL 7 DAY", futureTaskId);

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isEqualTo(1);
        assertThat(consecutiveAssignmentsMissed(studentId, batchId)).isZero();
    }

    /** A student reassigned into an existing, months-old batch must not be blamed for assignment
     * windows that closed before they ever joined. */
    @Test
    void refresh_studentJoinedAfterWindowClosed_notBlamedForPreEnrollmentMiss() {
        long pmId = insertUser();
        long batchId = insertBatch(pmId);
        long sprintId = insertSprint(batchId);

        long staleTaskId = insertTask(sprintId, insertUser(), "ASSIGNMENT", "BACKLOG", null);
        insertAssignmentWindow(batchId, "CURRENT_DATE - INTERVAL 90 DAY", staleTaskId);

        long studentId = insertUser();
        insertBatchStudent(batchId, studentId, "ACTIVE");

        int refreshed = studentMetricsService.refresh();

        assertThat(refreshed).isEqualTo(1);
        assertThat(consecutiveAssignmentsMissed(studentId, batchId)).isZero();
    }

    @Test
    void refresh_rerun_upsertsInPlaceWithoutDuplicating() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");

        studentMetricsService.refresh();
        long standupId = insertStandup(batchId, "NOW(6) - INTERVAL 1 DAY");
        insertAttendance(standupId, studentId, "PRESENT");
        int secondRun = studentMetricsService.refresh();

        assertThat(secondRun).isEqualTo(1);
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_metrics WHERE user_id = ? AND batch_id = ?",
                Long.class, studentId, batchId);
        assertThat(rowCount).isEqualTo(1);
        assertThat(attendanceTotal(studentId, batchId)).isEqualTo(1);
    }

    private boolean metricsRowExists(long studentId, long batchId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_metrics WHERE user_id = ? AND batch_id = ?",
                Long.class, studentId, batchId);
        return count != null && count > 0;
    }

    private int attendancePresent(long studentId, long batchId) {
        return metricColumn("attendance_present", studentId, batchId, Integer.class);
    }

    private int attendanceTotal(long studentId, long batchId) {
        return metricColumn("attendance_total", studentId, batchId, Integer.class);
    }

    private BigDecimal attendancePercent(long studentId, long batchId) {
        return metricColumn("attendance_percent", studentId, batchId, BigDecimal.class);
    }

    private boolean attendancePercentIsNull(long studentId, long batchId) {
        return metricColumn("attendance_percent", studentId, batchId, BigDecimal.class) == null;
    }

    private int tasksAssigned(long studentId, long batchId) {
        return metricColumn("tasks_assigned", studentId, batchId, Integer.class);
    }

    private int tasksCompleted(long studentId, long batchId) {
        return metricColumn("tasks_completed", studentId, batchId, Integer.class);
    }

    private int tasksOverdue48h(long studentId, long batchId) {
        return metricColumn("tasks_overdue_48h", studentId, batchId, Integer.class);
    }

    private boolean taskCompletionPercentIsNull(long studentId, long batchId) {
        return metricColumn("task_completion_percent", studentId, batchId, BigDecimal.class) == null;
    }

    private int daysSinceLastActivity(long studentId, long batchId) {
        return metricColumn("days_since_last_activity", studentId, batchId, Integer.class);
    }

    private int quizAttemptsCount(long studentId, long batchId) {
        return metricColumn("quiz_attempts_count", studentId, batchId, Integer.class);
    }

    private BigDecimal quizAveragePercent(long studentId, long batchId) {
        return metricColumn("quiz_average_percent", studentId, batchId, BigDecimal.class);
    }

    private int consecutiveAssignmentsMissed(long studentId, long batchId) {
        return metricColumn("consecutive_assignments_missed", studentId, batchId, Integer.class);
    }

    private int unsatisfactoryReviews(long studentId, long batchId) {
        return metricColumn("unsatisfactory_reviews", studentId, batchId, Integer.class);
    }

    private <T> T metricColumn(String column, long studentId, long batchId, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM student_metrics WHERE user_id = ? AND batch_id = ?",
                type, studentId, batchId);
    }

    private long insertUser() {
        String email = "metrics-job-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Metrics Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "MET-" + UUID.randomUUID().toString().substring(0, 8);
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

    /** {@code joinedAtExpr} is a literal SQL expression (e.g. {@code "NOW(6) - INTERVAL 30 DAY"}) —
     * see the class Javadoc. Needed whenever a test's fixture data (assignment windows, standups)
     * predates "now": {@code applyAssignmentWindows}'s joined_at boundary (added during /review)
     * would otherwise treat a same-instant enrollment as postdating every historical window,
     * masking the very misses the test means to exercise. */
    private void insertBatchStudent(long batchId, long userId, String status, String joinedAtExpr) {
        jdbcTemplate.update("""
                INSERT INTO batch_students (batch_id, user_id, status, joined_at)
                VALUES (?, ?, ?, %s)
                """.formatted(joinedAtExpr), batchId, userId, status);
    }

    private long insertSprint(long batchId) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status)
                VALUES (?, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL 14 DAY, 'ACTIVE')
                """, batchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sprints WHERE batch_id = ? AND sprint_number = 1", Long.class, batchId);
    }

    /** {@code scheduledAtExpr} is a literal SQL expression (e.g. {@code "NOW(6) - INTERVAL 2 DAY"}),
     * not a bound parameter — see the class Javadoc. */
    private long insertStandup(long batchId, String scheduledAtExpr) {
        jdbcTemplate.update("""
                INSERT INTO standups (batch_id, scheduled_at, late_cutoff_minutes, status)
                VALUES (?, %s, 15, 'CONDUCTED')
                """.formatted(scheduledAtExpr), batchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM standups WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    private void insertAttendance(long standupId, long userId, String status) {
        jdbcTemplate.update("""
                INSERT INTO attendance (standup_id, user_id, status, checked_in_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))
                """, standupId, userId, status);
    }

    /** {@code dueAtExpr} is a literal SQL expression or {@code null} (no due date) — see the class
     * Javadoc. */
    private long insertTask(long sprintId, long assignedTo, String taskType, String status, String dueAtExpr) {
        String dueAtSql = dueAtExpr == null ? "NULL" : dueAtExpr;
        jdbcTemplate.update("""
                INSERT INTO tasks (sprint_id, title, task_type, assigned_to, status, due_at)
                VALUES (?, ?, ?, ?, ?, %s)
                """.formatted(dueAtSql), sprintId, "Task-" + UUID.randomUUID(), taskType, assignedTo, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tasks WHERE sprint_id = ? ORDER BY id DESC LIMIT 1", Long.class, sprintId);
    }

    private long insertQuiz(long batchId, long createdBy) {
        jdbcTemplate.update("""
                INSERT INTO quizzes (batch_id, title, duration_minutes, pass_percentage, max_attempts, created_by)
                VALUES (?, ?, 30, 60, 5, ?)
                """, batchId, "Quiz-" + UUID.randomUUID(), createdBy);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM quizzes WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    private void insertQuizAttempt(long quizId, long userId, int attemptNumber, String status, BigDecimal percentage) {
        jdbcTemplate.update("""
                INSERT INTO quiz_attempts (quiz_id, user_id, attempt_number, submitted_at, percentage, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), ?, ?)
                """, quizId, userId, attemptNumber, percentage, status);
    }

    /** {@code weekStartExpr} is a literal SQL expression (e.g. {@code "CURRENT_DATE - INTERVAL 7 DAY"}). */
    private long insertAssignmentWindow(long batchId, String weekStartExpr, Long taskId) {
        jdbcTemplate.update("""
                INSERT INTO assignment_windows (batch_id, week_start, week_end, due_at, task_id)
                VALUES (?, %s, %s + INTERVAL 6 DAY, %s + INTERVAL 7 DAY, ?)
                """.formatted(weekStartExpr, weekStartExpr, weekStartExpr), batchId, taskId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM assignment_windows WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    private void insertTaskSubmission(long taskId, long userId) {
        jdbcTemplate.update("INSERT INTO task_submissions (task_id, user_id) VALUES (?, ?)", taskId, userId);
    }

    private void insertWeeklyReview(long batchId, long userId, long sprintId, long reviewedBy, String rating) {
        jdbcTemplate.update("""
                INSERT INTO weekly_reviews (batch_id, user_id, sprint_id, week_start, rating, reviewed_by)
                VALUES (?, ?, ?, CURRENT_DATE - INTERVAL 3 DAY, ?, ?)
                """, batchId, userId, sprintId, rating, reviewedBy);
    }
}
