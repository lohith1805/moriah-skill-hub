package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;

import java.util.Map;

/**
 * One implementation per {@link NotificationChannel} — {@code NotificationWorker} picks the
 * right one by {@link #channel()} into a {@code Map<NotificationChannel, ...>} built from every
 * bean of this type Spring finds, rather than a switch statement, so a future channel is a new
 * {@code @Component}, not an edit to the worker.
 * <p>
 * {@code payload} is the notification row's {@code payload} JSON already deserialized once by
 * the worker — never a {@code userId} lookup back into {@code user/}. The caller that enqueues a
 * notification already has the recipient's email/phone (it's the one that just looked the user
 * up for its own reasons) and puts it directly in the payload — {@code common/notification}
 * never imports the {@code user/} package to re-derive it (architecture.md's package-boundary
 * rule: {@code common/} never imports a feature package).
 */
public interface NotificationChannelDispatcher {

    NotificationChannel channel();

    /** Throws on any failure — the worker catches, leaves the message in the processing list for
     * the reaper, and increments {@code attempts}. Never swallow a failure here. */
    void dispatch(Notification notification, Map<String, Object> payload) throws Exception;
}
