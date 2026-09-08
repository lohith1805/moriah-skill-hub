package com.moriah.skillhub.permission.dto;

import java.util.List;

/** {@code GET /api/v1/admin/permissions} — the full catalogue plus, per role, which codes it
 * currently holds. Drives the admin role/permission matrix screen. */
public record PermissionMatrixResponse(
        List<PermissionEntry> permissions,
        List<RoleGrants> roles
) {
    public record PermissionEntry(String code, String label, String category) {
    }

    public record RoleGrants(String role, List<String> permissionCodes) {
    }
}
