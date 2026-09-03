package com.moriah.skillhub.crm.dto;

import java.math.BigDecimal;

/**
 * One row of {@code GET /api/v1/leads/targets/leaderboard} — a per-agent standings view for the
 * current month. The lead-derived figures ({@code totalLeads}, {@code converted}, {@code
 * pipelineValue}) are computed live from {@code leads}; the {@code *Target} / {@code callsMade}
 * figures come from that agent's {@code sales_targets} row for the current month and are {@code
 * null} when no target has been set. {@code sales_targets} has no create/update API in feature
 * 18's scope, so those stay read-only.
 */
public record SalesLeaderboardRowResponse(
        String agentUuid,
        String agentName,
        long totalLeads,
        long converted,
        BigDecimal pipelineValue,
        Integer callsTarget,
        Integer callsMade,
        Integer conversionsTarget,
        Integer conversionsMade,
        BigDecimal revenueTarget,
        BigDecimal revenueAchieved
) {
}
