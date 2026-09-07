package com.moriah.skillhub.admin;

import com.moriah.skillhub.client.ClientProjectService;
import com.moriah.skillhub.client.StaffAssignmentService;
import com.moriah.skillhub.client.dto.AssignClientProjectRequest;
import com.moriah.skillhub.client.dto.ClientProjectResponse;
import com.moriah.skillhub.client.dto.StaffWorkloadResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.user.entity.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN oversight of the BA/developer round-robin routing that {@code ClientProjectService}
 * (BA, at submission) and {@code RequirementDocumentApprovalService} (developer, at first BA
 * sign-off) apply automatically. {@code GET /admin/client-projects} needs no new endpoint here —
 * the existing {@code GET /api/v1/clients/projects} already gives ADMIN the full picture, now
 * that {@link ClientProjectResponse} carries the assignment fields.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin")
public class ClientProjectAssignmentController {

    private final StaffAssignmentService staffAssignmentService;
    private final ClientProjectService clientProjectService;

    @GetMapping("/api/v1/admin/staff-workload")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Current client-project workload for every active BA or developer",
            description = "role must be BUSINESS_ANALYST or DEVELOPER — sorted lightest-loaded first")
    public ResponseEntity<ApiResponse<List<StaffWorkloadResponse>>> staffWorkload(
            @RequestParam RoleCode role) {
        if (role != RoleCode.BUSINESS_ANALYST && role != RoleCode.DEVELOPER) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "role must be BUSINESS_ANALYST or DEVELOPER.");
        }
        return ResponseEntity.ok(ApiResponse.success(staffAssignmentService.workload(role)));
    }

    @PutMapping("/api/v1/admin/client-projects/{id}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually (re)assign a client project's BA and/or developer",
            description = "Overrides the automatic round-robin pick; either field may be omitted to leave it untouched")
    public ResponseEntity<ApiResponse<ClientProjectResponse>> assign(
            @PathVariable Long id, @Valid @RequestBody AssignClientProjectRequest request,
            @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(clientProjectService.assign(id, request, callerUserId)));
    }
}
