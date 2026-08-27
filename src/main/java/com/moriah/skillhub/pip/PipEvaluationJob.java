package com.moriah.skillhub.pip;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * build-plan.md feature 17 / architecture.md's nightly chain: 02:00 IST, strictly after {@code
 * MetricsRefreshJob} (01:45) — "loads the whole cohort from student_metrics in ONE query." Same
 * {@code JobRunTracker}/{@code @SchedulerLock} shape as every other nightly job in this codebase.
 * The atomic, {@code @Transactional} evaluate-and-trigger work lives on {@link
 * PipEvaluationService}, a separate bean — see that class's Javadoc for why this class cannot hold
 * {@code @Transactional} on its own scheduled method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipEvaluationJob {

    private static final String JOB_NAME = "PipEvaluationJob";

    private final PipEvaluationService pipEvaluationService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.pip.evaluation-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "30m")
    public void evaluateScheduled() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = pipEvaluationService.evaluate();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] triggered {} new PIP record(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
