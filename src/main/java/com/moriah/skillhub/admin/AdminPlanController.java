package com.moriah.skillhub.admin;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.subscription.EntitlementService;
import com.moriah.skillhub.subscription.dto.PlanResponse;
import com.moriah.skillhub.subscription.dto.UpdatePlanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code {id}} stays a real numeric id, deliberately not swapped for a uuid — {@code
 * subscription_plans} has no {@code uuid} column (architecture.md V3), unlike every {@code
 * {userUuid}} path variable elsewhere in this feature. Routes through {@code
 * EntitlementService.updatePlan} rather than touching {@code SubscriptionPlanRepository}
 * directly — the {@code plans}/{@code planCodesById} caches this write must evict live on that
 * class, so the eviction and the mutation belong together there. */
@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminPlanController {

    private final EntitlementService entitlementService;

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a subscription plan's pricing/feature flags at runtime — cache evicted immediately")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No plan with this id")
    })
    public ResponseEntity<ApiResponse<PlanResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdatePlanRequest request) {

        return ResponseEntity.ok(ApiResponse.success(entitlementService.updatePlan(id, request)));
    }
}
