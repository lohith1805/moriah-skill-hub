package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InAppDispatcherTest {

    @Test
    void channel_isInApp() {
        assertThat(new InAppDispatcher().channel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void dispatch_neverThrows_theRowItselfIsTheNotification() {
        assertThatCode(() -> new InAppDispatcher().dispatch(new Notification(), Map.of()))
                .doesNotThrowAnyException();
    }
}
