-- Feature 21 — BA and Client Portal. architecture.md's "V14 — BA and Client" heading is stale —
-- progress-tracker.md's Migration Ledger is authoritative: V9 was reassigned to
-- batch_allocation.sql when feature 10 was `/architect`'d mid-plan, shifting every placeholder
-- after it down one version, so this table set lands in V15 here, not V14 (same reassignment
-- V13__hr.sql/V14__certificates.sql already document).

-- clients.user_id is the only nullable FK on this table (architecture.md) — a client company can
-- exist with no portal login at all ("CLIENT users are provisioned by ADMIN — a client cannot
-- self-register"; if the create request omits portal-login fields, the row is saved with
-- user_id NULL). phone/industry are supplementary contact details, nullable like
-- leads.institution; company_name/contact_person/email are the row's actual identity.
-- status has no enumerated value set in architecture.md; this build's own minimal closed set
-- (see ClientStatus's own Javadoc for why only ACTIVE is reachable from this feature).
CREATE TABLE clients (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_name   VARCHAR(150)    NOT NULL,
    contact_person VARCHAR(150)    NOT NULL,
    email          VARCHAR(180)    NOT NULL,
    phone          VARCHAR(20)     NULL,
    industry       VARCHAR(100)    NULL,
    user_id        BIGINT UNSIGNED NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_clients_user (user_id),
    CONSTRAINT fk_clients_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    INDEX idx_clients_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- target_batch_id nullable — a submitted project may have no batch allocated yet (build-plan.md
-- feature 21 decision: "If target_batch_id is null ... return an empty/zeroed progress shape,
-- not an error" — see ClientProjectService#progress). status has no enumerated value set in
-- architecture.md; this build's own minimal closed set — see ClientProjectStatus's own Javadoc
-- for which of these this feature's one creation endpoint can actually reach.
CREATE TABLE client_projects (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    client_id         BIGINT UNSIGNED NOT NULL,
    title             VARCHAR(200)    NOT NULL,
    scope_description TEXT            NOT NULL,
    budget_range      VARCHAR(50)     NULL,
    target_batch_id   BIGINT UNSIGNED NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    submitted_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_client_projects_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_client_projects_batch FOREIGN KEY (target_batch_id) REFERENCES batches (id),
    CONSTRAINT chk_client_projects_status CHECK (status IN ('SUBMITTED', 'IN_PROGRESS', 'COMPLETED')),
    INDEX idx_client_projects_client (client_id),
    INDEX idx_client_projects_batch (target_batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- client_project_id nullable per architecture.md — this feature's own POST /ba/documents always
-- supplies one (CreateRequirementDocumentRequest.clientProjectId is @NotNull), but the column is
-- left nullable to match architecture.md's schema exactly for a possible future "general"
-- document not tied to any specific project (no such route exists yet). doc_type is a real
-- enumerated set (architecture.md: "BRD/SRS/FRS/user story"); status starts at IN_REVIEW, never
-- DRAFT, from this feature's own creation endpoint (see RequirementDocumentStatus's own
-- Javadoc). Versioning ("a new document for the same (client_project_id, doc_type) pair gets
-- version = max existing version for that pair + 1") is enforced in
-- RequirementDocumentService, not by a DB constraint — client_project_id's own nullability would
-- make a UNIQUE KEY on (client_project_id, doc_type, version) admit unlimited NULL-project
-- duplicates anyway (MySQL treats NULL as distinct in a unique index), so the real guarantee has
-- to live in application code regardless. file_key is schema-only for this feature — content
-- (LONGTEXT) is the one storage mechanism POST /ba/documents actually uses; no upload endpoint
-- exists in build-plan.md's feature 21 list to ever populate it.
CREATE TABLE requirement_documents (
    id                 BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    client_project_id  BIGINT UNSIGNED   NULL,
    doc_type           VARCHAR(20)       NOT NULL,
    title              VARCHAR(200)      NOT NULL,
    version            SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    content            LONGTEXT          NOT NULL,
    file_key           VARCHAR(255)      NULL,
    status             VARCHAR(20)       NOT NULL DEFAULT 'IN_REVIEW',
    authored_by        BIGINT UNSIGNED   NOT NULL,
    approved_by        BIGINT UNSIGNED   NULL,
    created_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_requirement_documents_project FOREIGN KEY (client_project_id) REFERENCES client_projects (id),
    CONSTRAINT fk_requirement_documents_authored_by FOREIGN KEY (authored_by) REFERENCES users (id),
    CONSTRAINT fk_requirement_documents_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_requirement_documents_type CHECK (doc_type IN ('BRD', 'SRS', 'FRS', 'USER_STORY')),
    CONSTRAINT chk_requirement_documents_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED')),
    INDEX idx_requirement_documents_project_type (client_project_id, doc_type),
    INDEX idx_requirement_documents_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Maps a student/staff user and a batch onto a client project. No status column — no lifecycle,
-- this is a create-only mapping table (build-plan.md's feature 21 endpoint list has no update/
-- list/delete route for it).
CREATE TABLE resource_allocations (
    id                    BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    client_project_id     BIGINT UNSIGNED   NOT NULL,
    batch_id              BIGINT UNSIGNED   NOT NULL,
    user_id               BIGINT UNSIGNED   NOT NULL,
    role_in_project       VARCHAR(100)      NOT NULL,
    allocated_days        SMALLINT UNSIGNED NOT NULL,
    story_points_estimate SMALLINT UNSIGNED NULL,
    from_date             DATE              NOT NULL,
    to_date               DATE              NOT NULL,
    created_at            DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_resource_allocations_project FOREIGN KEY (client_project_id) REFERENCES client_projects (id),
    CONSTRAINT fk_resource_allocations_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_resource_allocations_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_resource_allocations_project (client_project_id),
    INDEX idx_resource_allocations_batch (batch_id),
    INDEX idx_resource_allocations_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
