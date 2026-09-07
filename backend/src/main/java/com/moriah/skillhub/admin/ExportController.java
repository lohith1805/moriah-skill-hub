package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ExportResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUserUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code {report}} is one of {@link com.moriah.skillhub.admin.dto.ExportReport}'s three values
 * (case-insensitive) — see that enum's own Javadoc for why exactly these three and no others.
 * {@code format} is one of {@link com.moriah.skillhub.admin.dto.ExportFormat}'s values
 * (case-insensitive), defaulting to {@code xlsx} so no existing caller has to change. Synchronous
 * from the caller's point of view (the request blocks on the async generation, see {@code
 * ExportService}'s Javadoc) — there is no separate status/download endpoint in this feature's
 * scope. */
@RestController
@RequestMapping("/api/v1/admin/exports")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/{report}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate an XLSX or CSV export (users/revenue/audit) and return a presigned download URL")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Export generated, presigned URL returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported report type or format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    })
    public ResponseEntity<ApiResponse<ExportResponse>> export(
            @PathVariable String report,
            @RequestParam(defaultValue = "xlsx") String format,
            @CurrentUserUuid String callerUuid) {

        return ResponseEntity.ok(ApiResponse.success(exportService.export(report, format, callerUuid)));
    }
}
