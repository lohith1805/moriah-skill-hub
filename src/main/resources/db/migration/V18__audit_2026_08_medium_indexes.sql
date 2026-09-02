-- Audit 2026-08-31, MEDIUM severity (M3 + M19). Index-only, no schema/entity change.

-- M3 --------------------------------------------------------------------------------------------
-- v_revenue_monthly: WHERE p.status = 'CAPTURED' GROUP BY DATE_FORMAT(captured_at,...) — payments
-- has no index on status or captured_at, so every materialisation full-scans the table + filesort.
-- Consumed uncached by MetricsService.revenue and the REVENUE export.
ALTER TABLE payments
    ADD INDEX idx_payments_status_captured (status, captured_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- v_lead_funnel: GROUP BY l.status, l.assigned_agent_id, DATE_FORMAT(l.created_at,...) — leads has
-- no index on created_at.
ALTER TABLE leads
    ADD INDEX idx_leads_created (created_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- TaskSubmissionRepository.search with taskId = null (GET /submissions?status=...) filters status
-- and orders by submitted_at with neither indexed → full scan + filesort of the whole table.
ALTER TABLE task_submissions
    ADD INDEX idx_task_submissions_status_submitted (status, submitted_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- M19 -------------------------------------------------------------------------------------------
-- idx_pip_records_user (user_id) is a strict left-prefix of idx_pip_records_pull_block
-- (user_id, status, blocks_task_pull), which also satisfies fk_pip_records_user's index
-- requirement — so the standalone index is pure write overhead.
ALTER TABLE pip_records
    DROP INDEX idx_pip_records_user,
    ALGORITHM = INPLACE, LOCK = NONE;

-- idx_standups_finalised (finalised_at) is a strict left-prefix of idx_standups_finalisation
-- (finalised_at, scheduled_at) added in V16; V16 explicitly deferred this drop.
ALTER TABLE standups
    DROP INDEX idx_standups_finalised,
    ALGORITHM = INPLACE, LOCK = NONE;
