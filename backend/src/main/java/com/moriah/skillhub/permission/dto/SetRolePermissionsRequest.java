package com.moriah.skillhub.permission.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** {@code PUT /api/v1/admin/roles/{roleCode}/permissions} — the complete set of permission codes
 * the role should hold afterwards (a full replace, not a delta). Empty set = revoke all. */
public record SetRolePermissionsRequest(
        @NotNull Set<String> codes
) {
}
