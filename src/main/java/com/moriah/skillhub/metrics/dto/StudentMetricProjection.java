package com.moriah.skillhub.metrics.dto;

import java.math.BigDecimal;

/** Every column {@code pip.engine}'s six evaluators and {@code PipService}'s clearance check read
 * — {@code StudentMetricsService}'s bulk/single-user read side, backing feature 17 without it
 * touching {@code StudentMetricRepository}/{@code StudentMetric} directly (architecture.md: "a
 * feature module may call another module's service interface, never its repository or entity"). */
public record StudentMetricProjection(
        Long userId,
        Long batchId,
        BigDecimal attendancePercent,
        Integer tasksOverdue48h,
        BigDecimal taskCompletionPercent,
        Integer consecutiveAssignmentsMissed,
        BigDecimal quizAveragePercent,
        Integer unsatisfactoryReviews,
        Integer daysSinceLastActivity) {
}
