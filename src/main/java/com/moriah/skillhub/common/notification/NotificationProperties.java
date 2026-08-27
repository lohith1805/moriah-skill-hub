package com.moriah.skillhub.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code reaperCron} — how often {@code NotificationReaperJob} sweeps
 * {@code queue:notifications:processing} for entries older than {@code
 * Constants.NOTIFICATION_PROCESSING_STALE_MINUTES}. Every 60s by default — frequent enough that
 * a dead worker's messages are redelivered promptly, cheap enough not to matter (`/architect
 * feature 08` decision; build-plan.md doesn't name a cadence, only the 5-minute staleness
 * threshold library-docs.md's code sample already fixes). */
@ConfigurationProperties(prefix = "moriah.notification")
public record NotificationProperties(String reaperCron) {
}
