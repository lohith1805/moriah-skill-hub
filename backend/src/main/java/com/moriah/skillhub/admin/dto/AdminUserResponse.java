package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;

/** {@code uuid} only — {@code users.id} never leaves the service layer (architecture.md
 * invariant), same as every other response record in this codebase. */
public record AdminUserResponse(
        String uuid,
        String fullName,
        String email,
        UserStatus status,
        List<RoleCode> roles,
        Instant createdAt
) {
}
