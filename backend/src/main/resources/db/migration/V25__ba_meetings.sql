-- Feature: BA Meetings coordination (frontend-integration gap B1.14). The BA module shipped
-- requirement documents + resource allocations only; the FE's BA workspace also schedules
-- meetings (client kickoffs, requirement walkthroughs, retros) and records minutes afterwards.
-- client_project_id is nullable — a general BA sync is not tied to one project.

CREATE TABLE ba_meetings (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title             VARCHAR(200)    NOT NULL,
    agenda            TEXT            NULL,
    client_project_id BIGINT UNSIGNED NULL,
    scheduled_at      DATETIME(6)     NOT NULL,
    duration_minutes  INT UNSIGNED    NULL,
    location          VARCHAR(255)    NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    minutes           TEXT            NULL,
    created_by        BIGINT UNSIGNED NOT NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ba_meetings_project FOREIGN KEY (client_project_id) REFERENCES client_projects (id),
    CONSTRAINT fk_ba_meetings_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_ba_meetings_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    INDEX idx_ba_meetings_scheduled (status, scheduled_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
