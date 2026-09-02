package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadCampaignChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code POST /api/v1/leads/campaigns} (gap B1.7). {@code status} is not accepted here — a new
 * campaign always starts {@code PLANNED} and is advanced via {@code PUT}. */
public record CreateLeadCampaignRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LeadCampaignChannel channel,
        @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal budget,
        @Positive Integer targetLeads
) {
}
