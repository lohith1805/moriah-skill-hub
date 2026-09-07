-- Feature 18 — CRM Module. architecture.md "V11 — CRM" (file numbered V12 here: V9 was
-- reassigned to batch_allocation.sql when feature 10 was `/architect`'d mid-plan, shifting every
-- placeholder after it down one version — see progress-tracker.md's migration ledger).

-- source is a closed set (build-plan.md: "Ingestion from landing page, college, corporate,
-- referral, walk-in") so it's a CHECK-constrained enum column. lead_type has no enumerated value
-- set anywhere in build-plan.md/architecture.md, so it stays free text like `institution`.
CREATE TABLE leads (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name               VARCHAR(150)    NOT NULL,
    email              VARCHAR(255)    NOT NULL,
    phone              VARCHAR(20)     NOT NULL,
    source             VARCHAR(20)     NOT NULL,
    lead_type          VARCHAR(50)     NOT NULL,
    institution        VARCHAR(150)    NULL,
    interested_plan_id BIGINT UNSIGNED NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'NEW',
    assigned_agent_id  BIGINT UNSIGNED NULL,
    lost_reason        VARCHAR(500)    NULL,
    converted_user_id  BIGINT UNSIGNED NULL,
    dedupe_hash        CHAR(64)        NOT NULL,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_leads_dedupe_hash (dedupe_hash),
    CONSTRAINT fk_leads_plan FOREIGN KEY (interested_plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT fk_leads_agent FOREIGN KEY (assigned_agent_id) REFERENCES users (id),
    CONSTRAINT fk_leads_converted_user FOREIGN KEY (converted_user_id) REFERENCES users (id),
    CONSTRAINT chk_leads_source CHECK (source IN ('LANDING_PAGE', 'COLLEGE', 'CORPORATE', 'REFERRAL', 'WALK_IN')),
    CONSTRAINT chk_leads_status CHECK (status IN
        ('NEW', 'CONTACTED', 'DEMO_SCHEDULED', 'COUNSELLING_DONE', 'PAYMENT_PENDING', 'ENROLLED', 'LOST')),
    INDEX idx_leads_status_agent (status, assigned_agent_id),
    INDEX idx_leads_source (source),
    INDEX idx_leads_agent (assigned_agent_id),
    -- Every column used in a WHERE/ORDER BY on this table has an index (code-standards.md's
    -- "Every foreign key has an index"): phone backs WhatsAppWebhookService's per-inbound-message
    -- lookup (findFirstByPhoneOrderByCreatedAtDesc), plan/converted_user back their own FKs.
    INDEX idx_leads_phone (phone),
    INDEX idx_leads_plan (interested_plan_id),
    INDEX idx_leads_converted_user (converted_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- activity_type isn't an enumerated value set in architecture.md; LeadActivityType is this
-- build's own closed set (code-standards.md: "Enums are VARCHAR with a CHECK constraint").
CREATE TABLE lead_activities (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lead_id           BIGINT UNSIGNED NOT NULL,
    agent_id          BIGINT UNSIGNED NULL,
    activity_type     VARCHAR(30)     NOT NULL,
    outcome           VARCHAR(100)    NULL,
    notes             TEXT            NULL,
    next_follow_up_at DATETIME(6)     NULL,
    occurred_at       DATETIME(6)     NOT NULL,
    created_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_lead_activities_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_lead_activities_agent FOREIGN KEY (agent_id) REFERENCES users (id),
    CONSTRAINT chk_lead_activities_type CHECK (activity_type IN
        ('CALL', 'EMAIL', 'WHATSAPP', 'MEETING', 'NOTE', 'STATUS_CHANGE', 'WHATSAPP_INBOUND')),
    INDEX idx_lead_activities_lead (lead_id, occurred_at),
    INDEX idx_lead_activities_agent (agent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_targets (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_id           BIGINT UNSIGNED NOT NULL,
    period_month       DATE            NOT NULL,
    calls_target       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    calls_made         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    conversions_target SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    conversions_made   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    revenue_target     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    revenue_achieved   DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_sales_targets_agent_month (agent_id, period_month),
    CONSTRAINT fk_sales_targets_agent FOREIGN KEY (agent_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Deferred from V10 (architecture.md's documented view list) — its source table `leads` didn't
-- exist until this migration, same forward-dependency treatment as `tasks.project_id`'s V6->V8 FK.
CREATE VIEW v_lead_funnel AS
SELECT
    l.status,
    l.assigned_agent_id AS agent_id,
    DATE_FORMAT(l.created_at, '%Y-%m-01') AS period_month,
    COUNT(*) AS lead_count
FROM leads l
GROUP BY l.status, l.assigned_agent_id, DATE_FORMAT(l.created_at, '%Y-%m-01');
