package com.moriah.skillhub.submission;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.submission.dto.CreateSubmissionRequest;
import com.moriah.skillhub.submission.dto.SubmissionResponse;
import com.moriah.skillhub.submission.entity.SubmissionStatus;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.gateway.GithubVerificationService;
import com.moriah.skillhub.submission.gateway.PrVerification;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * library-docs.md "GitHub REST API" + build-plan.md feature 12.
 * <p>
 * {@code create} deliberately has no {@code @Transactional} of its own — AGENTS.md: "Never make
 * an outbound HTTP call inside a transaction." {@code githubVerificationService.verify} can reach
 * out to GitHub over the network (via {@code GithubPrFetcher}), so it runs first, before anything
 * is touched in the database at all: a malformed URL, a genuinely missing PR, or an author
 * mismatch all fail here with nothing yet mutated, so there's no status flip to roll back and no
 * compensating transaction needed. {@code TaskService.markInReview} and {@code
 * TaskSubmissionWriter.tryInsert}/{@code .findExisting} each still open their own short
 * transaction (their own {@code @Transactional} beans, called cross-bean so the proxy applies) —
 * confirmed the hard way, this coupling of an HTTP call to an open transaction is exactly the kind
 * of thing that can quietly starve the connection pool under a slow GitHub response, not just a
 * clean 5xx (the case this design otherwise already handles).
 * <p>
 * A bad/unauthorized {@code taskId} now surfaces after one GitHub call has already happened,
 * rather than before (the previous ordering) — accepted deliberately: that call is cheap (Redis-
 * cached 5 min, 5,000/hr limit) and only reachable with an already-well-formed PR URL, a
 * materially smaller cost than the transaction/pool-exhaustion risk it replaces.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {

    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskSubmissionWriter taskSubmissionWriter;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final GithubVerificationService githubVerificationService;

    public SubmissionResponse create(Long callerUserId, CreateSubmissionRequest request) {
        User student = requireUser(callerUserId);

        Optional<PrVerification> verification = githubVerificationService.verify(request.prUrl(), student.getGithubUsername());

        taskService.markInReview(request.taskId(), callerUserId);
        int attemptNumber = nextAttemptNumber(request.taskId(), callerUserId);

        TaskSubmission submission = new TaskSubmission();
        submission.setTaskId(request.taskId());
        submission.setUser(student);
        submission.setAttemptNumber(attemptNumber);
        submission.setPrUrl(request.prUrl());
        submission.setVideoUrl(request.videoUrl());
        submission.setNotes(request.notes());
        submission.setSubmittedAt(Instant.now());
        applyVerification(submission, verification);

        TaskSubmission saved;
        try {
            // TaskSubmissionWriter, not taskSubmissionRepository directly — REQUIRES_NEW isolates
            // the insert into its own transaction/session, so a failed flush here can't poison
            // *this* method's own session before the recovery read below runs. See its Javadoc:
            // confirmed the hard way — catching the exception here and reading through the same
            // (already-poisoned) session threw a second, different failure ("has a null
            // identifier ... session is flushed after an exception occurs"), the exact class of
            // bug WebhookIdempotencyService's own Javadoc already documents.
            saved = taskSubmissionWriter.tryInsert(submission);
        } catch (DataIntegrityViolationException | CannotAcquireLockException e) {
            // build-plan.md feature 12: "double-POST does not create two submissions." The
            // unique (task_id, user_id, attempt_number) constraint is the real idempotency
            // guarantee (library-docs.md "Webhook Idempotency" — same reasoning, never a
            // SELECT-then-INSERT, that races) — a genuine double-POST lands here; return
            // whichever row actually won.
            //
            // Two exception types, not one: MySQL/InnoDB doesn't always resolve two transactions
            // concurrently inserting the *same* unique key as a clean "one wins, one gets a
            // duplicate-key error" — both can end up taking a gap/next-key lock on the not-yet-
            // existing index entry first, and InnoDB detects that mutual wait as a genuine
            // deadlock instead, killing one transaction with `CannotAcquireLockException` rather
            // than `DataIntegrityViolationException`. Either way, by the time we reach this catch
            // block the winning transaction has already committed — InnoDB blocks/kills the
            // loser until the winner resolves — so the winning row is guaranteed durably visible
            // here regardless of which exception this particular loser got.
            // TaskSubmissionWriter.findExisting, not the repository directly — see its own
            // Javadoc. `create()` no longer wraps this in one outer transaction (see this class's
            // own Javadoc), so the original REPEATABLE READ snapshot-staleness failure mode this
            // comment used to describe can no longer happen here — but `findExisting` stays
            // REQUIRES_NEW anyway (defensive: correct regardless of whether a future caller ever
            // wraps `create()` in its own transaction) and still needs its `JOIN FETCH ts.user`
            // (see the repository query's Javadoc) so `toResponse` below can read `saved.getUser()`
            // after `findExisting`'s own transaction/session has already closed.
            saved = taskSubmissionWriter.findExisting(request.taskId(), callerUserId, attemptNumber)
                    .orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> list(Long taskId, SubmissionStatus status, Pageable pageable) {
        return PageResponse.from(taskSubmissionRepository.search(taskId, status, pageable).map(this::toResponse));
    }

    private int nextAttemptNumber(Long taskId, Long userId) {
        Integer maxAttempt = taskSubmissionRepository.findMaxAttemptNumber(taskId, userId);
        return (maxAttempt == null ? 0 : maxAttempt) + 1;
    }

    private void applyVerification(TaskSubmission submission, Optional<PrVerification> verification) {
        if (verification.isEmpty()) {
            return; // GitHub unreachable — verifiedAt stays null, SubmissionVerificationRetryJob tries later
        }
        PrVerification pr = verification.get();
        submission.setRepoOwner(pr.owner());
        submission.setRepoName(pr.repo());
        submission.setPrNumber(pr.prNumber());
        submission.setPrState(pr.state());
        submission.setCommitCount(pr.commitCount());
        submission.setLatestCommitSha(pr.latestCommitSha());
        submission.setVerifiedAt(Instant.now());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    TaskSubmission requireSubmission(Long submissionId) {
        return taskSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SUBMISSION_NOT_FOUND, submissionId));
    }

    SubmissionResponse toResponse(TaskSubmission submission) {
        User student = submission.getUser();
        return new SubmissionResponse(
                submission.getId(),
                submission.getTaskId(),
                student.getUuid(),
                student.getFullName(),
                submission.getAttemptNumber(),
                submission.getPrUrl(),
                submission.getRepoOwner(),
                submission.getRepoName(),
                submission.getPrNumber(),
                submission.getPrState(),
                submission.getCommitCount(),
                submission.getLatestCommitSha(),
                submission.getVideoUrl(),
                submission.getNotes(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getVerifiedAt());
    }
}
