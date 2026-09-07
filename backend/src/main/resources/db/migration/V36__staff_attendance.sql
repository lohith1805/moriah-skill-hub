-- Feature: HR staff attendance ledger (frontend gap — hr/AttendanceLeave.jsx's "Staff Attendance
-- Ledger" and "Live Biometric / Web Check-ins" tabs had no backend). One row per staff user per
-- calendar day (Asia/Kolkata). A row is created by a self / web check-in, a biometric-device log
-- an HR user records on someone's behalf, or an HR status override. Students are NOT tracked here
-- — their daily standup attendance is the `attendance` table (V7), owned by the trainer flow.
CREATE TABLE staff_attendance (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    work_date      DATE            NOT NULL,
    checked_in_at  DATETIME(6)     NULL,
    checked_out_at DATETIME(6)     NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'PRESENT',
    -- Free-text terminal / channel id (e.g. BIO-GATE-01, WEB-AUTH-PORTAL). NULL for a pure
    -- HR status override with no check-in.
    device         VARCHAR(50)     NULL,
    -- Set when an HR_MANAGER / ADMIN created or overrode this row rather than the staff member
    -- checking in themselves.
    marked_by      BIGINT UNSIGNED NULL,
    notes          VARCHAR(500)    NULL,
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_staff_attendance_user_date (user_id, work_date),
    CONSTRAINT fk_staff_attendance_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_staff_attendance_marked_by FOREIGN KEY (marked_by) REFERENCES users (id),
    CONSTRAINT chk_staff_attendance_status
        CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'HALF_DAY', 'ON_LEAVE')),
    INDEX idx_staff_attendance_date (work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
