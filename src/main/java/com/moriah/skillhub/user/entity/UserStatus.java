package com.moriah.skillhub.user.entity;

/** Mirrors the {@code chk_users_status} CHECK constraint in {@code V1__core_users_roles.sql}. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    PENDING_VERIFICATION
}
