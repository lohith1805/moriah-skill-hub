package com.moriah.skillhub;

import com.moriah.skillhub.attendance.AttendanceFinalisationService;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.pip.PipEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 24 (Integration Testing and UAT) — UAT Scenario 3 (progress-tracker.md's UAT Scenarios
 * table, row 3 / build-plan.md feature 24's own third bullet): "A student who stops attending is
 * auto-marked ABSENT, drops below 75%, and is triggered into PIP on the next nightly run."
 * <p>
 * {@code AttendanceFinalisationJobIT}, {@code MetricsRefreshFlowIT}, and {@code
 * PipEvaluationFlowIT} each already prove their own job correct in isolation — but every one of
 * them deliberately starts from fixture rows written directly into the *next* job's input table
 * (metrics-refresh tests write {@code attendance} rows directly rather than running the
 * finalisation job first; PIP-evaluation tests write {@code student_metrics} rows directly rather
 * than running the refresh job first — each file's own Javadoc says so, and says why: proving the
 * chain end to end isn't that file's job). No test anywhere in the suite starts from a real missed
 * standup and runs all three nightly jobs in their real 01:30 -> 01:45 -> 02:00 order end to end.
 * This is that test — the one place the full chain from AGENTS.md's "nightly chain is ordered"
 * invariant is actually proven against real, freshly-written data rather than assumed from three
 * separate unit-scoped ITs.
 * <p>
 * Same "call the service directly, not the {@code @Scheduled} method" pattern every other job IT
 * in this codebase uses (a {@code @Scheduled} caller can't also carry {@code @Transactional} on a
 * self-invoked method) — {@link AttendanceFinalisationService#finalise()}, {@link
 * StudentMetricsService#refresh()}, and {@link PipEvaluationService#evaluate()} are called back to
 * back, in the real nightly order, inside one {@code @Transactional} test method.
 */
@Transactional
class NightlyChainUatFlowIT extends IntegrationTestBase {

    @Autowired
    private AttendanceFinalisationService attendanceFinalisationService;
    @Autowired
    private StudentMetricsService studentMetricsService;
    @Autowired
    private PipEvaluationService pipEvaluationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StandupRepository standupRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private BatchStudentRepository batchStudentRepository;

    /** Same flush-timing reasoning {@code AttendanceFinalisationJobIT}/{@code
     * PipEvaluationFlowIT}'s own Javadoc gives: the dirty-checked {@code UPDATE}s each service
     * issues (standup status, batch_students status) stay buffered in the persistence context
     * until flushed — a raw {@code JdbcTemplate} read afterward is a separate query path from
     * Hibernate's own session and would otherwise see stale data. */
    private void flush() {
        standupRepository.flush();
        attendanceRepository.flush();
        batchStudentRepository.flush();
    }

    @Test
    void studentStopsAttending_autoAbsentBelow75Percent_triggersPipOnNextNightlyRun() {
        long pmId = insertUser();
        long studentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");

        // "Stops attending": four standups inside the 14-day rolling window
        // (Constants.STUDENT_METRICS_ATTENDANCE_WINDOW_DAYS) the student never checks into,
        // already past their late-cutoff so AttendanceFinalisationService.finalise() considers
        // them eligible.
        for (int daysAgo = 1; daysAgo <= 4; daysAgo++) {
            insertPastDueStandup(batchId, Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        }

        // --- 01:30 IST — AttendanceFinalisationJob ---
        int finalisedStandups = attendanceFinalisationService.finalise();
        flush();

        assertThat(finalisedStandups).isEqualTo(4);
        Long absentRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM attendance a JOIN standups s ON s.id = a.standup_id
                 WHERE s.batch_id = ? AND a.user_id = ? AND a.status = 'ABSENT' AND a.is_auto_marked = TRUE
                """, Long.class, batchId, studentId);
        assertThat(absentRows).isEqualTo(4);

        // --- 01:45 IST — MetricsRefreshJob ---
        int refreshedRows = studentMetricsService.refresh();
        flush();

        assertThat(refreshedRows).isGreaterThanOrEqualTo(1);
        BigDecimal attendancePercent = jdbcTemplate.queryForObject(
                "SELECT attendance_percent FROM student_metrics WHERE user_id = ? AND batch_id = ?",
                BigDecimal.class, studentId, batchId);
        assertThat(attendancePercent).isNotNull();
        assertThat(attendancePercent).isLessThan(new BigDecimal("75.00"));
        // Zero present out of four scheduled — the concrete number this scenario's "drops below
        // 75%" wording describes, not just "some number under 75."
        assertThat(attendancePercent).isEqualByComparingTo(BigDecimal.ZERO);

        // --- 02:00 IST — PipEvaluationJob ---
        int triggeredCount = pipEvaluationService.evaluate();
        flush();

        assertThat(triggeredCount).isGreaterThanOrEqualTo(1);
        String ruleCode = jdbcTemplate.queryForObject("""
                SELECT rule_code FROM pip_records WHERE user_id = ? AND batch_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, studentId, batchId);
        assertThat(ruleCode).isEqualTo("ATTENDANCE_LOW");
        String pipStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM pip_records WHERE user_id = ? AND batch_id = ? ORDER BY id DESC LIMIT 1",
                String.class, studentId, batchId);
        assertThat(pipStatus).isEqualTo("TRIGGERED");
        String batchStudentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM batch_students WHERE batch_id = ? AND user_id = ?",
                String.class, batchId, studentId);
        assertThat(batchStudentStatus).isEqualTo("ON_PIP");
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures — same shapes as AttendanceFinalisationJobIT/MetricsRefreshFlowIT/
    // PipEvaluationFlowIT (each job IT in this suite keeps its own copies).
    // ---------------------------------------------------------------------------------------

    private long insertUser() {
        String email = "nightly-chain-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Nightly Chain Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "CHAIN-" + UUID.randomUUID().toString().substring(0, 8);
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

    /** {@code Timestamp.valueOf(LocalDateTime.ofInstant(instant, UTC))}, not a raw {@code Instant}
     * bind parameter — {@code AttendanceFinalisationJobIT}'s own Javadoc documents the 5.5-hour
     * offset bug that binding one produces against this driver/Hibernate combination. */
    private long insertPastDueStandup(long batchId, Instant scheduledAt) {
        Timestamp utcTimestamp = Timestamp.valueOf(LocalDateTime.ofInstant(scheduledAt, ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO standups (batch_id, scheduled_at, late_cutoff_minutes, status)
                VALUES (?, ?, 15, 'SCHEDULED')
                """, batchId, utcTimestamp);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM standups WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }
}
