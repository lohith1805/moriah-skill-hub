package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminMetricsOverviewResponse;
import com.moriah.skillhub.admin.dto.MonthlyRevenueSummary;
import com.moriah.skillhub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** {@code @ApiResponses} documents non-200s with the fully-qualified springdoc annotation
 * throughout this package — {@code ApiResponse} unqualified is already this codebase's own
 * response-envelope DTO ({@code com.moriah.skillhub.common.dto.ApiResponse}), so the two names
 * collide on a plain import. */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminMetricsController {

    private final MetricsService metricsService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin KPI overview — attendance/task/quiz averages, recent revenue, lead funnel, batch velocity")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Overview KPIs, cached 5 minutes"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    })
    public ResponseEntity<ApiResponse<AdminMetricsOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success(metricsService.overview()));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Monthly captured revenue for an optional date range, defaults to the trailing 12 months")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Monthly revenue rows, oldest first"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    })
    public ResponseEntity<ApiResponse<List<MonthlyRevenueSummary>>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(metricsService.revenue(from, to)));
    }
}
