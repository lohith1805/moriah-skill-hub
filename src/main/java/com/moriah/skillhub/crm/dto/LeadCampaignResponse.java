package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadCampaignChannel;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A lead-gen campaign (gap B1.7). {@code leadIds} is the audience picked at creation (or a later
 * edit) — always a list, never {@code null}, empty when nobody's been picked yet. */
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
        List<Long> leadIds,
        String createdByUuid,
        Instant createdAt,
        Instant updatedAt
) {
}
