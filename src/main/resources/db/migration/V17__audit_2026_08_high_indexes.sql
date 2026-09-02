-- Audit 2026-08-31, HIGH severity (H5). Index-only, no schema/entity change.
--
-- SubmissionVerificationRetryJob runs TaskSubmissionRepository.findByVerifiedAtIsNull() every 15
-- minutes to find submissions still awaiting GitHub verification. task_submissions (V7) has no
-- index on verified_at, so that query full-scans the whole table 96 times a day forever to
-- return a handful of rows. InnoDB indexes NULLs (sorted first), so `verified_at IS NULL` becomes
-- a short range scan. Same shape as the full-scan gaps V16 closed elsewhere; this one was missed.

ALTER TABLE task_submissions
    ADD INDEX idx_task_submissions_verified_at (verified_at),
    ALGORITHM = INPLACE, LOCK = NONE;
