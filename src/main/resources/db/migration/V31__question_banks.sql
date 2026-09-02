-- Feature: Assessment question bank (frontend-integration gap B1.15, first half). Feature 14
-- only ships POST /assessments (create a quiz with its questions inline) — there is no reusable
-- pool a trainer can build up and draw from. This adds one.
--
-- question_bank_items mirrors quiz_questions' JSON conventions exactly: options is a JSON array
-- of option strings (null for CODE); correct_answer is a JSON array of 0-based indices (null for
-- CODE) and is never serialized into an API response.

CREATE TABLE question_banks (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    topic       VARCHAR(100)    NOT NULL,
    description TEXT            NULL,
    created_by  BIGINT UNSIGNED NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_question_banks_creator FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_question_banks_active_topic (is_active, topic)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_bank_items (
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    bank_id        BIGINT UNSIGNED  NOT NULL,
    question_text  TEXT             NOT NULL,
    question_type  VARCHAR(20)      NOT NULL,
    options        JSON             NULL,
    correct_answer JSON             NULL,
    marks          TINYINT UNSIGNED NOT NULL DEFAULT 1,
    explanation    TEXT             NULL,
    difficulty     VARCHAR(10)      NULL,
    created_by     BIGINT UNSIGNED  NOT NULL,
    created_at     DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_question_bank_items_bank FOREIGN KEY (bank_id) REFERENCES question_banks (id),
    CONSTRAINT fk_question_bank_items_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_question_bank_items_type CHECK (question_type IN ('MCQ', 'MULTI_SELECT', 'CODE')),
    CONSTRAINT chk_question_bank_items_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    INDEX idx_question_bank_items_bank (bank_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
