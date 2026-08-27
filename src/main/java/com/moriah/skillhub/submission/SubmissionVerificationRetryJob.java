package com.moriah.skillhub.submission;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import com.moriah.skillhub.submission.entity.TaskSubmission;
import com.moriah.skillhub.submission.gateway.GithubVerificationService;
import com.moriah.skillhub.submission.gateway.PrVerification;
import com.moriah.skillhub.submission.repository.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * `/architect feature 12` decision: every 15 minutes, no attempt cap — build-plan.md: "GitHub
 * 5xx -> persist verified_at = null, status SUBMITTED, retry job verifies later. An outage never
 * blocks a student." Same {@code JobRunTracker}/{@code @SchedulerLock} shape as {@code
 * SubscriptionExpiryJob} (feature 07).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionVerificationRetryJob {

    private static final String JOB_NAME = "SubmissionVerificationRetryJob";

    private final TaskSubmissionRepository taskSubmissionRepository;
    private final GithubVerificationService githubVerificationService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.submission.verification-retry-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "10m")
    public void retryScheduled() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = retry();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] re-verified {} submission(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }

    /** One flat query for the whole cohort, processed in memory (code-standards.md "Async and
     * Scheduled Work") — public so {@code SubmissionVerificationRetryJobIT}/tests can call it
     * directly rather than waiting on the real 15-minute cron trigger, matching {@code
     * SubscriptionExpiryService.runExpiry}'s naming and reasoning. Unlike {@code
     * AttendanceFinalisationJob}/{@code SubscriptionExpiryJob}, this method needed no
     * self-invocation fix — it was never {@code @Transactional} in the first place, deliberately,
     * per the note below.
     * <p>
     * Deliberately not {@code @Transactional} — AGENTS.md: "Never make an outbound HTTP call
     * inside a transaction." {@code applyIfVerifiable} calls GitHub once per unverified
     * submission; wrapping the whole loop in one transaction would hold a DB connection open
     * across N sequential network calls, worse than {@code SubmissionService.create}'s single-call
     * version of the same mistake. {@code findByVerifiedAtIsNull} and {@code saveAll} each still
     * run in their own short transaction (Spring Data's per-call default on the repository proxy)
     * — the entities returned by the first read are ordinary detached objects by the time the
     * network calls run, and {@code saveAll} merges the mutated ones back in its own transaction
     * at the end, with no HTTP call inside it. */
    public int retry() {
        List<TaskSubmission> unverified = taskSubmissionRepository.findByVerifiedAtIsNull();
        List<TaskSubmission> nowVerified = new ArrayList<>();

        for (TaskSubmission submission : unverified) {
            applyIfVerifiable(submission).ifPresent(nowVerified::add);
        }

        taskSubmissionRepository.saveAll(nowVerified);
        return nowVerified.size();
    }

    /** Never lets one bad row crash the whole sweep (code-standards.md "Failures are expected").
     * A genuinely bad PR (author mismatch, deleted PR) surfacing only now — after initially
     * failing with a transient 5xx at submission time — is logged and left unverified; this
     * schema has no dedicated "verification permanently failed" state, and build-plan.md doesn't
     * ask for one. */
    private Optional<TaskSubmission> applyIfVerifiable(TaskSubmission submission) {
        try {
            Optional<PrVerification> verification = githubVerificationService.verify(
                    submission.getPrUrl(), submission.getUser().getGithubUsername());
            if (verification.isEmpty()) {
                return Optional.empty(); // still unreachable — try again next sweep
            }

            PrVerification pr = verification.get();
            submission.setRepoOwner(pr.owner());
            submission.setRepoName(pr.repo());
            submission.setPrNumber(pr.prNumber());
            submission.setPrState(pr.state());
            submission.setCommitCount(pr.commitCount());
            submission.setLatestCommitSha(pr.latestCommitSha());
            submission.setVerifiedAt(Instant.now());
            return Optional.of(submission);
        } catch (Exception e) {
            log.warn("[{}] submission {} still cannot be verified: {}", JOB_NAME, submission.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
