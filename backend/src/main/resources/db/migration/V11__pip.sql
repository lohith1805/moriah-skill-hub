-- Feature 17. The PIP rule engine: six fixed evaluators (Java classes, see pip/engine/), each
-- mapped to one student_metrics column, with thresholds/severity/active-flag as admin-tunable rows
-- in pip_rules rather than Java constants (architecture.md: "PIP thresholds are rows in
-- pip_rules. A numeric threshold in Java is a defect.").

CREATE TABLE pip_rules (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    rule_code       VARCHAR(30)      NOT NULL,
    description     VARCHAR(255)     NOT NULL,
    threshold_value DECIMAL(6,2)     NOT NULL,
    -- Informational only — NOT read by any evaluator to change what it computes. The two real
    -- lookback windows this rule set depends on (attendance's rolling 14 days, task-overdue's
    -- 48 hours) are schema-fixed in student_metrics itself (Constants.java on the Java side,
    -- feature 16's own decision), not re-derived from this column at evaluation time. Storing and
    -- exposing it via PUT anyway would create a config value that looks live but silently isn't —
    -- the exact anti-pattern feature 16's own /review flagged for tasks_overdue_48h. Left NULL for
    -- rules with no independent "window" concept beyond their own threshold_value.
    window_days     SMALLINT UNSIGNED NULL,
    severity        VARCHAR(20)      NOT NULL,
    is_active       BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_pip_rules_code (rule_code),
    CONSTRAINT chk_pip_rules_code CHECK (rule_code IN
        ('ATTENDANCE_LOW', 'PROJECT_DELAY', 'ASSIGNMENT_MISSED', 'QUIZ_FAILURE', 'REVIEW_FAILED', 'TASK_ABANDONED')),
    -- Not spelled out in architecture.md — inferred as a simple three-tier scale, shared with
    -- pip_records.severity below.
    CONSTRAINT chk_pip_rules_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pip_records (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED  NOT NULL,
    batch_id          BIGINT UNSIGNED  NOT NULL,
    rule_code         VARCHAR(30)      NOT NULL,
    trigger_reason    TEXT             NOT NULL,
    severity          VARCHAR(20)      NOT NULL,
    triggered_at      DATETIME(6)      NOT NULL,
    start_date        DATE             NOT NULL,
    end_date          DATE             NOT NULL,
    status            VARCHAR(20)      NOT NULL DEFAULT 'TRIGGERED',
    blocks_task_pull  BOOLEAN          NOT NULL DEFAULT FALSE,
    reviewed_by       BIGINT UNSIGNED  NULL,
    review_notes      TEXT             NULL,
    outcome_at        DATETIME(6)      NULL,
    -- One open record per user (architecture.md V10) — MySQL has no partial unique indexes
    -- (code-standards.md "Conditional Uniqueness"), same STORED-generated-column pattern as
    -- user_subscriptions.active_user_id (V3) and pending_batch_allocations (V9).
    open_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        IF(status IN ('TRIGGERED', 'IN_PROGRESS'), user_id, NULL)
    ) STORED,
    created_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_one_open_pip (open_user_id),
    CONSTRAINT fk_pip_records_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_pip_records_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_pip_records_rule FOREIGN KEY (rule_code) REFERENCES pip_rules (rule_code),
    CONSTRAINT fk_pip_records_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT chk_pip_records_status CHECK (status IN ('TRIGGERED', 'IN_PROGRESS', 'CLEARED', 'TERMINATED', 'REASSIGNED')),
    CONSTRAINT chk_pip_records_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    -- GET /pip?batchId=&status= reads exactly this composite; GET /pip/me keys off user_id alone.
    -- The nightly open-user-ids lookup (PipEvaluationService.findOpenUserIds) instead filters on
    -- open_user_id IS NOT NULL and uses uq_one_open_pip directly, not this index.
    INDEX idx_pip_records_batch_status (batch_id, status),
    INDEX idx_pip_records_user (user_id),
    -- TaskPullGuard's blocksPull check (via PipService) runs on every POST /tasks/{id}/pull,
    -- across the whole platform, forever — by far the hottest read this feature has. A covering
    -- index on exactly its WHERE clause turns that into an index-only lookup instead of a
    -- clustered-index probe per candidate row (`/review` finding).
    INDEX idx_pip_records_pull_block (user_id, status, blocks_task_pull)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pip_milestones (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pip_record_id  BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(200)    NOT NULL,
    description    TEXT            NULL,
    due_date       DATE            NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    completed_at   DATETIME(6)     NULL,
    verified_by    BIGINT UNSIGNED NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_pip_milestones_record FOREIGN KEY (pip_record_id) REFERENCES pip_records (id),
    CONSTRAINT fk_pip_milestones_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT chk_pip_milestones_status CHECK (status IN ('PENDING', 'COMPLETED', 'MISSED')),
    INDEX idx_pip_milestones_record (pip_record_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Seed the six fixed rules build-plan.md names, with its own default thresholds. Not a
-- feature-flag catalogue — PipEvaluationService's evaluator list is a fixed six Java classes;
-- these rows exist purely so their thresholds/severity/active-flag are admin-editable config, not
-- so new rule codes can be added without a matching evaluator class.
INSERT INTO pip_rules (rule_code, description, threshold_value, window_days, severity, is_active) VALUES
    ('ATTENDANCE_LOW', 'Attendance below threshold over the trailing window', 75.00, 14, 'HIGH', TRUE),
    ('PROJECT_DELAY', 'One or more committed tasks overdue past the delay window', 1.00, 2, 'MEDIUM', TRUE),
    ('ASSIGNMENT_MISSED', 'Consecutive weekly assignments missed', 2.00, NULL, 'MEDIUM', TRUE),
    ('QUIZ_FAILURE', 'Quiz average below the passing threshold', 60.00, NULL, 'MEDIUM', TRUE),
    ('REVIEW_FAILED', 'One or more unsatisfactory weekly reviews on record', 1.00, NULL, 'LOW', TRUE),
    ('TASK_ABANDONED', 'No recorded activity for several consecutive days', 3.00, NULL, 'HIGH', TRUE);
