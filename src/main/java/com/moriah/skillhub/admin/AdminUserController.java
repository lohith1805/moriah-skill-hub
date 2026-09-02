package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminUserDetailResponse;
import com.moriah.skillhub.admin.dto.AdminUserResponse;
import com.moriah.skillhub.admin.dto.CreateStaffRequest;
import com.moriah.skillhub.admin.dto.UpdateUserRequest;
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

    @GetMapping("/{userUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Single-user detail — profile fields, roles, 2FA and login timestamps")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User detail"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid")
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> get(@PathVariable String userUuid) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.get(userUuid)));
    }

    @PutMapping("/{userUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Edit a user's profile fields (name, phone, GitHub, LinkedIn). "
            + "Status and roles have their own endpoints.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "phone already belongs to another account")
    })
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> update(
            @PathVariable String userUuid, @Valid @RequestBody UpdateUserRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(adminUserService.update(userUuid, request, callerUserId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Invite a staff member — creates an INVITED account and emails an accept-invite link. "
            + "roles must be staff roles (not STUDENT/CLIENT).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Invite created and emailed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "roles contains STUDENT or CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<ApiResponse<AdminUserResponse>> inviteStaff(
            @Valid @RequestBody CreateStaffRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminUserService.inviteStaff(request, callerUserId)));
    }

    @PostMapping("/{userUuid}/resend-invite")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Re-send the accept-invite link for an account still in INVITED state — burns the previous link")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invite re-sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this uuid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "This user's invite is not pending")
    })
    public ResponseEntity<ApiResponse<AdminUserResponse>> resendInvite(
            @PathVariable String userUuid,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(adminUserService.resendStaffInvite(userUuid, callerUserId)));
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
