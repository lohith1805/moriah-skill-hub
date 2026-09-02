package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.RequirementDocumentDetailResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Developer-facing view of the requirement documents a developer will build against (gap B1.16).
 * The BA authors and a BA/ADMIN approves them ({@link BaController}); here a developer reads the
 * requirement text and records an acknowledgement — a separate axis from the BA approval
 * {@code status}. {@code DEVELOPER}/{@code ADMIN} only, gated by the {@code /dev/**} path the
 * same way {@code /ba/**} and {@code /hr/**} are.
 */
@RestController
@RequestMapping("/api/v1/dev/requirement-documents")
@RequiredArgsConstructor
@Tag(name = "Dev")
public class DevRequirementController {

    private final RequirementDocumentService requirementDocumentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "List requirement documents to review — optional clientProjectId / status filters")
    public ResponseEntity<ApiResponse<PageResponse<RequirementDocumentResponse>>> list(
            @RequestParam(required = false) Long clientProjectId,
            @RequestParam(required = false) RequirementDocumentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                requirementDocumentService.list(clientProjectId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "One requirement document with its full content")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No document with this id")
    })
    public ResponseEntity<ApiResponse<RequirementDocumentDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(requirementDocumentService.getDetail(id)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Mark a requirement document as reviewed by the caller — idempotent; does not change status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Acknowledgement recorded (or already recorded)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No document with this id")
    })
    public ResponseEntity<ApiResponse<RequirementDocumentDetailResponse>> acknowledge(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                requirementDocumentService.acknowledgeByDeveloper(id, callerUserId)));
    }
}
