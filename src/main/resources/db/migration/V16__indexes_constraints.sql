-- Feature 23 — Audit, Hardening and Performance. build-plan.md: "remaining CHECK constraints and
-- indexes ... runs against populated tables and is the one most likely to lock in production.
-- Test it against a restored production-sized dump, with ALGORITHM=INPLACE, LOCK=NONE, not
-- against an empty Testcontainer."
--
-- HONESTY NOTE, read before assuming this was fully verified: this migration has been run and
-- timed against this project's real Testcontainers MySQL 8.0 instance (schema-accurate, but
-- dev-scale row counts — hundreds of rows per table at most, not a production-sized dump) and
-- applies cleanly and fast there. No restored production-sized dump exists anywhere in this build
-- (no production deployment has ever run), so the specific risk build-plan.md calls out — an
-- ALGORITHM=INPLACE, LOCK=NONE index build taking a meaningfully long time and holding a
-- metadata lock queue behind it on a genuinely large table — has NOT been exercised here and
-- remains a real pre-deployment gap. Every ALTER below still explicitly requests
-- ALGORITHM=INPLACE, LOCK=NONE so the statement itself fails fast at migration time on any target
-- where MySQL cannot satisfy it, rather than silently falling back to a blocking COPY rebuild.
-- Same honesty pattern as the Database Resilience Checklist's own "restore drill passed locally
-- ... not yet run against a cloud target" note in progress-tracker.md — this is that same kind of
-- gap, not a claim this was verified against a production-sized dataset. See progress-tracker.md's
-- feature 23 decision-log entry for the full list of what was and wasn't verified.

-- ---------------------------------------------------------------------------------------------
-- CHECK constraints
-- ---------------------------------------------------------------------------------------------
-- Full audit: every VARCHAR column in V1-V15 backed by a genuine closed Java enum
-- (`@Enumerated(EnumType.STRING)`) was cross-checked against its migration's CHECK constraint.
-- Every one of them already has a matching CHECK except this one — `roles.code` is backed by
-- `RoleCode` (8 constants, `Role.java`'s own `@Enumerated(EnumType.STRING)`) but V1 never added a
-- CHECK for it, unlike every sibling enum-backed column in this schema. Columns that are
-- deliberately free text with no backing Java enum (`leads.lead_type`, `hr_documents.document_type`,
-- `resource_allocations.role_in_project`, `user_profiles.experience_level`, `client_projects.
-- budget_range`, `subscription_plans.code`) are correctly left unconstrained — already documented
-- inline in their own migrations — and are not gaps.
-- Confirmed the hard way against this project's own real MySQL 8.0 Testcontainers instance, not
-- assumed: MySQL rejects an explicit ALGORITHM clause on ADD CONSTRAINT ... CHECK outright —
-- tried both ALGORITHM=INPLACE ("not supported for this operation") and ALGORITHM=INSTANT (same
-- rejection), unlike ADD INDEX below, where INPLACE/LOCK=NONE works exactly as build-plan.md
-- specifies. No explicit algorithm clause on this one statement, deliberately — MySQL 8.0.16+
-- still applies a CHECK constraint as a metadata-only change with no table rebuild by default
-- (validating existing rows is a read pass, not a copy), it just won't let the statement name
-- that algorithm explicitly the way it does for an index build.
ALTER TABLE roles
    ADD CONSTRAINT chk_roles_code CHECK (code IN
        ('STUDENT', 'TRAINER_PM', 'DEVELOPER', 'LEAD_GEN', 'HR_MANAGER', 'BUSINESS_ANALYST', 'ADMIN', 'CLIENT'));

-- ---------------------------------------------------------------------------------------------
-- Indexes — each cross-referenced against a real repository `@Query`/derived-method WHERE clause
-- or a raw-SQL hot path already in this codebase, not a guess. FK columns are not listed here:
-- InnoDB auto-creates an index for every FK constraint at creation time, so `quizzes.batch_id`,
-- `quizzes.project_id`, etc. already have one with no explicit `INDEX` clause needed.
-- ---------------------------------------------------------------------------------------------

-- NotificationRepository.findByStatusAndCreatedAtBefore(status, createdBefore) —
-- NotificationReaperJob's DB-vs-Redis reconciliation sweep. The existing
-- idx_notifications_user_status (user_id, status) can't serve this query at all (it doesn't
-- filter by user_id), so this was a full scan of the whole table every sweep.
ALTER TABLE notifications
    ADD INDEX idx_notifications_status_created (status, created_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- PaymentRepository.findByGatewayPaymentId — PaymentWebhookService#refundPayment's lookup, the
-- refund-webhook hot path (Razorpay `refund.processed`/Stripe `charge.refunded`). No index
-- existed on this column at all (it isn't a FK). Plain, not unique — gatewayPaymentId is only
-- ever set once by the capture event per the column's own Javadoc, but nothing in the schema
-- enforces that today, and adding a new uniqueness guarantee is out of scope for an index-only
-- hardening pass.
ALTER TABLE payments
    ADD INDEX idx_payments_gateway_payment_id (gateway_payment_id),
    ALGORITHM = INPLACE, LOCK = NONE;

-- ProjectRepository.search's optional `status` filter — GET /api/v1/projects. ProjectService's
-- own Javadoc: a non-privileged caller always has `status` forced to PUBLISHED server-side, so in
-- practice this is a bound filter on nearly every real call, not an optional one. No index
-- existed on `status`/`difficulty`/`domain`; `status` is the one with real, load-bearing
-- selectivity (a handful of DRAFT/ARCHIVED rows vs. every PUBLISHED one), matching the same
-- single-column `idx_..._status` precedent already used on batches/leads/pip_records/clients.
ALTER TABLE projects
    ADD INDEX idx_projects_status (status),
    ALGORITHM = INPLACE, LOCK = NONE;

-- StandupRepository.findEligibleForFinalisation — AttendanceFinalisationJob, the 01:30 nightly
-- job AGENTS.md lists as "never cut" and whose ordering the whole nightly chain depends on.
-- Filters `finalised_at IS NULL AND scheduled_at < :now` (plus a low-selectivity `status <>
-- CANCELLED`); V7/V10 only ever gave finalised_at and scheduled_at one single-column index each,
-- so MySQL could use at most one of the two per scan. This composite covers the query's actual
-- WHERE clause directly. The two single-column indexes are left in place, not dropped — dropping
-- them isn't this migration's job, and idx_standups_scheduled_at may still help `StandupRepository
-- .search`'s own optional date-range filter.
ALTER TABLE standups
    ADD INDEX idx_standups_finalisation (finalised_at, scheduled_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- TaskRepository.findReviewQueue — GET /api/v1/reviews/queue (feature 12, "never cut": GitHub
-- submission and review). Filters `t.status = :status` with no `sprint_id`/`assigned_to` bound,
-- so neither existing composite index (idx_tasks_sprint_status_due, idx_tasks_assigned_status_due)
-- has a usable leftmost prefix here — this was a full scan of every task in the platform, ordered
-- by due_at, on the PM review-queue endpoint.
ALTER TABLE tasks
    ADD INDEX idx_tasks_status_due (status, due_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- UserSubscriptionRepository.findActiveExpiredAsOf — SubscriptionExpiryJob's nightly cohort read
-- (`status = 'ACTIVE' AND end_date < :today`). No index on either column; the existing
-- uq_one_active_subscription generated column is a different, narrower guarantee (at most one
-- ACTIVE row per user) that this query's literal WHERE clause doesn't go through.
ALTER TABLE user_subscriptions
    ADD INDEX idx_user_subscriptions_status_end (status, end_date),
    ALGORITHM = INPLACE, LOCK = NONE;

-- BatchRepository.findAllocationCandidates — the batch-allocation hot path AGENTS.md calls out
-- explicitly ("Batch capacity uses a conditional atomic UPDATE") and build-plan.md lists as
-- "never cut": every captured payment webhook calls this. Filters `track_code = :trackCode AND
-- status IN ('PLANNED','ACTIVE')`; only a single-column idx_batches_status (status alone)
-- existed, with no index at all on track_code. track_code leads the composite — it is the more
-- selective of the two filters (a handful of values for status vs. many distinct tracks).
ALTER TABLE batches
    ADD INDEX idx_batches_track_status (track_code, status),
    ALGORITHM = INPLACE, LOCK = NONE;

-- PayrollRecordRepository.findByPeriodMonth — the admin "payroll for month" listing. The only
-- existing index touching period_month is uq_payroll_records_employee_month(employee_id,
-- period_month), unusable for a period_month-only filter since employee_id is its leftmost
-- column.
ALTER TABLE payroll_records
    ADD INDEX idx_payroll_records_period_month (period_month),
    ALGORITHM = INPLACE, LOCK = NONE;
