-- Feature 19 — HR Module. architecture.md "V12 — HR" (file numbered V13 here: V9 was reassigned
-- to batch_allocation.sql when feature 10 was `/architect`'d mid-plan, shifting every placeholder
-- after it down one version — see progress-tracker.md's migration ledger).

-- employment_type/status have no enumerated value set in architecture.md; this build's own
-- closed sets (code-standards.md: "Enums are VARCHAR with a CHECK constraint"), documented on
-- EmploymentType/EmployeeStatus. reporting_manager_id is self-referential to employees.id, not
-- users.id — an employee's manager is itself an employee record.
CREATE TABLE employees (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id              BIGINT UNSIGNED NOT NULL,
    employee_code        VARCHAR(30)     NOT NULL,
    department           VARCHAR(100)    NOT NULL,
    designation          VARCHAR(100)    NOT NULL,
    employment_type      VARCHAR(20)     NOT NULL,
    date_of_joining      DATE            NOT NULL,
    date_of_exit         DATE            NULL,
    base_salary          DECIMAL(12,2)   NULL,
    hourly_rate          DECIMAL(10,2)   NULL,
    reporting_manager_id BIGINT UNSIGNED NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_employees_user (user_id),
    UNIQUE KEY uq_employees_code (employee_code),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (reporting_manager_id) REFERENCES employees (id),
    CONSTRAINT chk_employees_employment_type CHECK (employment_type IN
        ('FULL_TIME', 'PART_TIME', 'INTERN', 'CONTRACT')),
    CONSTRAINT chk_employees_status CHECK (status IN ('ACTIVE', 'EXITED', 'TERMINATED')),
    -- Payroll's either/or compensation rule (base_salary XOR hourly_rate) enforced at the DB
    -- level too, not just in PayrollService — a defect either way is a bad payslip, not just a
    -- bad API response.
    CONSTRAINT chk_employees_compensation CHECK (
        (base_salary IS NOT NULL AND hourly_rate IS NULL) OR
        (base_salary IS NULL AND hourly_rate IS NOT NULL)
    ),
    INDEX idx_employees_manager (reporting_manager_id),
    INDEX idx_employees_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- document_type has no enumerated value set in architecture.md — genuinely open-ended KYC/ID
-- document categories (Aadhaar, PAN, degree certificate, ...), left free text like
-- `leads.lead_type`.
CREATE TABLE hr_documents (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    document_type       VARCHAR(50)     NOT NULL,
    file_key            VARCHAR(255)    NOT NULL,
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    verified_by         BIGINT UNSIGNED NULL,
    verified_at         DATETIME(6)     NULL,
    rejection_reason    VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_hr_documents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_hr_documents_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT chk_hr_documents_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    INDEX idx_hr_documents_user (user_id),
    INDEX idx_hr_documents_status (verification_status),
    INDEX idx_hr_documents_verified_by (verified_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- leave_type has no enumerated value set in architecture.md either — this build's own closed set
-- (SICK/CASUAL/EARNED/UNPAID, the standard four), documented on LeaveType.
CREATE TABLE leave_requests (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    leave_type   VARCHAR(20)     NOT NULL,
    from_date    DATE            NOT NULL,
    to_date      DATE            NOT NULL,
    days         DECIMAL(4,1)    NOT NULL,
    reason       TEXT            NULL,
    status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    approved_by  BIGINT UNSIGNED NULL,
    decided_at   DATETIME(6)     NULL,
    created_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_leave_requests_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT chk_leave_requests_type CHECK (leave_type IN ('SICK', 'CASUAL', 'EARNED', 'UNPAID')),
    CONSTRAINT chk_leave_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- Overlap-on-approve (LeaveService) needs every APPROVED leave for a user by date range in
    -- one indexed scan, not a full-table filter.
    INDEX idx_leave_requests_user_status_dates (user_id, status, from_date, to_date),
    INDEX idx_leave_requests_approved_by (approved_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payroll_records (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id    BIGINT UNSIGNED NOT NULL,
    period_month   DATE            NOT NULL,
    working_days   SMALLINT UNSIGNED NOT NULL,
    present_days   SMALLINT UNSIGNED NOT NULL,
    session_hours  DECIMAL(6,2)    NULL,
    gross_amount   DECIMAL(12,2)   NOT NULL,
    deductions     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    net_amount     DECIMAL(12,2)   NOT NULL,
    payslip_key    VARCHAR(255)    NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    paid_at        DATETIME(6)     NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_payroll_records_employee_month (employee_id, period_month),
    CONSTRAINT fk_payroll_records_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_payroll_records_status CHECK (status IN ('DRAFT', 'FINALISED', 'PAID'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
