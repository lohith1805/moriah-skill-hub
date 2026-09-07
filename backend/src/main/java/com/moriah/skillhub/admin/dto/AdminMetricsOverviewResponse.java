package com.moriah.skillhub.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /admin/metrics/overview} — build-plan.md feature 22: "KPIs read from {@code
 * student_metrics}, {@code v_revenue_monthly}, {@code v_lead_funnel}, {@code v_batch_velocity} —
 * never recomputed in Java." Every field here is either a direct column/row from one of those four
 * sources, or a single division between two already-aggregated SQL totals ({@code
 * overallVelocityRatio}) — the same "divide two aggregates for display" precedent {@code
 * ClientProjectService}'s {@code milestoneCompletionFraction} already established at feature 21,
 * not a recomputation of the underlying attendance/task/quiz percentages themselves.
 */
public record AdminMetricsOverviewResponse(
        long activeStudentCount,
        BigDecimal avgAttendancePercent,
        BigDecimal avgTaskCompletionPercent,
        BigDecimal avgQuizAveragePercent,
        List<MonthlyRevenueSummary> recentRevenue,
        List<LeadFunnelStageSummary> leadFunnel,
        long totalPlannedPoints,
        long totalCompletedPoints,
        BigDecimal overallVelocityRatio
) {
}
