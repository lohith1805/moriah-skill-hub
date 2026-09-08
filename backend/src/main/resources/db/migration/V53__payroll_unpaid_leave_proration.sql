-- Payroll: base salary is prorated for unpaid (LOP) leave days.
-- A salaried employee's gross was previously the flat base salary regardless of attendance —
-- approved paid leave still pays full (correct), but approved UNPAID leave days now reduce the
-- gross by (base_salary / working_days) per day. This column records how many unpaid-leave days
-- fell inside the pay period, so the payslip and the API response can show the breakdown.
-- Matches leave_requests.days precision (supports half-days).

ALTER TABLE payroll_records
    ADD COLUMN unpaid_leave_days DECIMAL(4,1) NOT NULL DEFAULT 0 AFTER present_days;
