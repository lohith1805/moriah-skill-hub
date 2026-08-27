package com.moriah.skillhub.admin.dto;

import java.math.BigDecimal;

/** One row of {@code v_revenue_monthly} (architecture.md V9), read verbatim — never recomputed
 * in Java (AGENTS.md: "KPIs read from... never recomputed in Java"). */
public record MonthlyRevenueSummary(String revenueMonth, String currency, BigDecimal totalCaptured) {
}
