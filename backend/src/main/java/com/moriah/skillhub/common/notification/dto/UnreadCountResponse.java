package com.moriah.skillhub.common.notification.dto;

/** {@code GET /api/v1/notifications/unread-count} — a bell-badge count, kept as its own object
 * (not a bare number) so every endpoint still returns a JSON object inside {@code ApiResponse}. */
public record UnreadCountResponse(long unreadCount) {
}
