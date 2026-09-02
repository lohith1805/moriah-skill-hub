package com.moriah.skillhub.metrics;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.dto.ActiveEnrollmentProjection;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.metrics.repository.StudentMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A separate bean from {@link MetricsRefreshJob} — same self-invocation reasoning as {@code
 * AttendanceFinalisationService}: a {@code @Scheduled} method on the job bean cannot also carry
 * {@code @Transactional} and call itself, so the atomic work lives here instead, reached through
 * Spring's proxy on every call.
 * <p>
 * The active/on-PIP cohort itself is read through {@link BatchService#activeAndOnPipEnrollments()}
 * — a proper cross-package service call, not a repository or raw-table read (architecture.md's
 * layer rule: "a feature module may call another module's service interface, never its repository
 * or entity directly"). The five remaining reads (attendance, tasks, quizzes, assignment-window
 * misses, weekly reviews) are deliberately different: each is a multi-table aggregate join with no
 * existing bulk-read method to call into, across five different packages that would each need a
 * brand-new bespoke aggregation API invented solely for this job to use once. Per code-standards.md
 * "Native SQL only for... metrics upserts" and the precedent {@code EntitlementGuard}/{@code
 * EntitlementFlagsLoader} already set for exactly this shape of read (raw {@code JdbcTemplate}
 * against another package's tables, no entity import, no repository call), these five stay native
 * SQL. <b>This job is meant to be the one place this kind of cross-package rollup happens</b> — a
 * future report or admin view needing similar data should extend {@code student_metrics} (or add a
 * genuine view, like {@code v_batch_velocity}/{@code v_revenue_monthly} in V10) rather than writing
 * a sixth raw-SQL cross-package block of its own.
 * <p>
 * Six data sources feed one in-memory map keyed by (batchId, userId): the cohort roster itself
 * (also the source of a fallback "last activity" timestamp — {@code joinedAt} — for a brand-new
 * student with no history yet), then attendance, tasks, quizzes, assignment-window misses, and
 * weekly-review counts, each folded on in a single pass. The whole refresh commits as one
 * transaction; the actual write is a native {@code INSERT ... ON DUPLICATE KEY UPDATE} batched in
 * chunks of {@link Constants#STUDENT_METRICS_UPSERT_BATCH_SIZE} (build-plan.md feature 16 — this is
 * exactly the "metrics upserts" case code-standards.md carves out for native SQL, not a JPA {@code
 * saveAll}).
 * <p>
 * Every time boundary below is a MySQL-side {@code NOW() - INTERVAL n <unit>} expression (the unit
 * count parameterized from {@link Constants}, the unit keyword itself literal SQL — MySQL doesn't
 * accept a bound parameter for the unit), never a bound {@code Instant}/{@code Timestamp} — the
 * same fix already proven for {@code NotificationReaperJob}'s test fixture (feature 08 decision
 * log): binding an absolute Java timestamp against a {@code DATETIME(6)} column risks a
 * JDBC-driver/Hibernate timezone translation mismatch, and a relative, server-computed interval
 * sidesteps the question entirely.
 */
@Service
@RequiredArgsConstructor
public class StudentMetricsService {

    private final JdbcTemplate jdbcTemplate;
    private final BatchService batchService;
    private final StudentMetricRepository studentMetricRepository;

    /** {@code PipEvaluationService}'s whole cohort in one flat query (feature 17) — a proper
     * cross-package service call, not {@code PipEvaluationService} touching {@code
     * StudentMetricRepository} directly.
     * <p>
     * Filtered against {@link BatchService#activeAndOnPipEnrollments()} before returning — {@code
     * student_metrics} rows are only ever upserted, never deleted, for a student who leaves the
     * active/on-PIP cohort (feature 16's own decision log flags this as a known gap for feature 17
     * to close at read time). Without this filter, a student's last-known metrics stay in the
     * table indefinitely after they're {@code CLEARED}/{@code TERMINATED}/{@code REASSIGNED} —
     * {@code /review} caught the real consequence: {@code PipEvaluationService} would keep seeing
     * a departed student's stale, sub-threshold numbers forever (no open {@code pip_records} row
     * blocks them, since their PIP already closed) and could re-trigger a brand-new PIP against
     * someone no longer even enrolled, silently flipping an already-{@code TERMINATED} {@code
     * batch_students} row back to {@code ON_PIP}. */
    @Transactional(readOnly = true)
    public List<StudentMetricProjection> currentCohortMetrics() {
        Set<CohortMembership> currentCohort = batchService.activeAndOnPipEnrollments().stream()
                .map(e -> new CohortMembership(e.batchId(), e.userId()))
                .collect(Collectors.toSet());
        return studentMetricRepository.findAllProjected().stream()
                .filter(m -> currentCohort.contains(new CohortMembership(m.batchId(), m.userId())))
                .toList();
    }

    private record CohortMembership(Long batchId, Long userId) {
    }

    /** {@code PipService}'s clearance check (feature 17) — a single user's latest refreshed row. */
    @Transactional(readOnly = true)
    public Optional<StudentMetricProjection> metricsFor(Long userId, Long batchId) {
        return studentMetricRepository.findProjectedByUserIdAndBatchId(userId, batchId);
    }

    @Transactional
    public int refresh() {
        Instant now = Instant.now();
        Map<CohortKey, Accumulator> cohort = loadActiveCohort();
        if (cohort.isEmpty()) {
            return 0;
        }
        applyAttendance(cohort);
        applyTasks(cohort);
        applyQuizzes(cohort);
        applyAssignmentWindows(cohort);
        applyWeeklyReviews(cohort);
        upsert(cohort, now);
        return cohort.size();
    }

    private Map<CohortKey, Accumulator> loadActiveCohort() {
        Map<CohortKey, Accumulator> cohort = new LinkedHashMap<>();
        for (ActiveEnrollmentProjection enrollment : batchService.activeAndOnPipEnrollments()) {
            CohortKey key = new CohortKey(enrollment.batchId(), enrollment.userId());
            Accumulator acc = new Accumulator();
            acc.joinedAt = enrollment.joinedAt();
            acc.bumpActivity(enrollment.joinedAt());
            cohort.put(key, acc);
        }
        return cohort;
    }

    private void applyAttendance(Map<CohortKey, Accumulator> cohort) {
        jdbcTemplate.query("""
                SELECT s.batch_id, a.user_id,
                       SUM(CASE WHEN a.status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END) AS present_count,
                       SUM(CASE WHEN a.status IN ('PRESENT', 'LATE', 'ABSENT') THEN 1 ELSE 0 END) AS total_count,
                       MAX(a.checked_in_at) AS last_checkin
                  FROM attendance a
                  JOIN standups s ON s.id = a.standup_id
                 WHERE s.scheduled_at >= NOW(6) - INTERVAL ? DAY
                 GROUP BY s.batch_id, a.user_id
                """, rs -> {
            Accumulator acc = cohort.get(new CohortKey(rs.getLong("batch_id"), rs.getLong("user_id")));
            if (acc == null) {
                return;
            }
            acc.attendancePresent = rs.getInt("present_count");
            acc.attendanceTotal = rs.getInt("total_count");
            var lastCheckin = rs.getTimestamp("last_checkin");
            if (lastCheckin != null) {
                acc.bumpActivity(lastCheckin.toInstant());
            }
        }, Constants.STUDENT_METRICS_ATTENDANCE_WINDOW_DAYS);
    }

    private void applyTasks(Map<CohortKey, Accumulator> cohort) {
        jdbcTemplate.query("""
                SELECT sp.batch_id, t.assigned_to AS user_id,
                       COUNT(*) AS assigned_count,
                       SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
                       SUM(CASE WHEN t.task_type = 'STORY' AND t.status NOT IN ('COMPLETED', 'REJECTED')
                                AND t.due_at IS NOT NULL AND t.due_at < NOW(6) - INTERVAL ? HOUR
                                THEN 1 ELSE 0 END) AS overdue_count,
                       MAX(t.updated_at) AS last_task_touch
                  FROM tasks t
                  JOIN sprints sp ON sp.id = t.sprint_id
                 WHERE t.assigned_to IS NOT NULL
                 GROUP BY sp.batch_id, t.assigned_to
                """, rs -> {
            Accumulator acc = cohort.get(new CohortKey(rs.getLong("batch_id"), rs.getLong("user_id")));
            if (acc == null) {
                return;
            }
            acc.tasksAssigned = rs.getInt("assigned_count");
            acc.tasksCompleted = rs.getInt("completed_count");
            // 'STORY' / ('COMPLETED', 'REJECTED') mirror tasks.chk_tasks_type/chk_tasks_status
            // (V6) verbatim — if that CHECK constraint ever gains a new task_type or status value,
            // this exclusion list needs a matching update or a newly-added status silently keeps
            // counting (or stops counting) toward tasks_overdue_48h / PROJECT_DELAY (feature 17).
            acc.tasksOverdue48h = rs.getInt("overdue_count");
            var lastTouch = rs.getTimestamp("last_task_touch");
            if (lastTouch != null) {
                acc.bumpActivity(lastTouch.toInstant());
            }
        }, Constants.STUDENT_METRICS_TASK_OVERDUE_HOURS);
    }

    private void applyQuizzes(Map<CohortKey, Accumulator> cohort) {
        jdbcTemplate.query("""
                SELECT q.batch_id, qa.user_id,
                       COUNT(*) AS attempts_count,
                       ROUND(AVG(qa.percentage), 2) AS avg_percentage,
                       MAX(qa.submitted_at) AS last_attempt
                  FROM quiz_attempts qa
                  JOIN quizzes q ON q.id = qa.quiz_id
                 WHERE q.batch_id IS NOT NULL
                   AND qa.status IN ('SUBMITTED', 'EXPIRED')
                   AND qa.percentage IS NOT NULL
                 GROUP BY q.batch_id, qa.user_id
                """, rs -> {
            Accumulator acc = cohort.get(new CohortKey(rs.getLong("batch_id"), rs.getLong("user_id")));
            if (acc == null) {
                return;
            }
            acc.quizAttemptsCount = rs.getInt("attempts_count");
            acc.quizAveragePercent = rs.getBigDecimal("avg_percentage");
            var lastAttempt = rs.getTimestamp("last_attempt");
            if (lastAttempt != null) {
                acc.bumpActivity(lastAttempt.toInstant());
            }
        });
    }

    /** "Consecutive" has no single-query SQL shape, so this walks each batch's assignment windows
     * (most recent first) in memory against a flat existence-set of submitted (batch, user, week)
     * triples — two flat queries, zero repository calls inside the per-student walk. A window with
     * no {@code task_id} carries no submission to check and is skipped entirely, not counted as a
     * miss (V8's own comment: {@code assignment_windows.task_id} is nullable). Two more boundaries
     * stop the walk before it reaches a week that isn't genuinely the student's fault: a window
     * whose {@code due_at} hasn't passed yet (still open, not missed) via the {@code due_at < NOW()}
     * filter below, and a week before the student's own {@code batch_students.joined_at} (they
     * can't have missed a deadline that predates their enrollment — real for a REASSIGNED student
     * dropped into an existing batch with months of history). */
    private void applyAssignmentWindows(Map<CohortKey, Accumulator> cohort) {
        Map<Long, List<LocalDate>> windowsByBatch = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT batch_id, week_start
                  FROM assignment_windows
                 WHERE task_id IS NOT NULL
                   AND due_at < NOW(6)
                 ORDER BY batch_id, week_start DESC
                """, rs -> {
            windowsByBatch.computeIfAbsent(rs.getLong("batch_id"), k -> new ArrayList<>())
                    .add(rs.getDate("week_start").toLocalDate());
        });

        Set<AssignmentSubmission> submitted = new HashSet<>();
        jdbcTemplate.query("""
                SELECT DISTINCT aw.batch_id, ts.user_id, aw.week_start
                  FROM assignment_windows aw
                  JOIN task_submissions ts ON ts.task_id = aw.task_id
                 WHERE aw.task_id IS NOT NULL
                """, rs -> {
            submitted.add(new AssignmentSubmission(rs.getLong("batch_id"), rs.getLong("user_id"),
                    rs.getDate("week_start").toLocalDate()));
        });

        for (Map.Entry<CohortKey, Accumulator> entry : cohort.entrySet()) {
            CohortKey key = entry.getKey();
            List<LocalDate> windows = windowsByBatch.get(key.batchId());
            if (windows == null) {
                continue;
            }
            LocalDate joinedDate = entry.getValue().joinedAt.atZone(ZoneOffset.UTC).toLocalDate();
            int consecutive = 0;
            for (LocalDate weekStart : windows) {
                if (weekStart.isBefore(joinedDate)) {
                    break;
                }
                if (submitted.contains(new AssignmentSubmission(key.batchId(), key.userId(), weekStart))) {
                    break;
                }
                consecutive++;
            }
            entry.getValue().consecutiveAssignmentsMissed = (byte) Math.min(consecutive, Byte.MAX_VALUE);
        }
    }

    private void applyWeeklyReviews(Map<CohortKey, Accumulator> cohort) {
        jdbcTemplate.query("""
                SELECT batch_id, user_id, COUNT(*) AS unsatisfactory_count
                  FROM weekly_reviews
                 WHERE rating = 'UNSATISFACTORY'
                 GROUP BY batch_id, user_id
                """, rs -> {
            Accumulator acc = cohort.get(new CohortKey(rs.getLong("batch_id"), rs.getLong("user_id")));
            if (acc == null) {
                return;
            }
            acc.unsatisfactoryReviews = (byte) Math.min(rs.getInt("unsatisfactory_count"), Byte.MAX_VALUE);
        });
    }

    private void upsert(Map<CohortKey, Accumulator> cohort, Instant now) {
        List<Object[]> rows = new ArrayList<>(cohort.size());
        for (Map.Entry<CohortKey, Accumulator> entry : cohort.entrySet()) {
            CohortKey key = entry.getKey();
            Accumulator acc = entry.getValue();
            rows.add(new Object[] {
                    key.userId(), key.batchId(),
                    acc.attendancePresent, acc.attendanceTotal, acc.attendancePercent(),
                    acc.tasksAssigned, acc.tasksCompleted, acc.tasksOverdue48h, acc.taskCompletionPercent(),
                    acc.daysSinceLastActivity(now),
                    acc.quizAttemptsCount, acc.quizAveragePercent,
                    acc.consecutiveAssignmentsMissed, acc.unsatisfactoryReviews
            });
        }

        String sql = """
                INSERT INTO student_metrics
                    (user_id, batch_id, computed_at, attendance_present, attendance_total, attendance_percent,
                     tasks_assigned, tasks_completed, tasks_overdue_48h, task_completion_percent,
                     days_since_last_activity, quiz_attempts_count, quiz_average_percent,
                     consecutive_assignments_missed, unsatisfactory_reviews)
                VALUES (
                    ?, ?, NOW(6),
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    ?,
                    ?, ?,
                    ?, ?
                )
                ON DUPLICATE KEY UPDATE
                    computed_at = NOW(6),
                    attendance_present = VALUES(attendance_present),
                    attendance_total = VALUES(attendance_total),
                    attendance_percent = VALUES(attendance_percent),
                    tasks_assigned = VALUES(tasks_assigned),
                    tasks_completed = VALUES(tasks_completed),
                    tasks_overdue_48h = VALUES(tasks_overdue_48h),
                    task_completion_percent = VALUES(task_completion_percent),
                    days_since_last_activity = VALUES(days_since_last_activity),
                    quiz_attempts_count = VALUES(quiz_attempts_count),
                    quiz_average_percent = VALUES(quiz_average_percent),
                    consecutive_assignments_missed = VALUES(consecutive_assignments_missed),
                    unsatisfactory_reviews = VALUES(unsatisfactory_reviews)
                """;

        for (int start = 0; start < rows.size(); start += Constants.STUDENT_METRICS_UPSERT_BATCH_SIZE) {
            List<Object[]> chunk = rows.subList(start,
                    Math.min(start + Constants.STUDENT_METRICS_UPSERT_BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(sql, chunk);
        }
    }

    private record CohortKey(Long batchId, Long userId) {
    }

    private record AssignmentSubmission(Long batchId, Long userId, LocalDate weekStart) {
    }

    private static final class Accumulator {
        private Instant joinedAt;
        private Instant lastActivity;
        private int attendancePresent;
        private int attendanceTotal;
        private int tasksAssigned;
        private int tasksCompleted;
        private int tasksOverdue48h;
        private int quizAttemptsCount;
        private BigDecimal quizAveragePercent;
        private byte consecutiveAssignmentsMissed;
        private byte unsatisfactoryReviews;

        void bumpActivity(Instant candidate) {
            if (lastActivity == null || candidate.isAfter(lastActivity)) {
                lastActivity = candidate;
            }
        }

        BigDecimal attendancePercent() {
            return attendanceTotal == 0 ? null
                    : BigDecimal.valueOf(attendancePresent)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(attendanceTotal), 2, RoundingMode.HALF_UP);
        }

        BigDecimal taskCompletionPercent() {
            return tasksAssigned == 0 ? null
                    : BigDecimal.valueOf(tasksCompleted)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(tasksAssigned), 2, RoundingMode.HALF_UP);
        }

        /** {@code now} is captured once for the whole refresh (passed down from {@link
         * StudentMetricsService#refresh}), not read fresh per student — otherwise two students with
         * the identical {@code lastActivity} could get different results if the upsert loop happens
         * to straddle a day boundary while building thousands of rows. */
        int daysSinceLastActivity(Instant now) {
            // Audit 2026-08-31 (L9): clamp at 0 — a future-dated lastActivity (clock skew on
            // tasks.updated_at, or a future joined_at) would otherwise go negative and could
            // never trip TaskAbandonedRule.
            return Math.max(0, (int) Duration.between(lastActivity, now).toDays());
        }
    }
}
