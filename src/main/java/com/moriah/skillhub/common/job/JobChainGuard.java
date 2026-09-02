package com.moriah.skillhub.common.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Audit 2026-08-31 (H4): the nightly chain — attendance finalisation (01:30) → metrics refresh
 * (01:45) → PIP evaluation (02:00) — is ordered only by three independent cron expressions.
 * ShedLock's {@code @SchedulerLock} is keyed per job name, so it provides no interlock between
 * them: if attendance finalisation runs past 01:45, metrics refresh still starts on schedule and
 * recomputes {@code student_metrics} against a half-finalised attendance denominator (AGENTS.md:
 * "Reordering silently corrupts the attendance denominator").
 * <p>
 * Each downstream job asks this guard whether its predecessor has a recent {@code SUCCESS} run
 * before doing any work. "Recent" = completed within {@link #MAX_CHAIN_AGE}, which is far wider
 * than the chain's own 30-minute span but far narrower than the 24 hours to the previous night's
 * run, so it cleanly means "tonight's run finished" without needing calendar-date arithmetic in
 * the jobs' scheduling zone.
 */
@Component
@RequiredArgsConstructor
public class JobChainGuard {

    /** Wide enough to absorb a slow predecessor at production scale, narrow enough to never
     * match last night's run of the same job. */
    static final Duration MAX_CHAIN_AGE = Duration.ofHours(6);

    private final JobRunRepository jobRunRepository;

    /**
     * True only if the most recent run of {@code predecessorJobName} finished with status
     * {@code SUCCESS} within {@link #MAX_CHAIN_AGE}. A downstream nightly job that gets
     * {@code false} here must skip its work and record a FAILED run rather than proceed against
     * stale upstream data.
     */
    public boolean predecessorSucceededRecently(String predecessorJobName) {
        return jobRunRepository.findFirstByJobNameOrderByStartedAtDesc(predecessorJobName)
                .filter(run -> run.getStatus() == JobRunStatus.SUCCESS)
                .filter(run -> run.getCompletedAt() != null
                        && run.getCompletedAt().isAfter(Instant.now().minus(MAX_CHAIN_AGE)))
                .isPresent();
    }
}
