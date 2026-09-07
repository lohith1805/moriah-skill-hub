package com.moriah.skillhub.admin.dto;

/**
 * {@code POST /admin/exports/{report}?format=}. FRS MSH-FR-ADM-05 asks for Excel, PDF, and CSV —
 * all three now exist. {@code PDF} renders a simple bordered table (title, generated-at stamp,
 * header row, data rows) via the OpenPDF dependency already used for certificates/invoices/HR
 * letters, capped at {@code ExportGenerationService.MAX_PDF_ROWS} — a printable report is for a
 * human to skim or file, not a bulk data dump; XLSX/CSV stay the path for the full row count.
 * Defaults to {@code XLSX} when the caller omits the param, so the original caller shape keeps
 * working unchanged.
 */
public enum ExportFormat {
    XLSX,
    CSV,
    PDF
}
