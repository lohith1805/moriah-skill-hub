package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminUserResponse;
import com.moriah.skillhub.admin.dto.UpdateUserRolesRequest;
import com.moriah.skillhub.admin.dto.UpdateUserStatusRequest;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.user.entity.RoleCode;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code {userUuid}}, not build-plan.md's literal {@code {id}} — same "public identifier is
 * always uuid" precedent {@code BatchController.removeStudent}/{@code .graduate} already
 * established at feature 10 (see progress-tracker.md's API Contract Changes table for this
 * feature's own row). */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users, optionally filtered by role and/or status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated user list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    })
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> list(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(adminUserService.list(role, status, pageable)));
    }

    @PutMapping("/{userUuid}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a user's status — increments token_version, invalidating every outstanding access token immediately")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid")
    })
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(
            @PathVariable String userUuid,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(adminUserService.updateStatus(userUuid, request, callerUserId)));
    }

    @PutMapping("/{userUuid}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a user's role assignments — increments token_version, invalidating every outstanding access token immediately")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Roles updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid")
    })
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateRoles(
            @PathVariable String userUuid,
            @Valid @RequestBody UpdateUserRolesRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(adminUserService.updateRoles(userUuid, request, callerUserId)));
    }
}
