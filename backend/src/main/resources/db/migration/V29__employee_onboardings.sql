-- Feature: HR Onboarding (frontend-integration gap B1.10, second slice). Tracks a new
-- employee's onboarding checklist and status. No side effect on the employees row — an employee
-- is created ACTIVE regardless; this is purely the HR workflow around it. Buddy is an optional
-- assigned mentor (a users row).

CREATE TABLE employee_onboardings (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT UNSIGNED NOT NULL,
    initiated_by BIGINT UNSIGNED NOT NULL,
    buddy_id     BIGINT UNSIGNED NULL,
    start_date   DATE            NOT NULL,
    status       VARCHAR(20)     NOT NULL DEFAULT 'NOT_STARTED',
    checklist    JSON            NULL,
    notes        TEXT            NULL,
    completed_at DATETIME(6)     NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_employee_onboardings_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_employee_onboardings_initiated_by FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT fk_employee_onboardings_buddy FOREIGN KEY (buddy_id) REFERENCES users (id),
    CONSTRAINT chk_employee_onboardings_status CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    INDEX idx_employee_onboardings_employee (employee_id, status),
    INDEX idx_employee_onboardings_status (status, start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
