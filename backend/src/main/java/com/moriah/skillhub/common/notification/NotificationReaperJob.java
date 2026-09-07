package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.job.JobRun;
import com.moriah.skillhub.common.job.JobRunTracker;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import com.moriah.skillhub.common.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * library-docs.md "Reliable Queue": "A reaper sweeps queue:notifications:processing and requeues
 * anything stale. Notifications carry PIP triggers and payment confirmations — silently dropping
 * one is not acceptable." Unlike {@link NotificationWorker} (safe to run N-way in parallel —
 * {@code RPOPLPUSH} is atomic), this scans-then-decides, so {@code @SchedulerLock} keeps it to
 * one instance at a time; the remove-then-push sequence below is still race-safe against a
 * worker legitimately acking the same entry mid-sweep even without the lock (a zero-count
 * removal just means "already handled, skip it"), but there's no reason to run it N-way.
 * <p>
 * {@code reconcileOrphanedRows()} is a second, independent responsibility added on `/review`:
 * the Redis-side sweep above only helps an entry that's still *somewhere* in Redis (main queue
 * or processing list). It does nothing for a {@code QUEUED} row whose Redis entry was lost
 * outright — Redis restarting without persistence, an eviction, an operator flushing the wrong
 * key — which library-docs.md's own "Redis" rule explicitly calls out as unacceptable: "Redis is
 * a cache, not a store. Losing Redis must degrade performance, never lose data." Without this,
 * such a row would sit in {@code QUEUED} forever with nothing left anywhere that would ever
 * pick it up again. This re-derives a fresh queue entry directly from the database — the source
 * of truth — for any {@code QUEUED} row old enough that it can no longer plausibly be a
 * legitimate first-attempt in flight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationReaperJob {

    private static final String JOB_NAME = "NotificationReaperJob";
    private static final String QUEUE_KEY = "queue:notifications";
    private static final String PROCESSING_KEY = "queue:notifications:processing";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JobRunTracker jobRunTracker;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "${moriah.notification.reaper-cron}", zone = "${moriah.jobs.zone}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "PT50S")
    public void sweepScheduled() {
        JobRun run = jobRunTracker.start(JOB_NAME);
        try {
            int total = sweep();
            jobRunTracker.succeed(run.getId(), total);
        } catch (Exception e) {
            log.error("[{}] failed", JOB_NAME, e);
            jobRunTracker.fail(run.getId(), e.getMessage());
        }
    }

    /** Deliberately not {@code @SchedulerLock}-guarded itself — same reasoning and same pattern
     * as {@code SubscriptionExpiryService.runExpiry()}: putting the lock directly on this method
     * would race it against the real 60s cron tick calling {@link #sweepScheduled()} on the same
     * lock whenever a test (or anything else) invokes this directly through the bean's proxy,
     * silently no-oping whichever call loses. Public so {@code NotificationQueueIT} can call it
     * directly rather than waiting on the real cron trigger. */
    public int sweep() {
        int requeued = sweepStaleEntries();
        int reconciled = reconcileOrphanedRows();
        return requeued + reconciled;
    }

    private int sweepStaleEntries() {
        List<String> entries = redisTemplate.opsForList().range(PROCESSING_KEY, 0, -1);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(Constants.NOTIFICATION_PROCESSING_STALE_MINUTES));
        int requeued = 0;
        for (String raw : entries) {
            QueueEnvelope envelope = tryParse(raw);
            if (envelope == null || envelope.queuedAt().isAfter(staleBefore)) {
                continue;
            }
            // Zero-count removal means a worker already acked this entry between our LRANGE and
            // now — already handled, nothing to requeue.
            Long removedCount = redisTemplate.opsForList().remove(PROCESSING_KEY, 1, raw);
            if (removedCount != null && removedCount > 0) {
                redisTemplate.opsForList().leftPush(QUEUE_KEY, serialize(envelope.notificationId()));
                requeued++;
                log.warn("[notify] requeued stale notification {} (stuck in processing since {})",
                        envelope.notificationId(), envelope.queuedAt());
            }
        }
        return requeued;
    }

    /** Re-pushes a fresh queue entry for every {@code QUEUED} row older than {@link
     * Constants#NOTIFICATION_ORPHAN_RECONCILE_MINUTES} — deliberately without first checking
     * whether it's still present somewhere in Redis (scanning every list entry for every
     * candidate row doesn't scale, and the 15-minute floor already makes "still legitimately
     * queued behind an enormous backlog" the rare case, not the common one). Worst case this
     * pushes a second entry for a row that's still genuinely in flight, which could in principle
     * cause it to be dispatched twice; that's an acceptable trade against the alternative this
     * exists to prevent — a row silently stuck in {@code QUEUED} forever with no path back to
     * ever being sent at all. */
    private int reconcileOrphanedRows() {
        Instant orphanBefore = Instant.now().minus(Duration.ofMinutes(Constants.NOTIFICATION_ORPHAN_RECONCILE_MINUTES));
        List<Notification> orphaned = notificationRepository.findByStatusAndCreatedAtBefore(
                NotificationStatus.QUEUED, orphanBefore);
        for (Notification notification : orphaned) {
            redisTemplate.opsForList().leftPush(QUEUE_KEY, serialize(notification.getId()));
            log.warn("[notify] re-pushed orphaned notification {} (QUEUED since {}, no live Redis entry found in time)",
                    notification.getId(), notification.getCreatedAt());
        }
        return orphaned.size();
    }

    private QueueEnvelope tryParse(String raw) {
        try {
            return objectMapper.readValue(raw, QueueEnvelope.class);
        } catch (Exception e) {
            log.error("[notify] malformed processing-list entry {}, leaving it alone", raw);
            return null;
        }
    }

    private String serialize(Long notificationId) {
        try {
            // Fresh timestamp — its age in *this* new trip through the processing list starts
            // now, not from the original enqueue time.
            return objectMapper.writeValueAsString(new QueueEnvelope(notificationId, Instant.now()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize queue envelope", e);
        }
    }
}
