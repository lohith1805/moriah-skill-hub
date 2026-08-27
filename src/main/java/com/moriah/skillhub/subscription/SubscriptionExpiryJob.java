package com.moriah.skillhub.subscription;

import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * `/architect feature 07` decision: 03:00 IST, after the 01:30->01:45->02:00 PIP pipeline
 * finishes — no stated dependency between them, but this ordering means PIP evaluation never
 * runs against entitlements mid-transition, and costs nothing to guarantee.
 * <p>
 * Without this job every subscription is perpetual (build-plan.md feature 07) — nothing else
 * ever transitions an {@code ACTIVE} subscription to {@code EXPIRED}. The atomic, {@code
 * @Transactional} core work lives on {@link SubscriptionExpiryService}, a separate bean — see
 * that class's Javadoc for why this class cannot hold {@code @Transactional} on its own
 * scheduled method and call it directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private static final String JOB_NAME = "SubscriptionExpiryJob";

    private final SubscriptionExpiryService subscriptionExpiryService;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.subscription.expiry-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "30m")
    public void expireOverdueSubscriptions() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int count = subscriptionExpiryService.runExpiry();
            jobRunTracker.succeed(run.getId(), count);
            log.info("[{}] expired {} subscription(s)", JOB_NAME, count);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }
}
