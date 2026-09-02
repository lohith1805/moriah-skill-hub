package com.moriah.skillhub.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Audit 2026-08-31 (C3): {@code WebhookIdempotencyService} now flips a claimed event to {@code
 * PROCESSED} in the same transaction as its state change and releases the claim on handler
 * failure, so the "claimed then silently lost" window is closed. This job is the backstop for
 * the one residual case — the process is killed between the business commit and the claim
 * release — by surfacing any {@code webhook_events} row still stuck in {@code RECEIVED} well past
 * the point a handler could still be running.
 * <p>
 * It does not re-drive anything (the gateway owns redelivery, and a stale row is reclaimable by
 * {@code claim()} on the next delivery anyway) — it exists so a genuinely stuck payment or
 * message is visible the next morning without reading gateway dashboards, per code-standards.md
 * "a job that silently did nothing must be diagnosable".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookReconciliationJob {

    private static final String JOB_NAME = "WebhookReconciliationJob";

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "${moriah.webhook.reconciliation-cron:0 */10 * * * *}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "5m")
    public void reconcile() {
        List<Long> stuck = jdbcTemplate.queryForList("""
                SELECT id FROM webhook_events
                 WHERE status = 'RECEIVED'
                   AND created_at < (NOW(6) - INTERVAL 30 MINUTE)
                 ORDER BY created_at
                 LIMIT 200
                """, Long.class);
        if (stuck.isEmpty()) {
            return;
        }
        log.error("[{}] {} webhook_events row(s) stuck in RECEIVED for >30min — ids {} — "
                        + "verify against the gateway; the next redelivery will reclaim them",
                JOB_NAME, stuck.size(), stuck);
    }
}
