package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * {@code POST /api/v1/admin/users} — an ADMIN creates a staff account in {@code INVITED} state
 * and an accept-invite link is emailed to {@code email}. No password here: the invitee sets one
 * at {@code POST /api/v1/auth/accept-invite}. {@code roles} must be staff roles — {@code STUDENT}
 * and {@code CLIENT} are rejected ({@code ROLE_NOT_STAFF_ASSIGNABLE}) because those come in
 * through the public registration flows.
 */
public record CreateStaffRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 20) String phone,
        @NotEmpty Set<RoleCode> roles
) {
}
