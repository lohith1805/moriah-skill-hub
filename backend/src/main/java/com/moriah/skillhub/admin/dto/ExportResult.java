package com.moriah.skillhub.admin.dto;

/** Internal handoff between {@code ExportGenerationService} (the {@code @Async} worker) and
 * {@code ExportService} (the orchestrator that uploads/presigns) — never returned from a
 * controller. {@code fileBytes} is a complete XLSX workbook or a complete CSV document depending
 * on which format {@code ExportGenerationService.generate} was asked for. */
public record ExportResult(byte[] fileBytes, int rowCount) {
}
