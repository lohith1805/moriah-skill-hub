package com.moriah.skillhub;

import com.moriah.skillhub.attendance.AttendanceFinalisationService;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * build-plan.md feature 13: "Without this job the 75% rule ... can never fire." Calls {@code
 * finalise()} directly on {@link AttendanceFinalisationService} — a separate bean from {@code
 * AttendanceFinalisationJob} (see that service's Javadoc: {@code @Transactional} on a
 * self-invoked method from a {@code @Scheduled} caller does nothing) — rather than waiting on the
 * real 01:30 IST cron trigger (same reasoning {@code SubscriptionExpiryJobIT} uses for testing
 * the mechanism itself, not the trigger). Real DB throughout — the anti-join across {@code
 * batch_students}/{@code standups}/{@code attendance} is exactly the part a mocked-repository
 * unit test (see {@code AttendanceFinalisationServiceTest}) can't prove correct on its own.
 */
@Transactional
class AttendanceFinalisationJobIT extends IntegrationTestBase {

    @Autowired
    private AttendanceFinalisationService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StandupRepository standupRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;

    /** {@code finalise()}'s {@code saveAll(eligible)} dirty-checks already-managed {@code Standup}
     * entities — unlike the {@code IDENTITY}-generated {@code Attendance} inserts (forced
     * immediate by the generator strategy), that UPDATE stays buffered in the persistence context
     * until a flush. Reading it back via raw {@code JdbcTemplate} — a separate query path from
     * Hibernate's own session — would otherwise see stale data (same flush-timing issue {@code
     * SubscriptionExpiryJobIT}'s own Javadoc documents). */
    private void flush() {
        standupRepository.flush();
        attendanceRepository.flush();
    }

    @Test
    void finalise_pastDueStandup_autoConductsAndMarksOnlyMissingActiveStudentsAbsent() {
        long pmId = insertUser();
        long presentStudentId = insertUser();
        long absentStudentId = insertUser();
        long reassignedStudentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, presentStudentId, "ACTIVE");
        insertBatchStudent(batchId, absentStudentId, "ACTIVE");
        insertBatchStudent(batchId, reassignedStudentId, "REASSIGNED");

        long standupId = insertStandup(batchId, Instant.now().minus(3, ChronoUnit.HOURS), "SCHEDULED");
        insertAttendance(standupId, presentStudentId, "PRESENT", false);

        int count = service.finalise();
        flush();

        assertThat(count).isEqualTo(1);
        assertThat(standupStatus(standupId)).isEqualTo("CONDUCTED");
        assertThat(standupFinalisedAt(standupId)).isNotNull();

        assertThat(attendanceStatus(standupId, presentStudentId)).isEqualTo("PRESENT");
        assertThat(attendanceStatus(standupId, absentStudentId)).isEqualTo("ABSENT");
        assertThat(isAutoMarked(standupId, absentStudentId)).isTrue();
        assertThat(attendanceRowExists(standupId, reassignedStudentId)).isFalse();
    }

    @Test
    void finalise_rerunning_doesNotDoubleWrite() {
        long pmId = insertUser();
        long absentStudentId = insertUser();
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, absentStudentId, "ACTIVE");
        long standupId = insertStandup(batchId, Instant.now().minus(3, ChronoUnit.HOURS), "SCHEDULED");

        int firstRun = service.finalise();
        flush();
        int secondRun = service.finalise();
        flush();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE standup_id = ? AND user_id = ?",
                Long.class, standupId, absentStudentId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void finalise_standupNotYetDue_isNotTouched() {
        long pmId = insertUser();
        long batchId = insertBatch(pmId);
        long futureStandupId = insertStandup(batchId, Instant.now().plus(1, ChronoUnit.DAYS), "SCHEDULED");

        int count = service.finalise();

        assertThat(count).isZero();
        assertThat(standupStatus(futureStandupId)).isEqualTo("SCHEDULED");
        assertThat(standupFinalisedAt(futureStandupId)).isNull();
    }

    @Test
    void finalise_cancelledStandup_isNeverConductedOrFinalised() {
        long pmId = insertUser();
        long batchId = insertBatch(pmId);
        long cancelledStandupId = insertStandup(batchId, Instant.now().minus(3, ChronoUnit.HOURS), "CANCELLED");

        int count = service.finalise();

        assertThat(count).isZero();
        assertThat(standupStatus(cancelledStandupId)).isEqualTo("CANCELLED");
        assertThat(standupFinalisedAt(cancelledStandupId)).isNull();
    }

    private String standupStatus(long standupId) {
        return jdbcTemplate.queryForObject("SELECT status FROM standups WHERE id = ?", String.class, standupId);
    }

    private Timestamp standupFinalisedAt(long standupId) {
        return jdbcTemplate.queryForObject("SELECT finalised_at FROM standups WHERE id = ?", Timestamp.class, standupId);
    }

    private String attendanceStatus(long standupId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM attendance WHERE standup_id = ? AND user_id = ?", String.class, standupId, userId);
    }

    private boolean isAutoMarked(long standupId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT is_auto_marked FROM attendance WHERE standup_id = ? AND user_id = ?",
                Boolean.class, standupId, userId);
    }

    private boolean attendanceRowExists(long standupId, long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE standup_id = ? AND user_id = ?",
                Long.class, standupId, userId);
        return count != null && count > 0;
    }

    private long insertUser() {
        String email = "attendance-job-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Job Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "JOB-" + UUID.randomUUID().toString().substring(0, 8);
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
     * bind parameter — confirmed the hard way: the MySQL driver converts a raw {@code
     * java.time.Instant} argument using the JVM's default timezone (this sandbox's is IST,
     * UTC+5:30) before writing the timezone-naive {@code DATETIME(6)} column, while Hibernate's
     * own reads/writes go through {@code hibernate.jdbc.time_zone=UTC} — a systematic 5.5-hour
     * offset between what this test inserts and what the application later reads back. Composing
     * the {@code LocalDateTime} in UTC first and handing the driver a plain {@code Timestamp}
     * sidesteps the driver's {@code Instant}-specific conversion entirely. */
    private long insertStandup(long batchId, Instant scheduledAt, String status) {
        Timestamp utcTimestamp = Timestamp.valueOf(java.time.LocalDateTime.ofInstant(scheduledAt, java.time.ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO standups (batch_id, scheduled_at, late_cutoff_minutes, status)
                VALUES (?, ?, 15, ?)
                """, batchId, utcTimestamp, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM standups WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    private void insertAttendance(long standupId, long userId, String status, boolean autoMarked) {
        jdbcTemplate.update("""
                INSERT INTO attendance (standup_id, user_id, status, checked_in_at, is_auto_marked)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                """, standupId, userId, status, autoMarked);
    }
}
