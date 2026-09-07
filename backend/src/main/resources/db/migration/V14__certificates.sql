-- Feature 20 — Certificate Engine, Graduation and Public Verification. architecture.md's
-- "V13 — Certificates" heading is stale — progress-tracker.md's Migration Ledger is authoritative:
-- V9 was reassigned to batch_allocation.sql when feature 10 was `/architect`'d mid-plan, shifting
-- every placeholder after it down one version, so this table lands in V14 here, not V13.

-- certificate_number is NULL-able, not NOT NULL, deliberately — CertificateService#issue inserts
-- the row first (IDENTITY generation assigns the id immediately), then computes
-- "MSH-CERT-{year}-{id, zero-padded}" from that now-known id and updates the same row in the same
-- transaction (Constants.CERTIFICATE_PREFIX's own Javadoc). A NOT NULL column would reject the
-- first INSERT outright. MySQL's unique index permits any number of NULLs, so the brief window
-- where a row has no number yet never risks a false collision.
--
-- certificate_type has a real enumerated value set unlike hr_documents.document_type or
-- leads.lead_type (both left free text) — COMPLETION/EXCELLENCE, matching the new CertificateType
-- enum, closed and small enough to be worth a CHECK constraint (code-standards.md: "Enums are
-- VARCHAR with a CHECK constraint").
--
-- No explicit ON DELETE clause on any FK — matches V13__hr.sql's exact convention (InnoDB's
-- default, RESTRICT, is what every other migration in this schema already relies on implicitly).
CREATE TABLE certificates (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    batch_id            BIGINT UNSIGNED NOT NULL,
    certificate_number  VARCHAR(30)     NULL,
    certificate_type    VARCHAR(20)     NOT NULL DEFAULT 'COMPLETION',
    verification_code   CHAR(12)        NOT NULL,
    pdf_key             VARCHAR(255)    NULL,
    issued_by           BIGINT UNSIGNED NOT NULL,
    issued_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at          DATETIME(6)     NULL,
    revoke_reason       VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_certificates_number (certificate_number),
    -- The only column the public verification endpoint is allowed to look up by (build-plan.md:
    -- "never accept a certificate id here") — this unique index is also what makes that lookup
    -- the hot path's genuinely indexed point read, not a scan.
    UNIQUE KEY uq_certificates_verification_code (verification_code),
    CONSTRAINT fk_certificates_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_certificates_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_certificates_issued_by FOREIGN KEY (issued_by) REFERENCES users (id),
    CONSTRAINT chk_certificates_type CHECK (certificate_type IN ('COMPLETION', 'EXCELLENCE')),
    -- GET /certificates/me (by user) and the eventual "certificates for this batch" admin view
    -- (by batch) are the only two query patterns beyond the verification-code point lookup above.
    INDEX idx_certificates_user (user_id),
    INDEX idx_certificates_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
