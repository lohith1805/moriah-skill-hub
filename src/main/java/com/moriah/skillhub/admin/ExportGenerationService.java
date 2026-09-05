package com.moriah.skillhub.admin;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.admin.dto.ExportFormat;
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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code @Async} half of {@code POST /admin/exports/{report}} — a separate bean from {@code
 * ExportService} for the same reason {@code EntitlementFlagsLoader} is separate from {@code
 * EntitlementGuard}: {@code @Async} is proxy-based AOP exactly like {@code @Cacheable}, and a
 * method calling another {@code @Async} method on {@code this} within the same class bypasses the
 * proxy entirely, silently running synchronously. {@code ExportService} (a different bean) calls
 * across the proxy correctly.
 * <p>
 * build-plan.md feature 22: "Exports run {@code @Async} via {@code SXSSFWorkbook}" — now joined by
 * CSV and PDF paths (FRS MSH-FR-ADM-05: "Excel, PDF, and CSV") that all reuse the exact same
 * {@link ReportSpec} (SQL, headers, row mapper) per report, so no format can ever drift into
 * showing different columns or rows for what's nominally "the same export." PDF renders via
 * OpenPDF (already a dependency — certificates/invoices/HR letters use it), capped at {@link
 * #MAX_PDF_ROWS}: unlike XLSX/CSV, a PDF is meant to be read or printed, not opened as a data
 * file, so rendering the full {@link #MAX_EXPORT_ROWS} into one document would produce something
 * nobody could use. Runs on the dedicated {@code exportExecutor} pool (audit 2026-08-31, H1) so a
 * slow export can't starve invoice generation.
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
 * live at a time on both the JDBC and POI sides) or straight into the CSV {@code Writer}, and every
 * query is hard-capped at {@link #MAX_EXPORT_ROWS}.
 */
@Service
@Slf4j
public class ExportGenerationService {

    /** Hard ceiling on any single export. USERS at ~10k and REVENUE (monthly rollup) are well
     * under this; AUDIT is genuinely unbounded in the table, so this truncates it to the newest
     * rows and logs when it bites. */
    static final int MAX_EXPORT_ROWS = 200_000;

    /** Hard ceiling on rows actually rendered into a PDF table — well below {@link
     * #MAX_EXPORT_ROWS}. A printed/skimmed report beyond a couple thousand rows stops being
     * something a person reads; past this point {@code buildPdf} still counts every row the query
     * returns (for an honest {@code rowCount}/truncation note) but stops adding table cells. */
    static final int MAX_PDF_ROWS = 2_000;

    private final JdbcTemplate replicaJdbcTemplate;

    public ExportGenerationService(@Qualifier("replicaJdbcTemplate") JdbcTemplate replicaJdbcTemplate) {
        this.replicaJdbcTemplate = replicaJdbcTemplate;
    }

    /** code-standards.md "Async and Scheduled Work": "{@code @Async} methods... have their own
     * try/catch — an exception in an async method is otherwise silently swallowed." A failed
     * future propagates to {@code ExportService}'s {@code .get()} as an {@code
     * ExecutionException}, which it converts to {@code EXPORT_GENERATION_FAILED}. */
    @Async("exportExecutor")
    public CompletableFuture<ExportResult> generate(ExportReport report, ExportFormat format) {
        try {
            ReportSpec spec = specFor(report);
            ExportResult result = switch (format) {
                case CSV -> buildCsv(spec);
                case PDF -> buildPdf(spec);
                case XLSX -> buildWorkbook(report, spec);
            };
            if (report == ExportReport.AUDIT && result.rowCount() == MAX_EXPORT_ROWS) {
                log.warn("[admin/export] AUDIT export truncated at the {}-row cap — older audit_logs rows "
                        + "are not included; add a date-range filter to this endpoint if full history is needed",
                        MAX_EXPORT_ROWS);
            }
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[admin/export] failed to generate {} {} export", report, format, e);
            CompletableFuture<ExportResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Per-report query specs — the single source of truth both formats stream from.
    // ---------------------------------------------------------------------------------------

    private ReportSpec specFor(ExportReport report) {
        return switch (report) {
            case USERS -> usersSpec();
            case REVENUE -> revenueSpec();
            case AUDIT -> auditSpec();
        };
    }

    private ReportSpec usersSpec() {
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
        return new ReportSpec("Users", new String[] { "UUID", "Full Name", "Email", "Status", "Created At", "Roles" },
                -1, sql,
                rs -> new Object[] {
                        rs.getString("uuid"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("status"), rs.getTimestamp("created_at"), rs.getString("roles") });
    }

    private ReportSpec revenueSpec() {
        String sql = """
                SELECT revenue_month, currency, total_captured
                  FROM v_revenue_monthly
                 ORDER BY revenue_month
                 LIMIT %d
                """.formatted(MAX_EXPORT_ROWS);
        return new ReportSpec("Revenue", new String[] { "Month", "Currency", "Total Captured" }, 2, sql,
                rs -> new Object[] {
                        rs.getString("revenue_month"), rs.getString("currency"), rs.getBigDecimal("total_captured") });
    }

    private ReportSpec auditSpec() {
        // old_value/new_value JSON blobs are deliberately excluded — a cell holding an
        // arbitrary-length JSON document is a poor fit for a spreadsheet/CSV column; the full
        // detail is available through GET /admin/audit for any one row. "Entity Ref" is the
        // target user's uuid when entity_type = 'User', otherwise entity_id — never a raw users.id.
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
        return new ReportSpec("Audit",
                new String[] { "ID", "Actor UUID", "Action", "Entity Type", "Entity Ref", "IP Address", "Created At" },
                -1, sql,
                rs -> new Object[] {
                        rs.getLong("id"), rs.getString("actor_uuid"), rs.getString("action"),
                        rs.getString("entity_type"), rs.getString("entity_ref"), rs.getString("ip_address"),
                        rs.getTimestamp("created_at") });
    }

    // ---------------------------------------------------------------------------------------
    // XLSX
    // ---------------------------------------------------------------------------------------

    private ExportResult buildWorkbook(ExportReport report, ReportSpec spec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rowCount;
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            rowCount = streamSheet(workbook, spec);
            workbook.write(out);
            // try-with-resources calls close(), which disposes the streaming temp files in
            // POI 5.x — the old explicit dispose() is deprecated.
        }
        return new ExportResult(out.toByteArray(), rowCount);
    }

    /**
     * Streams {@code spec.sql} straight into a new sheet, one row at a time, without ever holding
     * the full result set in memory. Returns the number of data rows written.
     */
    private int streamSheet(SXSSFWorkbook workbook, ReportSpec spec) {
        SXSSFSheet sheet = workbook.createSheet(spec.sheetName());
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < spec.headers().length; c++) {
            headerRow.createCell(c).setCellValue(spec.headers()[c]);
        }

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

        int[] rowNum = { 0 };
        RowCallbackHandler intoSheet = rs -> {
            Object[] values = spec.rowMapper().map(rs);
            Row row = sheet.createRow(++rowNum[0]);
            for (int c = 0; c < values.length; c++) {
                Cell cell = row.createCell(c);
                setCellValue(cell, values[c]);
                if (c == spec.moneyColumnIndex()) {
                    cell.setCellStyle(moneyStyle);
                }
            }
        };
        streamQuery(spec.sql(), intoSheet);
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

    // ---------------------------------------------------------------------------------------
    // CSV — same spec, same streaming-cursor approach, RFC 4180 quoting.
    // ---------------------------------------------------------------------------------------

    private ExportResult buildCsv(ReportSpec spec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // UTF-8 BOM so Excel (still the most common opener for a "download as CSV" link) detects
        // the encoding instead of mis-rendering non-ASCII names/emails as mojibake.
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);
        int[] rowNum = { 0 };
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeCsvRow(writer, (Object[]) spec.headers());
            RowCallbackHandler intoCsv = rs -> {
                Object[] values = spec.rowMapper().map(rs);
                Object[] formatted = new Object[values.length];
                for (int c = 0; c < values.length; c++) {
                    formatted[c] = formatCsvValue(values[c]);
                }
                // RowCallbackHandler#processRow only declares SQLException — writeCsvRow's
                // IOException (an in-memory ByteArrayOutputStream/Writer, so realistically never
                // actually thrown) is rethrown unchecked rather than silently swallowed.
                try {
                    writeCsvRow(writer, formatted);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                rowNum[0]++;
            };
            streamQuery(spec.sql(), intoCsv);
        }
        return new ExportResult(out.toByteArray(), rowNum[0]);
    }

    private Object formatCsvValue(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal bigDecimal -> bigDecimal.toPlainString();
            case Timestamp timestamp -> timestamp.toInstant().toString();
            default -> sanitiseCell(value.toString());
        };
    }

    private void writeCsvRow(Writer writer, Object[] values) throws IOException {
        for (int c = 0; c < values.length; c++) {
            if (c > 0) {
                writer.write(',');
            }
            writer.write(csvEscape(String.valueOf(values[c])));
        }
        writer.write("\r\n");
    }

    /** RFC 4180: a field is quoted (with internal quotes doubled) whenever it contains a comma,
     * quote, or line break — quoting everything else too would be legal but noisier to read raw. */
    private String csvEscape(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ---------------------------------------------------------------------------------------
    // PDF — same spec, same streaming-cursor approach, capped at MAX_PDF_ROWS for a document a
    // person could actually read or print. OpenPDF (com.lowagie.text) — already a dependency,
    // the same one certificates/invoices/HR letters render through.
    // ---------------------------------------------------------------------------------------

    private static final Font PDF_TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font PDF_SUBTITLE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font PDF_HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font PDF_BODY_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font PDF_NOTE_FONT = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.DARK_GRAY);
    private static final Color PDF_HEADER_BG = new Color(0x0D, 0x28, 0x45);

    private ExportResult buildPdf(ReportSpec spec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Landscape — every report here is wider than it is tall (up to seven columns for AUDIT),
        // and a printable admin report has no reason to fight a portrait page for column width.
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 40, 28);
        int[] rowNum = { 0 };
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(spec.sheetName() + " Export", PDF_TITLE_FONT));
            doc.add(new Paragraph("Moriah Skill Hub · Generated " + Instant.now(), PDF_SUBTITLE_FONT));
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(spec.headers().length);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : spec.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(header, PDF_HEADER_FONT));
                cell.setBackgroundColor(PDF_HEADER_BG);
                cell.setPadding(4f);
                table.addCell(cell);
            }

            RowCallbackHandler intoPdf = rs -> {
                Object[] values = spec.rowMapper().map(rs);
                rowNum[0]++;
                // Still counted above for an honest rowCount/truncation note — just not rendered
                // past the cap, so the PDF itself never grows past something printable.
                if (rowNum[0] > MAX_PDF_ROWS) {
                    return;
                }
                for (Object value : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(formatPdfValue(value), PDF_BODY_FONT));
                    cell.setPadding(3f);
                    table.addCell(cell);
                }
            };
            streamQuery(spec.sql(), intoPdf);
            doc.add(table);

            if (rowNum[0] > MAX_PDF_ROWS) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(
                        "Showing the first %,d of %,d rows — use the XLSX or CSV export for the complete data set."
                                .formatted(MAX_PDF_ROWS, rowNum[0]),
                        PDF_NOTE_FONT));
            }
            doc.close();
        } catch (DocumentException e) {
            throw new IOException("PDF export rendering failed", e);
        }
        return new ExportResult(out.toByteArray(), rowNum[0]);
    }

    private String formatPdfValue(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal bigDecimal -> bigDecimal.toPlainString();
            case Timestamp timestamp -> timestamp.toInstant().toString();
            default -> value.toString();
        };
    }

    // ---------------------------------------------------------------------------------------
    // Shared streaming cursor
    // ---------------------------------------------------------------------------------------

    private void streamQuery(String sql, RowCallbackHandler handler) {
        replicaJdbcTemplate.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            // MySQL Connector/J: row-by-row streaming instead of buffering the whole result set
            // client-side.
            ps.setFetchSize(Integer.MIN_VALUE);
            return ps;
        }, handler);
    }

    /** Audit 2026-08-31 (L6): neutralise spreadsheet/CSV formula injection (CWE-1236) — a value
     * starting with =, +, -, @, or a control char is prefixed with an apostrophe so Excel and
     * downstream CSV tools treat it as literal text. Shared by both formats — the CSV path is
     * exactly the "downstream CSV tools" this guard already names. */
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

    /** One report's shape, independent of output format: sheet/file name, column headers, which
     * column (if any) gets money formatting in XLSX, the query, and how a result-set row becomes a
     * row of cell values. */
    private record ReportSpec(String sheetName, String[] headers, int moneyColumnIndex, String sql, RowMapper rowMapper) {
    }
}
