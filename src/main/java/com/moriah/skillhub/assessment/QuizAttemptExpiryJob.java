package com.moriah.skillhub.assessment;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * build-plan.md feature 14: "Unsubmitted attempts swept to EXPIRED and scored on answers given."
 * No cron time is specified in build-plan.md the way features 13/16/17's nightly chain is —
 * unlike attendance finalisation, a quiz attempt's own {@code durationMinutes} is what actually
 * bounds how stale an unswept {@code IN_PROGRESS} row can get, not a fixed daily slot, so this
 * runs frequently (every 15 minutes, same cadence and reasoning as {@code
 * SubmissionVerificationRetryJob}) rather than once a night.
 * <p>
 * Same {@code JobRunTracker}/{@code @SchedulerLock} shape as {@code AttendanceFinalisationJob}/
 * {@code SubscriptionExpiryJob}. The atomic, {@code @Transactional} core work lives on {@link
 * QuizAttemptExpiryService}, a separate bean — see that class's Javadoc for why this class cannot
 * hold {@code @Transactional} on its own scheduled method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuizAttemptExpiryJob {

    private static final String JOB_NAME = "QuizAttemptExpiryJob";

    private final QuizAttemptExpiryService quizAttemptExpiryService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.assessment.attempt-expiry-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "10m")
    public void expireScheduled() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = quizAttemptExpiryService.expire();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] expired {} attempt(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
