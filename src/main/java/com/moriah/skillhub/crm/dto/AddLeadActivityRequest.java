package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code lead_activities} logs what an agent did, real or system-driven. For {@link
 * LeadActivityType#WHATSAPP}, {@code templateCode} is required — build-plan.md: "WhatsApp
 * outbound uses pre-approved template codes outside the 24h session window" — and {@code
 * LeadService} actually dispatches it via {@code WhatsAppDispatcher.sendTemplate} after commit,
 * to the lead's own phone number; it is never free text (matches {@code WhatsAppDispatcher}'s own
 * template-only contract for every other channel in this codebase). {@code activityType} cannot
 * be {@link LeadActivityType#STATUS_CHANGE} or {@link LeadActivityType#WHATSAPP_INBOUND} — both
 * are written only by the server itself ({@code LeadService.updateStatus}, the inbound webhook).
 */
public record AddLeadActivityRequest(
        @NotNull LeadActivityType activityType,
        @Size(max = 100) String outcome,
        @Size(max = 2000) String notes,
        Instant nextFollowUpAt,
        @NotNull Instant occurredAt,
        @Size(max = 100) String templateCode
) {
    public AddLeadActivityRequest {
        if (activityType == LeadActivityType.STATUS_CHANGE || activityType == LeadActivityType.WHATSAPP_INBOUND) {
            throw new IllegalArgumentException("activityType " + activityType + " is written by the server only");
        }
        if (activityType == LeadActivityType.WHATSAPP && (templateCode == null || templateCode.isBlank())) {
            throw new IllegalArgumentException("templateCode is required when activityType is WHATSAPP");
        }
    }
}
