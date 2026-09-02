package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ExportReport;
import com.moriah.skillhub.admin.dto.ExportResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code @Async} half of {@code POST /admin/exports/{report}} — a separate bean from {@code
 * ExportService} for the same reason {@code EntitlementFlagsLoader} is separate from {@code
 * EntitlementGuard}: {@code @Async} is proxy-based AOP exactly like {@code @Cacheable}, and a
 * method calling another {@code @Async} method on {@code this} within the same class bypasses the
 * proxy entirely, silently running synchronously. {@code ExportService} (a different bean) calls
 * across the proxy correctly.
 * <p>
 * build-plan.md feature 22: "Exports run {@code @Async} via {@code SXSSFWorkbook}". Runs on the
 * dedicated {@code exportExecutor} pool (audit 2026-08-31, H1) so a slow export can't starve
 * invoice generation.
 * <p>
 * "target the read replica, so a 50,000-row XLSX export never touches the primary" — enforced by
 * construction: this class autowires only the {@code replicaJdbcTemplate}-qualified bean, never
 * the default one.
 * <p>
 * <b>Audit 2026-08-31 (H2):</b> each sheet builder used to run {@code replicaJdbcTemplate.query(...)}
 * into a fully-materialised {@code List<Object[]>} <i>before</i> a single row reached the
 * workbook, defeating {@code SXSSFWorkbook}'s streaming and — for the unbounded {@code audit_logs}
 * read — risking a multi-hundred-MB heap spike on an async thread. Rows now stream straight from
 * a forward-only, {@code fetchSize=Integer.MIN_VALUE} cursor into the {@code SXSSFSheet} (100 rows
 * live at a time on both the JDBC and POI sides), and every query is hard-capped at {@link
 * #MAX_EXPORT_ROWS}.
 */
@Service
@Slf4j
public class ExportGenerationService {

    /** Hard ceiling on any single export. USERS at ~10k and REVENUE (monthly rollup) are well
     * under this; AUDIT is genuinely unbounded in the table, so this truncates it to the newest
     * rows and logs when it bites. */
    static final int MAX_EXPORT_ROWS = 200_000;

    private final JdbcTemplate replicaJdbcTemplate;

    public ExportGenerationService(@Qualifier("replicaJdbcTemplate") JdbcTemplate replicaJdbcTemplate) {
        this.replicaJdbcTemplate = replicaJdbcTemplate;
    }

    /** code-standards.md "Async and Scheduled Work": "{@code @Async} methods... have their own
     * try/catch — an exception in an async method is otherwise silently swallowed." A failed
     * future propagates to {@code ExportService}'s {@code .get()} as an {@code
     * ExecutionException}, which it converts to {@code EXPORT_GENERATION_FAILED}. */
    @Async("exportExecutor")
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
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            rowCount = switch (report) {
                case USERS -> writeUsersSheet(workbook);
                case REVENUE -> writeRevenueSheet(workbook);
                case AUDIT -> writeAuditSheet(workbook);
            };
            workbook.write(out);
            // try-with-resources calls close(), which disposes the streaming temp files in
            // POI 5.x — the old explicit dispose() is deprecated.
        }
        return new ExportResult(out.toByteArray(), rowCount);
    }

    private int writeUsersSheet(SXSSFWorkbook workbook) {
        String sql = """
                SELECT u.uuid, u.full_name, u.email, u.status, u.created_at,
                       COALESCE(GROUP_CONCAT(r.code ORDER BY r.code SEPARATOR ','), '') AS roles
                  FROM users u
                  LEFT JOIN user_roles ur ON ur.user_id = u.id
                  LEFT JOIN roles r ON r.id = ur.role_id
                 GROUP BY u.id, u.uuid, u.full_name, u.email, u.status, u.created_at
                 ORDER BY u.id
                 LIMIT %d
                """.formatted(MAX_EXPORT_ROWS);
        return streamSheet(workbook, "Users",
                new String[] { "UUID", "Full Name", "Email", "Status", "Created At", "Roles" }, -1, sql,
                rs -> new Object[] {
                        rs.getString("uuid"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("status"), rs.getTimestamp("created_at"), rs.getString("roles") });
    }

    private int writeRevenueSheet(SXSSFWorkbook workbook) {
        String sql = """
                SELECT revenue_month, currency, total_captured
                  FROM v_revenue_monthly
                 ORDER BY revenue_month
                 LIMIT %d
                """.formatted(MAX_EXPORT_ROWS);
        return streamSheet(workbook, "Revenue", new String[] { "Month", "Currency", "Total Captured" }, 2, sql,
                rs -> new Object[] {
                        rs.getString("revenue_month"), rs.getString("currency"), rs.getBigDecimal("total_captured") });
    }

    private int writeAuditSheet(SXSSFWorkbook workbook) {
        // old_value/new_value JSON blobs are deliberately excluded — a cell holding an
        // arbitrary-length JSON document is a poor fit for a spreadsheet column; the full detail
        // is available through GET /admin/audit for any one row. "Entity Ref" is the target
        // user's uuid when entity_type = 'User', otherwise entity_id — never a raw users.id.
        String sql = """
                SELECT a.id, actor.uuid AS actor_uuid, a.action, a.entity_type,
                       CASE WHEN a.entity_type = 'User' THEN target.uuid ELSE CAST(a.entity_id AS CHAR) END AS entity_ref,
                       a.ip_address, a.created_at
                  FROM audit_logs a
                  LEFT JOIN users actor ON actor.id = a.user_id
                  LEFT JOIN users target ON a.entity_type = 'User' AND target.id = a.entity_id
                 ORDER BY a.created_at DESC
                 LIMIT %d
                """.formatted(MAX_EXPORT_ROWS);
        int written = streamSheet(workbook, "Audit",
                new String[] { "ID", "Actor UUID", "Action", "Entity Type", "Entity Ref", "IP Address", "Created At" },
                -1, sql,
                rs -> new Object[] {
                        rs.getLong("id"), rs.getString("actor_uuid"), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_ref"), rs.getString("ip_address"),
                        rs.getTimestamp("created_at") });
        if (written == MAX_EXPORT_ROWS) {
            log.warn("[admin/export] AUDIT export truncated at the {}-row cap — older audit_logs rows "
                    + "are not included; add a date-range filter to this endpoint if full history is needed",
                    MAX_EXPORT_ROWS);
        }
        return written;
    }

    /**
     * Streams {@code sql} straight into a new sheet, one row at a time, without ever holding the
     * full result set in memory. Returns the number of data rows written.
     */
    private int streamSheet(SXSSFWorkbook workbook, String sheetName, String[] headers, int moneyColumnIndex,
            String sql, RowMapper rowMapper) {
        SXSSFSheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        int[] rowNum = { 0 };
        RowCallbackHandler intoSheet = rs -> {
            Object[] values = rowMapper.map(rs);
            Row row = sheet.createRow(++rowNum[0]);
            for (int c = 0; c < values.length; c++) {
                Cell cell = row.createCell(c);
                setCellValue(cell, values[c]);
                if (c == moneyColumnIndex) {
                    cell.setCellStyle(moneyStyle);
                }
            }
        };
        replicaJdbcTemplate.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            // MySQL Connector/J: row-by-row streaming instead of buffering the whole result set
            // client-side.
            ps.setFetchSize(Integer.MIN_VALUE);
            return ps;
        }, intoSheet);
        return rowNum[0];
    }

    private void setCellValue(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal bigDecimal -> cell.setCellValue(bigDecimal.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            case Timestamp timestamp -> cell.setCellValue(timestamp.toInstant().toString());
            default -> cell.setCellValue(sanitiseCell(value.toString()));
        }
    }

    /** Audit 2026-08-31 (L6): neutralise spreadsheet/CSV formula injection (CWE-1236) — a value
     * starting with =, +, -, @, or a control char is prefixed with an apostrophe so Excel and
     * downstream CSV tools treat it as literal text. */
    private String sanitiseCell(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    @FunctionalInterface
    private interface RowMapper {
        Object[] map(ResultSet rs) throws java.sql.SQLException;
    }
}
