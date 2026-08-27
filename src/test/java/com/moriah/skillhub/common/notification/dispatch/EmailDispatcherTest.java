package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No real SendGrid account exists yet (`/architect feature 08` decision, same as Razorpay/Stripe)
 * — mocks the {@link SendGrid} client entirely, proving the request this class builds is
 * correct rather than exercising a real network call.
 */
@ExtendWith(MockitoExtension.class)
class EmailDispatcherTest {

    @Mock
    private SendGrid sendGridClient;

    private final EmailProperties props = new EmailProperties("test-key", "no-reply@test.example", "Test Sender");

    @Test
    void dispatch_success_sendsMailViaSendGridWithThePayloadsFields() throws Exception {
        Response response = new Response(202, "", Map.of());
        when(sendGridClient.api(any(Request.class))).thenReturn(response);

        EmailDispatcher dispatcher = new EmailDispatcher(sendGridClient, props);
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);

        dispatcher.dispatch(notification, Map.of(
                "to", "student@example.com",
                "subject", "Verify your email",
                "body", "Click here: http://localhost/verify?token=abc"));

        verify(sendGridClient).api(any(Request.class));
    }

    @Test
    void dispatch_sendGridReturnsErrorStatus_throws() throws Exception {
        Response response = new Response(400, "bad request", Map.of());
        when(sendGridClient.api(any(Request.class))).thenReturn(response);

        EmailDispatcher dispatcher = new EmailDispatcher(sendGridClient, props);
        Notification notification = new Notification();

        assertThatThrownBy(() -> dispatcher.dispatch(notification, Map.of(
                "to", "student@example.com", "subject", "x", "body", "y")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400");
    }

    @Test
    void dispatch_missingRequiredField_throwsBeforeCallingSendGrid() {
        EmailDispatcher dispatcher = new EmailDispatcher(sendGridClient, props);
        Notification notification = new Notification();

        assertThatThrownBy(() -> dispatcher.dispatch(notification, Map.of("to", "student@example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void channel_isEmail() {
        assertThat(new EmailDispatcher(sendGridClient, props).channel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
