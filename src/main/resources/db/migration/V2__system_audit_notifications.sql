-- Feature 02. Lands here, not at the end: feature 03 writes failed logins to audit_logs,
-- feature 08 writes to notifications. Both fail ddl-auto: validate if these arrive later.

-- Insert-only. moriah_app holds SELECT+INSERT only on this table (narrowed by afterMigrate.sql
-- once this table exists — see the callback and docker/mysql-init/01-users.sql).
CREATE TABLE audit_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NULL,
    action      VARCHAR(100)    NOT NULL,
    entity_type VARCHAR(50)     NOT NULL,
    entity_id   BIGINT          NULL,
    old_value   JSON            NULL,
    new_value   JSON            NULL,
    ip_address  VARCHAR(45)     NULL,
    user_agent  VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_audit_logs_user (user_id),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED  NOT NULL,
    channel       VARCHAR(20)      NOT NULL,
    template_code VARCHAR(100)     NOT NULL,
    payload       JSON             NULL,
    status        VARCHAR(20)      NOT NULL DEFAULT 'QUEUED',
    attempts      TINYINT UNSIGNED NOT NULL DEFAULT 0,
    sent_at       DATETIME(6)      NULL,
    error_message VARCHAR(500)     NULL,
    created_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'IN_APP', 'SMS')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    INDEX idx_notifications_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Fixed schema required by the ShedLock JDBC provider (net.javacrumbs.shedlock) — column names
-- and types are its contract, not this project's convention. No id/created_at/updated_at.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until DATETIME(3)  NOT NULL,
    locked_at  DATETIME(3)  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE job_runs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_name        VARCHAR(100)    NOT NULL,
    started_at      DATETIME(6)     NOT NULL,
    completed_at    DATETIME(6)     NULL,
    items_processed INT UNSIGNED    NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_job_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    INDEX idx_job_runs_name_started (job_name, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
