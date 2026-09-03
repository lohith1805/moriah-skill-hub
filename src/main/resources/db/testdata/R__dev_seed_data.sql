-- =============================================================================================
-- DEV-ONLY test data — a Flyway *repeatable* migration (R__), applied only in the `dev` profile.
--
-- It runs because application-dev.yml adds `classpath:db/testdata` to spring.flyway.locations.
-- application.yml (used by `prod`) and src/test/resources/application-test.yml (used by every
-- integration test) DO NOT add that location, so Flyway there never sees this file — the `test`
-- and `prod` databases stay free of sample data, which is the whole reason it isn't a versioned
-- V__ migration in db/migration/.
--
-- Repeatable = re-applied automatically whenever this file's checksum changes. Every statement
-- is idempotent (INSERT IGNORE / INSERT ... SELECT ... WHERE NOT EXISTS) and keyed by a natural
-- unique column, so re-running is a no-op and order does not depend on auto-increment ids.
--
-- All accounts: password = "Password123!"  (BCrypt cost 12)
-- =============================================================================================

-- ---- Users (one per role + 3 students) ------------------------------------------------------
INSERT IGNORE INTO users (uuid, full_name, email, password_hash, github_username, status, email_verified_at) VALUES
 ('11111111-0000-0000-0000-000000000001', 'Aria Admin',        'admin@moriah.test',    '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000002', 'Priya PM',          'pm@moriah.test',       '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'priya-pm',  'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000003', 'Deepak Dev',        'dev@moriah.test',      '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'deepak-dev','ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000004', 'Sana Sales',        'sales@moriah.test',    '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000005', 'Hina HR',           'hr@moriah.test',       '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000006', 'Bala BA',           'ba@moriah.test',       '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000007', 'Carl Client',       'client@moriah.test',   '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000008', 'Sam Student One',   'student1@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'sam-s1',    'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000009', 'Tara Student Two',  'student2@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'tara-s2',   'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000010', 'Uday Student Three','student3@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6));

-- ---- Role assignments (by natural keys, so no id juggling) --------------------------------
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ADMIN'            WHERE u.email = 'admin@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'TRAINER_PM'      WHERE u.email = 'pm@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'DEVELOPER'       WHERE u.email = 'dev@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'LEAD_GEN'        WHERE u.email = 'sales@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'HR_MANAGER'      WHERE u.email = 'hr@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'BUSINESS_ANALYST' WHERE u.email = 'ba@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'CLIENT'          WHERE u.email = 'client@moriah.test';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'STUDENT'
WHERE u.email IN ('student1@moriah.test', 'student2@moriah.test', 'student3@moriah.test');

-- ---- Student profiles ---------------------------------------------------------------------
INSERT IGNORE INTO user_profiles (user_id, bio, location, current_title, experience_level, skills, portfolio_slug, is_complete, completion_percent)
SELECT u.id, 'Full-stack learner.', 'Bengaluru', 'Trainee Engineer', 'JUNIOR',
       JSON_ARRAY('Java', 'Spring', 'MySQL', 'React'), 'sam-student-one', TRUE, 85
FROM users u WHERE u.email = 'student1@moriah.test';
INSERT IGNORE INTO user_profiles (user_id, bio, location, current_title, experience_level, skills, portfolio_slug, is_complete, completion_percent)
SELECT u.id, 'Backend-focused learner.', 'Hyderabad', 'Trainee Engineer', 'JUNIOR',
       JSON_ARRAY('Java', 'Spring', 'PostgreSQL'), 'tara-student-two', TRUE, 70
FROM users u WHERE u.email = 'student2@moriah.test';

-- ---- Subscriptions (payment_id NULL is allowed; gives entitlements without a payments row) --
--   student1, student2 -> ACTIVE PROJECT_BASED (batch + sprints + PIP)
--   student3           -> ACTIVE STARTER       (to test the 403 ENTITLEMENT_REQUIRED path)
INSERT INTO user_subscriptions (user_id, plan_id, payment_id, start_date, end_date, status, auto_renew)
SELECT u.id, p.id, NULL, CURDATE(), DATE_ADD(CURDATE(), INTERVAL p.duration_days DAY), 'ACTIVE', FALSE
FROM users u JOIN subscription_plans p ON p.code = 'PROJECT_BASED'
WHERE u.email IN ('student1@moriah.test', 'student2@moriah.test')
  AND NOT EXISTS (SELECT 1 FROM user_subscriptions s WHERE s.user_id = u.id AND s.status = 'ACTIVE');
INSERT INTO user_subscriptions (user_id, plan_id, payment_id, start_date, end_date, status, auto_renew)
SELECT u.id, p.id, NULL, CURDATE(), DATE_ADD(CURDATE(), INTERVAL p.duration_days DAY), 'ACTIVE', FALSE
FROM users u JOIN subscription_plans p ON p.code = 'STARTER'
WHERE u.email = 'student3@moriah.test'
  AND NOT EXISTS (SELECT 1 FROM user_subscriptions s WHERE s.user_id = u.id AND s.status = 'ACTIVE');

-- ---- One ACTIVE batch, PM = pm@moriah.test, min tier PROJECT_BASED ------------------------
INSERT INTO batches (name, track_code, pm_id, plan_tier_min_id, start_date, end_date, capacity, enrolled_count, status)
SELECT 'FS-2026-01', 'FULL_STACK',
       (SELECT id FROM users WHERE email = 'pm@moriah.test'),
       (SELECT id FROM subscription_plans WHERE code = 'PROJECT_BASED'),
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 90 DAY), 10, 0, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM batches WHERE name = 'FS-2026-01');

-- student1 + student2 enrolled ACTIVE
INSERT INTO batch_students (batch_id, user_id, status)
SELECT b.id, u.id, 'ACTIVE'
FROM batches b JOIN users u ON u.email IN ('student1@moriah.test', 'student2@moriah.test')
WHERE b.name = 'FS-2026-01'
  AND NOT EXISTS (SELECT 1 FROM batch_students bs WHERE bs.batch_id = b.id AND bs.user_id = u.id);

UPDATE batches
SET enrolled_count = (SELECT COUNT(*) FROM batch_students bs
                      WHERE bs.batch_id = batches.id AND bs.status IN ('ACTIVE', 'ON_PIP'))
WHERE name = 'FS-2026-01';

-- ---- Sprint 1 (ACTIVE) in that batch ----------------------------------------------------
INSERT INTO sprints (batch_id, sprint_number, goal, start_date, end_date, status, planned_points, completed_points)
SELECT b.id, 1, 'Ship the core CRUD API and get one PR merged per student.',
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'ACTIVE', 20, 0
FROM batches b WHERE b.name = 'FS-2026-01'
  AND NOT EXISTS (SELECT 1 FROM sprints s WHERE s.batch_id = b.id AND s.sprint_number = 1);

-- ---- Tasks in sprint 1: one BACKLOG (pull-testable), one ASSIGNED, one IN_REVIEW ---------
INSERT INTO tasks (sprint_id, title, description, task_type, assigned_to, story_points, due_at, status)
SELECT s.id, 'Implement GET /todos', 'List endpoint with pagination.', 'STORY', NULL, 3,
       DATE_ADD(NOW(6), INTERVAL 7 DAY), 'BACKLOG'
FROM sprints s JOIN batches b ON b.id = s.batch_id
WHERE b.name = 'FS-2026-01' AND s.sprint_number = 1
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.sprint_id = s.id AND t.title = 'Implement GET /todos');

INSERT INTO tasks (sprint_id, title, description, task_type, assigned_to, story_points, due_at, status)
SELECT s.id, 'Implement POST /todos', 'Create endpoint with validation.', 'STORY',
       (SELECT id FROM users WHERE email = 'student1@moriah.test'), 5,
       DATE_ADD(NOW(6), INTERVAL 5 DAY), 'ASSIGNED'
FROM sprints s JOIN batches b ON b.id = s.batch_id
WHERE b.name = 'FS-2026-01' AND s.sprint_number = 1
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.sprint_id = s.id AND t.title = 'Implement POST /todos');

INSERT INTO tasks (sprint_id, title, description, task_type, assigned_to, story_points, due_at, status)
SELECT s.id, 'Implement DELETE /todos/{id}', 'Delete endpoint, soft-delete.', 'STORY',
       (SELECT id FROM users WHERE email = 'student2@moriah.test'), 3,
       DATE_ADD(NOW(6), INTERVAL 3 DAY), 'IN_REVIEW'
FROM sprints s JOIN batches b ON b.id = s.batch_id
WHERE b.name = 'FS-2026-01' AND s.sprint_number = 1
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.sprint_id = s.id AND t.title = 'Implement DELETE /todos/{id}');

-- ---- One SCHEDULED standup for today ---------------------------------------------------
INSERT INTO standups (batch_id, sprint_id, scheduled_at, late_cutoff_minutes, status)
SELECT b.id, s.id, TIMESTAMP(CURDATE(), '10:00:00'), 15, 'SCHEDULED'
FROM batches b JOIN sprints s ON s.batch_id = b.id AND s.sprint_number = 1
WHERE b.name = 'FS-2026-01'
  AND NOT EXISTS (SELECT 1 FROM standups st WHERE st.batch_id = b.id
                  AND st.scheduled_at = TIMESTAMP(CURDATE(), '10:00:00'));

-- ---- One PUBLISHED project authored by the developer ---------------------------------
INSERT INTO projects (title, slug, description, tech_stack, difficulty, domain, version, status, created_by)
SELECT 'Todo API', 'todo-api', 'A REST API for a todo list - the sprint-1 reference project.',
       JSON_ARRAY('Java', 'Spring Boot', 'MySQL'), 'BEGINNER', 'Web', 'v1', 'PUBLISHED',
       (SELECT id FROM users WHERE email = 'dev@moriah.test')
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE slug = 'todo-api');

-- ---- A small CRM pipeline for the sales rep -----------------------------------------
--   One lead per pipeline stage so /leads/pipeline, the leaderboard and the activity
--   history all have something to show on a fresh `dev` boot. dedupe_hash is just any
--   unique 64-hex value here (SHA2 of email+phone) — the real hasher runs only on the
--   POST /leads path.
INSERT INTO leads (name, email, phone, source, lead_type, institution, deal_value, status, assigned_agent_id, dedupe_hash)
SELECT v.name, v.email, v.phone, v.source, v.lead_type, v.institution, v.deal_value, v.status,
       (SELECT id FROM users WHERE email = 'sales@moriah.test'),
       SHA2(CONCAT(v.email, v.phone), 256)
FROM (
    SELECT 'Nikhil Prospect'  AS name, 'nikhil.prospect@example.com' AS email, '919900112233' AS phone,
           'LANDING_PAGE' AS source, 'B2C' AS lead_type, 'Self' AS institution,
           CAST(NULL AS DECIMAL(12,2)) AS deal_value, 'NEW' AS status
    UNION ALL SELECT 'Priya Menon',   'priya.menon@example.com',   '919812345670', 'REFERRAL',     'B2C', 'Self',            29999.00, 'CONTACTED'
    UNION ALL SELECT 'Arjun Rao',     'arjun.rao@example.com',     '919845000021', 'COLLEGE',      'B2B2C', 'VIT Chennai',   0.00,     'DEMO_SCHEDULED'
    UNION ALL SELECT 'Meera Nair',    'meera.nair@example.com',    '919820777310', 'CORPORATE',    'B2B', 'Zoho Corp',       450000.00, 'COUNSELLING_DONE'
    UNION ALL SELECT 'Rohan Gupta',   'rohan.gupta@example.com',   '919811223344', 'LANDING_PAGE', 'B2C', 'Self',            29999.00, 'ENROLLED'
) v
WHERE NOT EXISTS (SELECT 1 FROM leads l WHERE l.email = v.email);

-- A couple of activities on the CONTACTED lead so the detail drawer isn't empty.
INSERT INTO lead_activities (lead_id, agent_id, activity_type, outcome, notes, occurred_at)
SELECT l.id, l.assigned_agent_id, 'CALL', 'NO_ANSWER', 'First dial — went to voicemail.',
       DATE_SUB(NOW(6), INTERVAL 2 DAY)
FROM leads l WHERE l.email = 'priya.menon@example.com'
  AND NOT EXISTS (SELECT 1 FROM lead_activities a WHERE a.lead_id = l.id AND a.outcome = 'NO_ANSWER');
INSERT INTO lead_activities (lead_id, agent_id, activity_type, outcome, notes, next_follow_up_at, occurred_at)
SELECT l.id, l.assigned_agent_id, 'WHATSAPP', 'SENT', 'Sent the syllabus brochure template.',
       DATE_ADD(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 1 DAY)
FROM leads l WHERE l.email = 'priya.menon@example.com'
  AND NOT EXISTS (SELECT 1 FROM lead_activities a WHERE a.lead_id = l.id AND a.outcome = 'SENT');
UPDATE leads SET next_follow_up_at = DATE_ADD(NOW(6), INTERVAL 2 DAY)
WHERE email = 'priya.menon@example.com' AND next_follow_up_at IS NULL;

-- A sales target for the current month so /leads/targets/me and the leaderboard's quota
-- columns are populated for `sales@`.
INSERT INTO sales_targets (agent_id, period_month, calls_target, calls_made, conversions_target,
                           conversions_made, revenue_target, revenue_achieved)
SELECT (SELECT id FROM users WHERE email = 'sales@moriah.test'),
       DATE_FORMAT(CURDATE(), '%Y-%m-01'), 120, 34, 8, 1, 500000.00, 29999.00
WHERE NOT EXISTS (
    SELECT 1 FROM sales_targets s
    WHERE s.agent_id = (SELECT id FROM users WHERE email = 'sales@moriah.test')
      AND s.period_month = DATE_FORMAT(CURDATE(), '%Y-%m-01'));

-- ---- Two employee records (HR + payroll testing) -----------------------------------
INSERT INTO employees (user_id, employee_code, department, designation, employment_type, date_of_joining, base_salary, status)
SELECT u.id, 'EMP-0001', 'People', 'HR Manager', 'FULL_TIME', DATE_SUB(CURDATE(), INTERVAL 400 DAY), 90000.00, 'ACTIVE'
FROM users u WHERE u.email = 'hr@moriah.test'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);

INSERT INTO employees (user_id, employee_code, department, designation, employment_type, date_of_joining, base_salary, reporting_manager_id, status)
SELECT u.id, 'EMP-0002', 'Engineering', 'Content Developer', 'FULL_TIME', DATE_SUB(CURDATE(), INTERVAL 200 DAY), 80000.00,
       (SELECT id FROM employees WHERE employee_code = 'EMP-0001'), 'ACTIVE'
FROM users u WHERE u.email = 'dev@moriah.test'
  AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.user_id = u.id);
