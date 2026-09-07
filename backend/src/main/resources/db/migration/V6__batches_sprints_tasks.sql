-- Feature 06. Learning schema, part 1: batches, sprints, tasks. Schema-only — no entities/
-- services/endpoints ship in feature 06 (build-plan.md); the features that own each table
-- (10, 11, ...) add their own JPA entities when they're built.
--
-- tasks.project_id references `projects`, which doesn't exist until V8 (architecture.md groups
-- it under "Assessments and Content", not here) — the column is created here without the FK,
-- and the FK constraint itself is added at the end of V8 once `projects` exists. This is the one
-- forward dependency between these three files; every other FK resolves within the same or an
-- earlier migration.

-- No `version` column, deliberately — capacity is enforced by a conditional atomic update
-- (`UPDATE batches SET enrolled_count = enrolled_count + 1 WHERE id = ? AND enrolled_count <
-- capacity`), not optimistic locking (code-standards.md "Transactions": a hot counter under
-- launch-day load would thrash on retries under @Version). The CHECK below is defense-in-depth,
-- not the enforcement mechanism itself.
CREATE TABLE batches (
    id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    name              VARCHAR(150)      NOT NULL,
    track_code        VARCHAR(30)       NOT NULL,
    pm_id             BIGINT UNSIGNED   NOT NULL,
    -- Not spelled out further in architecture.md beyond "plan_tier_min FK" — inferred as an FK to
    -- subscription_plans, the minimum plan tier a student must hold to be allocated to this
    -- batch (feature 10 reads it). Nullable: a batch with no minimum-tier requirement is valid.
    plan_tier_min_id  BIGINT UNSIGNED   NULL,
    start_date        DATE              NOT NULL,
    end_date          DATE              NOT NULL,
    capacity          SMALLINT UNSIGNED NOT NULL,
    enrolled_count    SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    status            VARCHAR(20)       NOT NULL DEFAULT 'PLANNED',
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_batches_pm FOREIGN KEY (pm_id) REFERENCES users (id),
    CONSTRAINT fk_batches_plan_tier_min FOREIGN KEY (plan_tier_min_id) REFERENCES subscription_plans (id),
    CONSTRAINT chk_batches_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_batches_capacity CHECK (enrolled_count <= capacity),
    INDEX idx_batches_pm (pm_id),
    INDEX idx_batches_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE batch_students (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id      BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    joined_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    graduated_at  DATETIME(6)     NULL,
    graduated_by  BIGINT UNSIGNED NULL,
    final_score   DECIMAL(5,2)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_batch_students_batch_user (batch_id, user_id),
    CONSTRAINT fk_batch_students_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_batch_students_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_batch_students_graduated_by FOREIGN KEY (graduated_by) REFERENCES users (id),
    CONSTRAINT chk_batch_students_status CHECK (status IN ('ACTIVE', 'ON_PIP', 'GRADUATED', 'TERMINATED', 'REASSIGNED')),
    INDEX idx_batch_students_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sprints (
    id                BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    batch_id          BIGINT UNSIGNED   NOT NULL,
    sprint_number     TINYINT UNSIGNED  NOT NULL,
    goal              TEXT              NULL,
    start_date        DATE              NOT NULL,
    end_date          DATE              NOT NULL,
    status            VARCHAR(20)       NOT NULL DEFAULT 'PLANNED',
    planned_points    SMALLINT UNSIGNED NULL,
    completed_points  SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_sprints_batch_number (batch_id, sprint_number),
    CONSTRAINT fk_sprints_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT chk_sprints_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    sprint_id     BIGINT UNSIGNED  NOT NULL,
    -- FK added at the end of V8, once `projects` exists — see the file header.
    project_id    BIGINT UNSIGNED  NULL,
    title         VARCHAR(200)     NOT NULL,
    description   TEXT             NULL,
    task_type     VARCHAR(20)      NOT NULL,
    assigned_to   BIGINT UNSIGNED  NULL,
    story_points  TINYINT UNSIGNED NULL,
    due_at        DATETIME(6)      NULL,
    status        VARCHAR(20)      NOT NULL DEFAULT 'BACKLOG',
    completed_at  DATETIME(6)      NULL,
    created_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id),
    CONSTRAINT fk_tasks_assigned_to FOREIGN KEY (assigned_to) REFERENCES users (id),
    CONSTRAINT chk_tasks_type CHECK (task_type IN ('DAILY', 'ASSIGNMENT', 'STORY', 'BUGFIX')),
    CONSTRAINT chk_tasks_status CHECK (status IN ('BACKLOG', 'ASSIGNED', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED', 'REJECTED')),
    -- build-plan.md feature 06: the PM review queue is tasks awaiting review within a sprint,
    -- ordered by due date — exactly this composite, leftmost-prefix-usable for
    -- `WHERE sprint_id = ? AND status = 'IN_REVIEW' ORDER BY due_at` (proven by
    -- LearningSchemaIT's EXPLAIN assertion).
    INDEX idx_tasks_sprint_status_due (sprint_id, status, due_at),
    INDEX idx_tasks_assigned_status_due (assigned_to, status, due_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
