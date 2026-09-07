-- Feature: Client Talent Pool + recruitment requests (frontend-integration gap B1.9). The FE
-- lets a client browse candidate profiles and submit a request to recruit one. The talent-pool
-- browse itself is a read over the existing user_profiles table (complete + published portfolio)
-- and needs no schema. This migration adds the recruitment_requests object.
--
-- A candidate's resume is deliberately NOT exposed through this flow yet — presigning another
-- user's resume key to a client needs a dedicated OwnershipGuard branch (IDOR-sensitive) and is
-- a separate change.

CREATE TABLE recruitment_requests (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    candidate_id    BIGINT UNSIGNED NOT NULL,
    requested_by    BIGINT UNSIGNED NOT NULL,
    role_title      VARCHAR(150)    NOT NULL,
    engagement_type VARCHAR(20)     NOT NULL,
    message         TEXT            NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    decision_note   VARCHAR(500)    NULL,
    decided_by      BIGINT UNSIGNED NULL,
    decided_at      DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recruitment_requests_candidate FOREIGN KEY (candidate_id) REFERENCES users (id),
    CONSTRAINT fk_recruitment_requests_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_recruitment_requests_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT chk_recruitment_requests_engagement CHECK (engagement_type IN (
        'FULL_TIME', 'CONTRACT', 'INTERNSHIP', 'PROJECT')),
    CONSTRAINT chk_recruitment_requests_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    INDEX idx_recruitment_requests_requester (requested_by, status),
    INDEX idx_recruitment_requests_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
