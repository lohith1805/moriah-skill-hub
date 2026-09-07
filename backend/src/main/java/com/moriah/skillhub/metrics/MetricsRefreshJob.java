package com.moriah.skillhub.metrics;

import com.moriah.skillhub.common.job.JobChainGuard;
import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * build-plan.md feature 16 / architecture.md's nightly chain: 01:45 IST, strictly after {@code
 * AttendanceFinalisationJob} (01:30) and strictly before {@code PipEvaluationJob} (02:00, feature
 * 17) — "metrics computed before absences are written are wrong, and the PIP engine reading stale
 * metrics is worse than it not running." The three cron expressions are independently configured
 * (not chained/triggered off each other), so this ordering is enforced by the clock, not code —
 * see progress-tracker.md's Nightly Job Chain table for the standing verification obligation this
 * creates on every future change to any of the three cron values.
 * <p>
 * Same {@code JobRunTracker}/{@code @SchedulerLock} shape as every other nightly job in this
 * codebase. The atomic, {@code @Transactional} aggregate-and-upsert work lives on {@link
 * StudentMetricsService}, a separate bean — see that class's Javadoc for why this class cannot
 * hold {@code @Transactional} on its own scheduled method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsRefreshJob {

    private static final String JOB_NAME = "MetricsRefreshJob";
    private static final String PREDECESSOR = "AttendanceFinalisationJob";

    private final StudentMetricsService studentMetricsService;
    private final JobRunTracker jobRunTracker;
    private final JobChainGuard jobChainGuard;

    @Scheduled(cron = "${moriah.metrics.refresh-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "30m")
    public void refreshScheduled() {
        // Audit 2026-08-31 (H4): refuse to run against a half-finalised attendance denominator.
        if (!jobChainGuard.predecessorSucceededRecently(PREDECESSOR)) {
            JobRun skipped = jobRunTracker.start(JOB_NAME);
            String reason = PREDECESSOR + " has no recent SUCCESS run — skipping to avoid corrupting metrics";
            log.error("[{}] {}", JOB_NAME, reason);
            jobRunTracker.fail(skipped.getId(), reason);
            return;
        }
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = studentMetricsService.refresh();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] refreshed {} student metric row(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
