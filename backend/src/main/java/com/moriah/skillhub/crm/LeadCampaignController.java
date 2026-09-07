package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.crm.dto.CreateLeadCampaignRequest;
import com.moriah.skillhub.crm.dto.LeadCampaignResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadCampaignRequest;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lead-Gen Campaigns (gap B1.7). Separate controller from {@link LeadController} — same {@code
 * /api/v1/leads} prefix, distinct sub-path {@code /campaigns} (like {@code LeadController}'s own
 * {@code /targets/me}). {@code LEAD_GEN}/{@code ADMIN} only.
 */
@RestController
@RequestMapping("/api/v1/leads/campaigns")
@RequiredArgsConstructor
@Tag(name = "CRM")
public class LeadCampaignController {

    private final LeadCampaignService leadCampaignService;

    @GetMapping
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    @Operation(summary = "List campaigns — optional status filter")
    public ResponseEntity<ApiResponse<PageResponse<LeadCampaignResponse>>> list(
            @RequestParam(required = false) LeadCampaignStatus status,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(leadCampaignService.list(status, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    @Operation(summary = "Create a campaign (starts PLANNED)")
    public ResponseEntity<ApiResponse<LeadCampaignResponse>> create(
            @Valid @RequestBody CreateLeadCampaignRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(leadCampaignService.create(request, callerUserId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    @Operation(summary = "Replace a campaign's fields, including its status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No campaign with this id")
    })
    public ResponseEntity<ApiResponse<LeadCampaignResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateLeadCampaignRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(leadCampaignService.update(id, request, callerUserId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    @Operation(summary = "Cancel a campaign (status = CANCELLED) — never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Campaign cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No campaign with this id")
    })
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id, @CurrentUser Long callerUserId) {
        leadCampaignService.cancel(id, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
