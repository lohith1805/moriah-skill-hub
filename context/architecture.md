# Architecture

## Stack

| Layer                  | Tool                                | Purpose                                                     |
| ---------------------- | ----------------------------------- | ----------------------------------------------------------- |
| Language               | Java 21 (LTS)                       | Records, sealed types, pattern matching, virtual threads    |
| Framework              | Spring Boot 3.5                     | Application framework, auto-configuration                    |
| Web                    | Spring Web MVC                      | REST controllers — virtual threads enabled                   |
| Security               | Spring Security 6 + OAuth2 Resource Server | JWT validation, RBAC, OAuth2 login                     |
| Persistence            | Spring Data JPA + Hibernate 6       | ORM, repositories, specifications                            |
| Database               | MySQL 8.0 (InnoDB) + 1 read replica | Relational source of truth, ACID transactions                |
| Migrations             | Flyway                              | Versioned schema — the only way schema ever changes          |
| Cache + Queue          | Redis 7                             | Cache, rate limiting, token denylist, notification queue     |
| Async Jobs             | Spring `@Scheduled` + `@Async`      | PIP evaluation, metrics refresh, notification dispatch       |
| Object Storage         | AWS SDK v2 (S3)                     | Resumes, assets, certificates, payslips — R2-compatible      |
| Payments               | Razorpay Java SDK, Stripe Java SDK  | Checkout orders, webhook verification, refunds               |
| Mapping                | MapStruct                           | Entity ↔ DTO mapping — no manual mappers                     |
| Validation             | Jakarta Bean Validation             | Request DTO validation                                       |
| PDF                    | OpenPDF + ZXing                     | Certificates, invoices, payslips, QR codes                   |
| Excel                  | Apache POI                          | XLSX report exports                                          |
| API Docs               | springdoc-openapi 2                 | OpenAPI 3 spec + Swagger UI                                  |
| GitHub                 | Spring `RestClient`                 | PR and commit verification via GitHub REST API               |
| Email                  | SendGrid Java / AWS SES v2          | Transactional email                                          |
| WhatsApp               | Meta WhatsApp Cloud API             | Template messages and alerts                                 |
| Testing                | JUnit 5, Mockito, Testcontainers, REST Assured | Integration tests against real MySQL              |
| Build                  | Maven                               | Build, dependency management, `mvn verify` gate              |

Schema modelling patterns are informed by mature LMS/CRM products (Rocket LMS, Worksuite, Perfex, Grow CRM) as **design reference only**. None are installed, purchased, called, or deployed.

---

## Architectural Style

Modular monolith. One deployable Spring Boot JAR, organised package-by-feature. Each feature module owns its entities, repositories, services, and controllers, and exposes a service interface to other modules.

One developer, 17 working days. Package boundaries are strict enough that extraction into services later is mechanical.

---

## Package Structure

```
com.moriah.skillhub
├── SkillHubApplication.java
├── common/
│   ├── config/
│   │   ├── SecurityConfig.java              → filter chain, CORS, method security
│   │   ├── JpaConfig.java                   → auditing, transaction manager
│   │   ├── RedisConfig.java                 → cache manager, serializers
│   │   ├── AsyncConfig.java                 → virtual thread executors
│   │   ├── SchedulingConfig.java            → scheduler pool, ShedLock
│   │   ├── S3Config.java                    → S3 client, endpoint override for R2
│   │   ├── OpenApiConfig.java               → springdoc metadata, security scheme
│   │   └── RestClientConfig.java            → GitHub, WhatsApp, SendGrid clients
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java      → the only place errors become responses
│   │   ├── BusinessException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── ForbiddenOperationException.java
│   │   └── ErrorCode.java                   → enum of every error code in the system
│   ├── dto/
│   │   ├── ApiResponse.java                 → { success, data, error, timestamp }
│   │   ├── PageResponse.java
│   │   └── ErrorDetail.java
│   ├── entity/
│   │   ├── BaseEntity.java                  → id, createdAt, updatedAt
│   │   └── AuditableEntity.java             → adds createdBy, updatedBy
│   ├── security/
│   │   ├── JwtService.java                  → issue, parse, validate
│   │   ├── JwtAuthFilter.java
│   │   ├── TokenRevocationService.java      → token_version check + Redis denylist
│   │   ├── CurrentUser.java
│   │   ├── SecurityUtils.java
│   │   ├── EntitlementGuard.java            → tier entitlement checks, DB-backed
│   │   └── OwnershipGuard.java              → resource ownership + storage key ownership
│   ├── audit/
│   │   ├── AuditLogService.java
│   │   └── Auditable.java
│   ├── storage/
│   │   ├── StorageService.java
│   │   └── S3StorageService.java
│   ├── notification/
│   │   ├── NotificationService.java         → enqueue only, never sends inline
│   │   ├── NotificationWorker.java          → reliable queue drain with ack
│   │   ├── EmailSender.java
│   │   └── WhatsAppSender.java
│   └── util/
│       ├── DateUtils.java
│       ├── PdfUtils.java
│       └── Constants.java
├── auth/            → AuthController, AuthService, OAuth2Service, TwoFactorService,
│                       RefreshToken, PasswordResetToken
├── user/            → UserController, UserService, ProfileService, ResumeService,
│                       User, Role, Permission, UserProfile
├── subscription/    → PlanController, SubscriptionController, EntitlementService,
│                       SubscriptionExpiryJob, SubscriptionPlan, UserSubscription
├── payment/         → CheckoutController, PaymentWebhookController, RazorpayService,
│                       StripeService, InvoiceService, WebhookIdempotencyService,
│                       InvoiceGenerationJob, Payment, Invoice, WebhookEvent
├── batch/           → BatchController, BatchService, BatchAllocationService,
│                       Batch, BatchStudent
├── sprint/          → SprintController, TaskController, SprintService, TaskService,
│                       Sprint, Task
├── submission/      → SubmissionController, ReviewController, SubmissionService,
│                       GitHubVerificationService, CodeReviewService, WeeklyReviewService,
│                       TaskSubmission, CodeReview, WeeklyReview
├── attendance/      → StandupController, AttendanceController, StandupService,
│                       AttendanceService, AttendanceFinalisationJob, Standup, Attendance
├── assessment/      → AssessmentController, QuizService, GradingService,
│                       Quiz, QuizQuestion, QuizAttempt, QuizAnswer, AssignmentWindow
├── project/         → ProjectController, ChallengeController, ProjectService,
│                       Project, ProjectAsset, BugChallenge
├── metrics/         → StudentMetricsService, MetricsRefreshJob, StudentMetric
├── pip/
│   ├── engine/      → PipEvaluationJob, PipRuleEvaluator (interface),
│   │                   AttendanceRule, ProjectDelayRule, AssignmentMissedRule,
│   │                   QuizFailureRule, ReviewFailedRule, TaskAbandonedRule
│   └── ...          → PipController, PipService, PipRecord, PipMilestone, PipRule
├── certificate/     → CertificateController, VerificationController, CertificateService,
│                       GraduationService, QrCodeService, Certificate
├── crm/             → LeadController, LeadService, LeadDeduplicationService,
│                       Lead, LeadActivity, SalesTarget
├── hr/              → OnboardingController, LeaveController, PayrollController,
│                       Employee, LeaveRequest, PayrollRecord, HrDocument
├── ba/              → RequirementController, ResourcePlanController,
│                       RequirementDocument, ResourceAllocation
├── client/          → ClientProjectController, ClientProjectService, Client, ClientProject
└── admin/           → AdminMetricsController, AuditController, ExportController,
                        MetricsService, ExportService

src/main/resources/db/migration/
├── V1__core_users_roles.sql              ← feature 02
├── V2__system_audit_notifications.sql    ← feature 02
├── V3__subscriptions_payments.sql        ← feature 02
├── V4__seed_roles_permissions.sql        ← feature 02
├── V5__seed_plans.sql                    ← feature 02
├── V6__batches_sprints_tasks.sql         ← feature 06
├── V7__submissions_reviews_attendance.sql← feature 06
├── V8__assessments_projects.sql          ← feature 06
├── V9__student_metrics.sql               ← feature 16
├── V10__pip.sql                          ← feature 17
├── V11__crm.sql                          ← feature 18
├── V12__hr.sql                           ← feature 19
├── V13__certificates.sql                 ← feature 20
├── V14__ba_client.sql                    ← feature 21
└── V15__indexes_constraints.sql          ← feature 23
```

**Migration versions ascend in the order features are built.** V1 is applied at feature 02 and V15 at feature 23, with nothing out of sequence. If a migration is added mid-build, it takes the next unused number — never one slotted between existing versions.

---

## Layer Boundaries

| Layer          | Owns                                                                 | Must Never                                              |
| -------------- | -------------------------------------------------------------------- | ------------------------------------------------------- |
| `controller/`  | HTTP mapping, request validation, response wrapping                  | Contain business logic or touch a repository directly   |
| `service/`     | All business logic, transaction boundaries, orchestration            | Return entities to controllers, or import HTTP types    |
| `repository/`  | Data access via Spring Data JPA                                      | Contain business rules or call other services           |
| `entity/`      | JPA-mapped persistent state                                          | Leave the service layer                                 |
| `dto/`         | Request and response shapes as Java records                          | Contain logic beyond compact-constructor validation     |
| `engine/`      | PIP rule evaluation                                                  | Be invoked from a controller synchronously              |
| `common/`      | Cross-cutting concerns only                                          | Import from any feature package                         |

A feature module may call another feature module's **service interface**. It may never touch another module's repository or entity directly.

---

## Data Flow

### Standard Request

```
HTTP request
     ↓
JwtAuthFilter → parse token, check token_version + Redis denylist
     ↓
Controller → @Valid on request record
     ↓
@PreAuthorize role check + EntitlementGuard tier check (DB-backed)
     ↓
Service (@Transactional)
     ↓
Repository → MySQL
     ↓
MapStruct → response record
     ↓
ApiResponse.success(data)
```

### Payment Webhook

The gateway timeout is roughly 5 seconds. Only the claim and the state transition happen inline; PDF rendering does not.

```
Gateway POST /api/v1/webhooks/razorpay
     ↓
Raw body captured before deserialization
     ↓
HMAC signature verified — reject 400 if invalid
     ↓
WebhookIdempotencyService.claim(): INSERT event_id into webhook_events
     ↓
Duplicate key → return 200 immediately, do nothing
     ↓
@Transactional: payment CAPTURED → subscription ACTIVE → invoice row (status PENDING)
     ↓
BatchAllocationService allocates student
     ↓
afterCommit: enqueue invoice PDF generation + notification
     ↓
200 OK returned to gateway, typically well under 1s
     ↓
InvoiceGenerationJob renders PDF → S3 → invoice status ISSUED
```

### PIP Nightly Pipeline

Two jobs in sequence, not one. Metrics are computed and persisted first; the rules then read a flat table.

```
01:30 IST  AttendanceFinalisationJob
             → any student with no attendance row for a conducted standup
               gets an ABSENT row written
             → without this, attendance percentage is computed against a
               shrinking denominator and never falls below 75%

01:45 IST  MetricsRefreshJob
             → recomputes student_metrics for every active student
             → three aggregate queries, one upsert per batch of 500

02:00 IST  PipEvaluationJob (ShedLock)
             → loads the whole cohort from student_metrics in ONE query
             → runs 6 rule evaluators in memory
             → breach + no open record → create record, milestones, notify
```

---

## Database Schema

MySQL 8.0, InnoDB, `utf8mb4_0900_ai_ci`. All tables carry `id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`, `created_at`, `updated_at` unless noted. Money is `DECIMAL(12,2)` — never float. Enums are `VARCHAR` with a `CHECK` constraint, never MySQL `ENUM`.

### V1 — Identity

**`users`**

| Column            | Type            | Notes                                    |
| ----------------- | --------------- | ---------------------------------------- |
| id                | BIGINT UNSIGNED | PK                                       |
| uuid              | CHAR(36)        | Public identifier, unique — never expose `id` |
| full_name         | VARCHAR(150)    |                                          |
| email             | VARCHAR(180)    | Unique                                   |
| phone             | VARCHAR(20)     | Unique, nullable                         |
| password_hash     | VARCHAR(100)    | BCrypt cost 12, null for OAuth-only users |
| github_username   | VARCHAR(100)    | Required before task submission          |
| linkedin_url      | VARCHAR(255)    |                                          |
| status            | VARCHAR(20)     | ACTIVE / SUSPENDED / TERMINATED / PENDING_VERIFICATION |
| token_version     | INT             | Default 0. **Incremented on suspend, password reset, role change, or logout-all. Every JWT carries it; a mismatch rejects the token immediately.** |
| two_factor_secret | VARBINARY(255)  | AES-GCM encrypted, nullable              |
| two_factor_enabled| BOOLEAN         | Default false                            |
| email_verified_at | DATETIME(6)     |                                          |
| last_login_at     | DATETIME(6)     |                                          |

**`roles`** — id, code (unique: STUDENT, TRAINER_PM, DEVELOPER, LEAD_GEN, HR_MANAGER, BUSINESS_ANALYST, ADMIN, CLIENT), name, description

**`permissions`** — id, code (unique, e.g. `sprint:create`), module, description. **Table exists, migration ships it, no row is ever inserted by any of the 24 features.** RBAC in this build is role-based (`@PreAuthorize("hasRole('X')")`) throughout — no controller checks a permission code. See the note under V1 below.

**`role_permissions`** — role_id FK, permission_id FK, composite PK. Same status as `permissions` — schema present, unpopulated, unread.

**`user_roles`** — user_id FK, role_id FK, composite PK

**`user_profiles`** — id, user_id FK unique, bio TEXT, location, current_title, experience_level, years_experience, skills JSON, education JSON, work_experience JSON, resume_key, portfolio_slug (unique), is_complete BOOLEAN, completion_percent TINYINT

**`refresh_tokens`** — id, user_id FK, token_hash (unique), expires_at, revoked_at, replaced_by, user_agent, ip_address

**`password_reset_tokens`** — id, user_id FK, token_hash (unique), expires_at, used_at

**`email_verification_tokens`** — id, user_id FK, token_hash (unique), expires_at, used_at

> **Permission-code RBAC is deferred, not implemented.** `permissions` and `role_permissions` are created by V1 so the schema doesn't need a breaking migration if fine-grained permissions are ever needed, but no feature in `build-plan.md` writes to them, and every authorization check in this codebase — every `@PreAuthorize`, every example in `code-standards.md` and `library-docs.md` — is `hasRole('X')` against `user_roles`. Treat `permissions`/`role_permissions` as present-but-inert. Adding a permission-code check anywhere is out of scope unless a feature is explicitly added for it; don't invent codes to make the empty table feel used. See `progress-tracker.md` for the decision record.

### V2 — System

Created in feature 02, not at the end. Feature 03 writes failed logins to `audit_logs` and feature 08 writes to `notifications`; both must exist first.

**`audit_logs`** — id, user_id FK nullable, action VARCHAR(100), entity_type, entity_id BIGINT, old_value JSON, new_value JSON, ip_address VARCHAR(45), user_agent VARCHAR(255), created_at. **Insert-only — the runtime DB user holds `INSERT` and `SELECT` only.**

**`notifications`** — id, user_id FK, channel (EMAIL / WHATSAPP / IN_APP / SMS), template_code, payload JSON, status (QUEUED / SENT / FAILED), attempts TINYINT, sent_at, error_message

**`shedlock`** — name VARCHAR(64) PK, lock_until DATETIME(3), locked_at DATETIME(3), locked_by VARCHAR(255). Required by the ShedLock JDBC provider.

**`job_runs`** — id, job_name, started_at, completed_at, items_processed, status (RUNNING / SUCCESS / FAILED), error_message. Every scheduled job writes a row. This is how you diagnose a PIP run that silently did nothing.

### V3 — Commerce

**`subscription_plans`** — id, code (unique), name, price_inr DECIMAL(12,2), tier_rank TINYINT, duration_days, max_projects, mentor_support BOOLEAN, allows_batch BOOLEAN, allows_sprints BOOLEAN, allows_pip BOOLEAN, allows_internship_letter BOOLEAN, allows_client_project BOOLEAN, is_active BOOLEAN

**`user_subscriptions`** — id, user_id FK, plan_id FK, payment_id FK nullable, start_date, end_date, status (PENDING / ACTIVE / EXPIRED / CANCELLED), auto_renew BOOLEAN

MySQL has no partial unique indexes. One active subscription per user is enforced with a generated column:

```sql
active_user_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (IF(status = 'ACTIVE', user_id, NULL)) STORED,
UNIQUE KEY uq_one_active_subscription (active_user_id)
```

**`payments`** — id, user_id FK, plan_id FK, gateway (RAZORPAY / STRIPE), gateway_order_id (unique), gateway_payment_id, amount DECIMAL(12,2), currency CHAR(3), status (CREATED / PENDING / CAPTURED / FAILED / REFUNDED), failure_reason, captured_at

**`invoices`** — id, payment_id FK unique, invoice_number (unique, `MSH-INV-{YYYY}-{seq}`), amount, tax_amount, total_amount, pdf_key, status (PENDING / ISSUED / FAILED), issued_at

**`webhook_events`** — id, gateway, event_id (**unique — the idempotency guarantee**), event_type, payload JSON, processed_at, status, error_message

**`coupons`** — id, code (unique), discount_type, discount_value, valid_from, valid_until, max_redemptions, times_redeemed, is_active

**`coupon_redemptions`** — id, coupon_id FK, user_id FK, payment_id FK, redeemed_at. Unique (coupon_id, user_id).

### V6 — Batches, Sprints, Tasks

**`batches`** — id, name, track_code, pm_id FK→users, plan_tier_min FK, start_date, end_date, capacity SMALLINT, enrolled_count SMALLINT, status (PLANNED / ACTIVE / COMPLETED / CANCELLED)

**No `version` column.** Capacity is enforced by a conditional atomic update, not optimistic locking — a hot counter under launch-day load would thrash on retries:

```sql
UPDATE batches
   SET enrolled_count = enrolled_count + 1
 WHERE id = ? AND enrolled_count < capacity;
-- 0 rows affected = batch full. No retry loop, no race.
```

**`batch_students`** — id, batch_id FK, user_id FK, joined_at, status (ACTIVE / ON_PIP / GRADUATED / TERMINATED / REASSIGNED), graduated_at, graduated_by FK→users, final_score DECIMAL(5,2). Unique (batch_id, user_id).

**`sprints`** — id, batch_id FK, sprint_number, goal TEXT, start_date, end_date, status (PLANNED / ACTIVE / COMPLETED), planned_points, completed_points. Unique (batch_id, sprint_number).

**`tasks`** — id, sprint_id FK, project_id FK nullable, title, description TEXT, task_type (DAILY / ASSIGNMENT / STORY / BUGFIX), assigned_to FK nullable, story_points TINYINT, due_at DATETIME(6), status (BACKLOG / ASSIGNED / IN_PROGRESS / IN_REVIEW / COMPLETED / REJECTED), completed_at

Index: `tasks(sprint_id, status, due_at)`, `tasks(assigned_to, status, due_at)`

### V7 — Submissions, Reviews, Attendance

**`task_submissions`** — id, task_id FK, user_id FK, attempt_number TINYINT, pr_url VARCHAR(500), repo_owner, repo_name, pr_number, pr_state, commit_count, latest_commit_sha, video_url, notes TEXT, status (SUBMITTED / APPROVED / CHANGES_REQUESTED), submitted_at, verified_at. **Unique (task_id, user_id, attempt_number)** — a double-POST cannot create two rows for one attempt.

**`code_reviews`** — id, submission_id FK, reviewer_id FK, score TINYINT (CHECK 1–10), verdict (APPROVED / CHANGES_REQUESTED), comments TEXT, inline_comments JSON, reviewed_at

**`weekly_reviews`** — id, batch_id FK, user_id FK, sprint_id FK, week_start DATE, rating (SATISFACTORY / NEEDS_IMPROVEMENT / UNSATISFACTORY), notes TEXT, reviewed_by FK, reviewed_at. Unique (user_id, week_start).

This table did not exist in the previous version of this document, and the `REVIEW_FAILED` PIP rule had nothing to read. The rule counts `UNSATISFACTORY` rows.

**`standups`** — id, batch_id FK, sprint_id FK nullable, scheduled_at DATETIME(6), late_cutoff_minutes SMALLINT, conducted_by FK, notes TEXT, status (SCHEDULED / CONDUCTED / CANCELLED), finalised_at DATETIME(6). `finalised_at` is set by `AttendanceFinalisationJob` and is what stops it reprocessing a standup.

**`attendance`** — id, standup_id FK, user_id FK, status (PRESENT / LATE / ABSENT / EXCUSED), checked_in_at, blocker_notes TEXT, marked_by FK nullable, is_auto_marked BOOLEAN. Unique (standup_id, user_id).

### V8 — Assessments and Content

**`quizzes`** — id, project_id FK nullable, batch_id FK nullable, title, duration_minutes, pass_percentage TINYINT default 60, max_attempts TINYINT, created_by FK, is_active

**`quiz_questions`** — id, quiz_id FK, question_text TEXT, question_type (MCQ / MULTI_SELECT / CODE), options JSON, correct_answer JSON, marks TINYINT, explanation TEXT

**`quiz_attempts`** — id, quiz_id FK, user_id FK, attempt_number, started_at, submitted_at, auto_graded_marks DECIMAL(6,2), auto_gradable_marks DECIMAL(6,2), percentage DECIMAL(5,2), passed BOOLEAN, status (IN_PROGRESS / SUBMITTED / EXPIRED / PENDING_MANUAL_GRADING)

**Percentage is `auto_graded_marks / auto_gradable_marks`** — ungraded `CODE` questions are excluded from the denominator entirely. Scoring them as zero would fail students on work nobody marked and fire `QUIZ_FAILURE` against them. If a quiz contains `CODE` questions, the attempt is also flagged `PENDING_MANUAL_GRADING`, and the `QUIZ_FAILURE` rule ignores attempts in that state.

**`quiz_answers`** — id, attempt_id FK, question_id FK, given_answer JSON, is_correct BOOLEAN nullable, marks_awarded DECIMAL(5,2) nullable

**`assignment_windows`** — id, batch_id FK, week_start DATE, week_end DATE, due_at DATETIME(6), task_id FK nullable. Unique (batch_id, week_start).

The `ASSIGNMENT_MISSED` rule needs discrete weekly windows to count consecutive misses against. Without this table "2+ consecutive weekly assignment windows" has no definition.

**`assignment_submissions_view`** is not a table — the rule joins `assignment_windows` against `task_submissions` on the window's `task_id`.

**`projects`** — id, title, slug (unique), description TEXT, tech_stack JSON, difficulty, domain, starter_repo_url, version VARCHAR(10), status (DRAFT / PUBLISHED / ARCHIVED), created_by FK

**`project_assets`** — id, project_id FK, asset_type, title, file_key, external_url, sort_order. CHECK: exactly one of `file_key` / `external_url` is non-null.

**`bug_challenges`** — id, project_id FK, title, broken_code_key, expected_behaviour TEXT, test_script_key, difficulty, created_by FK

### V9 — Student Metrics

**This replaces the nested reporting views from the previous version of this document.** MySQL 8 does not materialize views; three stacked aggregate views would re-scan `attendance`, `tasks`, and `quiz_attempts` in full on every PIP run and degrade as history accumulates.

**`student_metrics`**

| Column                   | Type            | Notes                                          |
| ------------------------ | --------------- | ---------------------------------------------- |
| id                       | BIGINT UNSIGNED | PK                                             |
| user_id                  | BIGINT UNSIGNED | FK                                             |
| batch_id                 | BIGINT UNSIGNED | FK                                             |
| computed_at              | DATETIME(6)     | Set by MetricsRefreshJob                       |
| attendance_present       | SMALLINT        | Rolling 14-day window                          |
| attendance_total         | SMALLINT        | Rolling 14-day window                          |
| attendance_percent       | DECIMAL(5,2)    |                                                |
| tasks_assigned           | SMALLINT        |                                                |
| tasks_completed          | SMALLINT        |                                                |
| tasks_overdue_48h        | SMALLINT        | Committed stories > 48h past `due_at`          |
| task_completion_percent  | DECIMAL(5,2)    |                                                |
| days_since_last_activity | SMALLINT        | Standup log or task progress                   |
| quiz_attempts_count      | SMALLINT        | Excludes PENDING_MANUAL_GRADING                |
| quiz_average_percent     | DECIMAL(5,2)    |                                                |
| consecutive_assignments_missed | TINYINT   | From `assignment_windows`                      |
| unsatisfactory_reviews   | TINYINT         | From `weekly_reviews`                          |

Unique (user_id, batch_id). Refreshed nightly by `MetricsRefreshJob` — three aggregate queries, upserted in batches of 500. The PIP job then reads this flat table in one indexed query.

Kept as genuine views (small, admin-facing, not on the nightly critical path):

| View               | Purpose                                        |
| ------------------ | ---------------------------------------------- |
| `v_batch_velocity` | Planned vs completed points per sprint         |
| `v_revenue_monthly`| Captured payment totals grouped by month       |
| `v_lead_funnel`    | Lead counts per status per agent per month     |

### V10 — PIP

**`pip_rules`** — id, rule_code (unique), description, threshold_value DECIMAL(6,2), window_days SMALLINT, severity, is_active. **Thresholds live here, not in Java.**

**`pip_records`** — id, user_id FK, batch_id FK, rule_code, trigger_reason TEXT, severity, triggered_at, start_date, end_date, status (TRIGGERED / IN_PROGRESS / CLEARED / TERMINATED / REASSIGNED), blocks_task_pull BOOLEAN, reviewed_by FK nullable, review_notes TEXT, outcome_at

One open record per user, via generated column (no partial indexes in MySQL):

```sql
open_user_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (
        IF(status IN ('TRIGGERED','IN_PROGRESS'), user_id, NULL)
    ) STORED,
UNIQUE KEY uq_one_open_pip (open_user_id)
```

**`pip_milestones`** — id, pip_record_id FK, title, description TEXT, due_date, status (PENDING / COMPLETED / MISSED), completed_at, verified_by FK

### V11 — CRM

**`leads`** — id, name, email, phone, source, lead_type, institution, interested_plan_id FK nullable, status (NEW / CONTACTED / DEMO_SCHEDULED / COUNSELLING_DONE / PAYMENT_PENDING / ENROLLED / LOST), assigned_agent_id FK, lost_reason, converted_user_id FK nullable, dedupe_hash CHAR(64) unique

**`lead_activities`** — id, lead_id FK, agent_id FK, activity_type, outcome, notes TEXT, next_follow_up_at, occurred_at

**`sales_targets`** — id, agent_id FK, period_month DATE, calls_target, calls_made, conversions_target, conversions_made, revenue_target, revenue_achieved. Unique (agent_id, period_month).

### V12 — HR

**`employees`** — id, user_id FK unique, employee_code (unique), department, designation, employment_type, date_of_joining, date_of_exit, base_salary DECIMAL(12,2), hourly_rate DECIMAL(10,2), reporting_manager_id FK, status

**`hr_documents`** — id, user_id FK, document_type, file_key, verification_status (PENDING / VERIFIED / REJECTED), verified_by FK, verified_at, rejection_reason

**`leave_requests`** — id, user_id FK, leave_type, from_date, to_date, days DECIMAL(4,1), reason TEXT, status, approved_by FK, decided_at

**`payroll_records`** — id, employee_id FK, period_month DATE, working_days, present_days, session_hours DECIMAL(6,2), gross_amount, deductions, net_amount DECIMAL(12,2), payslip_key, status (DRAFT / FINALISED / PAID), paid_at. Unique (employee_id, period_month).

### V13 — Certificates

**`certificates`** — id, user_id FK, batch_id FK, certificate_number (unique), certificate_type, verification_code CHAR(12) unique, pdf_key, issued_by FK, issued_at, revoked_at, revoke_reason

### V14 — BA and Client

**`clients`** — id, company_name, contact_person, email, phone, industry, user_id FK nullable, status

**`client_projects`** — id, client_id FK, title, scope_description TEXT, budget_range, target_batch_id FK nullable, status, submitted_at

**`requirement_documents`** — id, client_project_id FK nullable, doc_type, title, version, content LONGTEXT, file_key, status, authored_by FK, approved_by FK

**`resource_allocations`** — id, client_project_id FK, batch_id FK, user_id FK, role_in_project, allocated_days, story_points_estimate, from_date, to_date

---

## Object Storage

Private bucket. All access via presigned URLs with 15-minute TTL.

```
resumes/{userUuid}/resume.pdf
certificates/{certificateNumber}.pdf
invoices/{invoiceNumber}.pdf
payslips/{employeeCode}/{YYYY-MM}.pdf
projects/{projectId}/assets/{assetId}-{filename}
submissions/{submissionId}/{filename}
hr-documents/{userUuid}/{documentType}-{uuid}.pdf
```

**Every presigned URL request passes through `OwnershipGuard.canAccessKey(userId, key)`** before signing. The key layout embeds the owner's uuid or a resource id precisely so this check is possible. Signing a key because the caller asked for it is an IDOR.

---

## Database Users

Two, with different grants. This is what makes the insert-only audit log real rather than aspirational.

| User               | Used by            | Grants                                                        |
| ------------------ | ------------------ | ------------------------------------------------------------- |
| `moriah_migrate`   | Flyway, at startup | `ALL PRIVILEGES` on the schema (DDL required)                 |
| `moriah_app`       | HikariCP runtime   | `SELECT, INSERT, UPDATE, DELETE` on all tables **except** `audit_logs`, where it holds `SELECT, INSERT` only. No DDL. |

Flyway runs on a separate short-lived `DataSource` built from `MIGRATE_DB_USERNAME` / `MIGRATE_DB_PASSWORD`, then closes. The application pool never holds DDL rights.

---

## Backup, PITR and Disaster Recovery

Targets from the SRS: **RTO ≤ 2 hours, RPO ≤ 1 hour.** Meeting them requires configuration, not intention.

**Binary logging** — `log_bin` ON, `binlog_format = ROW`, `binlog_expire_logs_seconds = 604800` (7 days), `sync_binlog = 1`. This is the precondition for point-in-time recovery; without it, PITR is impossible regardless of backup frequency.

**Backups**

| What              | Frequency | Retention | Destination                       |
| ----------------- | --------- | --------- | --------------------------------- |
| Full logical dump | Nightly   | 30 days   | S3, server-side encrypted, versioned |
| Binlog shipping   | Every 5 min | 7 days  | S3, same bucket, separate prefix   |
| Pre-migration dump| Per deploy| 14 days   | S3, tagged with the Flyway version |

RPO is bounded by binlog shipping interval, not by the nightly dump — 5 minutes, comfortably inside the 1-hour target.

**Replication** — one asynchronous read replica in a second availability zone. It serves two purposes: a warm standby for promotion, and a read target for admin exports and metrics so a 50,000-row XLSX export never touches the primary.

**Restore drill** — monthly, and once during feature 24. Restore the latest full dump plus binlogs to a target timestamp on a scratch instance, then diff row counts against the primary. **A backup that has never been restored is not a backup.**

**Recovery runbook** — kept in `docs/runbook-dr.md`: promote replica, repoint the application, verify `flyway_schema_history` matches the deployed artifact, confirm `webhook_events` for reconciliation against the gateways.

---

## Schema Change Discipline

Flyway Community has no undo scripts. **Every migration is forward-only.** Reverting a deployment reverts the application, never the schema. Therefore:

**Expand / contract is mandatory for any destructive change.**

```
Deploy N     — expand:   add the new column, nullable, backfilled.
                         Application writes both old and new.
Deploy N+1   — migrate:  application reads the new column only.
Deploy N+2   — contract: drop the old column.
```

At every point, the previous application version still runs against the current schema. A single deploy that renames or drops a column in one step cannot be rolled back without data loss.

**Rules**

- Never `DROP COLUMN`, `RENAME COLUMN`, or narrow a type in the same release that changes the code using it
- Any migration touching a table over 1M rows uses `ALGORITHM=INPLACE, LOCK=NONE` and is verified with `EXPLAIN` first
- `V15__indexes_constraints.sql` adds `CHECK` constraints and indexes to populated tables — this is the one migration most likely to lock in production. Test it against a restored production-sized dump, not an empty Testcontainer.
- Seed migrations contain reference data only (roles, plans, pip_rules). `permissions`/`role_permissions` are created but deliberately not seeded — see the note under V1. **Sample users, batches, and leads live in `db/testdata/` and are loaded by a dev-profile runner, never by Flyway.**

---

## Authentication

- Access token: JWT, HS512, 60-minute expiry, carrying `sub` (uuid), `roles`, `jti`, `tv` (token_version)
- **`tier` is not a claim.** Entitlements are read from the database by `EntitlementGuard` on each check. A cached tier claim goes stale the moment a user upgrades, and stale entitlement is an authorization bug.
- Refresh token: opaque 512-bit value, SHA-256 hashed, 30-day expiry, **rotated on every use** — reuse of a consumed token revokes the entire chain
- **Revocation:** `JwtAuthFilter` compares the token's `tv` claim against `users.token_version`, read directly from the database on every request — **not** cached in Redis, a deliberate deviation from an earlier draft of this document. `JwtAuthFilter` must load the `User` row by `uuid` on every request regardless (`sub` is the uuid, never the numeric id), so the freshest `token_version` is already in hand from that same query; a separate 60s-TTL cache on top would be redundant and strictly weaker, letting a suspended user's token stay valid for up to 60 seconds instead of failing on the very next request. Suspension, password reset, and role change increment `token_version`, invalidating every outstanding token for that user within a second — the direct-read design is what makes "within a second" true rather than "within 60 seconds." `jti` additionally goes on a Redis denylist on explicit logout (this part is genuinely cached — see `TokenRevocationService`).
- `JWT_SECRET` carries a key id (`kid`) so it can be rotated with an overlap window rather than logging out every user at once
- OAuth2: Google and GitHub via Spring Security OAuth2 Client
- 2FA: TOTP, secret AES-GCM encrypted with a key from the secret store, mandatory for `ADMIN` and `HR_MANAGER`
- Public endpoints: `/api/v1/auth/**`, `GET /api/v1/plans`, `/api/v1/certificates/verify/{code}`, `/api/v1/webhooks/**`, `/actuator/health`, springdoc paths

---

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=true&serverTimezone=UTC
    username: ${DB_USERNAME}          # moriah_app — no DDL
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:50}
      minimum-idle: 10
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.jdbc.batch_size: 50
      hibernate.order_inserts: true
  flyway:
    enabled: true
    user: ${MIGRATE_DB_USERNAME}      # moriah_migrate — DDL
    password: ${MIGRATE_DB_PASSWORD}
    baseline-on-migrate: false
  threads:
    virtual:
      enabled: true

moriah:
  jwt:
    secret: ${JWT_SECRET}
    key-id: ${JWT_KEY_ID:v1}
    access-token-minutes: 60
  storage:
    bucket: ${S3_BUCKET}
    endpoint: ${S3_ENDPOINT:}
  jobs:
    attendance-finalisation-cron: "0 30 1 * * *"
    metrics-refresh-cron: "0 45 1 * * *"
    pip-evaluation-cron: "0 0 2 * * *"
    zone: Asia/Kolkata
```

**Pool sizing note:** virtual threads let thousands of requests block on the pool; they do not raise throughput. The connection pool, not the thread model, is the concurrency ceiling. `DB_POOL_MAX` is tunable per environment and must be load-tested in feature 23, not assumed.

---

## Invariants

- Flyway owns the schema. `ddl-auto` is `validate`. No entity change ships without a matching versioned migration.
- Migration versions ascend in feature order. A new migration takes the next unused number, never one inserted between existing versions.
- Never edit an applied migration. Never `DROP`/`RENAME` a column in the same release as the code change — expand/contract only.
- The application DB user has no DDL rights and cannot `UPDATE` or `DELETE` `audit_logs`.
- `open-in-view` is `false`. A `LazyInitializationException` means the query was wrong.
- Controllers never inject a repository. Services never return an entity across the controller boundary.
- Every response is wrapped in `ApiResponse`. Every list endpoint is paginated.
- Every write is inside a `@Transactional` service method. Never `@Transactional` on a controller.
- No outbound HTTP call inside a transaction. Side effects go through `afterCommit`.
- Every payment webhook is signature-verified and deduplicated via `webhook_events.event_id` before any state change. PDF rendering happens after the response.
- MySQL has no partial unique indexes. Conditional uniqueness uses a `STORED` generated column plus a unique key.
- Batch capacity is enforced by conditional atomic `UPDATE`, never by read-then-write or optimistic retry.
- The PIP engine reads `student_metrics` only, in one query. A repository call inside the per-student loop is a defect.
- `AttendanceFinalisationJob` runs before `MetricsRefreshJob`, which runs before `PipEvaluationJob`. Reordering breaks the attendance denominator.
- PIP thresholds are rows in `pip_rules`. A numeric threshold in Java is a defect.
- Ungraded `CODE` answers are excluded from the quiz denominator. Never scored as zero.
- Every presigned URL is authorized by `OwnershipGuard` before signing.
- Money is `BigDecimal` end to end. Timestamps stored UTC; `Asia/Kolkata` applied at presentation and scheduling only.
- No endpoint exposes `users.id`. The public identifier is `users.uuid`.
- Every scheduled job writes a `job_runs` row. A job that silently did nothing must be diagnosable.
- **RBAC is role-based only.** `permissions`/`role_permissions` exist in the schema but are seeded empty and read by nothing. Never gate an endpoint on a permission code unless a feature explicitly introduces that mechanism first.
