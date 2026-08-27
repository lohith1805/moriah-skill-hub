-- Feature 06. Learning schema, part 2: submissions, reviews, standups, attendance.

-- Unique (task_id, user_id, attempt_number) — a double-POST of the same attempt cannot create
-- two rows (build-plan.md feature 06 / feature 12's idempotency requirement).
CREATE TABLE task_submissions (
    id                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    task_id            BIGINT UNSIGNED  NOT NULL,
    user_id            BIGINT UNSIGNED  NOT NULL,
    attempt_number     TINYINT UNSIGNED NOT NULL DEFAULT 1,
    pr_url             VARCHAR(500)     NULL,
    repo_owner         VARCHAR(100)     NULL,
    repo_name          VARCHAR(100)     NULL,
    pr_number          INT UNSIGNED     NULL,
    -- Not spelled out in architecture.md — inferred as GitHub's own three PR states (feature 12
    -- reads this from the GitHub API verbatim).
    pr_state           VARCHAR(20)      NULL,
    commit_count       SMALLINT UNSIGNED NULL,
    latest_commit_sha  CHAR(40)         NULL,
    video_url          VARCHAR(500)     NULL,
    notes              TEXT             NULL,
    status             VARCHAR(30)      NOT NULL DEFAULT 'SUBMITTED',
    submitted_at       DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    verified_at        DATETIME(6)      NULL,
    created_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_task_submissions_task_user_attempt (task_id, user_id, attempt_number),
    CONSTRAINT fk_task_submissions_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_task_submissions_pr_state CHECK (pr_state IS NULL OR pr_state IN ('OPEN', 'CLOSED', 'MERGED')),
    CONSTRAINT chk_task_submissions_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'CHANGES_REQUESTED')),
    INDEX idx_task_submissions_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE code_reviews (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submission_id    BIGINT UNSIGNED NOT NULL,
    reviewer_id      BIGINT UNSIGNED NOT NULL,
    score            TINYINT UNSIGNED NULL,
    verdict          VARCHAR(20)     NOT NULL,
    comments         TEXT            NULL,
    inline_comments  JSON            NULL,
    reviewed_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_code_reviews_submission FOREIGN KEY (submission_id) REFERENCES task_submissions (id),
    CONSTRAINT fk_code_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT chk_code_reviews_score CHECK (score IS NULL OR score BETWEEN 1 AND 10),
    CONSTRAINT chk_code_reviews_verdict CHECK (verdict IN ('APPROVED', 'CHANGES_REQUESTED')),
    INDEX idx_code_reviews_submission (submission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Didn't exist in an earlier version of architecture.md; added because the REVIEW_FAILED PIP
-- rule (feature 17) has nothing to read without it — the rule counts UNSATISFACTORY rows.
CREATE TABLE weekly_reviews (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id     BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    sprint_id    BIGINT UNSIGNED NOT NULL,
    week_start   DATE            NOT NULL,
    rating       VARCHAR(20)     NOT NULL,
    notes        TEXT            NULL,
    reviewed_by  BIGINT UNSIGNED NOT NULL,
    reviewed_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_weekly_reviews_user_week (user_id, week_start),
    CONSTRAINT fk_weekly_reviews_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_weekly_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_weekly_reviews_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id),
    CONSTRAINT fk_weekly_reviews_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_weekly_reviews_rating CHECK (rating IN ('SATISFACTORY', 'NEEDS_IMPROVEMENT', 'UNSATISFACTORY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE standups (
    id                   BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    batch_id             BIGINT UNSIGNED   NOT NULL,
    sprint_id            BIGINT UNSIGNED   NULL,
    scheduled_at         DATETIME(6)       NOT NULL,
    -- Not spelled out in architecture.md — inferred as a reasonable default grace period;
    -- AttendanceFinalisationJob (feature 13) reads this per-standup, not a global constant, so
    -- the default only matters until a PM overrides it.
    late_cutoff_minutes  SMALLINT UNSIGNED NOT NULL DEFAULT 15,
    conducted_by         BIGINT UNSIGNED   NULL,
    notes                TEXT              NULL,
    status               VARCHAR(20)       NOT NULL DEFAULT 'SCHEDULED',
    -- Set by AttendanceFinalisationJob (feature 13) — what stops it reprocessing a standup.
    finalised_at         DATETIME(6)       NULL,
    created_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_standups_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_standups_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id),
    CONSTRAINT fk_standups_conducted_by FOREIGN KEY (conducted_by) REFERENCES users (id),
    CONSTRAINT chk_standups_status CHECK (status IN ('SCHEDULED', 'CONDUCTED', 'CANCELLED')),
    INDEX idx_standups_batch (batch_id),
    INDEX idx_standups_finalised (finalised_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attendance (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    standup_id       BIGINT UNSIGNED NOT NULL,
    user_id          BIGINT UNSIGNED NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    checked_in_at    DATETIME(6)     NULL,
    blocker_notes    TEXT            NULL,
    marked_by        BIGINT UNSIGNED NULL,
    is_auto_marked   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_attendance_standup_user (standup_id, user_id),
    CONSTRAINT fk_attendance_standup FOREIGN KEY (standup_id) REFERENCES standups (id),
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_attendance_marked_by FOREIGN KEY (marked_by) REFERENCES users (id),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    -- build-plan.md feature 06's explicitly required composite index (PIP attendance-percentage
    -- rule, feature 17, reads this per-student ordered by recency).
    INDEX idx_attendance_user_status_created (user_id, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
