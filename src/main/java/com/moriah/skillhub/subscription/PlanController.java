package com.moriah.skillhub.subscription;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.subscription.dto.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public at the filter level (architecture.md "Public endpoints": "GET /api/v1/plans") — no
 * {@code @PreAuthorize}, matching {@code AuthController}'s stated exception for explicitly
 * public endpoints. */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plans")
public class PlanController {

    private final EntitlementService entitlementService;

    @GetMapping
    @Operation(summary = "List active subscription plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.success(entitlementService.listActivePlans()));
    }
}
