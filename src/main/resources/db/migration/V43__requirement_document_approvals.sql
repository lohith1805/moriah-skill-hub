-- Feature: multi-party sign-off on requirement documents (frontend gap — the single IN_REVIEW ->
-- APPROVED flip could be done by any BA, including the document's own author, and nobody but a
-- BA/ADMIN could ever see or act on a document at all — not the client whose project it is, not
-- the developer who has to build it).
--
-- One row per required approval "slot" on a document, pre-created at authoring time from its
-- doc_type: BRD/FRS need CLIENT + BUSINESS_ANALYST + DEVELOPER; SRS/USER_STORY need only
-- BUSINESS_ANALYST + DEVELOPER (the client doesn't need to review internal/technical specs).
-- A slot is "pending" while approved_by is NULL; the document's own status flips IN_REVIEW ->
-- APPROVED once every one of its slots is filled (see RequirementDocumentService).
CREATE TABLE requirement_document_approvals (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id   BIGINT UNSIGNED NOT NULL,
    approver_role VARCHAR(30)     NOT NULL,
    approved_by   BIGINT UNSIGNED NULL,
    approved_at   DATETIME(6)     NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_requirement_document_approvals (document_id, approver_role),
    CONSTRAINT fk_rda_document FOREIGN KEY (document_id) REFERENCES requirement_documents (id),
    CONSTRAINT fk_rda_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_rda_role CHECK (approver_role IN ('CLIENT', 'BUSINESS_ANALYST', 'DEVELOPER')),
    INDEX idx_rda_document (document_id),
    INDEX idx_rda_approved_by (approved_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
