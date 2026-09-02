-- Feature: Video Lessons / self-paced learning (frontend-integration gap B1.4). Staff
-- (DEVELOPER/TRAINER_PM/ADMIN) publish lessons grouped into named modules; any authenticated
-- user watches them and their per-lesson progress is tracked. Link-only (video_url), same
-- rationale as learning_resources — no upload path in this slice. A per-lesson quiz is a later
-- addition; nothing here references the assessment module yet.

CREATE TABLE video_lessons (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200)    NOT NULL,
    description      TEXT            NULL,
    module_name      VARCHAR(120)    NOT NULL,
    video_url        VARCHAR(1000)   NOT NULL,
    duration_seconds INT UNSIGNED    NULL,
    sort_order       INT             NOT NULL DEFAULT 0,
    created_by       BIGINT UNSIGNED NOT NULL,
    is_published     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_video_lessons_creator FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_video_lessons_published_module (is_published, module_name, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per (lesson, user). A row exists only once the user has started the lesson; its
-- absence means NOT_STARTED (the DTO synthesises that). watched_seconds is the resume point the
-- player seeks to.
CREATE TABLE lesson_progress (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lesson_id       BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    watched_seconds INT UNSIGNED    NOT NULL DEFAULT 0,
    completed_at    DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES video_lessons (id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_lesson_progress UNIQUE (lesson_id, user_id),
    CONSTRAINT chk_lesson_progress_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    INDEX idx_lesson_progress_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
