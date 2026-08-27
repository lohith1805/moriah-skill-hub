-- Feature 02. Identity schema: users, roles, permissions (unpopulated — see progress-tracker.md),
-- user_roles, user_profiles, and the three token tables auth (feature 03) depends on.

CREATE TABLE users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uuid                CHAR(36)        NOT NULL,
    full_name           VARCHAR(150)    NOT NULL,
    email               VARCHAR(180)    NOT NULL,
    phone               VARCHAR(20)     NULL,
    password_hash       VARCHAR(100)    NULL,
    github_username     VARCHAR(100)    NULL,
    linkedin_url        VARCHAR(255)    NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    token_version       INT             NOT NULL DEFAULT 0,
    two_factor_secret   VARBINARY(255)  NULL,
    two_factor_enabled  BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verified_at   DATETIME(6)     NULL,
    last_login_at       DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_uuid (uuid),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_phone (phone),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED', 'PENDING_VERIFICATION')),
    INDEX idx_users_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Present but deliberately inert — see the note under "V1 — Identity" in architecture.md and the
-- 2026-08-24 decision in progress-tracker.md. Never populated, never read: RBAC in this build is
-- role-based (hasRole) throughout. Created now so a permission-code layer can be added later
-- without a breaking migration.
CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(100)    NOT NULL,
    module      VARCHAR(50)     NOT NULL,
    description VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissions_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    INDEX idx_role_permissions_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id),
    INDEX idx_user_roles_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_profiles (
    id                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED  NOT NULL,
    bio                TEXT             NULL,
    location           VARCHAR(150)     NULL,
    current_title      VARCHAR(150)     NULL,
    experience_level   VARCHAR(30)      NULL,
    years_experience   TINYINT UNSIGNED NULL,
    skills             JSON             NULL,
    education          JSON             NULL,
    work_experience    JSON             NULL,
    resume_key         VARCHAR(255)     NULL,
    portfolio_slug     VARCHAR(150)     NULL,
    is_complete        BOOLEAN          NOT NULL DEFAULT FALSE,
    completion_percent TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_profiles_user (user_id),
    UNIQUE KEY uq_user_profiles_slug (portfolio_slug),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_profiles_completion CHECK (completion_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- SHA-256 hex digest -> CHAR(64), consistent with "token_hash" everywhere in library-docs.md.
CREATE TABLE refresh_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    token_hash  CHAR(64)        NOT NULL,
    expires_at  DATETIME(6)     NOT NULL,
    revoked_at  DATETIME(6)     NULL,
    replaced_by BIGINT UNSIGNED NULL,
    user_agent  VARCHAR(255)    NULL,
    ip_address  VARCHAR(45)     NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_tokens_hash (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id),
    INDEX idx_refresh_tokens_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_password_reset_tokens_hash (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_password_reset_tokens_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_verification_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    expires_at DATETIME(6)     NOT NULL,
    used_at    DATETIME(6)     NULL,
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_email_verification_tokens_hash (token_hash),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_email_verification_tokens_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
