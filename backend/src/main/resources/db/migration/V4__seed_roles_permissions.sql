-- Feature 02. Despite the filename, this seeds the 8 roles only — permissions and
-- role_permissions stay empty. See the 2026-08-24 decision in progress-tracker.md: RBAC in this
-- build is role-based (hasRole) throughout, no feature reads a permission code, and seeding
-- invented codes now would be a guess against nothing that reads them. Filename kept as-is
-- rather than renumbering every migration after it.
--
-- Idempotent: safe to re-run, changes nothing after the first apply.

INSERT INTO roles (code, name, description) VALUES
    ('STUDENT',          'Student',                   'Learner / Intern'),
    ('TRAINER_PM',       'Trainer / Project Manager',  'Instructional Lead'),
    ('DEVELOPER',        'Developer',                 'Content Author'),
    ('LEAD_GEN',         'Lead Generator',             'Sales & Marketing'),
    ('HR_MANAGER',       'HR Manager',                 'Talent & Operations'),
    ('BUSINESS_ANALYST', 'Business Analyst',           'Product & Delivery'),
    ('ADMIN',            'Administrator',              'Platform Governance'),
    ('CLIENT',           'Client',                     'External Partner')
ON DUPLICATE KEY UPDATE
    name        = VALUES(name),
    description = VALUES(description);
