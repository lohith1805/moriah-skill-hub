package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadActivityType;

import java.time.Instant;

public record LeadActivityResponse(
        Long id,
        Long leadId,
        String agentUuid,
        String agentName,
        LeadActivityType activityType,
        String outcome,
        String notes,
        Instant nextFollowUpAt,
        Instant occurredAt
) {
}
