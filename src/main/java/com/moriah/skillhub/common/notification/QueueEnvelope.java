package com.moriah.skillhub.common.notification;

import java.time.Instant;

/**
 * What actually rides on {@code queue:notifications}/{@code queue:notifications:processing} — a
 * bare notification id isn't enough, since {@code NotificationReaperJob} needs to know how long
 * an entry has sat in the processing list to decide whether it's stale. {@code RPOPLPUSH} moves
 * the exact same string from one list to the other, so {@code queuedAt} set once at enqueue time
 * is all the reaper needs — no separate Redis structure required.
 */
public record QueueEnvelope(Long notificationId, Instant queuedAt) {
}
