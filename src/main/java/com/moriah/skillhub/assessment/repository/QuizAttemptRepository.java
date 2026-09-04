package com.moriah.skillhub.assessment.repository;

import com.moriah.skillhub.assessment.entity.AttemptStatus;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** Author-facing results screen ({@code GET /api/v1/assessments/results}). All filters
     * optional; {@code onlyFinished} true drops still-{@code IN_PROGRESS} attempts. Ordered
     * newest first. {@code quiz.batch} is left-joined — a project-scoped quiz has no batch. */
    @Query("""
            SELECT a FROM QuizAttempt a
              JOIN FETCH a.quiz q
              LEFT JOIN FETCH q.batch b
              JOIN FETCH a.user u
             WHERE (:assessmentId IS NULL OR q.id = :assessmentId)
               AND (:batchId IS NULL OR b.id = :batchId)
               AND (:track IS NULL OR b.trackCode = :track)
               AND (:onlyFinished = false OR a.status <> com.moriah.skillhub.assessment.entity.AttemptStatus.IN_PROGRESS)
             ORDER BY a.id DESC
            """)
    Page<QuizAttempt> searchResults(@Param("assessmentId") Long assessmentId,
                                    @Param("batchId") Long batchId,
                                    @Param("track") String track,
                                    @Param("onlyFinished") boolean onlyFinished,
                                    Pageable pageable);

    /** {@code JOIN FETCH} both associations, deliberately — backs {@code
     * QuizAttemptWriter.findExisting} (the concurrent double-start recovery read, run in its own
     * short-lived {@code REQUIRES_NEW} transaction/session). Same "confirmed the hard way"
     * reasoning as {@code TaskSubmissionRepository}/{@code AttendanceRepository}. */
    @Query("SELECT a FROM QuizAttempt a JOIN FETCH a.quiz JOIN FETCH a.user " +
            "WHERE a.quiz.id = :quizId AND a.user.id = :userId AND a.attemptNumber = :attemptNumber")
    Optional<QuizAttempt> findByQuizIdAndUserIdAndAttemptNumber(
            @Param("quizId") Long quizId, @Param("userId") Long userId, @Param("attemptNumber") int attemptNumber);

    /** "Resume, don't burn a second attempt" — {@code QuizService#startAttempt}'s first check
     * before touching {@link #findMaxAttemptNumber}/the writer at all. */
    @Query("SELECT a FROM QuizAttempt a JOIN FETCH a.quiz " +
            "WHERE a.quiz.id = :quizId AND a.user.id = :userId AND a.status = 'IN_PROGRESS'")
    Optional<QuizAttempt> findInProgress(@Param("quizId") Long quizId, @Param("userId") Long userId);

    /** Server-computes the next attempt number — never client-supplied. {@code null} means no
     * prior attempt exists yet. */
    @Query("SELECT MAX(a.attemptNumber) FROM QuizAttempt a WHERE a.quiz.id = :quizId AND a.user.id = :userId")
    Integer findMaxAttemptNumber(@Param("quizId") Long quizId, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"quiz", "user"})
    Optional<QuizAttempt> findById(Long id);

    /** {@code QuizAttemptExpiryJob}'s whole cohort in one flat query — every attempt still {@code
     * IN_PROGRESS}, regardless of quiz, expiry is then computed in memory against each attempt's
     * own {@code quiz.durationMinutes} (code-standards.md "N+1 Prevention": one flat query, no
     * repository call in the per-item loop; matches {@code SubmissionVerificationRetryJob}'s
     * "process in memory after one flat read" shape). */
    @EntityGraph(attributePaths = "quiz")
    List<QuizAttempt> findByStatus(AttemptStatus status);
}
