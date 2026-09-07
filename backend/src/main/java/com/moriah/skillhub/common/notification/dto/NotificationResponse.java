package com.moriah.skillhub.common.notification.dto;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the in-app notifications feed (gap B1.5). {@code payload} is the notification's
 * stored JSON deserialized to a map so the FE renders it without a second parse; a row with no
 * payload surfaces an empty map, never {@code null}. {@code templateCode} is the message key the
 * FE maps to display copy. {@code read} is {@code readAt != null}, exposed as a plain boolean
 * since that is all the feed UI binds to.
 */
public record NotificationResponse(
        Long id,
        String templateCode,
        Map<String, Object> payload,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}
