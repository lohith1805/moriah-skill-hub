package com.moriah.skillhub.common.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Audit 2026-08-31 (M20): {@code refresh_tokens} (30-day TTL, multi-device), {@code
 * password_reset_tokens} and {@code email_verification_tokens} are never pruned — at 10k users
 * {@code refresh_tokens} alone trends to 100k+ rows and only grows. Point lookups stay fast on
 * the unique hash index, but the tables and {@code revokeAllActiveForUser}-style scans degrade
 * over time.
 * <p>
 * Deletes rows whose {@code expires_at} is more than a grace window in the past — long enough
 * that a token still needed for after-the-fact debugging is kept, short enough to bound growth.
 * Bounded per run so the delete never takes a large lock; a backlog is cleared over successive
 * nightly runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredTokenReaperJob {

    private static final String JOB_NAME = "ExpiredTokenReaperJob";
    private static final int GRACE_DAYS = 30;
    private static final int MAX_ROWS_PER_TABLE_PER_RUN = 20_000;

    private final JdbcTemplate jdbcTemplate;
    private final JobRunTracker jobRunTracker;

    @Scheduled(cron = "${moriah.token-reaper.cron:0 20 3 * * *}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "10m")
    public void reap() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int total = 0;
            total += deleteExpired("refresh_tokens");
            total += deleteExpired("password_reset_tokens");
            total += deleteExpired("email_verification_tokens");
            jobRunTracker.succeed(run.getId(), total);
            if (total > 0) {
                log.info("[{}] deleted {} expired token row(s)", JOB_NAME, total);
            }
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }

    private int deleteExpired(String table) {
        // Table name is a compile-time constant from this class, never user input — safe to inline.
        return jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE expires_at < (NOW(6) - INTERVAL ? DAY) LIMIT ?",
                GRACE_DAYS, MAX_ROWS_PER_TABLE_PER_RUN);
    }
}
