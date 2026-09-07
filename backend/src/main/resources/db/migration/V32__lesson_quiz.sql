-- Feature: per-lesson quiz (frontend-integration gap B1.4, remaining half). A self-paced video
-- lesson can carry a short MCQ quiz the student must pass (>= 60%) to complete the lesson. The
-- FE authoring screen (developer/VideoLessons.jsx) requires at least one question before a
-- lesson can be published.
--
-- MCQ-only by design (matches the FE's { question, options[4], correctAnswer } shape). options
-- is a JSON array of option strings; correct_index is the 0-based winning option and is never
-- serialised into a student-facing response.

CREATE TABLE lesson_quiz_questions (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    lesson_id     BIGINT UNSIGNED  NOT NULL,
    question_text TEXT             NOT NULL,
    options       JSON             NOT NULL,
    correct_index TINYINT UNSIGNED NOT NULL,
    explanation   TEXT             NULL,
    sort_order    INT              NOT NULL DEFAULT 0,
    created_by    BIGINT UNSIGNED  NOT NULL,
    created_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_quiz_questions_lesson FOREIGN KEY (lesson_id) REFERENCES video_lessons (id),
    CONSTRAINT fk_lesson_quiz_questions_creator FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_lesson_quiz_questions_lesson (lesson_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- One row per (lesson, user): the learner's latest attempt. Passing also upserts
-- lesson_progress to COMPLETED (see LessonQuizService.submit).
CREATE TABLE lesson_quiz_attempts (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    lesson_id    BIGINT UNSIGNED  NOT NULL,
    user_id      BIGINT UNSIGNED  NOT NULL,
    score        TINYINT UNSIGNED NOT NULL,
    total        TINYINT UNSIGNED NOT NULL,
    passed       BOOLEAN          NOT NULL,
    submitted_at DATETIME(6)      NOT NULL,
    created_at   DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_quiz_attempts_lesson FOREIGN KEY (lesson_id) REFERENCES video_lessons (id),
    CONSTRAINT fk_lesson_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_lesson_quiz_attempts UNIQUE (lesson_id, user_id),
    INDEX idx_lesson_quiz_attempts_user (user_id, passed)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
