package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.notification.dispatch.NotificationChannelDispatcher;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * architecture.md package diagram: "enqueue only, never sends inline." The actual row write +
 * Redis push lives in {@link NotificationWriter}, a separate bean — see its Javadoc for why this
 * class can't just do that work itself for the {@link #enqueueAfterCommit} path.
 */
@Service
public class NotificationService {

    private final NotificationWriter notificationWriter;
    private final ObjectMapper objectMapper;
    private final Set<NotificationChannel> dispatchableChannels;

    public NotificationService(NotificationWriter notificationWriter, ObjectMapper objectMapper,
                                List<NotificationChannelDispatcher> dispatchers) {
        this.notificationWriter = notificationWriter;
        this.objectMapper = objectMapper;
        this.dispatchableChannels = dispatchers.stream()
                .map(NotificationChannelDispatcher::channel)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Writes the row and pushes to the queue immediately, in the caller's current transaction.
     * Prefer {@link #enqueueAfterCommit} for anything triggered by a business operation that
     * could still roll back — a notification for an event that never actually happened must
     * never be sent (library-docs.md "Notification Dispatch": "never send a notification for an
     * event that has not been persisted").
     *
     * @throws BusinessException {@code NOTIFICATION_CHANNEL_NOT_SUPPORTED} if no dispatcher is
     *         registered for {@code channel} (only {@code SMS}, today) — rejected at enqueue
     *         time rather than silently queued and failed later by the worker.
     */
    public Notification enqueue(Long userId, NotificationChannel channel, String templateCode,
                                 Map<String, Object> payload) {
        requireDispatchable(channel);
        return notificationWriter.write(userId, channel, templateCode, serialize(payload));
    }

    /**
     * library-docs.md "Notification Dispatch" sample, wrapped so call sites don't repeat the
     * {@link TransactionSynchronizationManager} boilerplate — "a notification failure must never
     * roll back the business transaction that caused it, hence afterCommit." The channel check
     * and payload serialization both happen eagerly, synchronously, before this method returns —
     * not deferred into the callback — so an unsupported channel or a bad payload fails the
     * caller immediately rather than silently failing post-commit (exceptions thrown from inside
     * an {@code afterCommit()} synchronization are caught and logged by Spring, never propagated
     * back to whoever registered it).
     */
    public void enqueueAfterCommit(Long userId, NotificationChannel channel, String templateCode,
                                    Map<String, Object> payload) {
        requireDispatchable(channel);
        String payloadJson = serialize(payload);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationWriter.writeInNewTransaction(userId, channel, templateCode, payloadJson);
            }
        });
    }

    /**
     * Writes the row in its own fresh transaction and pushes to the queue — for callers that
     * run with <em>no</em> ambient transaction and whose triggering event is already committed
     * (e.g. {@code InvoiceService.renderAndUpload}, an {@code @Async AFTER_COMMIT} listener). Not
     * {@link #enqueueAfterCommit} — there is no transaction to hang an {@code afterCommit} on;
     * not {@link #enqueue} — that would open a REQUIRED transaction that spans nothing.
     */
    public void enqueueNow(Long userId, NotificationChannel channel, String templateCode,
                            Map<String, Object> payload) {
        requireDispatchable(channel);
        notificationWriter.writeInNewTransaction(userId, channel, templateCode, serialize(payload));
    }

    private void requireDispatchable(NotificationChannel channel) {
        if (!dispatchableChannels.contains(channel)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_SUPPORTED);
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}
