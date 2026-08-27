package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.crm.dto.AddLeadActivityRequest;
import com.moriah.skillhub.crm.dto.CreateLeadRequest;
import com.moriah.skillhub.crm.dto.LeadActivityResponse;
import com.moriah.skillhub.crm.dto.LeadResponse;
import com.moriah.skillhub.crm.dto.SalesTargetResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadStatusRequest;
import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "CRM")
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    public ResponseEntity<ApiResponse<LeadResponse>> create(
            @Valid @RequestBody CreateLeadRequest request,
            @CurrentUser Long userId) {

        LeadResponse response = leadService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<LeadResponse>>> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String agentUuid,
            @RequestParam(required = false) LeadSource source,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(leadService.list(status, agentUuid, source, pageable)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    public ResponseEntity<ApiResponse<LeadResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLeadStatusRequest request,
            @CurrentUser Long userId) {

        return ResponseEntity.ok(ApiResponse.success(leadService.updateStatus(id, request, userId)));
    }

    @PostMapping("/{id}/activities")
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    public ResponseEntity<ApiResponse<LeadActivityResponse>> addActivity(
            @PathVariable Long id,
            @Valid @RequestBody AddLeadActivityRequest request,
            @CurrentUser Long userId) {

        LeadActivityResponse response = leadService.addActivity(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/targets/me")
    @PreAuthorize("hasAnyRole('LEAD_GEN','ADMIN')")
    public ResponseEntity<ApiResponse<SalesTargetResponse>> myTargets(@CurrentUser Long userId) {
        return ResponseEntity.ok(ApiResponse.success(leadService.myTargets(userId)));
    }
}
