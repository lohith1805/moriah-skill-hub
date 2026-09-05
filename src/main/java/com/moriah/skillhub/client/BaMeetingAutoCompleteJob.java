package com.moriah.skillhub.client;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The user's own ask: once a Client Pre-Project Discussion's scheduled time has passed, it
 * should mark itself over rather than sitting as {@code SCHEDULED} forever until someone edits
 * it by hand. No fixed daily slot the way the nightly PIP/metrics chain has — a meeting's own
 * {@code scheduledAt + durationMinutes} is what bounds staleness, so this runs frequently (same
 * 15-minute cadence/reasoning as {@code QuizAttemptExpiryJob}/{@code
 * SubmissionVerificationRetryJob}).
 * <p>
 * Same {@code JobRunTracker}/{@code @SchedulerLock} shape as those two jobs. The atomic, {@code
 * @Transactional} core work lives on {@link BaMeetingAutoCompleteService}, a separate bean — see
 * that class's Javadoc for why this class cannot hold {@code @Transactional} on its own scheduled
 * method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BaMeetingAutoCompleteJob {

    private static final String JOB_NAME = "BaMeetingAutoCompleteJob";

    private final BaMeetingAutoCompleteService baMeetingAutoCompleteService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.client.meeting-auto-complete-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "10m")
    public void autoComplete() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = baMeetingAutoCompleteService.autoComplete();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] marked {} meeting(s) completed", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
