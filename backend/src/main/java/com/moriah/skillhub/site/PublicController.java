package com.moriah.skillhub.site;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.site.dto.PublicStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated endpoints for the marketing site. Public at the filter level
 * ({@code SecurityConfig.PUBLIC_PATHS} — {@code /api/v1/public/**}); no {@code @PreAuthorize},
 * same explicit-public pattern as {@code PlanController}. Only ever exposes GETs of aggregate,
 * non-personal data. Rate-limited per-IP by {@code RateLimitFilter}.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicController {

    private final PublicStatsService publicStatsService;

    @GetMapping("/stats")
    @Operation(summary = "Aggregate counts for the landing page (graduates, placements, …)")
    public ResponseEntity<ApiResponse<PublicStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(publicStatsService.snapshot()));
    }
}
