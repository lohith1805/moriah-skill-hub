package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;

import java.math.BigDecimal;
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
        BigDecimal dealValue,
        LeadStatus status,
        String assignedAgentUuid,
        String assignedAgentName,
        String lostReason,
        String convertedUserUuid,
        Instant nextFollowUpAt,
        Instant createdAt,
        Instant updatedAt
) {
}
