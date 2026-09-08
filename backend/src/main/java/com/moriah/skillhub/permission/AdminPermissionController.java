package com.moriah.skillhub.permission;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.permission.dto.PermissionMatrixResponse;
import com.moriah.skillhub.permission.dto.SetRolePermissionsRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Granular permissions admin (V52). ADMIN-only. Read the role/permission matrix, and replace the
 * grant set for one role. Enforcement of individual codes is future work — see {@link
 * PermissionSecurity}.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Permissions")
public class AdminPermissionController {

    private final PermissionService permissionService;

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "The permission catalogue plus each role's current grants")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> matrix() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.matrix()));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace the full set of permission codes granted to one role")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> setRolePermissions(
            @PathVariable RoleCode roleCode,
            @Valid @RequestBody SetRolePermissionsRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(
                permissionService.setRolePermissions(roleCode, request.codes(), callerUserId)));
    }
}
