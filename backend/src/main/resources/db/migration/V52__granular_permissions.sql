-- Granular permissions. The `permissions` + `role_permissions` tables have existed since V1
-- (RBAC schema shipped, never populated or wired). This migration only seeds them: a catalogue
-- of capability codes and sensible per-role defaults an ADMIN can then edit.
--
-- Nothing enforces these yet (every endpoint still gates on hasRole(...)); the framework is in
-- place so a future check is one line: @PreAuthorize("@perms.has('CODE')").
--
-- Idempotent (INSERT IGNORE on unique keys) so it is safe if a previous attempt half-ran.

INSERT IGNORE INTO permissions (code, module, description) VALUES
    ('USER_VIEW',          'Users',     'View user accounts'),
    ('USER_INVITE',        'Users',     'Invite staff members'),
    ('USER_EDIT',          'Users',     'Edit user profile fields'),
    ('USER_ROLE_ASSIGN',   'Users',     'Assign / change user roles'),
    ('USER_SUSPEND',       'Users',     'Suspend or terminate accounts'),
    ('BILLING_VIEW',       'Billing',   'View transactions and invoices'),
    ('REFUND_ISSUE',       'Billing',   'Issue refunds'),
    ('PLAN_EDIT',          'Billing',   'Edit subscription plans and pricing'),
    ('LEAD_VIEW',          'CRM',       'View CRM leads'),
    ('LEAD_EDIT',          'CRM',       'Edit leads and pipeline stage'),
    ('LEAD_EXPORT',        'CRM',       'Export lead data'),
    ('BATCH_MANAGE',       'Training',  'Manage batches, sprints and tasks'),
    ('PIP_REVIEW',         'Training',  'Review and close PIP records'),
    ('CERT_ISSUE',         'Training',  'Issue and revoke certificates'),
    ('EMPLOYEE_MANAGE',    'HR',        'Manage employee records'),
    ('PAYROLL_RUN',        'HR',        'Generate payroll'),
    ('ONBOARDING_REVIEW',  'HR',        'Review onboarding documents'),
    ('EXIT_PROCESS',       'HR',        'Run the employee exit process'),
    ('PLACEMENT_MANAGE',   'Placement', 'Advance client placements'),
    ('OFFER_GENERATE',     'Placement', 'Generate offer letters'),
    ('AUDIT_VIEW',         'Platform',  'View audit and security logs'),
    ('REPORT_EXPORT',      'Platform',  'Run compliance exports'),
    ('PERMISSION_MANAGE',  'Platform',  'Edit role permissions');

-- ADMIN: everything.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON r.code = 'ADMIN';

-- Other roles: mirror what their hasRole(...) gates already allow today.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON (
    (r.code = 'HR_MANAGER'       AND p.code IN ('USER_VIEW','USER_INVITE','EMPLOYEE_MANAGE','PAYROLL_RUN',
                                               'ONBOARDING_REVIEW','EXIT_PROCESS','PLACEMENT_MANAGE',
                                               'OFFER_GENERATE','AUDIT_VIEW'))
 OR (r.code = 'TRAINER_PM'       AND p.code IN ('BATCH_MANAGE','PIP_REVIEW','CERT_ISSUE'))
 OR (r.code = 'LEAD_GEN'         AND p.code IN ('LEAD_VIEW','LEAD_EDIT','LEAD_EXPORT'))
 OR (r.code = 'BUSINESS_ANALYST' AND p.code IN ('LEAD_VIEW'))
);
