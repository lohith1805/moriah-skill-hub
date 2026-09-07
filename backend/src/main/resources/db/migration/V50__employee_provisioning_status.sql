-- Onboarding flow: a staff member's employees row is auto-created when they accept their invite
-- (StaffEmployeeProvisioningListener) but starts life as PENDING_HR — HR still has to fill in
-- real compensation / designation / reporting manager and approve it before it is CONFIRMED.
-- Existing rows are all real, HR-set records → CONFIRMED.

ALTER TABLE employees
    ADD COLUMN provisioning_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' AFTER status;

ALTER TABLE employees
    ADD CONSTRAINT chk_employees_provisioning_status
    CHECK (provisioning_status IN ('PENDING_HR', 'CONFIRMED'));
