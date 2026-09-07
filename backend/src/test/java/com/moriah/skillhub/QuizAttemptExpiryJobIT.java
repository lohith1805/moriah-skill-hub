package com.moriah.skillhub;

import com.moriah.skillhub.assessment.QuizAttemptExpiryService;
import com.moriah.skillhub.assessment.repository.QuizAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * build-plan.md feature 14: "Unsubmitted attempts swept to EXPIRED and scored on answers given."
 * Calls {@link QuizAttemptExpiryService#expire()} directly rather than waiting on the real
 * 15-minute cron trigger (same reasoning {@code AttendanceFinalisationJobIT}/{@code
 * SubscriptionExpiryJobIT} use) — {@code expire()} lives on a separate {@code @Service} bean from
 * {@code QuizAttemptExpiryJob} so that {@code @Transactional} actually applies on the real {@code
 * @Scheduled} trigger (see that class's Javadoc).
 */
@Transactional
class QuizAttemptExpiryJobIT extends IntegrationTestBase {

    @Autowired
    private QuizAttemptExpiryService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    /** Raw {@code java.time.Instant} bind parameters get converted using the JVM's default
     * timezone by the MySQL driver, not UTC, before landing in a timezone-naive {@code
     * DATETIME(6)} column — confirmed the hard way in {@code AttendanceFinalisationJobIT}. */
    private Timestamp utc(Instant instant) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    @Test
    void expire_pastDueAttempt_marksExpiredScoresZeroForAnsweredNothing() {
        long pmId = insertUser();
        long studentId = insertUser();
        long quizId = insertQuiz(pmId, 30);
        insertMcqQuestion(quizId, 10);
        long attemptId = insertAttempt(quizId, studentId, Instant.now().minus(1, ChronoUnit.HOURS));

        int count = service.expire();
        quizAttemptRepository.flush();

        assertThat(count).isEqualTo(1);
        assertThat(attemptStatus(attemptId)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT percentage FROM quiz_attempts WHERE id = ?", java.math.BigDecimal.class, attemptId))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void expire_notYetPastDuration_leftInProgress() {
        long pmId = insertUser();
        long studentId = insertUser();
        long quizId = insertQuiz(pmId, 30);
        insertMcqQuestion(quizId, 10);
        long attemptId = insertAttempt(quizId, studentId, Instant.now().minus(5, ChronoUnit.MINUTES));

        int count = service.expire();

        assertThat(count).isZero();
        assertThat(attemptStatus(attemptId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void expire_rerunning_doesNotDoubleWrite() {
        long pmId = insertUser();
        long studentId = insertUser();
        long quizId = insertQuiz(pmId, 30);
        insertMcqQuestion(quizId, 10);
        long attemptId = insertAttempt(quizId, studentId, Instant.now().minus(1, ChronoUnit.HOURS));

        int firstRun = service.expire();
        quizAttemptRepository.flush();
        int secondRun = service.expire();
        quizAttemptRepository.flush();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(attemptStatus(attemptId)).isEqualTo("EXPIRED");
    }

    private String attemptStatus(long attemptId) {
        return jdbcTemplate.queryForObject("SELECT status FROM quiz_attempts WHERE id = ?", String.class, attemptId);
    }

    private long insertUser() {
        String email = "quiz-expiry-test-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status) VALUES (UUID(), 'Quiz Expiry Test', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertQuiz(long pmId, int durationMinutes) {
        jdbcTemplate.update("""
                INSERT INTO quizzes (title, duration_minutes, pass_percentage, max_attempts, created_by, is_active)
                VALUES (?, ?, 60, 1, ?, TRUE)
                """, "Expiry Test Quiz " + UUID.randomUUID(), durationMinutes, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM quizzes WHERE created_by = ? ORDER BY id DESC LIMIT 1", Long.class, pmId);
    }

    private void insertMcqQuestion(long quizId, int marks) {
        jdbcTemplate.update("""
                INSERT INTO quiz_questions (quiz_id, question_text, question_type, options, correct_answer, marks)
                VALUES (?, 'What is 2+2?', 'MCQ', '["3","4","5"]', '[1]', ?)
                """, quizId, marks);
    }

    private long insertAttempt(long quizId, long userId, Instant startedAt) {
        jdbcTemplate.update("""
                INSERT INTO quiz_attempts (quiz_id, user_id, attempt_number, started_at, status)
                VALUES (?, ?, 1, ?, 'IN_PROGRESS')
                """, quizId, userId, utc(startedAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM quiz_attempts WHERE quiz_id = ? AND user_id = ?", Long.class, quizId, userId);
    }
}
