-- Feature: developer "Client Requirements" review (frontend-integration gap B1.16). The BA
-- authors requirement_documents and a BA/ADMIN approves them (feature 21). The FE also needs a
-- developer-facing acknowledgement: a developer records that they have read the requirement they
-- will build against. That is a separate axis from the BA approval `status`, so it is a
-- timestamp + who, not another status value. NULL dev_reviewed_at = not yet acknowledged.

ALTER TABLE requirement_documents
    ADD COLUMN dev_reviewed_at DATETIME(6)     NULL AFTER approved_by,
    ADD COLUMN dev_reviewed_by BIGINT UNSIGNED NULL AFTER dev_reviewed_at,
    ADD CONSTRAINT fk_requirement_documents_dev_reviewed_by
        FOREIGN KEY (dev_reviewed_by) REFERENCES users (id);
