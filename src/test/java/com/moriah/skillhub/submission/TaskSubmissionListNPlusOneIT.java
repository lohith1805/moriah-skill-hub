package com.moriah.skillhub.submission;

import com.moriah.skillhub.IntegrationTestBase;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 23 (Audit, Hardening and Performance) — N+1 audit, the one real finding this pass
 * caught: {@code TaskSubmissionRepository.search} (backing {@code GET
 * /api/v1/submissions?taskId=&status=}) built {@code SubmissionResponse.studentUuid}/{@code
 * studentName} from {@code submission.getUser().getUuid()}/{@code .getFullName()} — a genuine
 * field read on a lazy {@code @ManyToOne}, not the cheap {@code .getId()}-on-a-proxy pattern this
 * codebase otherwise relies on — with no {@code @EntityGraph} on the query. This test proves the
 * fix directly with {@code hibernate.generate_statistics} (enabled in {@code
 * application-test.yml} for exactly this), the same technique AGENTS.md's own {@code /recover}
 * guidance names ("Silent N+1 — enable hibernate.generate_statistics and count the queries before
 * guessing"): six distinct students' submissions on one page must cost the same query count as
 * one student's submission would, not scale with the row count.
 */
@Transactional
class TaskSubmissionListNPlusOneIT extends IntegrationTestBase {

    private static final int SUBMISSION_COUNT = 6;

    @Autowired
    private TaskSubmissionRepository taskSubmissionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void search_pageOfSubmissionsFromDistinctStudents_issuesAFixedSmallNumberOfQueries() {
        long pmId = insertUser("npo-pm");
        long batchId = insertBatch(pmId);
        long sprintId = insertSprint(batchId);

        for (int i = 0; i < SUBMISSION_COUNT; i++) {
            long studentId = insertUser("npo-student-" + i);
            long taskId = insertTask(sprintId, studentId);
            insertSubmission(taskId, studentId);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<TaskSubmission> page = taskSubmissionRepository.search(null, null, PageRequest.of(0, 20));
        // Force every row's user association to actually resolve — reading .getUuid()/
        // .getFullName() the exact way SubmissionService#toResponse does, not just .getId() on a
        // proxy (which would never hit the DB regardless of fetch strategy and prove nothing).
        page.getContent().forEach(submission -> {
            assertThat(submission.getUser().getUuid()).isNotNull();
            assertThat(submission.getUser().getFullName()).isNotNull();
        });

        assertThat(page.getContent()).hasSize(SUBMISSION_COUNT);
        // One query for the page content (with its @EntityGraph-driven join), one for the Page's
        // count query — bounded regardless of how many distinct students are on the page. Without
        // the @EntityGraph fix this was 1 (content) + 1 (count) + SUBMISSION_COUNT (one lazy
        // `user` load per row) = 8, not 2 — scaling with row count is exactly the N+1 shape this
        // asserts against.
        assertThat(statistics.getPrepareStatementCount())
                .as("SQL statements prepared for a %d-row page", SUBMISSION_COUNT)
                .isLessThanOrEqualTo(2);
    }

    private long insertUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), ?, ?, 'ACTIVE')
                """, "N Plus One Test " + prefix, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertBatch(long pmId) {
        String trackCode = "NPO-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private long insertSprint(long batchId) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status)
                VALUES (?, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL 7 DAY, 'ACTIVE')
                """, batchId);
        return jdbcTemplate.queryForObject("SELECT id FROM sprints WHERE batch_id = ?", Long.class, batchId);
    }

    private long insertTask(long sprintId, long assignedToUserId) {
        jdbcTemplate.update("""
                INSERT INTO tasks (sprint_id, title, task_type, assigned_to, status)
                VALUES (?, ?, 'ASSIGNMENT', ?, 'IN_PROGRESS')
                """, sprintId, "Task-" + UUID.randomUUID(), assignedToUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tasks WHERE sprint_id = ? AND assigned_to = ? ORDER BY id DESC LIMIT 1",
                Long.class, sprintId, assignedToUserId);
    }

    private void insertSubmission(long taskId, long userId) {
        jdbcTemplate.update("""
                INSERT INTO task_submissions (task_id, user_id, attempt_number, status)
                VALUES (?, ?, 1, 'SUBMITTED')
                """, taskId, userId);
    }
}
