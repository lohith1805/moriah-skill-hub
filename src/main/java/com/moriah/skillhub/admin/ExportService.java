package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ExportFormat;
import com.moriah.skillhub.admin.dto.ExportReport;
import com.moriah.skillhub.admin.dto.ExportResponse;
import com.moriah.skillhub.admin.dto.ExportResult;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * {@code POST /admin/exports/{report}?format=} — the synchronous orchestrator. Calls across to
 * {@link ExportGenerationService} (a different bean, required for {@code @Async} to actually
 * apply — see its Javadoc) and blocks on the returned future before responding: build-plan.md's
 * own framing is "run the read/workbook-build off the request thread", not "fire-and-forget with
 * no response" — there is no polling endpoint in this feature's scope, so the caller has to get
 * the answer from this one request.
 * <p>
 * Delivery: no new table exists to track export job state (this feature ships no migration —
 * every view/table it reads already exists), and build-plan.md's endpoint list has exactly one
 * export endpoint, with no separate status/download endpoint. Given that constraint, this always
 * uploads via {@link StorageService#uploadTrusted} to {@code exports/{report}/{uuid}.{ext}} and
 * returns a presigned URL — for &gt;1,000 rows because build-plan.md says so explicitly
 * ("delivered by presigned URL above 1,000 rows"), and for &le;1,000 rows too, as a documented
 * simplification: this codebase has no existing precedent anywhere for returning raw file bytes in
 * a JSON response (every other download — resume, certificate, payslip, HR letter — goes through
 * {@code StorageService.presignedGetUrl}), so reusing that one code path for every export is
 * simpler than adding a second, novel delivery mechanism for a threshold build-plan.md never
 * actually mandates behaviour below. {@link ExportResponse#deliveredInline} still reports which
 * regime applied, so this simplification is visible to the caller rather than silent.
 * <p>
 * {@code format} (FRS MSH-FR-ADM-05: "export... to Excel, PDF, and CSV") defaults to {@code XLSX}
 * when the caller omits it, so the one prior caller shape ({@code POST /admin/exports/{report}}
 * with no body/params) keeps working unchanged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final ExportGenerationService exportGenerationService;
    private final StorageService storageService;

    public ExportResponse export(String reportParam, String formatParam, String callerUuid) {
        ExportReport report = parseReport(reportParam);
        ExportFormat format = parseFormat(formatParam);
        ExportResult result = await(exportGenerationService.generate(report, format));

        String extension = format == ExportFormat.CSV ? "csv" : "xlsx";
        String contentType = format == ExportFormat.CSV ? CSV_CONTENT_TYPE : XLSX_CONTENT_TYPE;
        String key = "exports/%s/%s.%s".formatted(report.name().toLowerCase(Locale.ROOT), UUID.randomUUID(), extension);
        storageService.uploadTrusted(key, result.fileBytes(), contentType);

        Duration ttl = Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES);
        URL url = storageService.presignedGetUrl(callerUuid, key, ttl);

        boolean deliveredInline = deliveredInline(result.rowCount());
        return new ExportResponse(report.name(), format.name(), result.rowCount(), deliveredInline, url.toString(), Instant.now().plus(ttl));
    }

    /** The row-count seam {@code ExportServiceTest} exercises directly — see {@link
     * ExportResponse#deliveredInline}'s own Javadoc for what this signals today vs. what it would
     * gate in a future direct-bytes implementation. */
    boolean deliveredInline(long rowCount) {
        return rowCount <= Constants.EXPORT_SMALL_ROW_THRESHOLD;
    }

    private ExportReport parseReport(String reportParam) {
        try {
            return ExportReport.valueOf(reportParam.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.EXPORT_REPORT_NOT_SUPPORTED,
                    "'%s' is not a supported export report.".formatted(reportParam));
        }
    }

    private ExportFormat parseFormat(String formatParam) {
        try {
            return ExportFormat.valueOf(formatParam.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.EXPORT_FORMAT_NOT_SUPPORTED,
                    "'%s' is not a supported export format.".formatted(formatParam));
        }
    }

    private ExportResult await(CompletableFuture<ExportResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EXPORT_GENERATION_FAILED);
        } catch (ExecutionException e) {
            log.error("[admin/export] export generation failed", e.getCause());
            throw new BusinessException(ErrorCode.EXPORT_GENERATION_FAILED);
        }
    }
}
