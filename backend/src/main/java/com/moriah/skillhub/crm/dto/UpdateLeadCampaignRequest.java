package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadCampaignChannel;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code PUT /api/v1/leads/campaigns/{id}} (gap B1.7). Full-field replace, matching every other
 * {@code Update*Request}. {@code status} is settable here (e.g. PLANNED to ACTIVE to COMPLETED);
 * {@code DELETE} is the shortcut for {@code CANCELLED}. {@code leadIds} replaces the campaign's
 * whole audience wholesale, same as {@code name}/{@code channel}/etc. — {@code null} leaves the
 * audience untouched (distinguishing "didn't send an audience" from "clear it to empty" the same
 * way {@code UpdateResourceRequest.track}'s blank-vs-omitted split already does elsewhere). */
public record UpdateLeadCampaignRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LeadCampaignChannel channel,
        @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal budget,
        @Positive Integer targetLeads,
        @NotNull LeadCampaignStatus status,
        List<Long> leadIds
) {
}
