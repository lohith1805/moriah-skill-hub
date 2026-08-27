package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orchestrates a {@code notifications} row write followed by the Redis queue push — see {@link
 * NotificationRowWriter}'s Javadoc for why these are two separate beans and why the push happens
 * only after the save call returns, never from inside the same transactional method body.
 * <p>
 * Deliberately not {@code @Transactional} itself — the Redis push here is intentionally outside
 * any transaction (Redis isn't part of the JPA/JDBC transaction anyway), and by the time {@code
 * rowWriter.save(...)}/{@code saveInNewTransaction(...)} returns, its own transaction has
 * already committed (a real cross-bean call through {@code rowWriter}'s proxy), so the row is
 * guaranteed durably visible to {@link NotificationWorker}'s separate thread/connection before
 * the corresponding queue entry is ever pushed.
 */
@Service
@RequiredArgsConstructor
class NotificationWriter {

    private static final String QUEUE_KEY = "queue:notifications";

    private final NotificationRowWriter rowWriter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Participates in the caller's ambient transaction via {@code rowWriter.save} — correct
     * when {@code NotificationService.enqueue()} is called directly from within an
     * already-active, genuinely-open transaction (so the notification write rolls back with it,
     * if it fails). If no transaction is active (e.g. called from a plain, non-transactional
     * context), {@code @Transactional}'s default REQUIRED propagation just opens a fresh one. */
    Notification write(Long userId, NotificationChannel channel, String templateCode, String payloadJson) {
        Notification saved = rowWriter.save(buildNotification(userId, channel, templateCode, payloadJson));
        pushToQueue(saved.getId());
        return saved;
    }

    /** Always a fresh transaction — mandatory for the {@code enqueueAfterCommit} call path, see
     * {@code NotificationWriter}'s earlier self-invocation history in progress-tracker.md. */
    Notification writeInNewTransaction(Long userId, NotificationChannel channel, String templateCode,
                                        String payloadJson) {
        Notification saved = rowWriter.saveInNewTransaction(
                buildNotification(userId, channel, templateCode, payloadJson));
        pushToQueue(saved.getId());
        return saved;
    }

    private Notification buildNotification(Long userId, NotificationChannel channel, String templateCode,
                                            String payloadJson) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(channel);
        notification.setTemplateCode(templateCode);
        notification.setPayload(payloadJson);
        notification.setStatus(NotificationStatus.QUEUED);
        return notification;
    }

    private void pushToQueue(Long notificationId) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, serializeEnvelope(notificationId));
    }

    private String serializeEnvelope(Long notificationId) {
        try {
            return objectMapper.writeValueAsString(new QueueEnvelope(notificationId, Instant.now()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize queue envelope", e);
        }
    }
}
