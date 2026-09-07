package com.moriah.skillhub.submission;

import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * A separate bean, `REQUIRES_NEW`, deliberately — same reasoning {@code
 * WebhookIdempotencyService}'s own Javadoc already documents: catching a duplicate-key (or,
 * here, a deadlock — see {@link SubmissionService#create}'s Javadoc) failure from a
 * Hibernate-backed {@code save()} and continuing still leaves the *session* poisoned the instant
 * a flush fails, independent of whether the calling code catches the translated exception —
 * confirmed the hard way via {@code SubmissionFlowIT}'s concurrency test, which surfaced a
 * second, different failure ("has a null identifier ... session is flushed after an exception
 * occurs") once the first exception type was caught correctly.
 * <p>
 * Unlike {@code WebhookIdempotencyService}, this uses `REQUIRES_NEW` + the normal JPA repository
 * rather than switching to plain {@code JdbcTemplate} — the failure here is a race the caller is
 * always going to recover from with a follow-up JPA read in its own (separate, clean) session, so
 * isolating just the write into its own transaction/session is enough; there's no equivalent need
 * to avoid Hibernate machinery entirely the way the idempotency-claim table's hot path does.
 */
@Service
@RequiredArgsConstructor
class TaskSubmissionWriter {

    private final TaskSubmissionRepository taskSubmissionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    TaskSubmission tryInsert(TaskSubmission submission) {
        return taskSubmissionRepository.saveAndFlush(submission);
    }

    /** Also `REQUIRES_NEW`, not just the insert — confirmed the hard way, a second time, in the
     * same debugging session: MySQL/InnoDB's default REPEATABLE READ gives a transaction a
     * consistent snapshot as of its *first* read, not as of each individual query. {@code
     * SubmissionService.create}'s outer transaction already did a read earlier (via {@code
     * TaskService.markInReview}), so a plain read here — even issued in real time, after the
     * winning transaction has already committed — would still see that earlier, stale snapshot
     * and find nothing, causing {@code create}'s {@code orElseThrow(() -> e)} to rethrow the
     * original exception as if recovery had failed entirely. A fresh {@code REQUIRES_NEW}
     * transaction gets a fresh snapshot at *its* begin time, which is after the winner committed,
     * so it reads the winning row correctly. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<TaskSubmission> findExisting(Long taskId, Long userId, int attemptNumber) {
        return taskSubmissionRepository.findByTaskIdAndUserIdAndAttemptNumber(taskId, userId, attemptNumber);
    }
}
