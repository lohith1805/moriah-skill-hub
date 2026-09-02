package com.moriah.skillhub.user.entity;

/**
 * Mirrors the {@code chk_users_status} CHECK constraint — created in {@code V1__core_users_roles.sql},
 * extended in {@code V19__staff_invite_and_client_approval.sql} with the last three values.
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED,
    PENDING_VERIFICATION,
    /** Staff account created by an ADMIN; the invitee has not set a password yet. */
    INVITED,
    /** Client self-registered via {@code POST /api/v1/auth/register/client}; awaiting ADMIN review. */
    PENDING_APPROVAL,
    /** A client's self-registration was declined by an ADMIN. */
    REJECTED
}
