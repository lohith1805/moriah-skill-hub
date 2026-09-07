-- Feature 16. Refreshed table, not nested views (architecture.md: MySQL 8 does not materialize
-- views; three stacked aggregate views would re-scan attendance/tasks/quiz_attempts in full on
-- every PIP run and degrade as history accumulates). MetricsRefreshJob recomputes every row
-- nightly at 01:45 IST; PipEvaluationJob (feature 17) reads this table in one indexed query.

CREATE TABLE student_metrics (
    id                               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id                          BIGINT UNSIGNED NOT NULL,
    batch_id                         BIGINT UNSIGNED NOT NULL,
    computed_at                      DATETIME(6)     NOT NULL,
    attendance_present               SMALLINT        NOT NULL DEFAULT 0,
    attendance_total                 SMALLINT        NOT NULL DEFAULT 0,
    attendance_percent               DECIMAL(5,2)    NULL,
    tasks_assigned                   SMALLINT        NOT NULL DEFAULT 0,
    tasks_completed                  SMALLINT        NOT NULL DEFAULT 0,
    tasks_overdue_48h                SMALLINT        NOT NULL DEFAULT 0,
    task_completion_percent          DECIMAL(5,2)    NULL,
    days_since_last_activity         SMALLINT        NULL,
    quiz_attempts_count              SMALLINT        NOT NULL DEFAULT 0,
    quiz_average_percent             DECIMAL(5,2)    NULL,
    consecutive_assignments_missed   TINYINT         NOT NULL DEFAULT 0,
    unsatisfactory_reviews           TINYINT         NOT NULL DEFAULT 0,
    created_at                       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_student_metrics_user_batch (user_id, batch_id),
    CONSTRAINT fk_student_metrics_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_student_metrics_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    -- PipEvaluationJob (feature 17) loads the whole active cohort from this table in ONE query,
    -- ordered/filtered by batch — this index makes that a direct index scan, not a full scan.
    INDEX idx_student_metrics_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Genuine views, kept for small admin-facing reads only (architecture.md) — not on the nightly
-- critical path, never queried by MetricsRefreshJob or PipEvaluationJob.

CREATE VIEW v_batch_velocity AS
SELECT sp.batch_id,
       b.name AS batch_name,
       sp.sprint_number,
       sp.planned_points,
       sp.completed_points
FROM sprints sp
JOIN batches b ON b.id = sp.batch_id;

CREATE VIEW v_revenue_monthly AS
SELECT DATE_FORMAT(p.captured_at, '%Y-%m') AS revenue_month,
       p.currency,
       SUM(p.amount) AS total_captured
FROM payments p
WHERE p.status = 'CAPTURED'
GROUP BY DATE_FORMAT(p.captured_at, '%Y-%m'), p.currency;

-- v_lead_funnel ("Lead counts per status per agent per month") is deferred to V12 (feature 18,
-- CRM) — its source table, `leads`, does not exist until that migration. Same deferred-dependency
-- treatment as tasks.project_id (V6 -> V8): the two views this migration CAN build from
-- already-existing tables are built now; the third is added where its dependency is created.

-- Indexes StudentMetricsService's nightly aggregate reads need on tables owned by earlier
-- migrations. Added here via ALTER TABLE, not by editing V6/V7/V8 directly — the same
-- deferred-dependency precedent as V8's `tasks.project_id` FK: a Flyway migration already applied
-- is never modified, so a later feature's genuinely new access pattern adds what it needs in its
-- own migration. Without these, four of MetricsRefreshJob's five aggregate queries full-scan their
-- source table's entire history every night (batch_students/standups/quiz_attempts/weekly_reviews
-- all lacked an index on the column this job filters by) — cost that grows with total historical
-- row count, not with the ~5,000-student active cohort the "under 30 seconds" verify line budgets
-- for, and would erode that budget silently as more cohorts complete their programs.
ALTER TABLE batch_students ADD INDEX idx_batch_students_status (status);
ALTER TABLE standups ADD INDEX idx_standups_scheduled_at (scheduled_at);
ALTER TABLE quiz_attempts ADD INDEX idx_quiz_attempts_status (status);
ALTER TABLE weekly_reviews ADD INDEX idx_weekly_reviews_rating (rating);
