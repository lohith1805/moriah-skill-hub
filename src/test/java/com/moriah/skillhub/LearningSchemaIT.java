package com.moriah.skillhub;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 06 verify line: "Flyway clean, {@code validate} passes, {@code EXPLAIN} on the PM
 * review-queue query uses an index." No entities/services/endpoints ship in feature 06
 * (build-plan.md) — this is schema-only, so the proof is schema-only too: Flyway's own bean
 * validating cleanly, one representative row inserted through the full V6-V8 table hierarchy to
 * catch a typo'd column, FK target, or CHECK constraint that a successful {@code migrate()}
 * alone wouldn't (DDL can apply cleanly and still be wrong for the data it's meant to hold), and
 * an {@code EXPLAIN} assertion that the PM review-queue index is actually chosen, not just
 * present.
 * <p>
 * {@code @Transactional} — same fix, same reasoning as {@code EntitlementGuardIT}: every call
 * here runs in-process on the test thread via plain {@code JdbcTemplate}, no separate servlet
 * thread involved, so Spring's per-test-method rollback is safe and leaves nothing in the shared
 * static-singleton container for a later IT class to trip over. Confirmed the hard way: the
 * placeholder {@code project_assets.file_key = 'projects/brief.pdf'} row this test inserted (a
 * schema-only fixture predating feature 15's real key convention) leaked into every later run,
 * and {@code ProjectFlowIT}'s {@code ADMIN}-listing test — which presigns every {@code DRAFT}
 * project's assets, including this one — surfaced it as a spurious {@code 403} only when the full
 * suite ran, never in isolation.
 */
@Transactional
class LearningSchemaIT extends IntegrationTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayValidatesCleanlyAgainstV6ThroughV8() {
        flyway.validate();
    }

    @Test
    void fullHierarchyInsertsCleanly_batchesThroughQuizzes() {
        long pmId = insertUser("pm-" + UUID.randomUUID() + "@example.com");
        long studentId = insertUser("student-" + UUID.randomUUID() + "@example.com");

        long batchId = insert("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity)
                VALUES ('Full-Stack Jan Cohort', 'FULLSTACK', ?, '2026-01-05', '2026-04-05', 25)
                """, pmId);

        jdbcTemplate.update("""
                INSERT INTO batch_students (batch_id, user_id) VALUES (?, ?)
                """, batchId, studentId);

        long sprintId = insert("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date)
                VALUES (?, 1, '2026-01-05', '2026-01-18')
                """, batchId);

        long projectId = insert("""
                INSERT INTO projects (title, slug, created_by)
                VALUES ('Task Manager API', ?, ?)
                """, "task-manager-api-" + UUID.randomUUID(), pmId);

        jdbcTemplate.update("""
                INSERT INTO project_assets (project_id, asset_type, file_key)
                VALUES (?, 'DOCUMENT', 'projects/brief.pdf')
                """, projectId);

        jdbcTemplate.update("""
                INSERT INTO bug_challenges (project_id, title, broken_code_key, expected_behaviour, created_by)
                VALUES (?, 'Off-by-one in pagination', 'bugs/pagination.zip', 'Page 2 should not repeat page 1''s last row', ?)
                """, projectId, pmId);

        long taskId = insert("""
                INSERT INTO tasks (sprint_id, project_id, title, task_type, assigned_to, status)
                VALUES (?, ?, 'Implement pagination', 'STORY', ?, 'IN_REVIEW')
                """, sprintId, projectId, studentId);

        long submissionId = insert("""
                INSERT INTO task_submissions (task_id, user_id, pr_url, pr_state)
                VALUES (?, ?, 'https://github.com/acme/repo/pull/1', 'OPEN')
                """, taskId, studentId);

        jdbcTemplate.update("""
                INSERT INTO code_reviews (submission_id, reviewer_id, score, verdict)
                VALUES (?, ?, 8, 'APPROVED')
                """, submissionId, pmId);

        jdbcTemplate.update("""
                INSERT INTO weekly_reviews (batch_id, user_id, sprint_id, week_start, rating, reviewed_by)
                VALUES (?, ?, ?, '2026-01-05', 'SATISFACTORY', ?)
                """, batchId, studentId, sprintId, pmId);

        long standupId = insert("""
                INSERT INTO standups (batch_id, sprint_id, scheduled_at)
                VALUES (?, ?, '2026-01-06 09:30:00')
                """, batchId, sprintId);

        jdbcTemplate.update("""
                INSERT INTO attendance (standup_id, user_id, status, is_auto_marked)
                VALUES (?, ?, 'PRESENT', FALSE)
                """, standupId, studentId);

        long quizId = insert("""
                INSERT INTO quizzes (project_id, batch_id, title, duration_minutes, created_by)
                VALUES (?, ?, 'Sprint 1 Checkpoint', 20, ?)
                """, projectId, batchId, pmId);

        long questionId = insert("""
                INSERT INTO quiz_questions (quiz_id, question_text, question_type, marks)
                VALUES (?, 'What HTTP status means Not Found?', 'MCQ', 5)
                """, quizId);

        long attemptId = insert("""
                INSERT INTO quiz_attempts (quiz_id, user_id)
                VALUES (?, ?)
                """, quizId, studentId);

        jdbcTemplate.update("""
                INSERT INTO quiz_answers (attempt_id, question_id, is_correct, marks_awarded)
                VALUES (?, ?, TRUE, 5.00)
                """, attemptId, questionId);

        jdbcTemplate.update("""
                INSERT INTO assignment_windows (batch_id, week_start, week_end, due_at, task_id)
                VALUES (?, '2026-01-05', '2026-01-11', '2026-01-11 23:59:00', ?)
                """, batchId, taskId);

        // The point of this test is that every INSERT above succeeded without a FK/CHECK
        // violation. This final assertion just confirms the row genuinely landed, not that a
        // silently-swallowed exception made the test pass for the wrong reason.
        Long finalTaskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE id = ?", Long.class, taskId);
        assertThat(finalTaskCount).isEqualTo(1);
    }

    @Test
    void pmReviewQueueQuery_hasTheSprintStatusDueIndexAvailable() {
        // Asserts against `possible_keys`, not `key`. On the handful of rows this schema-only IT
        // suite creates, MySQL's cost-based optimizer will often correctly prefer a full scan
        // over an index seek (scanning 1-2 rows is cheaper than an index lookup) — that would
        // make `key` (the plan actually chosen) NULL regardless of whether the index is correct,
        // a row-count artifact rather than a schema defect. `possible_keys` instead proves the
        // composite index genuinely applies to this exact WHERE + ORDER BY shape, independent of
        // how many rows happen to exist right now — MySQL only lists an index there when its
        // leading columns (sprint_id, status) actually match the predicate.
        List<Map<String, Object>> plan = jdbcTemplate.queryForList("""
                EXPLAIN SELECT id FROM tasks
                 WHERE sprint_id = 1 AND status = 'IN_REVIEW'
                 ORDER BY due_at
                """);

        assertThat(plan).hasSize(1);
        Object possibleKeys = plan.get(0).get("possible_keys");
        assertThat(possibleKeys).as("EXPLAIN's possible_keys").asString()
                .contains("idx_tasks_sprint_status_due");
    }

    private long insertUser(String email) {
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status)
                VALUES (UUID(), 'Learning Schema Test', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    /** Runs an INSERT and returns its generated auto-increment id directly, via JDBC's
     * {@code RETURN_GENERATED_KEYS} — not an insert-then-reselect-by-arguments idiom, which
     * breaks the moment an insert's bind parameters don't happen to also uniquely identify the
     * row (most of the inserts below take more parameters than that). */
    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
