package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ExportReport;
import com.moriah.skillhub.admin.dto.ExportResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code @Async} half of {@code POST /admin/exports/{report}} — a separate bean from {@code
 * ExportService} for the same reason {@code EntitlementFlagsLoader} is separate from {@code
 * EntitlementGuard}: {@code @Async} is proxy-based AOP exactly like {@code @Cacheable}, and a
 * method calling another {@code @Async} method on {@code this} within the same class bypasses the
 * proxy entirely, silently running synchronously. {@code ExportService} (a different bean) calls
 * across the proxy correctly.
 * <p>
 * build-plan.md feature 22: "Exports run {@code @Async} via {@code SXSSFWorkbook}" — the point of
 * {@code @Async} here is not fire-and-forget (the caller still waits on the returned future — see
 * {@code ExportService}), it's keeping the potentially-large replica read and the streaming
 * workbook write off the original HTTP request thread, matching the brief's own framing.
 * <p>
 * "target the read replica, so a 50,000-row XLSX export never touches the primary" — enforced by
 * construction: this class autowires only the {@code replicaJdbcTemplate}-qualified bean, never
 * the default one.
 */
@Service
@Slf4j
public class ExportGenerationService {

    private final JdbcTemplate replicaJdbcTemplate;

    public ExportGenerationService(@Qualifier("replicaJdbcTemplate") JdbcTemplate replicaJdbcTemplate) {
        this.replicaJdbcTemplate = replicaJdbcTemplate;
    }

    /** code-standards.md "Async and Scheduled Work": "{@code @Async} methods... have their own
     * try/catch — an exception in an async method is otherwise silently swallowed." A failed
     * future propagates to {@code ExportService}'s {@code .get()} as an {@code
     * ExecutionException}, which it converts to {@code EXPORT_GENERATION_FAILED}. */
    @Async
    public CompletableFuture<ExportResult> generate(ExportReport report) {
        try {
            return CompletableFuture.completedFuture(buildWorkbook(report));
        } catch (Exception e) {
            log.error("[admin/export] failed to generate {} export", report, e);
            CompletableFuture<ExportResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    private ExportResult buildWorkbook(ExportReport report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rowCount;
        // library-docs.md "Apache POI": SXSSFWorkbook(100) — streaming, 100 rows in memory — not
        // XSSFWorkbook, so a 50,000-row export never exhausts heap.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            rowCount = switch (report) {
                case USERS -> writeUsersSheet(workbook);
                case REVENUE -> writeRevenueSheet(workbook);
                case AUDIT -> writeAuditSheet(workbook);
            };
            workbook.write(out);
            workbook.dispose();
        }
        return new ExportResult(out.toByteArray(), rowCount);
    }

    private int writeUsersSheet(SXSSFWorkbook workbook) {
        List<Object[]> rows = replicaJdbcTemplate.query("""
                SELECT u.uuid, u.full_name, u.email, u.status, u.created_at,
                       COALESCE(GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ','), '') AS roles
                  FROM users u
                  LEFT JOIN user_roles ur ON ur.user_id = u.id
                  LEFT JOIN roles r ON r.id = ur.role_id
                 GROUP BY u.id, u.uuid, u.full_name, u.email, u.status, u.created_at
                 ORDER BY u.id
                """, (rs, rowNum) -> new Object[] {
                rs.getString("uuid"), rs.getString("full_name"), rs.getString("email"),
                rs.getString("status"), rs.getTimestamp("created_at"), rs.getString("roles")
        });
        writeSheet(workbook, "Users",
                new String[] { "UUID", "Full Name", "Email", "Status", "Created At", "Roles" }, rows, -1);
        return rows.size();
    }

    private int writeRevenueSheet(SXSSFWorkbook workbook) {
        List<Object[]> rows = replicaJdbcTemplate.query("""
                SELECT revenue_month, currency, total_captured
                  FROM v_revenue_monthly
                 ORDER BY revenue_month
                """, (rs, rowNum) -> new Object[] {
                rs.getString("revenue_month"), rs.getString("currency"), rs.getBigDecimal("total_captured")
        });
        writeSheet(workbook, "Revenue", new String[] { "Month", "Currency", "Total Captured" }, rows, 2);
        return rows.size();
    }

    private int writeAuditSheet(SXSSFWorkbook workbook) {
        // old_value/new_value JSON blobs are deliberately excluded from the sheet — a cell
        // holding an arbitrary-length JSON document is a poor fit for a spreadsheet column an
        // admin filters and sorts (library-docs.md "Format money cells... accountants filter and
        // sum these" — the same "this is for spreadsheet consumption" reasoning applies here);
        // the full detail is still available through GET /admin/audit for any one row.
        //
        // "Entity Ref" is entity_id for every non-User entity type, or the target user's uuid
        // when entity_type = 'User' — never that row's raw users.id (architecture.md: "No
        // endpoint exposes users.id"). Same resolution AuditQueryService's own SQL uses, and for
        // the same reason: this is the one entity type that column can hold a real users.id for.
        List<Object[]> rows = replicaJdbcTemplate.query("""
                SELECT a.id, actor.uuid AS actor_uuid, a.action, a.entity_type,
                       CASE WHEN a.entity_type = 'User' THEN target.uuid ELSE CAST(a.entity_id AS CHAR) END AS entity_ref,
                       a.ip_address, a.created_at
                  FROM audit_logs a
                  LEFT JOIN users actor ON actor.id = a.user_id
                  LEFT JOIN users target ON a.entity_type = 'User' AND target.id = a.entity_id
                 ORDER BY a.created_at DESC
                """, (rs, rowNum) -> new Object[] {
                rs.getLong("id"), rs.getString("actor_uuid"), rs.getString("action"),
                rs.getString("entity_type"), rs.getString("entity_ref"), rs.getString("ip_address"),
                rs.getTimestamp("created_at")
        });
        writeSheet(workbook, "Audit",
                new String[] { "ID", "Actor UUID", "Action", "Entity Type", "Entity Ref", "IP Address", "Created At" },
                rows, -1);
        return rows.size();
    }

    /** @param moneyColumnIndex 0-based column to apply a numeric data format to (library-docs.md
     *                          "Format money cells with a data format, never as pre-formatted
     *                          strings — accountants filter and sum these"), or {@code -1} if the
     *                          sheet has no money column. */
    private void writeSheet(SXSSFWorkbook workbook, String sheetName, String[] headers, List<Object[]> rows, int moneyColumnIndex) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        int rowNum = 1;
        for (Object[] rowData : rows) {
            Row row = sheet.createRow(rowNum++);
            for (int c = 0; c < rowData.length; c++) {
                Cell cell = row.createCell(c);
                setCellValue(cell, rowData[c]);
                if (c == moneyColumnIndex) {
                    cell.setCellStyle(moneyStyle);
                }
            }
        }
    }

    private void setCellValue(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal bigDecimal -> cell.setCellValue(bigDecimal.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            case Timestamp timestamp -> cell.setCellValue(timestamp.toInstant().toString());
            default -> cell.setCellValue(value.toString());
        }
    }
}
