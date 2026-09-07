package com.moriah.skillhub.common.notification.dispatch;

import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp Cloud API, plain REST — no SDK is on the approved dependency list. No real Meta
 * developer account exists yet; unit-tested against a mocked {@link RestClient}.
 * <p>
 * {@code notification.templateCode} is the pre-approved WhatsApp template name itself (never
 * free text outside the 24-hour session window — library-docs.md "Notification Dispatch"), not
 * a separate payload field. Payload contract: {@code to} (E.164 phone number), optional {@code
 * parameters} (list of strings substituted into the template body, in order).
 * <p>
 * {@link #sendTemplate} is the extracted core call, reused directly by {@code
 * crm.LeadService} (feature 18) for outbound WhatsApp to a {@code Lead} — a lead isn't a
 * {@code User}, so it has no {@code notifications.user_id} to route through {@link #dispatch}'s
 * normal queued/retried path; this is the same "inject the common infra bean directly when the
 * generic pipeline doesn't fit the caller's data shape" pattern as {@code SprintService} injecting
 * {@code BatchRepository} directly.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppDispatcher implements NotificationChannelDispatcher {

    private final RestClient whatsAppRestClient;
    private final WhatsAppProperties props;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void dispatch(Notification notification, Map<String, Object> payload) throws Exception {
        Object to = payload.get("to");
        if (to == null) {
            throw new IllegalStateException("Notification payload missing required field: to");
        }
        List<String> parameters = (List<String>) payload.getOrDefault("parameters", List.of());
        sendTemplate(notification.getTemplateCode(), to.toString(), parameters);
    }

    public void sendTemplate(String templateCode, String to, List<String> parameters) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateCode,
                        "language", Map.of("code", "en_US"),
                        "components", parameters.isEmpty() ? List.of() : List.of(Map.of(
                                "type", "body",
                                "parameters", parameters.stream()
                                        .map(p -> Map.of("type", "text", "text", p))
                                        .toList()))));

        whatsAppRestClient.post()
                .uri("/{phoneNumberId}/messages", props.phoneNumberId())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
