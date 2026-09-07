-- The multi-party sign-off flow (V43) only ever had one outcome for a document: approve.
-- There was no way for a CLIENT, BUSINESS_ANALYST, or DEVELOPER to say "this is wrong" — only to
-- approve it or leave it pending forever. Adds a REJECTED terminal status alongside APPROVED, and
-- the columns to record who rejected a document, when, and why (rejection_reason is the one piece
-- of feedback a reject actually needs to be useful — see RequirementDocumentApprovalService#reject).
ALTER TABLE requirement_documents
    DROP CHECK chk_requirement_documents_status;

ALTER TABLE requirement_documents
    ADD CONSTRAINT chk_requirement_documents_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED'));

ALTER TABLE requirement_documents
    ADD COLUMN rejected_by      BIGINT UNSIGNED NULL AFTER approved_by,
    ADD COLUMN rejected_at      DATETIME(6)     NULL AFTER rejected_by,
    ADD COLUMN rejection_reason TEXT            NULL AFTER rejected_at;

ALTER TABLE requirement_documents
    ADD CONSTRAINT fk_requirement_documents_rejected_by FOREIGN KEY (rejected_by) REFERENCES users (id);
