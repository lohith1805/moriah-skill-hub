-- Adds OTHER to requirement_documents.doc_type — a BA authoring a document that isn't a
-- BRD/SRS/FRS/USER_STORY (e.g. a test plan, a change-request note) previously had no honest value
-- to pick and had to mislabel it. RequirementDocumentApprovalService's REQUIRED_ROLES map falls
-- back to BUSINESS_ANALYST+DEVELOPER for it, same as SRS/USER_STORY today.
ALTER TABLE requirement_documents
    DROP CHECK chk_requirement_documents_type;

ALTER TABLE requirement_documents
    ADD CONSTRAINT chk_requirement_documents_type CHECK (doc_type IN ('BRD', 'SRS', 'FRS', 'USER_STORY', 'OTHER'));
