-- Payroll proration reworked: a salaried employee is paid per working day for the days they were
-- present OR on approved PAID leave (SICK / CASUAL / EARNED). Every other working day is loss of
-- pay — unpaid leave, and plain unexplained absence alike.
--   gross = base_salary * (presentDays + paidLeaveDays) / workingDays
-- The column that recorded "unpaid leave days" (V53) now records the docked-day count, so rename
-- it to match. (Holidays are assumed already excluded from the HR-entered workingDays.)

ALTER TABLE payroll_records
    CHANGE COLUMN unpaid_leave_days lop_days DECIMAL(4,1) NOT NULL DEFAULT 0;
