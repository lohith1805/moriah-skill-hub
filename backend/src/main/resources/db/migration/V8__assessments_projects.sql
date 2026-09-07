-- Feature 06. Learning schema, part 3: projects/content, then quizzes/assessments (quizzes.
-- project_id needs `projects` to already exist, so that group is created first, out of the
-- order architecture.md lists them in). Finishes by adding the `tasks.project_id` FK deferred
-- from V6 — see that file's header.

CREATE TABLE projects (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title              VARCHAR(200)    NOT NULL,
    slug               VARCHAR(200)    NOT NULL,
    description        TEXT            NULL,
    tech_stack         JSON            NULL,
    -- Not spelled out in architecture.md — inferred as the standard three-tier difficulty scale,
    -- shared with bug_challenges.difficulty below.
    difficulty         VARCHAR(20)     NULL,
    domain             VARCHAR(100)    NULL,
    starter_repo_url   VARCHAR(500)    NULL,
    version            VARCHAR(10)     NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    created_by         BIGINT UNSIGNED NOT NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_projects_slug (slug),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_projects_difficulty CHECK (difficulty IS NULL OR difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT chk_projects_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_assets (
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    project_id     BIGINT UNSIGNED  NOT NULL,
    -- Not spelled out in architecture.md — inferred as the asset shapes a project brief
    -- plausibly needs (screenshots, demo videos, design files, written docs).
    asset_type     VARCHAR(30)      NOT NULL,
    title          VARCHAR(200)     NULL,
    file_key       VARCHAR(255)     NULL,
    external_url   VARCHAR(500)     NULL,
    sort_order     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    created_at     DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_project_assets_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT chk_project_assets_type CHECK (asset_type IN ('IMAGE', 'VIDEO', 'DOCUMENT', 'DESIGN_FILE')),
    -- architecture.md: "exactly one of file_key / external_url is non-null" — a stored asset or a
    -- link, never both, never neither.
    CONSTRAINT chk_project_assets_source CHECK ((file_key IS NULL) <> (external_url IS NULL)),
    INDEX idx_project_assets_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bug_challenges (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id           BIGINT UNSIGNED NOT NULL,
    title                VARCHAR(200)    NOT NULL,
    broken_code_key      VARCHAR(255)    NOT NULL,
    expected_behaviour   TEXT            NOT NULL,
    test_script_key      VARCHAR(255)    NULL,
    difficulty           VARCHAR(20)     NULL,
    created_by           BIGINT UNSIGNED NOT NULL,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bug_challenges_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_bug_challenges_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_bug_challenges_difficulty CHECK (difficulty IS NULL OR difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    INDEX idx_bug_challenges_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE quizzes (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    project_id        BIGINT UNSIGNED  NULL,
    batch_id          BIGINT UNSIGNED  NULL,
    title             VARCHAR(200)     NOT NULL,
    duration_minutes  SMALLINT UNSIGNED NOT NULL,
    pass_percentage   TINYINT UNSIGNED NOT NULL DEFAULT 60,
    -- Not spelled out in architecture.md — inferred default of one attempt; quizzes needing more
    -- set it explicitly per-row, this is only the default.
    max_attempts      TINYINT UNSIGNED NOT NULL DEFAULT 1,
    created_by        BIGINT UNSIGNED  NOT NULL,
    is_active         BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_quizzes_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_quizzes_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_quizzes_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE quiz_questions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id        BIGINT UNSIGNED NOT NULL,
    question_text  TEXT            NOT NULL,
    question_type  VARCHAR(20)     NOT NULL,
    options        JSON            NULL,
    correct_answer JSON            NULL,
    marks          TINYINT UNSIGNED NOT NULL,
    explanation    TEXT            NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT chk_quiz_questions_type CHECK (question_type IN ('MCQ', 'MULTI_SELECT', 'CODE')),
    INDEX idx_quiz_questions_quiz (quiz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Percentage is auto_graded_marks / auto_gradable_marks — ungraded CODE questions are excluded
-- from the denominator entirely (architecture.md V8, library-docs.md). Scoring them zero would
-- fail students on work nobody marked and fire QUIZ_FAILURE against them.
CREATE TABLE quiz_attempts (
    id                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    quiz_id               BIGINT UNSIGNED  NOT NULL,
    user_id               BIGINT UNSIGNED  NOT NULL,
    attempt_number        TINYINT UNSIGNED NOT NULL DEFAULT 1,
    started_at            DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at          DATETIME(6)      NULL,
    auto_graded_marks     DECIMAL(6,2)     NULL,
    auto_gradable_marks   DECIMAL(6,2)     NULL,
    percentage            DECIMAL(5,2)     NULL,
    passed                BOOLEAN          NULL,
    status                VARCHAR(30)      NOT NULL DEFAULT 'IN_PROGRESS',
    created_at            DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Not spelled out explicitly in architecture.md, but inferred from the same
    -- (parent, user, attempt_number) uniqueness pattern used by task_submissions above — a
    -- double-POST of the same attempt must not create two rows here either.
    UNIQUE KEY uq_quiz_attempts_quiz_user_attempt (quiz_id, user_id, attempt_number),
    CONSTRAINT fk_quiz_attempts_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_quiz_attempts_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'PENDING_MANUAL_GRADING')),
    INDEX idx_quiz_attempts_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE quiz_answers (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attempt_id     BIGINT UNSIGNED NOT NULL,
    question_id    BIGINT UNSIGNED NOT NULL,
    given_answer   JSON            NULL,
    is_correct     BOOLEAN         NULL,
    marks_awarded  DECIMAL(5,2)    NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Inferred, same reasoning as quiz_attempts above: one answer per question per attempt.
    UNIQUE KEY uq_quiz_answers_attempt_question (attempt_id, question_id),
    CONSTRAINT fk_quiz_answers_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id),
    CONSTRAINT fk_quiz_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The ASSIGNMENT_MISSED PIP rule (feature 17) needs discrete weekly windows to count consecutive
-- misses against — without this table, "2+ consecutive weekly assignment windows" has no
-- definition. assignment_submissions_view is not a real table/view: the rule joins this against
-- task_submissions on the window's task_id at read time.
CREATE TABLE assignment_windows (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id    BIGINT UNSIGNED NOT NULL,
    week_start  DATE            NOT NULL,
    week_end    DATE            NOT NULL,
    due_at      DATETIME(6)     NOT NULL,
    task_id     BIGINT UNSIGNED NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_assignment_windows_batch_week (batch_id, week_start),
    CONSTRAINT fk_assignment_windows_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_assignment_windows_task FOREIGN KEY (task_id) REFERENCES tasks (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Deferred from V6 — projects didn't exist yet when tasks was created. See that file's header.
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id);
