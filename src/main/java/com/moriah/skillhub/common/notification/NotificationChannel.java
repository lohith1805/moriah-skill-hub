package com.moriah.skillhub.common.notification;

/** Matches V2's {@code chk_notifications_channel} CHECK constraint exactly (architecture.md
 * "notifications" table definition). Only {@link #EMAIL} and {@link #WHATSAPP} have a real
 * dispatcher this feature — see {@code NotificationChannelDispatcher} implementations. */
public enum NotificationChannel {
    EMAIL,
    WHATSAPP,
    IN_APP,
    SMS
}
