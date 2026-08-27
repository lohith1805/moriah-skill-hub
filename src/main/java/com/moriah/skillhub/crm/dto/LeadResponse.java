package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;

import java.time.Instant;

public record LeadResponse(
        Long id,
        String name,
        String email,
        String phone,
        LeadSource source,
        String leadType,
        String institution,
        Long interestedPlanId,
        LeadStatus status,
        String assignedAgentUuid,
        String assignedAgentName,
        String lostReason,
        String convertedUserUuid,
        Instant createdAt,
        Instant updatedAt
) {
}
