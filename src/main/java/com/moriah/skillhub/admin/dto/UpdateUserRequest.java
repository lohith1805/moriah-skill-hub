package com.moriah.skillhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/admin/users/{userUuid}} (gap B1.17) — an ADMIN edits a user's profile
 * fields. Deliberately excludes {@code email} (the login identifier — changing it is an
 * account-recovery concern, not a profile edit), {@code status} (has its own endpoint, which
 * also bumps {@code token_version}), and {@code roles} (its own endpoint, same reason). A full
 * replace of the editable fields, matching every other {@code Update*Request}: {@code phone},
 * {@code githubUsername} and {@code linkedinUrl} left {@code null} are cleared.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 100) String githubUsername,
        @Size(max = 255) String linkedinUrl
) {
}
