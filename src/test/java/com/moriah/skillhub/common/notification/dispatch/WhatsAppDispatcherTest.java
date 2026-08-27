package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No real Meta developer account exists yet — mocks {@link RestClient} entirely (deep stubs,
 * since its fluent {@code post().uri().body().retrieve()...} chain returns a different
 * intermediate interface at each step).
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppDispatcherTest {

    private final WhatsAppProperties props = new WhatsAppProperties(
            "https://graph.facebook.com/v18.0", "test-phone-number-id", "test-token", "test-app-secret");

    @Test
    void dispatch_success_postsTemplateMessageToThePhoneNumberIdPath() throws Exception {
        RestClient restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        WhatsAppDispatcher dispatcher = new WhatsAppDispatcher(restClient, props);

        Notification notification = new Notification();
        notification.setTemplateCode("PIP_TRIGGERED");
        notification.setChannel(NotificationChannel.WHATSAPP);

        dispatcher.dispatch(notification, Map.of("to", "+15551234567", "parameters", List.of("John")));

        verify(restClient.post()).uri("/{phoneNumberId}/messages", "test-phone-number-id");
    }

    @Test
    void dispatch_missingTo_throwsBeforeCallingRestClient() {
        RestClient restClient = mock(RestClient.class);
        WhatsAppDispatcher dispatcher = new WhatsAppDispatcher(restClient, props);
        Notification notification = new Notification();

        assertThatThrownBy(() -> dispatcher.dispatch(notification, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("to");
    }

    @Test
    void channel_isWhatsapp() {
        assertThat(new WhatsAppDispatcher(mock(RestClient.class), props).channel())
                .isEqualTo(NotificationChannel.WHATSAPP);
    }
}
