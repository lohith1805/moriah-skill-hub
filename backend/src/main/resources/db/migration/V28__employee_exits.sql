-- Feature: HR Exit Management (frontend-integration gap B1.10, first slice). The HR module had
-- employees / leaves / payroll / documents / letters but no structured offboarding. This tracks
-- the exit process; completing it flips employees.status (EXITED for a clean departure,
-- TERMINATED for a for-cause one) and stamps employees.date_of_exit — the same fields
-- HrLetterService's relieving/experience eligibility rule already reads.
--
-- Onboarding and disciplinary (the other two halves of B1.10) are separate follow-ups.

CREATE TABLE employee_exits (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id          BIGINT UNSIGNED NOT NULL,
    initiated_by         BIGINT UNSIGNED NOT NULL,
    exit_type            VARCHAR(20)     NOT NULL,
    last_working_day     DATE            NOT NULL,
    reason               TEXT            NULL,
    notice_period_days   INT UNSIGNED    NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'INITIATED',
    clearance_checklist  JSON            NULL,
    exit_interview_notes TEXT            NULL,
    completed_at         DATETIME(6)     NULL,
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_employee_exits_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_employee_exits_initiated_by FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT chk_employee_exits_type CHECK (exit_type IN (
        'RESIGNATION', 'TERMINATION', 'RETIREMENT', 'CONTRACT_END', 'OTHER')),
    CONSTRAINT chk_employee_exits_status CHECK (status IN (
        'INITIATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    INDEX idx_employee_exits_employee (employee_id, status),
    INDEX idx_employee_exits_status (status, last_working_day)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
