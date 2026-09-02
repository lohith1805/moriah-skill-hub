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
 * <p>
 * <b>Audit 2026-08-31 (C3 / I5):</b> {@code handleInbound} used to be one transaction wrapping
 * every message, each calling {@code claim()} (which commits in {@code REQUIRES_NEW}) and then
 * possibly returning early or throwing — so a later message's failure rolled back the earlier
 * activity rows while their claims stayed committed, and Meta's redelivery then skipped them for
 * good. Now each message is its own unit: claim → {@link WebhookIdempotencyService#runAndMarkProcessed}
 * (activity row + {@code PROCESSED} flip in one transaction) → on failure the claim is released
 * and the delivery is failed so Meta redelivers only the messages that did not complete.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookService {

    private static final String GATEWAY = "WHATSAPP";

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final WebhookIdempotencyService webhookIdempotencyService;

    public void handleInbound(JsonNode payload, String rawBody) {
        RuntimeException firstFailure = null;
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode message : change.path("value").path("messages")) {
                    try {
                        handleMessage(message, rawBody);
                    } catch (RuntimeException e) {
                        // Keep processing the rest of the batch; remember the first failure so the
                        // whole delivery is failed at the end and Meta redelivers.
                        log.error("[webhook/whatsapp] failed to process a message in the batch", e);
                        if (firstFailure == null) {
                            firstFailure = e;
                        }
                    }
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void handleMessage(JsonNode message, String rawBody) {
        String messageId = message.path("id").asText(null);
        if (messageId == null || messageId.isBlank()) {
            log.warn("[webhook/whatsapp] inbound message missing an id, skipping: {}", message);
            return;
        }
        if (!webhookIdempotencyService.claim(GATEWAY, messageId, "message", rawBody)) {
            return;
        }
        try {
            webhookIdempotencyService.runAndMarkProcessed(GATEWAY, messageId, () -> persistInbound(message, messageId));
        } catch (RuntimeException e) {
            webhookIdempotencyService.releaseClaim(GATEWAY, messageId);
            throw e;
        }
    }

    private void persistInbound(JsonNode message, String messageId) {
        String from = message.path("from").asText(null);
        if (from == null || from.isBlank()) {
            log.warn("[webhook/whatsapp] message {} has no 'from' phone number, nothing to log", messageId);
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
