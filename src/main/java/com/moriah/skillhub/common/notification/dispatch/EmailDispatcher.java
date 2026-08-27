package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * No real SendGrid account exists yet — same no-real-credentials situation as Razorpay/Stripe/
 * OAuth2. Unit-tested against a mocked {@link SendGrid} client, not a real network call.
 * <p>
 * Payload contract (built entirely by the caller — {@code common/notification} never templates
 * anything itself): {@code to} (recipient email), {@code subject}, {@code body} (plain text).
 * Every current consumer (email verification, password reset) builds these directly since each
 * is a one-off link, not a reusable multi-variable template.
 */
@Component
@RequiredArgsConstructor
public class EmailDispatcher implements NotificationChannelDispatcher {

    private final SendGrid sendGridClient;
    private final EmailProperties props;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void dispatch(Notification notification, Map<String, Object> payload) throws Exception {
        String to = requireString(payload, "to");
        String subject = requireString(payload, "subject");
        String body = requireString(payload, "body");

        Email from = new Email(props.fromAddress(), props.fromName());
        Mail mail = new Mail(from, subject, new Email(to), new Content("text/plain", body));

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGridClient.api(request);
        if (response.getStatusCode() >= 300) {
            throw new IllegalStateException(
                    "SendGrid returned status " + response.getStatusCode() + ": " + response.getBody());
        }
    }

    private String requireString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalStateException("Notification payload missing required field: " + key);
        }
        return value.toString();
    }
}
