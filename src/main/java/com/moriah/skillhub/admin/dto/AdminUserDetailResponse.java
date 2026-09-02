package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/admin/users/{userUuid}} (gap B1.17) — the single-user detail view, richer
 * than {@link AdminUserResponse} (the list row). Still {@code uuid} only, never {@code id}
 * (architecture.md invariant). {@code passwordHash} / {@code twoFactorSecret} are never exposed.
 */
public record AdminUserDetailResponse(
        String uuid,
        String fullName,
        String email,
        String phone,
        String githubUsername,
        String linkedinUrl,
        UserStatus status,
        List<RoleCode> roles,
        boolean twoFactorEnabled,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
