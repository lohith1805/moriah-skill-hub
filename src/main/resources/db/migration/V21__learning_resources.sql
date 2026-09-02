-- Feature: Resource Library (frontend-integration gap B1.6). A shared catalogue of external
-- learning links (articles, videos, docs, tools) curated by staff and browsable by any
-- authenticated user. Link-only for now: no file upload path, so no S3/OwnershipGuard plumbing
-- — `url` is mandatory. `tags` is a JSON array of short strings, same "pre-serialized JSON text,
-- nothing queries into it" treatment as notifications.payload / webhook_events.payload.

CREATE TABLE learning_resources (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200)    NOT NULL,
    description TEXT            NULL,
    category    VARCHAR(20)     NOT NULL,
    url         VARCHAR(1000)   NOT NULL,
    tags        JSON            NULL,
    created_by  BIGINT UNSIGNED NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_resources_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_learning_resources_category CHECK (category IN (
        'ARTICLE', 'VIDEO', 'BOOK', 'TOOL', 'TEMPLATE', 'COURSE', 'OTHER')),
    INDEX idx_learning_resources_active_category (is_active, category, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
