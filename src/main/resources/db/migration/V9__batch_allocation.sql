-- Feature 10. `/architect feature 10` decisions:
-- 1. `payments.track_code` — the student's chosen track, captured at checkout (CheckoutRequest),
--    not derived from user_profiles (which may not be filled out yet when the webhook fires).
--    Nullable at the DB level — every new checkout populates it (CheckoutRequest.trackCode is
--    @NotBlank), but existing rows from before this feature have none, and adding a NOT NULL
--    column with no default to a populated table isn't something MySQL allows anyway.
-- 2. `pending_batch_allocations` — the real "pending queue" build-plan.md feature 10 describes.
--    Without this table there is nowhere to persist "payment captured, no batch available yet";
--    `batch_students.batch_id` is NOT NULL, so an unallocated student cannot be represented there.
--    One open (unresolved) row per user, enforced the same way as `uq_one_active_subscription`
--    (code-standards.md "Conditional Uniqueness") — a `STORED` generated column that is NULL
--    once `resolved_at` is set, so MySQL's unlimited-NULLs-in-a-unique-index behaviour is what
--    actually enforces "one open pending allocation per user".

ALTER TABLE payments ADD COLUMN track_code VARCHAR(30) NULL AFTER plan_id;

CREATE TABLE pending_batch_allocations (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED NOT NULL,
    track_code         VARCHAR(30)     NOT NULL,
    plan_id            BIGINT UNSIGNED NOT NULL,
    reason             VARCHAR(255)    NULL,
    resolved_at        DATETIME(6)     NULL,
    resolved_batch_id  BIGINT UNSIGNED NULL,
    -- NULL once resolved_at is set — see the file header note on conditional uniqueness.
    open_user_id       BIGINT UNSIGNED GENERATED ALWAYS AS (IF(resolved_at IS NULL, user_id, NULL)) STORED,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_one_open_pending_allocation (open_user_id),
    CONSTRAINT fk_pending_batch_allocations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_pending_batch_allocations_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT fk_pending_batch_allocations_resolved_batch FOREIGN KEY (resolved_batch_id) REFERENCES batches (id),
    INDEX idx_pending_batch_allocations_track (track_code, resolved_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
