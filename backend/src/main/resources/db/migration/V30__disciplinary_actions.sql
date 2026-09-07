-- Feature: HR Disciplinary (frontend-integration gap B1.10, third slice). Records a disciplinary
-- action raised against an employee, its severity and lifecycle. Deliberately does NOT auto-flip
-- employees.status even for a TERMINATION_RECOMMENDATION — that is a recommendation; the actual
-- termination goes through the exit flow (V28).

CREATE TABLE disciplinary_actions (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id      BIGINT UNSIGNED NOT NULL,
    raised_by        BIGINT UNSIGNED NOT NULL,
    action_type      VARCHAR(30)     NOT NULL,
    severity         VARCHAR(10)     NOT NULL,
    incident_date    DATE            NOT NULL,
    description      TEXT            NOT NULL,
    action_taken     TEXT            NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    acknowledged_at  DATETIME(6)     NULL,
    resolved_at      DATETIME(6)     NULL,
    resolution_notes TEXT            NULL,
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_disciplinary_actions_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_disciplinary_actions_raised_by FOREIGN KEY (raised_by) REFERENCES users (id),
    CONSTRAINT chk_disciplinary_actions_type CHECK (action_type IN (
        'VERBAL_WARNING', 'WRITTEN_WARNING', 'PIP', 'SUSPENSION', 'TERMINATION_RECOMMENDATION', 'OTHER')),
    CONSTRAINT chk_disciplinary_actions_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_disciplinary_actions_status CHECK (status IN (
        'OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'ESCALATED')),
    INDEX idx_disciplinary_actions_employee (employee_id, status),
    INDEX idx_disciplinary_actions_status (status, incident_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
