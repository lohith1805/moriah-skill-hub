package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.notification.dispatch.NotificationChannelDispatcher;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import com.moriah.skillhub.common.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * library-docs.md "Reliable Queue", applied directly: {@code RPOPLPUSH} into a processing list,
 * ack (remove) only after a confirmed dispatch. A plain {@code LPOP} loses the message if this
 * process dies between pop and dispatch.
 * <p>
 * No {@code @SchedulerLock} here — unlike a traditional cron job, running this on every instance
 * concurrently is exactly the point: {@code RPOPLPUSH} is atomic, so N instances draining the
 * same list just means N-way parallel consumption, never double-processing the same entry.
 * {@code NotificationReaperJob} (the one thing that must run as a single instance) does use
 * {@code @SchedulerLock}.
 * <p>
 * Retries are spaced by the reaper's 5-minute staleness sweep, not literal exponential backoff —
 * library-docs.md's own reliable-queue code sample has no backoff calculation, only "the reaper
 * requeues entries older than 5 minutes." `/architect feature 08` treated that as the actual
 * retry cadence rather than inventing a backoff schedule nothing else specifies.
 */
@Component
@Slf4j
public class NotificationWorker {

    private static final String QUEUE_KEY = "queue:notifications";
    private static final String PROCESSING_KEY = "queue:notifications:processing";

    // Must stay comfortably under spring.data.redis.timeout (2000ms, application.yml) — Lettuce
    // applies that as the client-side command timeout uniformly, including for a blocking
    // command whose own requested server-side wait is longer, so a 5s BRPOPLPUSH here would
    // throw RedisCommandTimeoutException on every single empty-queue poll rather than gracefully
    // returning null (found running the full IT suite: this class runs continuously in every
    // integration test via @EnableScheduling, so an empty queue is the common case, not an edge
    // case). 1s trades a slightly less efficient blocking wait for staying safely under that
    // ceiling with margin.
    private static final Duration POP_TIMEOUT = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final Map<NotificationChannel, NotificationChannelDispatcher> dispatchersByChannel;

    public NotificationWorker(StringRedisTemplate redisTemplate, NotificationRepository notificationRepository,
                               ObjectMapper objectMapper, List<NotificationChannelDispatcher> dispatchers) {
        this.redisTemplate = redisTemplate;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.dispatchersByChannel = dispatchers.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationChannelDispatcher::channel, d -> d));
    }

    /** Blocks up to {@link #POP_TIMEOUT} waiting for an entry; {@code @Scheduled}'s fixedDelay
     * fires the next invocation immediately after this one returns, so an empty queue just means
     * this method spends most of its time blocked inside the pop call — functionally a
     * continuous consumer loop without a dedicated thread. */
    @Scheduled(fixedDelay = 100)
    public void drainOne() {
        String raw = redisTemplate.opsForList()
                .rightPopAndLeftPush(QUEUE_KEY, PROCESSING_KEY, POP_TIMEOUT);
        if (raw == null) {
            return;
        }
        processOne(raw);
    }

    private void processOne(String raw) {
        QueueEnvelope envelope;
        try {
            envelope = objectMapper.readValue(raw, QueueEnvelope.class);
        } catch (Exception e) {
            log.error("[notify] malformed queue entry {}, dropping", raw);
            ack(raw);
            return;
        }
        // Syntactically valid JSON missing the id field itself (e.g. "{}") parses without
        // throwing but leaves notificationId null — findById(null) throws
        // IllegalArgumentException, not a clean "not found," so this needs its own guard rather
        // than falling through to the same handling as a genuinely missing notification below.
        if (envelope.notificationId() == null) {
            log.error("[notify] queue entry with no notification id {}, dropping", raw);
            ack(raw);
            return;
        }
        Long id = envelope.notificationId();

        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            log.warn("[notify] notification {} no longer exists, dropping", id);
            ack(raw);
            return;
        }
        if (notification.getStatus() != NotificationStatus.QUEUED) {
            // Already SENT or FAILED by an earlier delivery of this same message — the reaper's
            // at-least-once redelivery means this can happen; never double-send.
            ack(raw);
            return;
        }

        NotificationChannelDispatcher dispatcher = dispatchersByChannel.get(notification.getChannel());
        try {
            dispatcher.dispatch(notification, deserialize(notification.getPayload()));
            markSent(notification);
            ack(raw);
        } catch (Exception e) {
            log.warn("[notify] dispatch failed for notification {}", id, e);
            recordFailedAttempt(notification, e);
            if (notification.getStatus() == NotificationStatus.FAILED) {
                // Attempts exhausted — terminal, no more retries. Ack now rather than leaving it
                // for the reaper to redeliver and drop on a wasted extra cycle.
                ack(raw);
            }
            // Otherwise deliberately left in the processing list — the reaper requeues it onto
            // queue:notifications for another attempt once it's older than the staleness window.
        }
    }

    @Transactional
    void markSent(Notification notification) {
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional
    void recordFailedAttempt(Notification notification, Exception e) {
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setErrorMessage(truncate(e.getMessage()));
        if (notification.getAttempts() >= Constants.NOTIFICATION_MAX_ATTEMPTS) {
            notification.setStatus(NotificationStatus.FAILED);
        }
        notificationRepository.save(notification);
    }

    private void ack(String raw) {
        redisTemplate.opsForList().remove(PROCESSING_KEY, 1, raw);
    }

    private Map<String, Object> deserialize(String payload) {
        if (payload == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize notification payload", e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
