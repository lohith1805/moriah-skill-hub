package com.moriah.skillhub.submission.repository;

import com.moriah.skillhub.submission.entity.SubmissionStatus;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    /** {@code JOIN FETCH ts.user}, deliberately, not the derived-query default: this is the
     * concurrent-double-post recovery read ({@code TaskSubmissionWriter.findExisting}), run in
     * its own short-lived {@code REQUIRES_NEW} transaction/session. Without eager fetch, the
     * returned entity's {@code user} stays a lazy proxy tied to that already-closed session — the
     * caller's later {@code toResponse(saved).getUser()} then throws {@code
     * LazyInitializationException: no session}, confirmed the hard way via {@code
     * SubmissionFlowIT}'s concurrency test once the earlier snapshot-staleness bug was fixed and
     * this recovery read actually started finding a row. */
    @Query("SELECT ts FROM TaskSubmission ts JOIN FETCH ts.user WHERE ts.taskId = :taskId AND ts.user.id = :userId AND ts.attemptNumber = :attemptNumber")
    Optional<TaskSubmission> findByTaskIdAndUserIdAndAttemptNumber(
            @Param("taskId") Long taskId, @Param("userId") Long userId, @Param("attemptNumber") int attemptNumber);

    /** Server-computes the next attempt number — never client-supplied. {@code null} means no
     * prior attempt exists for this task+user yet. */
    @Query("SELECT MAX(ts.attemptNumber) FROM TaskSubmission ts WHERE ts.taskId = :taskId AND ts.user.id = :userId")
    Integer findMaxAttemptNumber(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Query("""
            SELECT ts FROM TaskSubmission ts
             WHERE (:taskId IS NULL OR ts.taskId = :taskId)
               AND (:status IS NULL OR ts.status = :status)
             ORDER BY ts.submittedAt DESC
            """)
    Page<TaskSubmission> search(@Param("taskId") Long taskId, @Param("status") SubmissionStatus status, Pageable pageable);

    /** {@code SubmissionVerificationRetryJob}'s cohort — build-plan.md feature 12: "GitHub 5xx ->
     * persist verified_at = null ... retry job verifies later." One flat query, processed in
     * memory (code-standards.md "N+1 Prevention" / "Async and Scheduled Work"). */
    List<TaskSubmission> findByVerifiedAtIsNull();
}
