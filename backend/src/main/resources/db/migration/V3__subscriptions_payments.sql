-- Feature 02. Commerce schema. Table creation order follows FK dependency, not the listing
-- order in architecture.md: subscription_plans -> payments -> user_subscriptions -> invoices,
-- so user_subscriptions.payment_id and invoices.payment_id can reference payments in the same
-- migration file without a forward reference.

CREATE TABLE subscription_plans (
    id                       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    code                     VARCHAR(30)       NOT NULL,
    name                     VARCHAR(100)      NOT NULL,
    price_inr                DECIMAL(12,2)     NOT NULL,
    tier_rank                TINYINT UNSIGNED  NOT NULL,
    duration_days            SMALLINT UNSIGNED NOT NULL,
    max_projects             SMALLINT UNSIGNED NULL,
    mentor_support           BOOLEAN           NOT NULL DEFAULT FALSE,
    allows_batch             BOOLEAN           NOT NULL DEFAULT FALSE,
    allows_sprints           BOOLEAN           NOT NULL DEFAULT FALSE,
    allows_pip               BOOLEAN           NOT NULL DEFAULT FALSE,
    allows_internship_letter BOOLEAN           NOT NULL DEFAULT FALSE,
    allows_client_project    BOOLEAN           NOT NULL DEFAULT FALSE,
    is_active                BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at               DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_subscription_plans_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    plan_id             BIGINT UNSIGNED NOT NULL,
    gateway             VARCHAR(20)     NOT NULL,
    gateway_order_id    VARCHAR(100)    NOT NULL,
    gateway_payment_id  VARCHAR(100)    NULL,
    amount              DECIMAL(12,2)   NOT NULL,
    currency            CHAR(3)         NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    failure_reason      VARCHAR(255)    NULL,
    captured_at         DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_payments_gateway_order (gateway_order_id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_payments_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT chk_payments_gateway CHECK (gateway IN ('RAZORPAY', 'STRIPE')),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATED', 'PENDING', 'CAPTURED', 'FAILED', 'REFUNDED')),
    INDEX idx_payments_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One ACTIVE subscription per user via a STORED generated column — MySQL has no partial unique
-- indexes (code-standards.md, "Conditional Uniqueness").
CREATE TABLE user_subscriptions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    plan_id        BIGINT UNSIGNED NOT NULL,
    payment_id     BIGINT UNSIGNED NULL,
    start_date     DATE            NOT NULL,
    end_date       DATE            NOT NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    auto_renew     BOOLEAN         NOT NULL DEFAULT FALSE,
    active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (IF(status = 'ACTIVE', user_id, NULL)) STORED,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_one_active_subscription (active_user_id),
    CONSTRAINT fk_user_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id),
    CONSTRAINT fk_user_subscriptions_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_user_subscriptions_status CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    INDEX idx_user_subscriptions_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE invoices (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_id     BIGINT UNSIGNED NOT NULL,
    invoice_number VARCHAR(30)     NOT NULL,
    amount         DECIMAL(12,2)   NOT NULL,
    tax_amount     DECIMAL(12,2)   NOT NULL DEFAULT 0,
    total_amount   DECIMAL(12,2)   NOT NULL,
    pdf_key        VARCHAR(255)    NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    issued_at      DATETIME(6)     NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_invoices_payment (payment_id),
    UNIQUE KEY uq_invoices_number (invoice_number),
    CONSTRAINT fk_invoices_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_invoices_status CHECK (status IN ('PENDING', 'ISSUED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- event_id uniqueness is the entire webhook idempotency guarantee (library-docs.md,
-- "Webhook Idempotency") — claim() relies on the unique-key violation, never a SELECT-then-INSERT.
-- status values (RECEIVED/PROCESSED/FAILED) aren't spelled out in architecture.md; inferred as
-- the obvious three states WebhookIdempotencyService needs.
CREATE TABLE webhook_events (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    gateway       VARCHAR(20)     NOT NULL,
    event_id      VARCHAR(150)    NOT NULL,
    event_type    VARCHAR(100)    NOT NULL,
    payload       JSON            NULL,
    processed_at  DATETIME(6)     NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(500)    NULL,
    created_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_webhook_events_event_id (event_id),
    CONSTRAINT chk_webhook_events_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- discount_type (PERCENTAGE/FLAT) isn't spelled out in architecture.md either; inferred as the
-- standard two coupon-discount shapes.
CREATE TABLE coupons (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)     NOT NULL,
    discount_type   VARCHAR(20)     NOT NULL,
    discount_value  DECIMAL(12,2)   NOT NULL,
    valid_from      DATE            NOT NULL,
    valid_until     DATE            NOT NULL,
    max_redemptions INT UNSIGNED    NULL,
    times_redeemed  INT UNSIGNED    NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupons_code (code),
    CONSTRAINT chk_coupons_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FLAT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE coupon_redemptions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    coupon_id   BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    payment_id  BIGINT UNSIGNED NOT NULL,
    redeemed_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupon_redemptions_coupon_user (coupon_id, user_id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coupon_redemptions_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
