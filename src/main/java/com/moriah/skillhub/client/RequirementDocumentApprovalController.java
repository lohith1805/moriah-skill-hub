package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.PendingApprovalResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentDetailResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;

/**
 * The one cross-role surface for the "Client Project Documents" section the user asked for on
 * every relevant dashboard — a CLIENT, the project's assigned DEVELOPER, any BUSINESS_ANALYST
 * (other than a document's own author), or ADMIN can all read and act here, whereas {@code
 * BaController} ({@code /ba/documents/**}) and {@code DevRequirementController} ({@code
 * /dev/requirement-documents/**}) stay role-restricted to BA/ADMIN and DEVELOPER/ADMIN
 * respectively (kept as-is for those two portals' existing muscle memory). All three surfaces
 * share the same {@link RequirementDocumentService}/{@link RequirementDocumentApprovalService}
 * underneath — one set of documents, three doors in.
 */
@RestController
@RequestMapping("/api/v1/requirement-documents")
@RequiredArgsConstructor
@Tag(name = "Requirement Document Approvals")
public class RequirementDocumentApprovalController {

    private final RequirementDocumentService requirementDocumentService;
    private final RequirementDocumentApprovalService requirementDocumentApprovalService;

    @GetMapping("/pending-my-approval")
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','DEVELOPER','ADMIN')")
    @Operation(summary = "Documents where the caller's own sign-off slot is still open",
            description = "ADMIN gets an empty list here — admins already have unrestricted oversight via "
                    + "/ba/documents and /dev/requirement-documents; this inbox is for the parties actually "
                    + "waited on.")
    public ResponseEntity<ApiResponse<List<PendingApprovalResponse>>> pendingMyApproval(
            @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(requirementDocumentApprovalService.pendingFor(callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','DEVELOPER','ADMIN')")
    @Operation(summary = "Requirement documents for a client project",
            description = "CLIENT callers may only request their own project's id — 403 otherwise")
    public ResponseEntity<ApiResponse<PageResponse<RequirementDocumentResponse>>> list(
            @RequestParam Long clientProjectId,
            @RequestParam(required = false) RequirementDocumentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                requirementDocumentService.listForCaller(clientProjectId, status, callerUserId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','DEVELOPER','ADMIN')")
    @Operation(summary = "One requirement document with its full content and approval slots",
            description = "CLIENT callers may only read a document on their own project — 403 otherwise")
    public ResponseEntity<ApiResponse<RequirementDocumentDetailResponse>> get(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(requirementDocumentService.getDetailForCaller(id, callerUserId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','DEVELOPER','ADMIN')")
    @Operation(summary = "Record the caller's own sign-off on this document",
            description = "The caller's role slot is inferred from who they are: the project's client contact, "
                    + "its assigned developer, any business analyst other than the document's author, or ADMIN "
                    + "(fast-tracks every still-pending slot at once). 403 if none of those apply to the caller; "
                    + "409 if that slot (or the whole document) is already approved.")
    public ResponseEntity<ApiResponse<RequirementDocumentResponse>> approve(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(requirementDocumentService.approve(id, callerUserId)));
    }
}
