package com.moriah.skillhub.admin.dto;

/** {@code v_lead_funnel} (architecture.md V12) rolled up by status only — the view itself is
 * per-agent-per-month, but the admin overview is a headline KPI, not a per-agent breakdown
 * (that's {@code SalesTargetResponse}'s job); the {@code SUM} across agent/month happens in SQL,
 * not Java (AGENTS.md: never recomputed in Java). */
public record LeadFunnelStageSummary(String status, long leadCount) {
}
