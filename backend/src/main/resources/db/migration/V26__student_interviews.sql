-- Feature: Student Interviews (frontend-integration gap B1.8). The FE has a /student/interviews
-- screen and a staff-side scheduler; the backend had nothing. A TRAINER_PM/ADMIN schedules mock
-- / technical / HR / placement interviews for a student; the student sees their own list, and
-- staff record the outcome (feedback + rating) afterwards.

CREATE TABLE student_interviews (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id       BIGINT UNSIGNED NOT NULL,
    scheduled_by     BIGINT UNSIGNED NOT NULL,
    interview_type   VARCHAR(20)     NOT NULL,
    scheduled_at     DATETIME(6)     NOT NULL,
    duration_minutes INT UNSIGNED    NULL,
    mode             VARCHAR(20)     NULL,
    location         VARCHAR(255)    NULL,
    interviewer_name VARCHAR(150)    NULL,
    meeting_link     VARCHAR(1000)   NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',
    feedback         TEXT            NULL,
    rating           TINYINT UNSIGNED NULL,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_interviews_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_student_interviews_scheduled_by FOREIGN KEY (scheduled_by) REFERENCES users (id),
    CONSTRAINT chk_student_interviews_type CHECK (interview_type IN (
        'MOCK', 'TECHNICAL', 'HR', 'PLACEMENT', 'OTHER')),
    CONSTRAINT chk_student_interviews_mode CHECK (mode IS NULL OR mode IN ('ONLINE', 'ONSITE')),
    CONSTRAINT chk_student_interviews_status CHECK (status IN (
        'SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT chk_student_interviews_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 10),
    INDEX idx_student_interviews_student (student_id, scheduled_at),
    INDEX idx_student_interviews_status (status, scheduled_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
