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

-- ---- A richer PUBLISHED project with a real brief + bug challenges -------------------
--   Gives every "Projects & Bug Challenges" screen (student / developer / trainer) a
--   substantial project to exercise: multi-line brief, a full stack, a starter repo, and
--   two attached bug-fix challenges the new challenge_submissions flow can be tested on.
INSERT INTO projects (title, slug, description, tech_stack, difficulty, domain, version, starter_repo_url, status, created_by)
SELECT 'ShopSprint — E-Commerce Storefront API', 'shopsprint-storefront-api',
       CONCAT(
         'A production-shaped storefront backend built over one agile sprint. You will implement ',
         'the cart, checkout and order-history slices on top of a provided catalogue service.\n\n',
         'Scope:\n',
         '  - GET /api/products with pagination, text search and a category filter\n',
         '  - Cart: add / update quantity / remove line items, server-side price + stock re-check\n',
         '  - POST /api/checkout: validate stock, apply a single coupon, persist an Order, decrement stock atomically\n',
         '  - GET /api/orders/me: the signed-in customer''s past orders, newest first\n\n',
         'Non-functional: every write is transactional, money is stored in paise (integer), and the ',
         'checkout endpoint must be safe to call twice (idempotency key). Ship a Postman collection ',
         'and a short Loom walkthrough with your PR.'
       ),
       JSON_ARRAY('Java 21', 'Spring Boot 3', 'Spring Data JPA', 'MySQL 8', 'Redis', 'Testcontainers'),
       'INTERMEDIATE', 'E-Commerce', 'v1',
       'https://github.com/moriah/shopsprint-storefront-api', 'PUBLISHED',
       (SELECT id FROM users WHERE email = 'dev@moriah.test')
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE slug = 'shopsprint-storefront-api');

INSERT INTO bug_challenges (project_id, title, broken_code_key, expected_behaviour, test_script_key, difficulty, created_by)
SELECT p.id, v.title, v.broken_key, v.expected, v.test_key, v.diff,
       (SELECT id FROM users WHERE email = 'dev@moriah.test')
FROM projects p JOIN (
    SELECT 'Cart total is wrong when a coupon is applied' AS title,
           'projects/shopsprint/challenges/cart-total-broken.java' AS broken_key,
           CONCAT(
             'CartService.total() should return the sum of (unitPricePaise * quantity) for every line, ',
             'then subtract the coupon discount, and never return a negative number. Right now it applies ',
             'the discount once per line item instead of once per cart, so a 3-line cart with a flat ₹100 ',
             'coupon is discounted ₹300. Rewrite total() so the coupon is applied exactly once and the ',
             'result is floored at 0.'
           ) AS expected,
           'projects/shopsprint/challenges/cart-total-tests.java' AS test_key,
           'INTERMEDIATE' AS diff
    UNION ALL SELECT 'Checkout oversells the last item under concurrency',
           'projects/shopsprint/challenges/checkout-oversell-broken.java',
           CONCAT(
             'CheckoutService.checkout() reads stock, checks quantity, then decrements in three separate ',
             'statements with no lock, so two simultaneous checkouts for the last unit both succeed. ',
             'Rewrite it so stock can never go below zero — either a conditional UPDATE ... WHERE stock >= :qty ',
             'that fails the checkout when it affects 0 rows, or a SELECT ... FOR UPDATE inside the transaction.'
           ),
           NULL,
           'ADVANCED'
) v
WHERE p.slug = 'shopsprint-storefront-api'
  AND NOT EXISTS (SELECT 1 FROM bug_challenges bc WHERE bc.project_id = p.id AND bc.title = v.title);

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

-- =============================================================================================
-- Expanded sample data (2026-09) — more students, batches, sprints/tasks, a question-bank
-- library, learning resources, notifications, one graduate + certificate. Every statement is
-- idempotent on a natural key, same as everything above.
-- =============================================================================================

-- ---- Six more students -------------------------------------------------------------------
INSERT IGNORE INTO users (uuid, full_name, email, password_hash, github_username, status, email_verified_at) VALUES
 ('11111111-0000-0000-0000-000000000011', 'Ishaan Student Four',  'student4@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'ishaan-s4', 'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000012', 'Diya Student Five',    'student5@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'diya-s5',   'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000013', 'Kabir Student Six',    'student6@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', 'kabir-s6',  'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000014', 'Anaya Student Seven',  'student7@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000015', 'Vivaan Student Eight', 'student8@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6)),
 ('11111111-0000-0000-0000-000000000016', 'Myra Student Nine',    'student9@moriah.test', '$2a$12$f6U44aOOdmmxhJLSBN2D4uMCxyUemT3/K/IufvQj58SDMh6/PmLMC', NULL,        'ACTIVE', NOW(6));

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'STUDENT'
WHERE u.email IN ('student4@moriah.test','student5@moriah.test','student6@moriah.test',
                  'student7@moriah.test','student8@moriah.test','student9@moriah.test');

INSERT IGNORE INTO user_profiles (user_id, bio, location, current_title, experience_level, skills, portfolio_slug, is_complete, completion_percent)
SELECT u.id,
       CONCAT(SUBSTRING_INDEX(u.full_name, ' ', 1), ' - learner in the 2026 cohort.'),
       ELT(1 + (u.id % 5), 'Bengaluru', 'Pune', 'Chennai', 'Hyderabad', 'Remote'),
       'Trainee Engineer', 'JUNIOR',
       JSON_ARRAY('Java', 'SQL', 'Git'),
       LOWER(REPLACE(SUBSTRING_INDEX(u.email, '@', 1), '.', '-')),
       FALSE, 55
FROM users u WHERE u.email IN ('student4@moriah.test','student5@moriah.test','student6@moriah.test',
                               'student7@moriah.test','student8@moriah.test','student9@moriah.test');

-- ACTIVE PROJECT_BASED subscription for each (entitlements without a payments row).
INSERT INTO user_subscriptions (user_id, plan_id, payment_id, start_date, end_date, status, auto_renew)
SELECT u.id, p.id, NULL, CURDATE(), DATE_ADD(CURDATE(), INTERVAL p.duration_days DAY), 'ACTIVE', FALSE
FROM users u JOIN subscription_plans p ON p.code = 'PROJECT_BASED'
WHERE u.email IN ('student4@moriah.test','student5@moriah.test','student6@moriah.test',
                  'student7@moriah.test','student8@moriah.test','student9@moriah.test')
  AND NOT EXISTS (SELECT 1 FROM user_subscriptions s WHERE s.user_id = u.id AND s.status = 'ACTIVE');

-- ---- Three more batches (PM = pm@moriah.test) ------------------------------------------
INSERT INTO batches (name, track_code, pm_id, plan_tier_min_id, start_date, end_date, capacity, enrolled_count, status)
SELECT v.name, v.track_code,
       (SELECT id FROM users WHERE email = 'pm@moriah.test'),
       (SELECT id FROM subscription_plans WHERE code = 'PROJECT_BASED'),
       v.start_date, v.end_date, v.capacity, v.enrolled_count, v.status
FROM (
    SELECT 'FS-2026-02' AS name, 'FULL_STACK'     AS track_code, CURDATE()                          AS start_date,
           DATE_ADD(CURDATE(), INTERVAL 90 DAY)   AS end_date,   15 AS capacity, 3 AS enrolled_count, 'ACTIVE'  AS status
    UNION ALL SELECT 'DA-2026-01', 'DATA_ANALYTICS', DATE_SUB(CURDATE(), INTERVAL 20 DAY),
           DATE_ADD(CURDATE(), INTERVAL 70 DAY),  12, 2, 'ACTIVE'
    UNION ALL SELECT 'BE-2026-01', 'BACKEND',        DATE_ADD(CURDATE(), INTERVAL 14 DAY),
           DATE_ADD(CURDATE(), INTERVAL 104 DAY), 10, 1, 'PLANNED'
) v
WHERE NOT EXISTS (SELECT 1 FROM batches b WHERE b.name = v.name);

-- ---- Enrolments -----------------------------------------------------------------------
INSERT INTO batch_students (batch_id, user_id, status)
SELECT b.id, u.id, 'ACTIVE'
FROM batches b JOIN users u
  ON  (b.name = 'FS-2026-02' AND u.email IN ('student4@moriah.test','student5@moriah.test','student6@moriah.test'))
   OR (b.name = 'DA-2026-01' AND u.email IN ('student7@moriah.test','student8@moriah.test'))
   OR (b.name = 'BE-2026-01' AND u.email = 'student9@moriah.test')
WHERE NOT EXISTS (SELECT 1 FROM batch_students bs WHERE bs.batch_id = b.id AND bs.user_id = u.id);

-- student6 has finished FS-2026-02 - mark GRADUATED so /trainer/graduation and a certificate
-- have real data.
UPDATE batch_students bs
JOIN batches b ON b.id = bs.batch_id AND b.name = 'FS-2026-02'
JOIN users u  ON u.id = bs.user_id  AND u.email = 'student6@moriah.test'
SET bs.status = 'GRADUATED',
    bs.final_score = 82.50,
    bs.graduated_at = DATE_SUB(NOW(6), INTERVAL 3 DAY),
    bs.graduated_by = (SELECT id FROM users WHERE email = 'pm@moriah.test')
WHERE bs.status <> 'GRADUATED';

INSERT INTO certificates (user_id, batch_id, certificate_number, certificate_type, verification_code, issued_by, issued_at)
SELECT u.id, b.id, 'MSH-2026-900001', 'COMPLETION', 'SEEDCERT0006',
       (SELECT id FROM users WHERE email = 'pm@moriah.test'), DATE_SUB(NOW(6), INTERVAL 2 DAY)
FROM users u JOIN batches b ON b.name = 'FS-2026-02'
WHERE u.email = 'student6@moriah.test'
  AND NOT EXISTS (SELECT 1 FROM certificates c WHERE c.verification_code = 'SEEDCERT0006');

-- ---- One ACTIVE sprint per running batch + a PLANNED one for BE ------------------------
INSERT INTO sprints (batch_id, sprint_number, goal, start_date, end_date, status, planned_points, completed_points)
SELECT b.id, 1, v.goal, v.start_date, v.end_date, v.status, v.planned_points, 0
FROM batches b JOIN (
    SELECT 'FS-2026-02' AS name, 'Auth + user CRUD, one PR merged each.' AS goal,
           CURDATE() AS start_date, DATE_ADD(CURDATE(), INTERVAL 14 DAY) AS end_date, 'ACTIVE' AS status, 24 AS planned_points
    UNION ALL SELECT 'DA-2026-01', 'Clean + load the sales dataset; first dashboard.',
           DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'ACTIVE', 18
    UNION ALL SELECT 'BE-2026-01', 'Environment setup + REST fundamentals.',
           DATE_ADD(CURDATE(), INTERVAL 14 DAY), DATE_ADD(CURDATE(), INTERVAL 28 DAY), 'PLANNED', 20
) v ON v.name = b.name
WHERE NOT EXISTS (SELECT 1 FROM sprints s WHERE s.batch_id = b.id AND s.sprint_number = 1);

-- Tasks in FS-2026-02 sprint 1 (one BACKLOG, one ASSIGNED, one COMPLETED).
INSERT INTO tasks (sprint_id, title, description, task_type, assigned_to, story_points, due_at, status, completed_at)
SELECT s.id, v.title, v.descr, 'STORY', v.assignee, v.points, DATE_ADD(NOW(6), INTERVAL v.due_days DAY), v.status, v.completed
FROM sprints s JOIN batches b ON b.id = s.batch_id AND b.name = 'FS-2026-02' AND s.sprint_number = 1
JOIN (
    SELECT 'Login + JWT issue' AS title, 'POST /auth/login returning an access token.' AS descr,
           NULL AS assignee, 5 AS points, 7 AS due_days, 'BACKLOG' AS status, NULL AS completed
    UNION ALL SELECT 'User profile GET/PUT', 'Self-service profile read + update.',
           (SELECT id FROM users WHERE email = 'student4@moriah.test'), 5, 5, 'ASSIGNED', NULL
    UNION ALL SELECT 'Project scaffolding', 'Spring Boot project skeleton + CI.',
           (SELECT id FROM users WHERE email = 'student5@moriah.test'), 3, -2, 'COMPLETED', DATE_SUB(NOW(6), INTERVAL 1 DAY)
) v
WHERE NOT EXISTS (SELECT 1 FROM tasks t WHERE t.sprint_id = s.id AND t.title = v.title);

-- Tasks in DA-2026-01 sprint 1.
INSERT INTO tasks (sprint_id, title, description, task_type, assigned_to, story_points, due_at, status)
SELECT s.id, v.title, v.descr, 'ASSIGNMENT', v.assignee, v.points, DATE_ADD(NOW(6), INTERVAL v.due_days DAY), v.status
FROM sprints s JOIN batches b ON b.id = s.batch_id AND b.name = 'DA-2026-01' AND s.sprint_number = 1
JOIN (
    SELECT 'Ingest sales.csv' AS title, 'Load + validate the raw sales extract.' AS descr,
           (SELECT id FROM users WHERE email = 'student7@moriah.test') AS assignee, 3 AS points, 3 AS due_days, 'IN_PROGRESS' AS status
    UNION ALL SELECT 'Revenue-by-region chart', 'First Looker/Metabase view.',
           (SELECT id FROM users WHERE email = 'student8@moriah.test'), 5, 6, 'ASSIGNED'
) v
WHERE NOT EXISTS (SELECT 1 FROM tasks t WHERE t.sprint_id = s.id AND t.title = v.title);

-- ---- Question-bank library (author = dev@moriah.test) ---------------------------------
INSERT INTO question_banks (name, topic, description, created_by, is_active)
SELECT v.name, v.topic, v.descr, (SELECT id FROM users WHERE email = 'dev@moriah.test'), TRUE
FROM (
    SELECT 'Java Fundamentals'      AS name, 'Java'   AS topic, 'Core language: types, OOP, collections, exceptions.' AS descr
    UNION ALL SELECT 'Spring Boot Essentials', 'Spring', 'DI, REST controllers, data access, validation.'
    UNION ALL SELECT 'SQL and Data Modelling', 'SQL',   'SELECT/JOIN, indexing, normalisation, transactions.'
) v
WHERE NOT EXISTS (SELECT 1 FROM question_banks qb WHERE qb.name = v.name);

INSERT INTO question_bank_items (bank_id, question_text, question_type, options, correct_answer, marks, explanation, difficulty, created_by)
SELECT qb.id, v.qtext, v.qtype, v.opts, v.ans, v.marks, v.expl, v.diff,
       (SELECT id FROM users WHERE email = 'dev@moriah.test')
FROM question_banks qb JOIN (
    SELECT 'Java Fundamentals' AS bank, 'Which keyword prevents a class from being subclassed?' AS qtext, 'MCQ' AS qtype,
           JSON_ARRAY('static','final','sealed','private') AS opts, JSON_ARRAY(1) AS ans, 1 AS marks,
           'final on a class forbids extension.' AS expl, 'EASY' AS diff
    UNION ALL SELECT 'Java Fundamentals', 'Pick the collections that allow duplicate elements.', 'MULTI_SELECT',
           JSON_ARRAY('ArrayList','HashSet','LinkedList','TreeSet'), JSON_ARRAY(0,2), 2,
           'List implementations allow duplicates; Set implementations do not.', 'MEDIUM'
    UNION ALL SELECT 'Java Fundamentals', 'What does Optional.orElseThrow() do when the value is present?', 'MCQ',
           JSON_ARRAY('Throws immediately','Returns the contained value','Returns null','Logs a warning'), JSON_ARRAY(1), 1,
           'It returns the value; it only throws when empty.', 'EASY'
    UNION ALL SELECT 'Spring Boot Essentials', 'Which annotation marks a class as a REST endpoint holder?', 'MCQ',
           JSON_ARRAY('@Service','@Component','@RestController','@Repository'), JSON_ARRAY(2), 1,
           '@RestController = @Controller + @ResponseBody.', 'EASY'
    UNION ALL SELECT 'Spring Boot Essentials', 'Select valid ways to inject a dependency in Spring.', 'MULTI_SELECT',
           JSON_ARRAY('Constructor injection','Field injection','Setter injection','Static block injection'), JSON_ARRAY(0,1,2), 2,
           'Constructor is preferred; field and setter also work. There is no static-block injection.', 'MEDIUM'
    UNION ALL SELECT 'Spring Boot Essentials', 'Where does spring.datasource.url belong?', 'MCQ',
           JSON_ARRAY('pom.xml','application.yml','SecurityConfig.java','schema.sql'), JSON_ARRAY(1), 1,
           'Datasource settings are configuration properties.', 'EASY'
    UNION ALL SELECT 'SQL and Data Modelling', 'Which JOIN keeps unmatched left-table rows?', 'MCQ',
           JSON_ARRAY('INNER JOIN','LEFT JOIN','CROSS JOIN','SELF JOIN'), JSON_ARRAY(1), 1,
           'LEFT JOIN returns all left rows, NULL-filled where no match.', 'EASY'
    UNION ALL SELECT 'SQL and Data Modelling', 'Pick the statements that are true about a PRIMARY KEY.', 'MULTI_SELECT',
           JSON_ARRAY('It is unique','It can be NULL','It creates an index','You can have many per table'), JSON_ARRAY(0,2), 2,
           'A PK is unique, non-null, backed by an index, and there is exactly one per table.', 'MEDIUM'
) v ON v.bank = qb.name
WHERE NOT EXISTS (
    SELECT 1 FROM question_bank_items qbi WHERE qbi.bank_id = qb.id AND qbi.question_text = v.qtext);

-- ---- Learning resource library (author = dev@moriah.test) ----------------------------
INSERT INTO learning_resources (title, description, category, url, tags, created_by, is_active)
SELECT v.title, v.descr, v.cat, v.url, v.tags, (SELECT id FROM users WHERE email = 'dev@moriah.test'), TRUE
FROM (
    SELECT 'Spring Boot Reference - Getting Started' AS title, 'Official guide to your first Spring Boot app.' AS descr,
           'ARTICLE' AS cat, 'https://docs.spring.io/spring-boot/index.html' AS url, JSON_ARRAY('spring','backend') AS tags
    UNION ALL SELECT 'Java Collections in 20 Minutes', 'Short video tour of List/Set/Map.',
           'VIDEO', 'https://www.youtube.com/watch?v=rzA7tch1D_E', JSON_ARRAY('java','collections')
    UNION ALL SELECT 'Use The Index, Luke', 'A practical primer on SQL indexing.',
           'BOOK', 'https://use-the-index-luke.com/', JSON_ARRAY('sql','performance')
    UNION ALL SELECT 'HTTPie', 'A friendlier curl for testing your endpoints.',
           'TOOL', 'https://httpie.io/', JSON_ARRAY('http','testing')
    UNION ALL SELECT 'PR Description Template', 'Copy-paste checklist for every pull request.',
           'TEMPLATE', 'https://github.com/moriah/skillhub/blob/main/pull_request_template.md', JSON_ARRAY('git','process')
) v
WHERE NOT EXISTS (SELECT 1 FROM learning_resources lr WHERE lr.title = v.title);

-- Two resources scoped to the ShopSprint project, so the Resource Library's project filter
-- has something to show. project_id is resolved from the slug.
INSERT INTO learning_resources (title, description, category, track, project_id, url, tags, created_by, is_active)
SELECT v.title, v.descr, v.cat, 'FULL_STACK',
       (SELECT id FROM projects WHERE slug = 'shopsprint-storefront-api'),
       v.url, v.tags, (SELECT id FROM users WHERE email = 'dev@moriah.test'), TRUE
FROM (
    SELECT 'ShopSprint — API contract (OpenAPI)' AS title,
           'The frozen request/response contract for the storefront endpoints you implement.' AS descr,
           'ARTICLE' AS cat, 'https://github.com/moriah/shopsprint-storefront-api/blob/main/openapi.yaml' AS url,
           JSON_ARRAY('shopsprint','api') AS tags
    UNION ALL SELECT 'ShopSprint — Postman collection',
           'Ready-made requests for the cart, checkout and orders flows.',
           'TOOL', 'https://github.com/moriah/shopsprint-storefront-api/blob/main/ShopSprint.postman_collection.json',
           JSON_ARRAY('shopsprint','testing')
) v
WHERE EXISTS (SELECT 1 FROM projects WHERE slug = 'shopsprint-storefront-api')
  AND NOT EXISTS (SELECT 1 FROM learning_resources lr WHERE lr.title = v.title);

-- ---- A few IN_APP notifications for student1 -----------------------------------------
INSERT INTO notifications (user_id, channel, template_code, payload, status, sent_at)
SELECT (SELECT id FROM users WHERE email = 'student1@moriah.test'), 'IN_APP', v.tpl,
       JSON_OBJECT('title', v.title, 'body', v.body), v.status, v.sent_at
FROM (
    SELECT 'SUBMISSION_REVIEWED' AS tpl, 'Your PR was reviewed' AS title,
           'Priya PM approved "Implement DELETE /todos/{id}" - nice work.' AS body, 'SENT' AS status,
           DATE_SUB(NOW(6), INTERVAL 6 HOUR) AS sent_at
    UNION ALL SELECT 'ASSESSMENT_PUBLISHED', 'New assessment available',
           'A timed quiz for Sprint 1 has been published to your batch.', 'SENT', DATE_SUB(NOW(6), INTERVAL 2 DAY)
    UNION ALL SELECT 'STANDUP_REMINDER', 'Standup at 10:00',
           'Todays standup for FS-2026-01 starts at 10:00. Post your update.', 'QUEUED', NULL
) v
WHERE NOT EXISTS (
    SELECT 1 FROM notifications n
    WHERE n.user_id = (SELECT id FROM users WHERE email = 'student1@moriah.test')
      AND n.template_code = v.tpl);
