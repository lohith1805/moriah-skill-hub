package com.moriah.skillhub.crm.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.moriah.skillhub.crm.LeadDedupeHasher;
import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadActivity;
import com.moriah.skillhub.crm.entity.LeadActivityType;
import com.moriah.skillhub.crm.repository.LeadActivityRepository;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.payment.WebhookIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Meta's WhatsApp Cloud API inbound payload shape: {@code entry[].changes[].value.messages[]},
 * each with {@code from} (phone, no leading {@code +}), {@code id} (globally unique, the
 * idempotency key), {@code timestamp} (epoch seconds), and a type-specific body — only {@code
 * text.body} is read here, every other message type is logged with a placeholder note rather than
 * dropped, since a lead's reply still deserves a follow-up regardless of its media type.
 * <p>
 * {@code lead_activities} has no columns for template code or dispatch parameters (see {@link
 * com.moriah.skillhub.crm.dto.AddLeadActivityRequest}'s Javadoc) — inbound messages are logged the
 * same way an agent's own outreach is, as a plain activity row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookService {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WebhookIdempotencyService webhookIdempotencyService;

    @Transactional
    public void handleInbound(JsonNode payload, String rawBody) {
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    handleMessage(message, rawBody);
                }
            }
        }
    }

    private void handleMessage(JsonNode message, String rawBody) {
        String messageId = message.path("id").asText(null);
        if (messageId == null || messageId.isBlank()) {
            log.warn("[webhook/whatsapp] inbound message missing an id, skipping: {}", message);
            return;
        }
        if (!webhookIdempotencyService.claim("WHATSAPP", messageId, "message", rawBody)) {
            return;
        }

        String from = message.path("from").asText(null);
        if (from == null || from.isBlank()) {
            log.warn("[webhook/whatsapp] message {} has no 'from' phone number, skipping", messageId);
            return;
        }
        // Meta's `from` is already digits-only, no leading '+' — normalized defensively so this
        // matches leads.phone exactly as LeadService stores it (LeadDedupeHasher.normalizePhone).
        String normalizedFrom = LeadDedupeHasher.normalizePhone(from);

        leadRepository.findFirstByPhoneOrderByCreatedAtDesc(normalizedFrom).ifPresentOrElse(
                lead -> logActivity(lead, message),
                () -> log.info("[webhook/whatsapp] message {} from {} matches no known lead", messageId, from));
    }

    private void logActivity(Lead lead, JsonNode message) {
        String body = message.path("text").path("body").asText(null);
        String type = message.path("type").asText("unknown");
        String notes = body != null ? body : "[" + type + " message, no text body]";
        long timestampSeconds = message.path("timestamp").asLong(Instant.now().getEpochSecond());

        LeadActivity activity = new LeadActivity();
        activity.setLead(lead);
        activity.setActivityType(LeadActivityType.WHATSAPP_INBOUND);
        activity.setOutcome("INBOUND_REPLY");
        activity.setNotes(notes);
        activity.setOccurredAt(Instant.ofEpochSecond(timestampSeconds));
        leadActivityRepository.save(activity);
    }
}
