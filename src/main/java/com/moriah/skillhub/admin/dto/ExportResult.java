package com.moriah.skillhub.admin.dto;

/** Internal handoff between {@code ExportGenerationService} (the {@code @Async} worker) and
 * {@code ExportService} (the orchestrator that uploads/presigns) — never returned from a
 * controller. */
public record ExportResult(byte[] workbookBytes, int rowCount) {
}
