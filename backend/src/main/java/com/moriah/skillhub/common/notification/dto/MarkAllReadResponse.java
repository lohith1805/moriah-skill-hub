package com.moriah.skillhub.common.notification.dto;

/** {@code PUT /api/v1/notifications/read-all} — how many unread rows were flipped to read. */
public record MarkAllReadResponse(int markedRead) {
}
