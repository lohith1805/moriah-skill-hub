package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.CreateRequirementDocumentRequest;
import com.moriah.skillhub.client.dto.CreateResourceAllocationRequest;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.dto.ResourceAllocationResponse;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

/** The {@code /ba/**} prefix is this codebase's existing role-gating-by-path convention (same as
 * {@code /hr/**}/{@code /pip/**}) — every endpoint here is BUSINESS_ANALYST/ADMIN only
 * (build-plan.md feature 21). Shares {@code client}/{@code entity} with {@link ClientController}
 * since both controllers operate on the same {@code Client}/{@code ClientProject} data — the
 * simplest reading of build-plan.md's own {@code /clients/...} vs {@code /ba/...} URL split is
 * two controllers in one package, not two packages. */
@RestController
@RequestMapping("/api/v1/ba")
@RequiredArgsConstructor
@Tag(name = "BA")
public class BaController {

    private final RequirementDocumentService requirementDocumentService;
    private final ResourceAllocationService resourceAllocationService;

    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "List requirement documents — optional clientProjectId / status filters")
    public ResponseEntity<ApiResponse<PageResponse<RequirementDocumentResponse>>> listDocuments(
            @RequestParam(required = false) Long clientProjectId,
            @RequestParam(required = false) RequirementDocumentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                requirementDocumentService.list(clientProjectId, status, pageable)));
    }

    @PostMapping("/documents")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Create a requirement document (BRD/SRS/FRS/USER_STORY) — lands IN_REVIEW directly",
            description = "Versioned per (clientProjectId, docType) pair; returns 404 CLIENT_PROJECT_NOT_FOUND if the project does not exist")
    public ResponseEntity<ApiResponse<RequirementDocumentResponse>> createDocument(
            @Valid @RequestBody CreateRequirementDocumentRequest request, @CurrentUser Long callerUserId) {
        RequirementDocumentResponse response = requirementDocumentService.create(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/documents/{id}/approve")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Approve a requirement document — IN_REVIEW to APPROVED",
            description = "Returns 409 BUSINESS_RULE_VIOLATION if the document is already APPROVED; 404 if it does not exist")
    public ResponseEntity<ApiResponse<RequirementDocumentResponse>> approveDocument(@PathVariable Long id,
            @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(requirementDocumentService.approve(id, callerUserId)));
    }

    @PostMapping("/allocations")
    @PreAuthorize("hasAnyRole('BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Allocate a student/staff user and batch to a client project",
            description = "Returns 404 if the referenced clientProjectId, batchId, or userUuid does not exist")
    public ResponseEntity<ApiResponse<ResourceAllocationResponse>> createAllocation(
            @Valid @RequestBody CreateResourceAllocationRequest request) {
        ResourceAllocationResponse response = resourceAllocationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
