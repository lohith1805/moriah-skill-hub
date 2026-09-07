package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.ClientRequestResponse;
import com.moriah.skillhub.admin.dto.RejectClientRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The corporate-client self-registration review queue. Companion to the public
 * {@code POST /api/v1/auth/register/client}. ADMIN-only (the decision that re-opened
 * build-plan.md feature 21 also set the approver to ADMIN, not BA).
 */
@RestController
@RequestMapping("/api/v1/admin/client-requests")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class ClientRequestController {

    private final ClientApprovalService clientApprovalService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List client self-registrations awaiting review (default) or already rejected",
            description = "status may only be PENDING_APPROVAL (default) or REJECTED")
    public ResponseEntity<ApiResponse<PageResponse<ClientRequestResponse>>> list(
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(clientApprovalService.list(status, pageable)));
    }

    @PostMapping("/{userUuid}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a client registration — account becomes ACTIVE and the applicant is emailed")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Approved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "This registration has already been decided")
    })
    public ResponseEntity<ApiResponse<ClientRequestResponse>> approve(
            @PathVariable String userUuid, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(clientApprovalService.approve(userUuid, callerUserId)));
    }

    @PostMapping("/{userUuid}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Decline a client registration — account becomes REJECTED and the applicant is emailed the reason")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rejected"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "This registration has already been decided")
    })
    public ResponseEntity<ApiResponse<ClientRequestResponse>> reject(
            @PathVariable String userUuid,
            @Valid @RequestBody RejectClientRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(
                clientApprovalService.reject(userUuid, request.reason(), callerUserId)));
    }
}
