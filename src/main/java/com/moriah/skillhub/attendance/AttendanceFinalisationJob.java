package com.moriah.skillhub.attendance;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * build-plan.md feature 13: "Without this job the 75% rule — the most important rule in the
 * product — can never fire." Two responsibilities in one nightly pass, deliberately combined
 * (`/architect feature 13` decision): promote any non-cancelled {@code SCHEDULED} standup whose
 * {@code scheduledAt} has passed to {@code CONDUCTED} — no endpoint in this feature's list ever
 * does that — then, for every {@code CONDUCTED} standup not yet {@code finalisedAt}, write an
 * {@code ABSENT} row for every enrolled student with none. Combining the two guarantees the
 * safety net fires even on a day nobody checks in and no PM touches the record; requiring a
 * separate manual "mark conducted" step would leave exactly that worst case undetected.
 * <p>
 * Same {@code JobRunTracker}/{@code @SchedulerLock} shape as {@code SubscriptionExpiryJob}/
 * {@code SubmissionVerificationRetryJob}. The atomic, {@code @Transactional} core work lives on
 * {@link AttendanceFinalisationService}, a separate bean — see that class's Javadoc for why this
 * class cannot hold {@code @Transactional} on its own scheduled method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceFinalisationJob {

    private static final String JOB_NAME = "AttendanceFinalisationJob";

    private final AttendanceFinalisationService attendanceFinalisationService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.attendance.finalisation-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "30m")
    public void finaliseScheduled() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = attendanceFinalisationService.finalise();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] finalised {} standup(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
