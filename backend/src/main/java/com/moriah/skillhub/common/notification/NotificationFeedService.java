package com.moriah.skillhub.common.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.dto.MarkAllReadResponse;
import com.moriah.skillhub.common.notification.dto.NotificationResponse;
import com.moriah.skillhub.common.notification.dto.UnreadCountResponse;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.moriah.skillhub.common.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Read side of the {@code notifications} table for the in-app feed (gap B1.5). The write side —
 * enqueue + dispatch — stays in {@link NotificationService}/{@link NotificationWorker}; this
 * class only ever reads a caller's own rows back and stamps {@code read_at}. Lives in {@code
 * common/} like the rest of the module and imports no feature package (architecture.md).
 * <p>
 * The feed is scoped to {@link NotificationChannel#IN_APP} deliberately: an {@code EMAIL} or
 * {@code WHATSAPP} row records that we sent something out of band, not something the recipient
 * should see in an inbox. {@code InAppDispatcher}'s Javadoc already anticipated "a future in-app
 * inbox endpoint would just read [the row] back" — this is that endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationFeedService {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> feed(Long callerUserId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? notificationRepository.findByUserIdAndChannelAndReadAtIsNull(callerUserId, NotificationChannel.IN_APP, pageable)
                : notificationRepository.findByUserIdAndChannel(callerUserId, NotificationChannel.IN_APP, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long callerUserId) {
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndChannelAndReadAtIsNull(callerUserId, NotificationChannel.IN_APP));
    }

    /** Idempotent — re-reading an already-read row keeps its original {@code readAt} rather than
     * moving the timestamp. An id that is not the caller's own is a 404, never a silent no-op. */
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long callerUserId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public MarkAllReadResponse markAllRead(Long callerUserId) {
        int marked = notificationRepository.markAllReadForUser(callerUserId, Instant.now());
        return new MarkAllReadResponse(marked);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTemplateCode(),
                deserializePayload(n),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getCreatedAt());
    }

    /** A malformed or absent payload is surfaced as an empty map, never a 500 — the feed must
     * still render even if one row's stored JSON is bad. */
    private Map<String, Object> deserializePayload(Notification n) {
        String raw = n.getPayload();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, PAYLOAD_TYPE);
        } catch (Exception e) {
            log.warn("[notifications/feed] notification {} has an unparseable payload", n.getId());
            return Map.of();
        }
    }
}
