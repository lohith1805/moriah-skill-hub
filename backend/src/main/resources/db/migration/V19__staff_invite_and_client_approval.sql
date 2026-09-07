-- Feature: staff invite + client self-registration approval (frontend-integration decision,
-- 2026-09-02). This deliberately re-opens build-plan.md feature 21's "a client cannot
-- self-register": clients now register themselves at POST /api/v1/auth/register/client and land
-- in PENDING_APPROVAL until an ADMIN approves them at POST /api/v1/admin/client-requests/{uuid}/
-- approve. POST /api/v1/clients (ADMIN-provisioned) is unchanged and still works.
--
-- Three new users.status values:
--   INVITED          — staff account created by ADMIN, waiting for the invitee to set a password
--   PENDING_APPROVAL — client self-registered, waiting for ADMIN review
--   REJECTED         — client registration declined
-- AuthService.login rejects all three with a distinct 403 error code.

ALTER TABLE users
    DROP CHECK chk_users_status;

ALTER TABLE users
    ADD CONSTRAINT chk_users_status CHECK (status IN (
        'ACTIVE', 'SUSPENDED', 'TERMINATED', 'PENDING_VERIFICATION',
        'INVITED', 'PENDING_APPROVAL', 'REJECTED'));

-- Mirrors email_verification_tokens / password_reset_tokens exactly (V1): SHA-256 hex digest
-- of the raw token -> CHAR(64), single-use (used_at), time-boxed (expires_at). The raw token is
-- emailed in the accept-invite link and never stored or logged.
CREATE TABLE staff_invite_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_staff_invite_tokens_hash (token_hash),
    CONSTRAINT fk_staff_invite_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_staff_invite_tokens_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
