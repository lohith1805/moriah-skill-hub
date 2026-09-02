package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadCampaignChannel;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A lead-gen campaign (gap B1.7). */
public record LeadCampaignResponse(
        Long id,
        String name,
        LeadCampaignChannel channel,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        Integer targetLeads,
        LeadCampaignStatus status,
        String createdByUuid,
        Instant createdAt,
        Instant updatedAt
) {
}
