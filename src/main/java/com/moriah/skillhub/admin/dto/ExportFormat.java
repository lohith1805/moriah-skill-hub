package com.moriah.skillhub.admin.dto;

/**
 * {@code POST /admin/exports/{report}?format=}. FRS MSH-FR-ADM-05 asks for Excel, PDF, and CSV;
 * this adds CSV alongside the existing XLSX. PDF isn't included here — a tabular admin report
 * (users/revenue/audit rows) has no natural PDF layout the way a certificate or invoice does, and
 * nothing in this codebase has asked for one; XLSX/CSV cover every "hand this to a spreadsheet"
 * need the three report types actually have. Defaults to {@code XLSX} when the caller omits the
 * param, so every existing caller (there were none in the frontend before this) keeps working
 * unchanged.
 */
public enum ExportFormat {
    XLSX,
    CSV
}
