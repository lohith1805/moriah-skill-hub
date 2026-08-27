package com.moriah.skillhub.admin.dto;

/**
 * feature 22: {@code POST /admin/exports/{report}}. Three values, chosen because each maps
 * directly onto a query this same feature already builds for another endpoint — no report type
 * exists with no backing read (AGENTS.md: "don't invent report types with no backing query"):
 * {@code USERS} mirrors {@code GET /admin/users} (unpaginated), {@code REVENUE} mirrors {@code GET
 * /admin/metrics/revenue} (the full {@code v_revenue_monthly} history, not a range), {@code AUDIT}
 * mirrors {@code GET /admin/audit} (unpaginated).
 */
public enum ExportReport {
    USERS,
    REVENUE,
    AUDIT
}
