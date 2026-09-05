package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ExportFormat;
import com.moriah.skillhub.admin.dto.ExportReport;
import com.moriah.skillhub.admin.dto.ExportResponse;
import com.moriah.skillhub.admin.dto.ExportResult;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 22: "delivered by presigned URL above 1,000 rows" — {@link
 * ExportService#deliveredInline} is the row-count seam this test exercises directly (both
 * branches still resolve to a presigned URL today; see the class Javadoc for why). Also covers
 * the {@code {report}} parsing (unsupported value -> 400) and the async-failure-to-BusinessException
 * conversion path, none of which need real POI/S3/replica infrastructure — {@link
 * ExportGenerationService} and {@link StorageService} are mocked entirely; their own behaviour has
 * its own coverage (an IT exercising the real path end to end). */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private ExportGenerationService exportGenerationService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private ExportService exportService;

    @Test
    void deliveredInline_atAndBelowThreshold_true() {
        assertThat(exportService.deliveredInline(0)).isTrue();
        assertThat(exportService.deliveredInline(1000)).isTrue();
    }

    @Test
    void deliveredInline_aboveThreshold_false() {
        assertThat(exportService.deliveredInline(1001)).isFalse();
        assertThat(exportService.deliveredInline(50_000)).isFalse();
    }

    @Test
    void export_smallResult_deliveredInlineTrueAndPresigned() throws Exception {
        byte[] bytes = { 1, 2, 3 };
        when(exportGenerationService.generate(ExportReport.USERS, ExportFormat.XLSX))
                .thenReturn(CompletableFuture.completedFuture(new ExportResult(bytes, 5)));
        when(storageService.presignedGetUrl(eq("caller-uuid"), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://example.com/presigned").toURL());

        ExportResponse response = exportService.export("users", "xlsx", "caller-uuid");

        assertThat(response.report()).isEqualTo("USERS");
        assertThat(response.format()).isEqualTo("XLSX");
        assertThat(response.rowCount()).isEqualTo(5);
        assertThat(response.deliveredInline()).isTrue();
        assertThat(response.downloadUrl()).isEqualTo("https://example.com/presigned");
        verify(storageService).uploadTrusted(anyString(), eq(bytes), eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void export_csvFormat_usesCsvContentTypeAndExtension() throws Exception {
        byte[] bytes = { 4, 5, 6 };
        when(exportGenerationService.generate(ExportReport.USERS, ExportFormat.CSV))
                .thenReturn(CompletableFuture.completedFuture(new ExportResult(bytes, 3)));
        when(storageService.presignedGetUrl(eq("caller-uuid"), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://example.com/presigned.csv").toURL());

        ExportResponse response = exportService.export("users", "csv", "caller-uuid");

        assertThat(response.format()).isEqualTo("CSV");
        verify(storageService).uploadTrusted(
                org.mockito.ArgumentMatchers.contains(".csv"), eq(bytes), eq("text/csv;charset=UTF-8"));
    }

    @Test
    void export_defaultFormat_isXlsx() throws Exception {
        when(exportGenerationService.generate(ExportReport.USERS, ExportFormat.XLSX))
                .thenReturn(CompletableFuture.completedFuture(new ExportResult(new byte[0], 0)));
        when(storageService.presignedGetUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://example.com/presigned").toURL());

        ExportResponse response = exportService.export("users", "xlsx", "caller-uuid");

        assertThat(response.format()).isEqualTo("XLSX");
    }

    @Test
    void export_largeResult_deliveredInlineFalse() throws Exception {
        when(exportGenerationService.generate(ExportReport.AUDIT, ExportFormat.XLSX))
                .thenReturn(CompletableFuture.completedFuture(new ExportResult(new byte[0], 50_000)));
        when(storageService.presignedGetUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://example.com/presigned-large").toURL());

        ExportResponse response = exportService.export("audit", "xlsx", "caller-uuid");

        assertThat(response.rowCount()).isEqualTo(50_000);
        assertThat(response.deliveredInline()).isFalse();
    }

    @Test
    void export_unsupportedReport_throwsBusinessException() {
        assertThatThrownBy(() -> exportService.export("not-a-report", "xlsx", "caller-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPORT_REPORT_NOT_SUPPORTED);
    }

    @Test
    void export_unsupportedFormat_throwsBusinessException() {
        assertThatThrownBy(() -> exportService.export("users", "pdf", "caller-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPORT_FORMAT_NOT_SUPPORTED);
    }

    @Test
    void export_generationFails_throwsExportGenerationFailed() {
        CompletableFuture<ExportResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        when(exportGenerationService.generate(ExportReport.REVENUE, ExportFormat.XLSX)).thenReturn(failed);

        assertThatThrownBy(() -> exportService.export("revenue", "xlsx", "caller-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPORT_GENERATION_FAILED);
    }
}
