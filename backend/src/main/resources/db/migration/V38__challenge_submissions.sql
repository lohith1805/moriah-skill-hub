-- Feature: bug-fix challenge submissions (frontend gap — student/Projects.jsx could only toggle a
-- localStorage "fixed" flag; there was no place for a student to actually submit their rewritten
-- code). The student is shown the broken code + the expected behaviour, rewrites the fix, and
-- submits it here. A developer / trainer can then read every submission and (optionally) leave
-- feedback and a score. Row-per-submission — a student may resubmit, each attempt is kept.
CREATE TABLE challenge_submissions (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    challenge_id      BIGINT UNSIGNED NOT NULL,
    student_id        BIGINT UNSIGNED NOT NULL,
    -- The student's rewritten code, pasted in the browser (no file upload for a submission).
    solution_code     MEDIUMTEXT      NOT NULL,
    -- Optional "what I changed and why" note from the student.
    notes             TEXT            NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    reviewer_id       BIGINT UNSIGNED NULL,
    reviewer_feedback TEXT            NULL,
    -- 0-100, set by the reviewer. NULL until reviewed.
    score             TINYINT UNSIGNED NULL,
    submitted_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at       DATETIME(6)     NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_challenge_submissions_challenge FOREIGN KEY (challenge_id) REFERENCES bug_challenges (id),
    CONSTRAINT fk_challenge_submissions_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_challenge_submissions_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT chk_challenge_submissions_status
        CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'NEEDS_WORK')),
    CONSTRAINT chk_challenge_submissions_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    INDEX idx_challenge_submissions_challenge (challenge_id),
    INDEX idx_challenge_submissions_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
