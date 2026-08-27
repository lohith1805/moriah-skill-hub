package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.springframework.stereotype.Component;

import java.util.Map;

/** {@code IN_APP} has nothing to send externally — the {@code notifications} row itself is the
 * notification (a future in-app inbox endpoint would just read it back). A no-op success is
 * enough for the worker's normal ack/mark-SENT flow to apply uniformly. */
@Component
public class InAppDispatcher implements NotificationChannelDispatcher {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void dispatch(Notification notification, Map<String, Object> payload) {
        // Nothing to do — see class Javadoc.
    }
}
