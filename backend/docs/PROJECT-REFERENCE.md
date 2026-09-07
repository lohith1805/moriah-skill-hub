# Moriah Skill Hub — Backend Project Reference

**A single, deep reference for the whole backend: what the platform is, how it is built, every
module and feature, every endpoint with its full controller → service → repository → database call
chain, and an appendix of every issue found in the Aug 2026 audit and exactly how it was fixed.**

Companion documents in `docs/`:

| File | What it is |
|---|---|
| **`PROJECT-REFERENCE.md`** (this file) | The deep, everything-in-one reference |
| `API-Documentation.md` | Quick per-endpoint request/response reference for frontend + QA |
| `audit-2026-08-31.md` | The audit in full — findings, "how each fix works", fix log, `mvn verify` results |
| `openapi.json` | Machine-readable OpenAPI 3.1 spec (source for the two generated docs) |
| `runbook-dr.md` | Disaster-recovery runbook (replica promotion, PITR) |
| `context/*.md` | The authoritative living design docs (`architecture.md`, `build-plan.md`, `code-standards.md`, `library-docs.md`, `progress-tracker.md`, `project-overview.md`, `AGENTS.md`) |

> The `context/` files are the source of truth. This document summarises and cross-references them
> and adds the per-endpoint call-chain detail; where this file and `context/architecture.md`
> disagree, `context/` wins.

---

# Part 1 — The Project

## 1.1 What it is

Moriah Skill Hub is an **enterprise EdTech / project-based-learning platform**. It replaces passive
video-watching with a task-driven, sprint-oriented training model:

> A student subscribes to a plan → is placed into a **batch** → works client-simulated projects in
> 1–2 week agile **sprints** → attends daily **standups** → pulls **tasks** from a backlog →
> submits code as **GitHub pull requests** → is **reviewed and scored 1–10** by a Project Manager
> → and **graduates with a QR-verifiable certificate**.

Around that learning core sit three operational systems:

1. **Agile project tracker** — sprints, tasks, story points, velocity.
2. **Automated PIP engine** — a Performance Improvement Plan engine that detects underperformance
   from hard numeric thresholds (attendance, delivery, assessment scores) and drives a 15-day
   remediation cycle **with no human bias** — a struggling student is caught in days, not months.
3. **Combined CRM + HR suite** — lead acquisition, staff onboarding, payroll, document
   verification, exit management.

**This codebase is the backend and database only.** Spring Boot 3.5 · Java 21 · MySQL 8.0 · Redis
7 · Maven. The React SPA is a separate track and is **not** in this repo. The backend is the single
source of truth for every business rule — the frontend never computes a threshold, a percentage,
an entitlement, or a PIP decision; it renders what the API returns.

## 1.2 The problem it solves

Bootcamps and colleges produce graduates who have watched hundreds of hours of video and never
opened a pull request — no attendance discipline, no code review, no deadline pressure, and no
early-warning system when a student silently falls behind. Moriah Skill Hub makes the learning
process **operationally identical to a real engineering job** and intervenes automatically at fixed
thresholds.

## 1.3 Roles (8)

RBAC is enforced with `@PreAuthorize("hasRole('X')")` on **every** non-public endpoint. `ADMIN`
bypasses ownership checks but **never** bypasses audit logging.

| Role | Category | Core capabilities |
|---|---|---|
| `STUDENT` | Learner / Intern | Subscribe, enrol, check in to standups, pull tasks, submit PRs, take quizzes, view PIP status, download certificate |
| `TRAINER_PM` | Instructional Lead | Create batches, plan sprints, assign tasks, log attendance, review & score code, approve/reject PIP, sign off graduation |
| `DEVELOPER` | Content Author | Author projects, upload starter repos & docs, create bug-fix challenges, build question banks |
| `LEAD_GEN` | Sales & Marketing | Ingest leads, move pipeline stages, log calls, dispatch WhatsApp/email, track targets |
| `HR_MANAGER` | Talent & Operations | Onboard staff/students, verify documents, manage leave, run payroll, issue letters |
| `BUSINESS_ANALYST` | Product & Delivery | Author BRD/SRS/FRS, map client scope to sprints, plan resource allocation |
| `ADMIN` | Platform Governance | Global user management, plan/pricing config, revenue metrics, audit trails |
| `CLIENT` | External Partner | Submit project requirements, view progress on their own project only |

## 1.4 Subscription tiers (5)

Prices and entitlement flags live in the `subscription_plans` table and are **ADMIN-configurable
at runtime** — never hardcoded. `tier` is **not** a JWT claim; entitlements are read from the DB on
every check (`EntitlementService`, 60-second Redis cache), so an upgrade takes effect immediately.
A `STARTER` student calling a sprint endpoint gets **`403 ENTITLEMENT_REQUIRED`**, not an empty
list.

| Tier | Price (INR) | Adds |
|---|---|---|
| Starter | 3,999 | Video + quiz access only. No batch, no sprints, no PIP. |
| Professional | 7,999 | + assignments, task submission, auto-graded assessments |
| Project Based | 14,999 | + batch allocation, sprints, GitHub PR review, standups, **PIP** |
| Internship | 19,999 | + 1:1 mentor, HR onboarding, experience letter |
| Corporate Program | 29,999 | + client project assignment, placement referral records |

Entitlement enum: `BATCH`, `SPRINTS`, `PIP`, `MENTOR`, `INTERNSHIP_LETTER`, `CLIENT_PROJECT`.

## 1.5 Core flows

### Enrolment (one request cycle + webhook latency)

```
POST /api/v1/subscriptions/checkout
  → CheckoutService: re-reads plan price from subscription_plans, applies coupon,
    creates payments row (CREATED), calls the gateway, returns the order
  → gateway redirect / client-side payment
  → gateway calls POST /api/v1/webhooks/{razorpay|stripe}
  → raw body captured → HMAC signature verified (400 if bad)
  → WebhookIdempotencyService.claim(): INSERT event_id into webhook_events
       duplicate key → 200 immediately, do nothing
  → @Transactional: payments → CAPTURED, user_subscriptions → ACTIVE,
    invoices row (PENDING)
  → BatchAllocationService.allocate(): conditional atomic UPDATE on batches to
    reserve a seat in an ACTIVE/PLANNED batch matching track + tier
  → afterCommit: enqueue invoice-PDF generation + welcome notification
  → 200 OK to the gateway, well under 1s
  → InvoiceGenerationJob (@Async): render PDF → S3 → invoices.status = ISSUED
```

### Daily sprint cycle

```
Student checks in            → attendance row, PRESENT / LATE by cutoff
PM opens standup             → standup row, blockers per student
Student pulls a task         → tasks.assigned_to set, status IN_PROGRESS
                               (blocked if an open PROJECT_DELAY PIP has blocks_task_pull)
Student submits a PR URL     → GitHub REST verifies PR exists, author = github_username, state open
PM reviews                   → code_reviews row, score 1–10, APPROVED | CHANGES_REQUESTED
Task closes                  → tasks.status COMPLETED, feeds sprint velocity
```

### PIP engine — three ordered nightly jobs

The ordering is load-bearing: metrics computed before absences are written are wrong.

```
01:30 IST  AttendanceFinalisationJob
             every enrolled student with no attendance row for a CONDUCTED standup
             gets an ABSENT row (is_auto_marked = true), then finalised_at is set.
             Without this the attendance denominator only counts students who showed
             up, so a student who never attends reads as 100%.

01:45 IST  MetricsRefreshJob  (gated on AttendanceFinalisationJob SUCCESS via JobChainGuard)
             recomputes student_metrics for every active student — several flat
             aggregate queries (attendance / tasks / quizzes / assignment windows /
             weekly reviews), upserted in batches of 500.

02:00 IST  PipEvaluationJob  (ShedLock; gated on MetricsRefreshJob SUCCESS)
             loads the whole active cohort from student_metrics in one query,
             runs 6 rule evaluators in memory. Breach + no open record →
             pip_records row (TRIGGERED), generated pip_milestones, notifications
             to student + PM + HR (afterCommit). One open record per user, enforced
             by a STORED generated column + unique key.

Day 15     PM records the exit review. Clearance requires task completion ≥ 85%
           AND a passed review, both verified server-side against student_metrics —
           the PM cannot clear a student who does not meet criteria.
           Outcome: CLEARED (reinstate) | TERMINATED | REASSIGNED, all audited.
```

### PIP trigger rules (thresholds live in `pip_rules`, never in Java)

| Rule code | Reads from `student_metrics` | Default threshold | Severity |
|---|---|---|---|
| `ATTENDANCE_LOW` | `attendance_percent` | < 75% (rolling 14-day) | HIGH |
| `PROJECT_DELAY` | `tasks_overdue_48h` | ≥ 1 (committed story > 48h past `due_at`) — also sets `blocks_task_pull` | CRITICAL |
| `ASSIGNMENT_MISSED` | `consecutive_assignments_missed` | ≥ 2 consecutive weekly windows | MEDIUM |
| `QUIZ_FAILURE` | `quiz_average_percent` | < 60% cumulative | MEDIUM |
| `REVIEW_FAILED` | `unsatisfactory_reviews` | ≥ 1 `UNSATISFACTORY` weekly review | HIGH |
| `TASK_ABANDONED` | `days_since_last_activity` | ≥ 3 days no standup log & no task progress | HIGH |

---

# Part 2 — Architecture

## 2.1 Style & layer boundaries

**Modular monolith** — one deployable Spring Boot JAR, organised **package-by-feature**. Each
feature module owns its entities, repositories, services and controllers and exposes a **service
interface** to other modules. A module may call another module's service; it may **never** touch
another module's repository or entity. `common/` never imports a feature package.

| Layer | Owns | Must never |
|---|---|---|
| `controller/` | HTTP mapping, `@Valid`, response wrapping | contain business logic or touch a repository |
| `service/` | all business logic, `@Transactional` boundaries, orchestration | return an entity past the controller boundary, or import HTTP types |
| `repository/` | Spring Data JPA data access | contain business rules or call another service |
| `entity/` | JPA-mapped persistent state | leave the service layer |
| `dto/` | request/response shapes as Java `record`s | logic beyond compact-constructor validation |
| `engine/` (pip) | PIP rule evaluation | be invoked synchronously from a controller |
| `common/` | cross-cutting concerns only | import from any feature package |

## 2.2 Standard request lifecycle

```
HTTP request
  → RateLimitFilter          (Redis fixed-window; per bearer-token for authed calls, per IP otherwise;
                              10/min on /auth/**, 300/min elsewhere)
  → JwtAuthFilter            parse HS512 JWT → check jti not on the Redis denylist →
                              load users row by uuid (lightweight AuthUserView projection) →
                              compare token 'tv' claim against users.token_version (fresh DB read,
                              NOT cached → suspension revokes within ~1s)
  → @PreAuthorize            role check (ROLE_* authorities from user_roles)
  → EntitlementService       tier entitlement check where required (DB-backed, 60s Redis cache)
  → Controller               @Valid on the request record
  → Service (@Transactional) business logic + orchestration
  → Repository → MySQL
  → MapStruct / manual map   entity → response record
  → ApiResponse.success(data)
```

`open-in-view` is **false** — a `LazyInitializationException` means the query was wrong, not that
the view should be opened.

## 2.3 The response envelope

**Every** endpoint returns `ApiResponse<T>` — never a bare object or list.

```json
{ "success": true, "data": { "...": "payload" }, "error": null, "timestamp": "2026-01-15T10:30:00Z" }
```

On error, `GlobalExceptionHandler` (the only place errors become responses) emits:

```json
{ "success": false, "data": null,
  "error": { "code": "VALIDATION_FAILED", "message": "…", "fieldErrors": [{ "field": "email", "message": "must not be blank" }] },
  "timestamp": "2026-01-15T10:30:00Z" }
```

`ErrorCode` is an enum of every code in the system; each carries an HTTP status. Common ones:
`VALIDATION_FAILED` (400), `INVALID_CREDENTIALS` / `UNAUTHENTICATED` (401),
`INSUFFICIENT_ROLE` / `NOT_RESOURCE_OWNER` / `ENTITLEMENT_REQUIRED` (403),
`RESOURCE_NOT_FOUND` (404), `*_ALREADY_*` / illegal-state (409), `RATE_LIMIT_EXCEEDED` (429),
`INTERNAL_ERROR` (500). No stack trace, SQL fragment or raw exception message ever reaches a
client.

**Pagination** — every list endpoint is paginated: `?page=0&size=20&sort=field,asc` (max
`size` = 100), returning `{ content[], page, size, totalElements, totalPages, last }` inside the
envelope.

## 2.4 Authentication & authorization

- **Access token** — JWT, **HS512**, 60-minute expiry, claims `sub` (uuid — the numeric id is
  never exposed), `roles`, `jti`, `tv` (token_version); `kid` header for key rotation.
- **Refresh token** — opaque 512-bit value, **SHA-256 hashed at rest**, 30-day expiry,
  **rotated on every use**. Reuse of a consumed token revokes the entire chain and writes an audit
  row.
- **Revocation** — `JwtAuthFilter` compares the token's `tv` against `users.token_version` read
  **directly from the DB on every request** (not cached). Suspend / password-reset / role-change /
  logout-all increments `token_version` → every outstanding token for that user is invalid on the
  **next request** (~1s), not after the 60-minute expiry. `jti` additionally goes on a Redis
  denylist on explicit logout (this part is cached — `TokenRevocationService`).
- **OAuth2** — Google (OIDC) and GitHub (plain OAuth2). First OAuth login creates a `STUDENT` with
  `email_verified_at` set and no password hash; an existing email **links** the provider, never
  creates a duplicate. GitHub login captures `github_username` (needed for PR verification). The
  in-flight auth request is stored in a **signed** (HMAC-SHA256) JSON cookie — never Java
  serialization (see audit C1).
- **2FA** — TOTP, secret **AES-256-GCM encrypted** at rest, **mandatory for `ADMIN` and
  `HR_MANAGER`** — they get a challenge token on login and exchange it at `POST /auth/2fa/verify`
  for the token pair.
- **Public endpoints** — `/api/v1/auth/**`, `GET /api/v1/plans`,
  `/api/v1/certificates/verify/{code}`, `/api/v1/webhooks/**`, `/actuator/health`, springdoc paths.
- **Rate limiting** — Redis fixed 1-minute windows; `/auth/**` 10/min per client IP,
  everything else 300/min keyed on a bearer-token hash (per client IP when unauthenticated).
  `trusted-proxies` (CIDR-aware) must be set at deploy time for per-client IP resolution behind a
  load balancer.

## 2.5 Entitlement & ownership guards

- `EntitlementService` — reads the caller's active subscription and its plan flags from the DB
  (60s Redis cache, evicted on the webhook activation path). No active subscription →
  `403 ENTITLEMENT_REQUIRED`, never `401`.
- `OwnershipGuard.canAccessKey(userId, key)` — **every presigned S3 URL request passes through
  this before signing.** The storage key layout embeds the owner's uuid or a resource id precisely
  so this check is possible; signing a key because the caller asked for it is an IDOR.
- `SecurityUtils` / `@CurrentUser` — the caller's identity always comes from the `SecurityContext`,
  never from a request body or path.

## 2.6 Object storage

Private bucket, all access via **presigned GET URLs with a 15-minute TTL**. The DB stores the key,
never the URL. Uploads are validated for content type, magic bytes, a 10 MB cap, and a
**server-generated filename**.

```
resumes/{userUuid}/resume.pdf
certificates/{certificateNumber}.pdf
invoices/{invoiceNumber}.pdf
payslips/{employeeCode}/{YYYY-MM}.pdf
projects/{projectId}/assets/{assetId}-{filename}
submissions/{submissionId}/{filename}
hr-documents/{userUuid}/{documentType}-{uuid}.pdf
exports/{report}/{uuid}.xlsx
```

## 2.7 Notifications

`NotificationService.enqueueAfterCommit(...)` writes a `notifications` row then pushes to a Redis
list — it **never sends inline**. `NotificationWorker` drains the list as a **reliable queue**
(`RPOPLPUSH` into a processing list, ack only after a confirmed dispatch), now in **concurrent
batches** on a bounded executor with per-provider HTTP timeouts (audit H3). `NotificationReaperJob`
re-queues anything stuck in the processing list past 5 minutes and re-derives lost entries from
the DB. Channels: `EMAIL` (SendGrid), `WHATSAPP` (Meta Cloud API), `IN_APP`, `SMS`.

## 2.8 Scheduled jobs (all write a `job_runs` row; all `@SchedulerLock`-guarded where single-instance matters)

| Job | Cron (Asia/Kolkata) | Reads → Writes |
|---|---|---|
| `AttendanceFinalisationJob` | `0 30 1 * * *` | `standups`, `batch_students`, `attendance` → `attendance` (ABSENT rows), `standups.finalised_at` |
| `MetricsRefreshJob` | `0 45 1 * * *` | `attendance`, `tasks`, `quiz_attempts`, `assignment_windows`, `weekly_reviews` → `student_metrics` (upsert 500/batch). Gated on `AttendanceFinalisationJob` SUCCESS. |
| `PipEvaluationJob` | `0 0 2 * * *` | `student_metrics`, `pip_rules` → `pip_records`, `pip_milestones` + notifications. Gated on `MetricsRefreshJob` SUCCESS. |
| `SubscriptionExpiryJob` | `0 0 3 * * *` | `user_subscriptions` → status `EXPIRED` for rows past `end_date` |
| `InvoiceGenerationJob` | `@Async` on `PaymentCapturedEvent` (AFTER_COMMIT) | renders invoice PDF → S3 → `invoices.status = ISSUED` |
| `SubmissionVerificationRetryJob` | `0 */15 * * * *` | re-verifies `task_submissions` with `verified_at IS NULL` against GitHub |
| `QuizAttemptExpiryJob` | `0 */15 * * * *` | sweeps un-submitted `quiz_attempts` past their duration to `EXPIRED`, scored on answers given |
| `NotificationReaperJob` | `0 * * * * *` | re-queues stale `notifications` processing entries |
| `WebhookReconciliationJob` | `0 */10 * * * *` | logs `ERROR` for `webhook_events` stuck in `RECEIVED` > 30 min (audit C3) |
| `ExpiredTokenReaperJob` | `0 20 3 * * *` | deletes expired `refresh_tokens` / `password_reset_tokens` / `email_verification_tokens` past a 30-day grace (audit M20) |

## 2.9 Caching (Redis)

`@Cacheable` only on genuinely read-heavy, rarely-changing, non-student-scoped data: `plans` /
`planCodesById` (default TTL), `entitlements` (60s — short so upgrades take effect fast; explicit
`@CacheEvict` on the webhook activation path), `githubPr` (5 min — protects the GitHub 5,000/hr
limit), `adminMetricsOverview` (5 min). Redis also holds the JWT `jti` denylist, rate-limit
counters, and 2FA challenge tokens.

## 2.10 Database

MySQL 8.0, InnoDB, `utf8mb4_0900_ai_ci`. Every table has `id BIGINT UNSIGNED AUTO_INCREMENT PK`,
`created_at`, `updated_at`. Money is `DECIMAL(12,2)` / `BigDecimal` end-to-end — never float.
Enums are `VARCHAR` + a `CHECK` constraint (never MySQL `ENUM`). Timestamps are stored UTC;
`Asia/Kolkata` is applied only at presentation and scheduling.

**Two DB users** make the insert-only audit log real:

| User | Used by | Grants |
|---|---|---|
| `moriah_migrate` | Flyway at startup (separate short-lived DataSource) | `ALL PRIVILEGES` (DDL) |
| `moriah_app` | HikariCP runtime pool | `SELECT/INSERT/UPDATE/DELETE` on all tables **except** `audit_logs` (`SELECT, INSERT` only). No DDL. |

**One async read replica** — warm standby + read target for admin exports / metrics so a
50,000-row XLSX never touches the primary (`ReplicaDataSourceConfig`, a second `JdbcTemplate`).

### Migration ledger

Flyway, forward-only, `ddl-auto: validate`. Versions ascend in feature order.

| Version | Adds |
|---|---|
| `V1__core_users_roles` | `users`, `roles`, `permissions`†, `role_permissions`†, `user_roles`, `user_profiles`, `refresh_tokens`, `password_reset_tokens`, `email_verification_tokens` |
| `V2__system_audit_notifications` | `audit_logs` (insert-only), `notifications`, `shedlock`, `job_runs` |
| `V3__subscriptions_payments` | `subscription_plans`, `user_subscriptions` (+ `active_user_id` generated col), `payments`, `invoices`, `webhook_events` (unique `event_id`), `coupons`, `coupon_redemptions` |
| `V4__seed_roles_permissions` | seeds the 8 roles only (idempotent) |
| `V5__seed_plans` | seeds the 5 tiers with entitlement flags (idempotent) |
| `V6__batches_sprints_tasks` | `batches` (no `version` col — conditional atomic UPDATE), `batch_students`, `sprints`, `tasks` |
| `V7__submissions_reviews_attendance` | `task_submissions` (unique `task_id,user_id,attempt_number`), `code_reviews`, `weekly_reviews`, `standups`, `attendance` (unique `standup_id,user_id`) |
| `V8__assessments_projects` | `quizzes`, `quiz_questions`, `quiz_attempts` (`auto_graded_marks`/`auto_gradable_marks`), `quiz_answers`, `assignment_windows`, `projects`, `project_assets`, `bug_challenges` |
| `V9__batch_allocation` | `pending_batch_allocations` (the "no batch available yet" queue) |
| `V10__student_metrics` | `student_metrics` (unique `user_id,batch_id`); views `v_batch_velocity`, `v_revenue_monthly`, `v_lead_funnel` |
| `V11__pip` | `pip_rules`, `pip_records` (+ `open_user_id` generated col, `blocks_task_pull`), `pip_milestones` |
| `V12__crm` | `leads` (unique `dedupe_hash`), `lead_activities`, `sales_targets` |
| `V13__hr` | `employees`, `hr_documents`, `leave_requests`, `payroll_records` (unique `employee_id,period_month`) |
| `V14__certificates` | `certificates` (unique `certificate_number`, unique `verification_code`) |
| `V15__ba_client` | `clients`, `client_projects`, `requirement_documents`, `resource_allocations` |
| `V16__indexes_constraints` | feature-23 hardening pass — CHECK gaps + targeted indexes on populated tables |
| `V17__audit_2026_08_high_indexes` | audit H5 — `idx_task_submissions_verified_at` |
| `V18__audit_2026_08_medium_indexes` | audit M3/M19 — `payments(status,captured_at)`, `leads(created_at)`, `task_submissions(status,submitted_at)`; drops two redundant indexes |

† `permissions` / `role_permissions` are created but **seeded empty and read by nothing** — RBAC
is role-based only. Never gate an endpoint on a permission code.

### Table catalogue (key columns; see `context/architecture.md` for full column lists)

**Identity** — `users` (uuid, email unique, `password_hash`, `github_username`, `status`,
`token_version`, `two_factor_secret`/`_enabled`), `roles` (code unique), `user_roles` (composite
PK), `user_profiles` (`skills`/`education`/`work_experience` JSON, `resume_key`, `portfolio_slug`
unique, `completion_percent`), `refresh_tokens` / `password_reset_tokens` /
`email_verification_tokens` (`token_hash` unique, `expires_at`).

**System** — `audit_logs` (`action`, `entity_type`, `entity_id`, `old_value`/`new_value` JSON,
`ip_address` — INSERT-only), `notifications` (`channel`, `template_code`, `payload` JSON,
`status`, `attempts`), `shedlock`, `job_runs` (`job_name`, `status`, `items_processed`,
`error_message`).

**Commerce** — `subscription_plans` (`price_inr`, `tier_rank`, `allows_batch`/`_sprints`/`_pip`/…),
`user_subscriptions` (`status`, `active_user_id` STORED generated → `uq_one_active_subscription`),
`payments` (`gateway`, `gateway_order_id` unique, `amount`, `status`, `captured_at`), `invoices`
(`payment_id` unique, `invoice_number` unique `MSH-INV-{id6}`, `pdf_key`, `status`),
`webhook_events` (`event_id` unique — the idempotency guarantee, `status`, `processed_at`),
`coupons` / `coupon_redemptions` (unique `coupon_id,user_id`).

**Learning** — `batches` (`track_code`, `pm_id`, `capacity`, `enrolled_count`, `status`),
`batch_students` (`status` ACTIVE/ON_PIP/GRADUATED/TERMINATED/REASSIGNED, `graduated_at/_by`,
`final_score`, unique `batch_id,user_id`), `pending_batch_allocations` (`user_id`, `track_code`,
`plan_id`, `resolved_at`), `sprints` (unique `batch_id,sprint_number`, `planned_points`,
`completed_points`), `tasks` (`sprint_id`, `project_id`?, `task_type`, `assigned_to`?,
`story_points`, `due_at`, `status`), `assignment_windows` (unique `batch_id,week_start`,
`due_at`, `task_id`?).

**Submissions & attendance** — `task_submissions` (`pr_url`, `repo_owner/_name`, `pr_number`,
`pr_state`, `commit_count`, `latest_commit_sha`, `status`, `verified_at`, unique
`task_id,user_id,attempt_number`), `code_reviews` (`score` CHECK 1–10, `verdict`,
`inline_comments` JSON), `weekly_reviews` (`rating` SATISFACTORY/NEEDS_IMPROVEMENT/UNSATISFACTORY,
unique `user_id,week_start`), `standups` (`scheduled_at`, `late_cutoff_minutes`, `status`,
`finalised_at`), `attendance` (`status` PRESENT/LATE/ABSENT/EXCUSED, `checked_in_at`,
`is_auto_marked`, unique `standup_id,user_id`).

**Assessments & content** — `quizzes` (`duration_minutes`, `pass_percentage` default 60,
`max_attempts`), `quiz_questions` (`question_type` MCQ/MULTI_SELECT/CODE, `options`/`correct_answer`
JSON, `marks`), `quiz_attempts` (`auto_graded_marks`/`auto_gradable_marks`, `percentage`,
`passed`, `status` — `PENDING_MANUAL_GRADING` when CODE questions present), `quiz_answers`
(`given_answer` JSON, `is_correct`, `marks_awarded`), `projects` (`slug` unique, `tech_stack`
JSON, `status` DRAFT/PUBLISHED/ARCHIVED), `project_assets` (CHECK exactly one of
`file_key`/`external_url`), `bug_challenges`.

**Metrics** — `student_metrics` (unique `user_id,batch_id`; `attendance_present`/`_total`/
`_percent`, `tasks_assigned`/`_completed`/`_overdue_48h`/`_completion_percent`,
`days_since_last_activity`, `quiz_attempts_count`/`_average_percent`,
`consecutive_assignments_missed`, `unsatisfactory_reviews`). Views: `v_batch_velocity`,
`v_revenue_monthly`, `v_lead_funnel`.

**PIP** — `pip_rules` (`rule_code` unique, `threshold_value`, `window_days`, `severity`,
`is_active`), `pip_records` (`rule_code`, `trigger_reason`, `start_date`/`end_date`, `status`,
`blocks_task_pull`, `open_user_id` STORED generated → `uq_one_open_pip`, `outcome_at`),
`pip_milestones` (`due_date`, `status` PENDING/COMPLETED/MISSED, `verified_by`).

**CRM** — `leads` (`status` NEW→…→ENROLLED|LOST, `assigned_agent_id`, `converted_user_id`?,
`dedupe_hash` CHAR(64) unique), `lead_activities` (`activity_type`, `outcome`, `next_follow_up_at`),
`sales_targets` (unique `agent_id,period_month`).

**HR** — `employees` (`user_id` unique, `employee_code` unique, `base_salary`, `hourly_rate`,
`reporting_manager_id`), `hr_documents` (`document_type`, `file_key`, `verification_status`,
`rejection_reason`), `leave_requests` (`leave_type`, `from_date`/`to_date`, `days`, `status`,
`approved_by`), `payroll_records` (unique `employee_id,period_month`, `working_days`,
`present_days`, `session_hours`, `gross_amount`/`deductions`/`net_amount`, `payslip_key`,
`status`).

**Certificates** — `certificates` (`certificate_number` unique, `certificate_type`
COMPLETION/EXCELLENCE, `verification_code` CHAR(12) unique, `pdf_key`, `issued_by`, `revoked_at`,
`revoke_reason`).

**BA & Client** — `clients` (`company_name`, `user_id`?), `client_projects` (`scope_description`,
`budget_range`, `target_batch_id`?, `status`), `requirement_documents` (`doc_type` BRD/SRS/FRS/…,
`version`, `content` LONGTEXT, `status` DRAFT/IN_REVIEW/APPROVED, `authored_by`/`approved_by`),
`resource_allocations` (`client_project_id`, `batch_id`, `user_id`, `role_in_project`,
`allocated_days`, `from_date`/`to_date`).

## 2.11 Key invariants (from `context/architecture.md`)

- Flyway owns the schema; no entity change without a matching migration; never edit an applied
  migration; forward-only, expand/contract for destructive changes.
- `open-in-view: false`. Controllers never inject a repository. Services never return an entity
  across the controller boundary.
- Every response is `ApiResponse`; every list is paginated; every write is in a `@Transactional`
  service method; never `@Transactional` on a controller.
- **No outbound HTTP/S3 call inside a transaction** — side effects go through `afterCommit`.
- Every payment webhook is signature-verified and deduplicated via `webhook_events.event_id`
  before any state change; PDF rendering happens after the response.
- MySQL has no partial unique indexes → conditional uniqueness = `STORED` generated column + unique
  key (one active subscription per user; one open PIP per user).
- Batch capacity = conditional atomic `UPDATE … WHERE enrolled_count < capacity`, never
  read-then-write, never `@Version` on the hot counter.
- The PIP engine reads `student_metrics` only, in one query — a repository call in the per-student
  loop is a defect.
- `AttendanceFinalisationJob` → `MetricsRefreshJob` → `PipEvaluationJob`, strictly ordered (now
  interlocked by `JobChainGuard`).
- PIP thresholds are `pip_rules` rows; a numeric threshold in Java is a defect.
- Ungraded `CODE` quiz answers are excluded from the percentage denominator, never scored zero.
- Every presigned URL is authorised by `OwnershipGuard` before signing.
- No endpoint exposes `users.id`; the public identifier is `users.uuid`.
- Every scheduled job writes a `job_runs` row.

---

# Part 3 — Modules & Endpoints

Every endpoint below shows: the **auth/role** required, **what it does**, the **request body** (shape — field names/types exact, values illustrative), the **call chain** (`Controller.handler` → `Service.method` → `Repository.method` → **table**, with the SQL for `@Query` methods and the transaction / side-effect markers), the **success response** shape, and the **errors** it can return. The call chain is a 2–3 level static trace; a private helper inside a service may not fully expand — open the named service class for the last mile.

## Module: Admin

- **Package:** `com.moriah.skillhub.admin/`
- **Build-plan:** Feature 22
- **Tables:** `student_metrics, v_revenue_monthly, v_lead_funnel, users, user_roles, subscription_plans, audit_logs`

Cached KPI overview, revenue by range (read replica), user status/role management (status/role change bumps token_version → instant revocation), plan pricing config, read-only audit query, and async XLSX exports.

### `GET` `/api/v1/admin/audit`

_Read-only audit log, optionally filtered by entity type, user, and a start date_

- **Auth:** Role — ADMIN
- **Query params:** `entityType` (string), `userUuid` (string), `from` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `AuditController.list(…)`
**Call chain:**
- `AuditQueryService.list(…)`
    - `JdbcTemplate.replica query` → `SELECT a.id, actor.uuid AS user_uuid, a.action, a.entity_type, CASE WHEN a.entity_type = 'User' THEN NULL ELSE a.entity_id END AS entity_id, CASE WHEN a.entity_type = 'Us…`

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "userUuid": "string",
        "action": "string",
        "entityType": "string",
        "entityId": 0,
        "entityUuid": "string",
        "oldValue": "string",
        "newValue": "string",
        "ipAddress": "string",
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** `403`

---

### `GET` `/api/v1/admin/client-requests`

_List client self-registrations awaiting review (default) or already rejected_

- **Auth:** Role — ADMIN
- **Query params:** `status` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `ClientRequestController.list(…)`
**Call chain:**
- `ClientApprovalService.list(…)`  _[@Transactional]_
    - `ClientRepository.findByUserIdIn()` → table **`clients`** (derived query)
    - `UserRepository.search()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "uuid": "string",
        "fullName": "string",
        "email": "string",
        "phone": "string",
        "companyName": "string",
        "industry": "string",
        "status": "ACTIVE",
        "submittedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/admin/client-requests/{userUuid}/approve`

_Approve a client registration — account becomes ACTIVE and the applicant is emailed_

- **Auth:** Role — ADMIN
- **Path params:** `userUuid` (string)

**Handler:** `ClientRequestController.approve(…)`
**Call chain:**
- `ClientApprovalService.approve(…)`  _[@Transactional, enqueues notification, writes audit_logs]_
    - `ClientRepository.findByUserId()` → table **`clients`** (derived query)
    - `ClientRepository.save()` → table **`clients`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `NotificationService.enqueueAfterCommit(…)`  _[enqueues notification]_
**Side effects:** @Transactional, enqueues notification, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "companyName": "string",
    "industry": "string",
    "status": "ACTIVE",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `403`, `404`, `409`

---

### `POST` `/api/v1/admin/client-requests/{userUuid}/reject`

_Decline a client registration — account becomes REJECTED and the applicant is emailed the reason_

- **Auth:** Role — ADMIN
- **Path params:** `userUuid` (string)

**Request body:**

```json
{
  "reason": "string"
}
```

**Handler:** `ClientRequestController.reject(…)`
**Call chain:**
- `ClientApprovalService.reject(…)`  _[@Transactional, enqueues notification, writes audit_logs]_
    - `ClientRepository.findByUserId()` → table **`clients`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `NotificationService.enqueueAfterCommit(…)`  _[enqueues notification]_
**Side effects:** @Transactional, enqueues notification, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "companyName": "string",
    "industry": "string",
    "status": "ACTIVE",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `403`, `404`, `409`

---

### `POST` `/api/v1/admin/exports/{report}`

_Generate an XLSX export (users/revenue/audit) and return a presigned download URL_

- **Auth:** Role — ADMIN
- **Path params:** `report` (string)

**Handler:** `ExportController.export(…)`
**Call chain:**
- `ExportService.export(…)`  _[S3]_
    - `ExportGenerationService.generate(…)`
    - `StorageService.presignedGetUrl(…)`
    - `StorageService.uploadTrusted(…)`
**Side effects:** S3

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "report": "string",
    "rowCount": 0,
    "deliveredInline": true,
    "downloadUrl": "string",
    "urlExpiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `400`, `403`

---

### `GET` `/api/v1/admin/metrics/overview`

_Admin KPI overview — attendance/task/quiz averages, recent revenue, lead funnel, batch velocity_

- **Auth:** Role — ADMIN

**Handler:** `AdminMetricsController.overview(…)`
**Call chain:**
- `MetricsService.overview(…)`  _[Redis]_
    - `JdbcTemplate.replica queryForObject` → `SELECT COUNT(DISTINCT user_id) AS active_student_count, AVG(attendance_percent) AS avg_attendance_percent, AVG(task_completion_percent) AS avg_task_completion_percent, AV…`
    - `JdbcTemplate.replica query` → `SELECT revenue_month, currency, total_captured FROM v_revenue_monthly ORDER BY revenue_month DESC LIMIT ?`
    - `JdbcTemplate.replica query` → `SELECT status, SUM(lead_count) AS lead_count FROM v_lead_funnel GROUP BY status`
    - `JdbcTemplate.replica queryForObject` → `SELECT COALESCE(SUM(planned_points), 0) AS total_planned, COALESCE(SUM(completed_points), 0) AS total_completed FROM v_batch_velocity`
**Side effects:** Redis

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "activeStudentCount": 0,
    "avgAttendancePercent": 0.0,
    "avgTaskCompletionPercent": 0.0,
    "avgQuizAveragePercent": 0.0,
    "recentRevenue": [
      {
        "revenueMonth": "string",
        "currency": "string",
        "totalCaptured": 0.0
      }
    ],
    "leadFunnel": [
      {
        "status": "string",
        "leadCount": 0
      }
    ],
    "totalPlannedPoints": 0,
    "totalCompletedPoints": 0,
    "overallVelocityRatio": 0.0
  },
  "error": null
}
```

**Errors:** `403`

---

### `GET` `/api/v1/admin/metrics/revenue`

_Monthly captured revenue for an optional date range, defaults to the trailing 12 months_

- **Auth:** Role — ADMIN
- **Query params:** `from` (string), `to` (string)

**Handler:** `AdminMetricsController.revenue(…)`
**Call chain:**
- `MetricsService.revenue(…)`
    - `JdbcTemplate.replica query` → `SELECT revenue_month, currency, total_captured FROM v_revenue_monthly WHERE revenue_month >= ? AND revenue_month <= ? ORDER BY revenue_month`

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "revenueMonth": "string",
      "currency": "string",
      "totalCaptured": 0.0
    }
  ],
  "error": null
}
```

**Errors:** `403`

---

### `PUT` `/api/v1/admin/plans/{id}`

_Update a subscription plan's pricing/feature flags at runtime — cache evicted immediately_

- **Auth:** Role — ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "name": "string",
  "priceInr": 0.0,
  "durationDays": 0,
  "maxProjects": 0,
  "mentorSupport": true,
  "allowsBatch": true,
  "allowsSprints": true,
  "allowsPip": true,
  "allowsInternshipLetter": true,
  "allowsClientProject": true,
  "active": true
}
```

**Handler:** `AdminPlanController.update(…)`
**Call chain:**
- `EntitlementService.updatePlan(…)`  _[@Transactional, Redis]_
    - `SubscriptionPlanRepository.findById()` → table **`subscription_plans`** (derived query)
    - `SubscriptionPlanRepository.save()` → table **`subscription_plans`** (derived query)
**Side effects:** @Transactional, Redis

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "code": "string",
    "name": "string",
    "priceInr": 0.0,
    "tierRank": 0,
    "durationDays": 0,
    "mentorSupport": true,
    "allowsBatch": true,
    "allowsSprints": true,
    "allowsPip": true,
    "allowsInternshipLetter": true,
    "allowsClientProject": true
  },
  "error": null
}
```

**Errors:** `403`, `404`

---

### `GET` `/api/v1/admin/users`

_List users, optionally filtered by role and/or status_

- **Auth:** Role — ADMIN
- **Query params:** `role` (string), `status` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `AdminUserController.list(…)`
**Call chain:**
- `AdminUserService.list(…)`  _[@Transactional]_
    - `UserRepository.search()` → table **`users`** (derived query)
    - `UserRoleRepository.findRoleCodesByUserIds()` → table **`user_roles`** (`SELECT new com.moriah.skillhub.user.dto.UserRoleCodeProjection(ur.id.userId, r.code) FROM UserRole ur JOIN Role r ON r.id = ur.id.roleId WHERE ur.id.userId IN :…`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "uuid": "string",
        "fullName": "string",
        "email": "string",
        "status": "ACTIVE",
        "roles": [
          "STUDENT"
        ],
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** `403`

---

### `POST` `/api/v1/admin/users`

_Invite a staff member — creates an INVITED account and emails an accept-invite link. roles must be staff roles (not STUDENT/CLIENT)._

- **Auth:** Role — ADMIN

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "roles": [
    "STUDENT"
  ]
}
```

**Handler:** `AdminUserController.inviteStaff(…)`
**Call chain:**
- `AdminUserService.inviteStaff(…)`  _[@Transactional, writes audit_logs]_
    - `RoleRepository.findByCode()` → table **`roles`** (derived query)
    - `UserRepository.existsByEmail()` → table **`users`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `UserRoleRepository.saveAll()` → table **`user_roles`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `AuthService.issueStaffInvite(…)`  _[@Transactional, enqueues notification]_
        - `StaffInviteTokenRepository.markAllUnusedAsUsedForUser()` → table **`staff_invite_tokens`** (`UPDATE StaffInviteToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL`)
        - `StaffInviteTokenRepository.save()` → table **`staff_invite_tokens`** (derived query)
**Side effects:** @Transactional, enqueues notification, writes audit_logs

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `400`, `403`, `409`

---

### `POST` `/api/v1/admin/users/{userUuid}/resend-invite`

_Re-send the accept-invite link for an account still in INVITED state — burns the previous link_

- **Auth:** Role — ADMIN
- **Path params:** `userUuid` (string)

**Handler:** `AdminUserController.resendInvite(…)`
**Call chain:**
- `AdminUserService.resendStaffInvite(…)`  _[@Transactional, writes audit_logs]_
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `AuthService.issueStaffInvite(…)`  _[@Transactional, enqueues notification]_
        - `StaffInviteTokenRepository.markAllUnusedAsUsedForUser()` → table **`staff_invite_tokens`** (`UPDATE StaffInviteToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.usedAt IS NULL`)
        - `StaffInviteTokenRepository.save()` → table **`staff_invite_tokens`** (derived query)
**Side effects:** @Transactional, enqueues notification, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `403`, `404`, `409`

---

### `PUT` `/api/v1/admin/users/{userUuid}/roles`

_Replace a user's role assignments — increments token_version, invalidating every outstanding access token immediately_

- **Auth:** Role — ADMIN
- **Path params:** `userUuid` (string)

**Request body:**

```json
{
  "roles": [
    "STUDENT"
  ]
}
```

**Handler:** `AdminUserController.updateRoles(…)`
**Call chain:**
- `AdminUserService.updateRoles(…)`  _[@Transactional, writes audit_logs]_
    - `RoleRepository.findByCode()` → table **`roles`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `UserRoleRepository.deleteByIdUserId()` → table **`user_roles`** (derived query)
    - `UserRoleRepository.saveAll()` → table **`user_roles`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `403`, `404`

---

### `PUT` `/api/v1/admin/users/{userUuid}/status`

_Change a user's status — increments token_version, invalidating every outstanding access token immediately_

- **Auth:** Role — ADMIN
- **Path params:** `userUuid` (string)

**Request body:**

```json
{
  "status": "ACTIVE"
}
```

**Handler:** `AdminUserController.updateStatus(…)`
**Call chain:**
- `AdminUserService.updateStatus(…)`  _[@Transactional, writes audit_logs]_
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** `403`, `404`

---

## Module: Assessments

- **Package:** `com.moriah.skillhub.assessment/`
- **Build-plan:** Feature 14
- **Tables:** `quizzes, quiz_questions, quiz_attempts, quiz_answers`

Quiz authoring, timed attempts (server-enforced duration, correct answers never sent), auto-grading of MCQ/MULTI_SELECT, CODE answers flagged PENDING_MANUAL_GRADING.

### `GET` `/api/v1/assessments`

_List assessments for a batch_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `batchId`* (integer)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `AssessmentController.list(…)`
**Call chain:**
- `QuizService.list(…)`  _[@Transactional]_
    - `QuizRepository.findByBatchId()` → table **`quizzes`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "projectId": 0,
        "title": "string",
        "durationMinutes": 0,
        "passPercentage": 0,
        "maxAttempts": 0,
        "active": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/assessments`

_Create an assessment (quiz) with its questions_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "projectId": 0,
  "title": "string",
  "durationMinutes": 0,
  "passPercentage": 0,
  "maxAttempts": 0,
  "questions": [
    {
      "questionText": "string",
      "questionType": "MCQ",
      "options": [
        "string"
      ],
      "correctAnswerIndices": [
        0
      ],
      "marks": 0,
      "explanation": "string"
    }
  ]
}
```

**Handler:** `AssessmentController.create(…)`
**Call chain:**
- `QuizService.create(…)`  _[@Transactional]_
    - `QuizQuestionRepository.saveAll()` → table **`quiz_questions`** (derived query)
    - `QuizRepository.save()` → table **`quizzes`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
    - `ProjectService.requirePublished(…)`  _[@Transactional]_
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "projectId": 0,
    "title": "string",
    "durationMinutes": 0,
    "passPercentage": 0,
    "maxAttempts": 0,
    "active": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/assessments/attempts/{id}`

_Get an attempt — its questions while in progress, its graded result once terminal_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Path params:** `id` (integer)

**Handler:** `AssessmentController.getAttempt(…)`
**Call chain:**
- `QuizService.getAttempt(…)`  _[@Transactional]_
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/assessments/attempts/{id}/submit`

_Submit an attempt for grading_

- **Auth:** Role — STUDENT
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "answers": [
    {
      "questionId": 0,
      "selectedOptionIndices": [
        0
      ],
      "codeAnswer": "string"
    }
  ]
}
```

**Handler:** `AssessmentController.submit(…)`
**Call chain:**
- `QuizService.submit(…)`  _[@Transactional, writes audit_logs]_
    - `QuizQuestionRepository.findByQuizIdOrderByIdAsc()` → table **`quiz_questions`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/assessments/{id}/attempts`

_Start (or resume) an attempt at an assessment_

- **Auth:** Role — STUDENT
- **Path params:** `id` (integer)

**Handler:** `AssessmentController.startAttempt(…)`
**Call chain:**
- `QuizService.startAttempt(…)`  _[@Transactional]_
    - `QuizAttemptRepository.findInProgress()` → table **`quiz_attempts`** (`SELECT a FROM QuizAttempt a JOIN FETCH a.quiz`)
    - `QuizAttemptRepository.findMaxAttemptNumber()` → table **`quiz_attempts`** (`SELECT MAX(a.attemptNumber) FROM QuizAttempt a WHERE a.quiz.id = :quizId AND a.user.id = :userId`)
    - `BatchService.isActiveMember(…)`  _[@Transactional]_
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Attendance

- **Package:** `com.moriah.skillhub.attendance/`
- **Build-plan:** Feature 13
- **Tables:** `attendance, standups`

The caller's own attendance and a batch attendance roster. Percentages are never computed here — they come from student_metrics.

### `GET` `/api/v1/attendance/batch/{batchId}`

_Attendance roster for a batch_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `batchId` (integer)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `AttendanceController.batchRoster(…)`
**Call chain:**
- `AttendanceService.batchRoster(…)`  _[@Transactional]_
    - `AttendanceRepository.findByBatch()` → table **`attendance`** (`SELECT a FROM Attendance a WHERE a.standup.batch.id = :batchId ORDER BY a.standup.scheduledAt DESC`)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "standupId": 0,
        "batchId": 0,
        "standupScheduledAt": "2026-01-15T10:30:00Z",
        "userUuid": "string",
        "userFullName": "string",
        "status": "PRESENT",
        "checkedInAt": "2026-01-15T10:30:00Z",
        "blockerNotes": "string",
        "markedByUuid": "string",
        "autoMarked": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/attendance/me`

_My own attendance history_

- **Auth:** Role — STUDENT
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `AttendanceController.me(…)`
**Call chain:**
- `AttendanceService.me(…)`  _[@Transactional]_
    - `AttendanceRepository.findByUserId()` → table **`attendance`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "standupId": 0,
        "batchId": 0,
        "standupScheduledAt": "2026-01-15T10:30:00Z",
        "userUuid": "string",
        "userFullName": "string",
        "status": "PRESENT",
        "checkedInAt": "2026-01-15T10:30:00Z",
        "blockerNotes": "string",
        "markedByUuid": "string",
        "autoMarked": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/standups/{id}/attendance`

_PM override of a student's attendance status_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "userUuid": "string",
  "status": "PRESENT",
  "blockerNotes": "string"
}
```

**Handler:** `AttendanceController.override(…)`
**Call chain:**
- `AttendanceService.override(…)`  _[@Transactional, writes audit_logs]_
    - `AttendanceRepository.findByStandupIdAndUserId()` → table **`attendance`** (`SELECT a FROM Attendance a JOIN FETCH a.user JOIN FETCH a.standup LEFT JOIN FETCH a.markedBy`)
    - `AttendanceRepository.save()` → table **`attendance`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
    - `StandupService.requireStandup(…)`
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "standupId": 0,
    "batchId": 0,
    "standupScheduledAt": "2026-01-15T10:30:00Z",
    "userUuid": "string",
    "userFullName": "string",
    "status": "PRESENT",
    "checkedInAt": "2026-01-15T10:30:00Z",
    "blockerNotes": "string",
    "markedByUuid": "string",
    "autoMarked": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/standups/{id}/checkin`

_Self check in to a standup_

- **Auth:** Role — STUDENT
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "blockerNotes": "string"
}
```

**Handler:** `AttendanceController.checkin(…)`
**Call chain:**
- `AttendanceService.checkin(…)`  _[@Transactional]_
    - `BatchService.isActiveMember(…)`  _[@Transactional]_
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
    - `StandupService.requireStandup(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "standupId": 0,
    "batchId": 0,
    "standupScheduledAt": "2026-01-15T10:30:00Z",
    "userUuid": "string",
    "userFullName": "string",
    "status": "PRESENT",
    "checkedInAt": "2026-01-15T10:30:00Z",
    "blockerNotes": "string",
    "markedByUuid": "string",
    "autoMarked": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Auth

- **Package:** `com.moriah.skillhub.auth/`
- **Build-plan:** Features 03, 05
- **Tables:** `users, roles, user_roles, refresh_tokens, password_reset_tokens, email_verification_tokens`

Registration, login, JWT issue/refresh/rotation, email verification, password reset, Google/GitHub OAuth2, and TOTP 2FA (mandatory for ADMIN & HR_MANAGER).

> **OAuth2 login endpoints are filter-handled, not controllers**, so they are absent from `openapi.json` and the endpoint list below. Browser flow: `GET /api/v1/auth/oauth2/authorize/{google|github}` → 302 to the provider → the provider redirects to `GET /api/v1/auth/oauth2/callback/{google|github}?code=…&state=…`, which returns the **same `LoginResponse` envelope as `POST /api/v1/auth/login`** (token pair, or a 2FA challenge). `OAuth2AuthenticationSuccessHandler` / `…FailureHandler` write the JSON directly (they run before Spring MVC). Redirect URI to register with the provider: `{baseUrl}/api/v1/auth/oauth2/callback/{google|github}`.

### `POST` `/api/v1/auth/2fa/disable`

_Disable 2FA — requires a currently-valid code, not just an authenticated call_

- **Auth:** Public — no token

**Request body:**

```json
{
  "totpCode": "string"
}
```

**Handler:** `AuthController.disableTwoFactor(…)`
**Call chain:**
- `TwoFactorService.disable(…)`  _[@Transactional]_
    - `UserRepository.save()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/2fa/enable`

_Start 2FA setup — generates a secret; confirm it via POST /2fa/verify before it takes effect. Send {} for an already-logged-in caller voluntarily enabling 2FA; send {"challengeToken": "..."} for the mandatory-2FA setup path (LoginResponse.twoFactorSetupRequired = true), where there is no access token yet to authenticate this call with_

- **Auth:** Public — no token

**Request body:**

```json
{
  "challengeToken": "string"
}
```

**Handler:** `AuthController.enableTwoFactor(…)`
**Call chain:**
- `TwoFactorService.enable(…)`  _[@Transactional]_
    - `UserRepository.save()` → table **`users`** (derived query)
    - `TotpService.buildProvisioningUri(…)`
    - `TotpService.generateSecret(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "secret": "string",
    "provisioningUri": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/2fa/verify`

_Confirms 2FA setup (no challengeToken, uses the caller's access token) or completes a 2FA-gated login (challengeToken from LoginResponse)_

- **Auth:** Public — no token

**Request body:**

```json
{
  "challengeToken": "string",
  "totpCode": "string"
}
```

**Handler:** `AuthController.verifyTwoFactor(…)`
**Call chain:**
- `AuthService.completeTwoFactorLogin(…)`  _[@Transactional]_
    - `UserRepository.save()` → table **`users`** (derived query)
    - `TwoFactorService.verifyLoginChallenge(…)`  _[@Transactional]_
        - `UserRepository.findById()` → table **`users`** (derived query)
        - `UserRepository.save()` → table **`users`** (derived query)
- `TwoFactorService.confirmSetup(…)`  _[@Transactional]_
    - `UserRepository.save()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/accept-invite`

_Redeem a staff accept-invite link — sets the password, activates the account (INVITED -> ACTIVE) and logs in. Returns a 2FA challenge instead of tokens for an invited ADMIN / HR_MANAGER, exactly like a normal first login._

- **Auth:** Public — no token

**Request body:**

```json
{
  "token": "string",
  "password": "string"
}
```

**Handler:** `AuthController.acceptInvite(…)`
**Call chain:**
- `AuthService.acceptInvite(…)`  _[@Transactional, writes audit_logs]_
    - `StaffInviteTokenRepository.findByTokenHash()` → table **`staff_invite_tokens`** (derived query)
    - `StaffInviteTokenRepository.save()` → table **`staff_invite_tokens`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "twoFactorRequired": true,
    "twoFactorSetupRequired": true,
    "challengeToken": "string",
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/login`

_Log in with email and password. If 2FA is enabled, returns a challenge token instead of a token pair — exchange it at POST /2fa/verify._

- **Auth:** Public — no token

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**Handler:** `AuthController.login(…)`
**Call chain:**
- `AuthService.login(…)`  _[@Transactional, writes audit_logs]_
    - `UserRepository.findByEmail()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "twoFactorRequired": true,
    "twoFactorSetupRequired": true,
    "challengeToken": "string",
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/logout`

_Revoke one refresh token and its paired access token_

- **Auth:** Public — no token

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Handler:** `AuthController.logout(…)`
**Call chain:**
- `AuthService.logout(…)`  _[@Transactional]_
    - `RefreshTokenRepository.findByTokenHash()` → table **`refresh_tokens`** (derived query)
    - `RefreshTokenRepository.save()` → table **`refresh_tokens`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/logout-all`

_Revoke every session for the user identified by this refresh token_

- **Auth:** Public — no token

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Handler:** `AuthController.logoutAll(…)`
**Call chain:**
- `AuthService.logoutAll(…)`  _[@Transactional, writes audit_logs]_
    - `RefreshTokenRepository.findByTokenHash()` → table **`refresh_tokens`** (derived query)
    - `RefreshTokenRepository.revokeAllActiveForUser()` → table **`refresh_tokens`** (`UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL`)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/password/forgot`

_Request a password reset link — always responds the same way regardless of whether the email exists_

- **Auth:** Public — no token

**Request body:**

```json
{
  "email": "user@example.com"
}
```

**Handler:** `AuthController.forgotPassword(…)`
**Call chain:**
- `AuthService.forgotPassword(…)`  _[@Transactional]_
    - `UserRepository.findByEmail()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/password/reset`

_Reset a password with the token from the reset link_

- **Auth:** Public — no token

**Request body:**

```json
{
  "token": "string",
  "newPassword": "string"
}
```

**Handler:** `AuthController.resetPassword(…)`
**Call chain:**
- `AuthService.resetPassword(…)`  _[@Transactional, writes audit_logs]_
    - `PasswordResetTokenRepository.findByTokenHash()` → table **`password_reset_tokens`** (derived query)
    - `PasswordResetTokenRepository.save()` → table **`password_reset_tokens`** (derived query)
    - `RefreshTokenRepository.revokeAllActiveForUser()` → table **`refresh_tokens`** (`UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL`)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/refresh`

_Exchange a refresh token for a new token pair; rotates the refresh token_

- **Auth:** Public — no token

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Handler:** `AuthController.refresh(…)`
**Call chain:**
- `AuthService.refresh(…)`  _[@Transactional, writes audit_logs]_
    - `RefreshTokenRepository.findByTokenHash()` → table **`refresh_tokens`** (derived query)
    - `RefreshTokenRepository.save()` → table **`refresh_tokens`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `RefreshTokenRevocationService.revokeAllForUser(…)`  _[@Transactional]_
        - `RefreshTokenRepository.revokeAllActiveForUser()` → table **`refresh_tokens`** (`UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL`)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "expiresInSeconds": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/register`

_Register a new student account_

- **Auth:** Public — no token

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "password": "string",
  "githubUsername": "string"
}
```

**Handler:** `AuthController.register(…)`
**Call chain:**
- `AuthService.register(…)`  _[@Transactional]_
    - `RoleRepository.findByCode()` → table **`roles`** (derived query)
    - `UserRepository.existsByEmail()` → table **`users`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `UserRoleRepository.save()` → table **`user_roles`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/register/client`

_Corporate-client self-registration — creates a PENDING_APPROVAL account that cannot log in until an ADMIN approves it at POST /api/v1/admin/client-requests/{uuid}/approve_

- **Auth:** Public — no token

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "password": "string",
  "companyName": "string",
  "industry": "string"
}
```

**Handler:** `AuthController.registerClient(…)`
**Call chain:**
- `ClientRegistrationService.register(…)`  _[@Transactional, enqueues notification, writes audit_logs]_
    - `ClientRepository.save()` → table **`clients`** (derived query)
    - `RoleRepository.findByCode()` → table **`roles`** (derived query)
    - `UserRepository.existsByEmail()` → table **`users`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
    - `UserRoleRepository.save()` → table **`user_roles`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `NotificationService.enqueueAfterCommit(…)`  _[enqueues notification]_
**Side effects:** @Transactional, enqueues notification, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/auth/verify-email`

_Verify an email address with the token from the verification link_

- **Auth:** Public — no token

**Request body:**

```json
{
  "token": "string"
}
```

**Handler:** `AuthController.verifyEmail(…)`
**Call chain:**
- `AuthService.verifyEmail(…)`  _[@Transactional]_
    - `EmailVerificationTokenRepository.findByTokenHash()` → table **`email_verification_tokens`** (derived query)
    - `EmailVerificationTokenRepository.save()` → table **`email_verification_tokens`** (derived query)
    - `UserRepository.save()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: BA

- **Package:** `com.moriah.skillhub.ba/`
- **Build-plan:** Feature 21
- **Tables:** `requirement_documents, resource_allocations`

BUSINESS_ANALYST requirement documents (BRD/SRS/FRS, versioned, DRAFT→IN_REVIEW→APPROVED) and resource allocations mapping students/batches to a client project.

### `POST` `/api/v1/ba/allocations`

_Allocate a student/staff user and batch to a client project_

- **Auth:** Role — BUSINESS_ANALYST / ADMIN

**Request body:**

```json
{
  "clientProjectId": 0,
  "batchId": 0,
  "userUuid": "string",
  "roleInProject": "string",
  "allocatedDays": 0,
  "storyPointsEstimate": 0,
  "fromDate": "2026-01-15",
  "toDate": "2026-01-15"
}
```

**Handler:** `BaController.createAllocation(…)`
**Call chain:**
- `ResourceAllocationService.create(…)`  _[@Transactional]_
    - `BatchRepository.findById()` → table **`batches`** (derived query)
    - `ClientProjectRepository.findById()` → table **`client_projects`** (derived query)
    - `ResourceAllocationRepository.save()` → table **`resource_allocations`** (derived query)
    - `UserRepository.findByUuid()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "batchId": 0,
    "userUuid": "string",
    "userFullName": "string",
    "roleInProject": "string",
    "allocatedDays": 0,
    "storyPointsEstimate": 0,
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/ba/documents`

_Create a requirement document (BRD/SRS/FRS/USER_STORY) — lands IN_REVIEW directly_

- **Auth:** Role — BUSINESS_ANALYST / ADMIN

**Request body:**

```json
{
  "clientProjectId": 0,
  "docType": "BRD",
  "title": "string",
  "content": "string"
}
```

**Handler:** `BaController.createDocument(…)`
**Call chain:**
- `RequirementDocumentService.create(…)`  _[@Transactional]_
    - `RequirementDocumentRepository.findMaxVersion()` → table **`requirement_documents`** (`SELECT MAX(d.version) FROM RequirementDocument d WHERE d.clientProject.id = :clientProjectId AND d.docType = :docType`)
    - `RequirementDocumentRepository.save()` → table **`requirement_documents`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/ba/documents/{id}/approve`

_Approve a requirement document — IN_REVIEW to APPROVED_

- **Auth:** Role — BUSINESS_ANALYST / ADMIN
- **Path params:** `id` (integer)

**Handler:** `BaController.approveDocument(…)`
**Call chain:**
- `RequirementDocumentService.approve(…)`  _[@Transactional, writes audit_logs]_
    - `UserRepository.findById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Batches

- **Package:** `com.moriah.skillhub.batch/`
- **Build-plan:** Features 10, 20
- **Tables:** `batches, batch_students, pending_batch_allocations`

PM/Admin batch CRUD, manual student add/remove, the automatic BatchAllocationService (called by the payment webhook), and graduation sign-off.

### `GET` `/api/v1/batches`

_List batches_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `BatchController.list(…)`
**Call chain:**
- `BatchService.list(…)`  _[@Transactional]_
    - `BatchRepository.findAll()` → table **`batches`** (derived query)
    - `EntitlementService.planCodesById(…)`  _[@Transactional, Redis]_
        - `SubscriptionPlanRepository.findAll()` → table **`subscription_plans`** (derived query)
**Side effects:** @Transactional, Redis

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "name": "string",
        "trackCode": "string",
        "pmUuid": "string",
        "pmFullName": "string",
        "planTierMinCode": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "capacity": 0,
        "enrolledCount": 0,
        "status": "PLANNED"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/batches`

_Create a batch — the caller becomes its PM_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "name": "string",
  "trackCode": "string",
  "planTierMinCode": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "capacity": 0
}
```

**Handler:** `BatchController.create(…)`
**Call chain:**
- `BatchService.create(…)`  _[@Transactional]_
    - `BatchRepository.save()` → table **`batches`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/batches/{id}`

_Get one batch_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Handler:** `BatchController.get(…)`
**Call chain:**
- `BatchService.get(…)`  _[@Transactional]_
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/batches/{id}`

_Update a batch_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "name": "string",
  "planTierMinCode": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "capacity": 0,
  "status": "PLANNED"
}
```

**Handler:** `BatchController.update(…)`
**Call chain:**
- `BatchService.update(…)`  _[@Transactional]_
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/batches/{id}/students`

_Manually add a student to a batch_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Handler:** `BatchController.addStudent(…)`
**Call chain:**
- `BatchService.addStudent(…)`  _[@Transactional]_
    - `BatchRepository.tryReserveSeat()` → table **`batches`** (`[native] UPDATE batches SET enrolled_count = enrolled_count + 1 WHERE id = :batchId AND enrolled_count < capacity`)
    - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
    - `BatchStudentRepository.save()` → table **`batch_students`** (derived query)
    - `PendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull()` → table **`pending_batch_allocations`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `DELETE` `/api/v1/batches/{id}/students/{userUuid}`

_Remove a student from a batch (sets status to REASSIGNED, never deletes)_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer), `userUuid` (string)

**Handler:** `BatchController.removeStudent(…)`
**Call chain:**
- `BatchService.removeStudent(…)`  _[@Transactional]_
    - `BatchRepository.releaseSeat()` → table **`batches`** (`[native] UPDATE batches SET enrolled_count = enrolled_count - 1 WHERE id = :batchId AND enrolled_count > 0`)
    - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/batches/{id}/students/{userUuid}/graduate`

_Graduate an ACTIVE student — required before a certificate can be issued_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer), `userUuid` (string)

**Handler:** `BatchController.graduate(…)`
**Call chain:**
- `GraduationService.graduate(…)`  _[@Transactional, writes audit_logs]_
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.get(…)`  _[@Transactional]_
    - `BatchService.graduate(…)`  _[@Transactional]_
        - `BatchRepository.releaseSeat()` → table **`batches`** (`[native] UPDATE batches SET enrolled_count = enrolled_count - 1 WHERE id = :batchId AND enrolled_count > 0`)
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
        - `UserRepository.getReferenceById()` → table **`users`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "batchId": 0,
    "batchName": "string",
    "userUuid": "string",
    "userFullName": "string",
    "graduatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Bug Challenges

- **Package:** `com.moriah.skillhub.project/`
- **Build-plan:** Feature 15
- **Tables:** `bug_challenges`

Bug-fix challenges attached to a project (broken code, expected behaviour, test script).

### `POST` `/api/v1/projects/{id}/challenges`

_Attach a bug-fix challenge — brokenCode required, testScript optional_

- **Auth:** Role — DEVELOPER / ADMIN
- **Path params:** `id` (integer)
- **Query params:** `title`* (string), `expectedBehaviour`* (string), `difficulty` (string)

**Request body:**

```json
{
  "brokenCode": "<binary>",
  "testScript": "<binary>"
}
```

**Handler:** `ChallengeController.addChallenge(…)`
**Call chain:**
- `ProjectService.addChallenge(…)`  _[@Transactional]_
    - `BugChallengeRepository.save()` → table **`bug_challenges`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "expectedBehaviour": "string",
    "brokenCodeUrl": "string",
    "testScriptUrl": "string",
    "difficulty": "BEGINNER"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: CRM

- **Package:** `com.moriah.skillhub.crm/`
- **Build-plan:** Feature 18
- **Tables:** `leads, lead_activities, sales_targets`

Lead ingestion with SHA-256 email+phone dedup, pipeline stage transitions (forward-skip blocked, backward allowed with a reason), activity logging, monthly targets.

### `GET` `/api/v1/leads`

- **Auth:** Role — LEAD_GEN / ADMIN
- **Query params:** `status` (string), `agentUuid` (string), `source` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `LeadController.list(…)`
**Call chain:**
- `LeadService.list(…)`  _[@Transactional]_
    - `LeadRepository.search()` → table **`leads`** (derived query)
    - `UserRepository.findByUuid()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "name": "string",
        "email": "string",
        "phone": "string",
        "source": "LANDING_PAGE",
        "leadType": "string",
        "institution": "string",
        "interestedPlanId": 0,
        "status": "NEW",
        "assignedAgentUuid": "string",
        "assignedAgentName": "string",
        "lostReason": "string",
        "convertedUserUuid": "string",
        "createdAt": "2026-01-15T10:30:00Z",
        "updatedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/leads`

- **Auth:** Role — LEAD_GEN / ADMIN

**Request body:**

```json
{
  "name": "string",
  "email": "user@example.com",
  "phone": "string",
  "source": "LANDING_PAGE",
  "leadType": "string",
  "institution": "string",
  "interestedPlanId": 0
}
```

**Handler:** `LeadController.create(…)`
**Call chain:**
- `LeadService.create(…)`  _[@Transactional]_
    - `LeadRepository.findByDedupeHash()` → table **`leads`** (derived query)
    - `SubscriptionPlanRepository.existsById()` → table **`subscription_plans`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "email": "string",
    "phone": "string",
    "source": "LANDING_PAGE",
    "leadType": "string",
    "institution": "string",
    "interestedPlanId": 0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/leads/targets/me`

- **Auth:** Role — LEAD_GEN / ADMIN

**Handler:** `LeadController.myTargets(…)`
**Call chain:**
- `LeadService.myTargets(…)`  _[@Transactional]_
    - `SalesTargetRepository.findByAgentIdAndPeriodMonth()` → table **`sales_targets`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "agentUuid": "string",
    "periodMonth": "2026-01-15",
    "callsTarget": 0,
    "callsMade": 0,
    "conversionsTarget": 0,
    "conversionsMade": 0,
    "revenueTarget": 0.0,
    "revenueAchieved": 0.0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/leads/{id}/activities`

- **Auth:** Role — LEAD_GEN / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "activityType": "CALL",
  "outcome": "string",
  "notes": "string",
  "nextFollowUpAt": "2026-01-15T10:30:00Z",
  "occurredAt": "2026-01-15T10:30:00Z",
  "templateCode": "string"
}
```

**Handler:** `LeadController.addActivity(…)`
**Call chain:**
- `LeadService.addActivity(…)`  _[@Transactional]_
    - `LeadRepository.findById()` → table **`leads`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "leadId": 0,
    "agentUuid": "string",
    "agentName": "string",
    "activityType": "CALL",
    "outcome": "string",
    "notes": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "occurredAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/leads/{id}/status`

- **Auth:** Role — LEAD_GEN / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "newStatus": "NEW",
  "reason": "string",
  "lostReason": "string",
  "convertedUserUuid": "string"
}
```

**Handler:** `LeadController.updateStatus(…)`
**Call chain:**
- `LeadService.updateStatus(…)`  _[@Transactional]_
    - `LeadRepository.findById()` → table **`leads`** (derived query)
    - `LeadRepository.save()` → table **`leads`** (derived query)
    - `UserRepository.findByUuid()` → table **`users`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "email": "string",
    "phone": "string",
    "source": "LANDING_PAGE",
    "leadType": "string",
    "institution": "string",
    "interestedPlanId": 0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Certificates

- **Package:** `com.moriah.skillhub.certificate/`
- **Build-plan:** Feature 20
- **Tables:** `certificates, batch_students`

Certificate issuance (requires GRADUATED, no open PIP, all sprints closed), the caller's certificates, revocation, and the PUBLIC unauthenticated QR verification endpoint.

### `POST` `/api/v1/certificates/issue`

_Issue a certificate — requires GRADUATED, no open PIP, and every sprint COMPLETED_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "userUuid": "string",
  "certificateType": "COMPLETION"
}
```

**Handler:** `CertificateController.issue(…)`
**Call chain:**
- `CertificateService.issue(…)`  _[S3, writes audit_logs]_
    - `CertificateRepository.existsByUserIdAndBatchIdAndCertificateTypeAndRevokedAtIsNull()` → table **`certificates`** (derived query)
    - `CertificateRepository.save()` → table **`certificates`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.hasGraduatedFromBatch(…)`  _[@Transactional]_
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
    - `PipService.hasOpenPip(…)`  _[@Transactional]_
        - `PipRecordRepository.existsByOpenUserId()` → table **`pip_records`** (derived query)
    - `QrCodeService.png(…)`
    - `SprintService.allSprintsClosed(…)`  _[@Transactional]_
        - `SprintRepository.existsByBatchIdAndStatusNot()` → table **`sprints`** (derived query)
    - `StorageService.uploadTrusted(…)`
**Side effects:** @Transactional, S3, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "certificateNumber": "string",
    "userUuid": "string",
    "userFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "certificateType": "COMPLETION",
    "verificationCode": "string",
    "downloadUrl": "string",
    "issuedAt": "2026-01-15T10:30:00Z",
    "revokedAt": "2026-01-15T10:30:00Z",
    "revokeReason": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/certificates/me`

_The caller's own issued certificates_

- **Auth:** Role — STUDENT
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `CertificateController.me(…)`
**Call chain:**
- `CertificateService.me(…)`  _[@Transactional]_
    - `CertificateRepository.findByUserId()` → table **`certificates`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "certificateNumber": "string",
        "userUuid": "string",
        "userFullName": "string",
        "batchId": 0,
        "batchName": "string",
        "certificateType": "COMPLETION",
        "verificationCode": "string",
        "downloadUrl": "string",
        "issuedAt": "2026-01-15T10:30:00Z",
        "revokedAt": "2026-01-15T10:30:00Z",
        "revokeReason": "string"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/certificates/verify/{code}`

_Public certificate verification by code — never accepts a certificate id_

- **Auth:** Public — no token
- **Path params:** `code` (string)

**Handler:** `VerificationController.verify(…)`
**Call chain:**
- `CertificateService.verify(…)`  _[@Transactional]_
    - `CertificateRepository.findByVerificationCode()` → table **`certificates`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "holderFullName": "string",
    "batchName": "string",
    "trackCode": "string",
    "certificateType": "COMPLETION",
    "issuedAt": "2026-01-15T10:30:00Z",
    "valid": true,
    "revokedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/certificates/{id}/revoke`

_Revoke a certificate — never deletes it, still resolves publicly as invalid_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "reason": "string"
}
```

**Handler:** `CertificateController.revoke(…)`
**Call chain:**
- `CertificateService.revoke(…)`  _[@Transactional, writes audit_logs]_
    - `CertificateRepository.findWithAssociationsById()` → table **`certificates`** (`SELECT c FROM Certificate c WHERE c.id = :id`)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "certificateNumber": "string",
    "userUuid": "string",
    "userFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "certificateType": "COMPLETION",
    "verificationCode": "string",
    "downloadUrl": "string",
    "issuedAt": "2026-01-15T10:30:00Z",
    "revokedAt": "2026-01-15T10:30:00Z",
    "revokeReason": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Checkout

- **Package:** `com.moriah.skillhub.payment/`
- **Build-plan:** Feature 07
- **Tables:** `payments, coupons, coupon_redemptions`

Creates a Razorpay/Stripe order + a CREATED payments row; amount is always re-read from subscription_plans server-side.

### `POST` `/api/v1/subscriptions/checkout`

_Create a payment order/session for a plan; never activates anything directly_

- **Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "planCode": "string",
  "gateway": "RAZORPAY",
  "couponCode": "string",
  "trackCode": "string"
}
```

**Handler:** `CheckoutController.checkout(…)`
**Call chain:**
- `CheckoutService.checkout(…)`  _[@Transactional]_
    - `PaymentRepository.saveAndFlush()` → table **`payments`** (derived query)
    - `SubscriptionPlanRepository.findByCode()` → table **`subscription_plans`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
    - `CouponService.preview(…)`  _[@Transactional]_
    - `CouponService.redeem(…)`  _[@Transactional]_
        - `CouponRedemptionRepository.flush()` → table **`coupon_redemptions`** (derived query)
        - `CouponRedemptionRepository.save()` → table **`coupon_redemptions`** (derived query)
        - `CouponRepository.tryReserveRedemption()` → table **`coupons`** (`UPDATE Coupon c SET c.timesRedeemed = c.timesRedeemed + 1 WHERE c.id = :couponId AND (c.maxRedemptions IS NULL OR c.timesRedeemed < c.maxRedemptions)`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "gateway": "RAZORPAY",
    "paymentId": 0,
    "amount": 0.0,
    "currency": "string",
    "razorpayOrderId": "string",
    "razorpayKeyId": "string",
    "stripeCheckoutUrl": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Clients

- **Package:** `com.moriah.skillhub.client/`
- **Build-plan:** Feature 21
- **Tables:** `clients, client_projects, resource_allocations`

ADMIN-provisioned CLIENT users submit project scope and view burndown/milestone progress for their own project only.

### `POST` `/api/v1/clients`

_Provision a client company, optionally with a portal login_

- **Auth:** Role — ADMIN

**Request body:**

```json
{
  "companyName": "string",
  "contactPerson": "string",
  "email": "user@example.com",
  "phone": "string",
  "industry": "string",
  "provisionPortalLogin": true
}
```

**Handler:** `ClientController.create(…)`
**Call chain:**
- `ClientService.create(…)`  _[@Transactional]_
    - `ClientRepository.save()` → table **`clients`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "companyName": "string",
    "contactPerson": "string",
    "email": "string",
    "phone": "string",
    "industry": "string",
    "userUuid": "string",
    "status": "ACTIVE"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/clients/projects`

_Submit a new project scope as the caller's own client company_

- **Auth:** Role — CLIENT

**Request body:**

```json
{
  "title": "string",
  "scopeDescription": "string",
  "budgetRange": "string"
}
```

**Handler:** `ClientController.createProject(…)`
**Call chain:**
- `ClientProjectService.create(…)`  _[@Transactional]_
    - `ClientProjectRepository.save()` → table **`client_projects`** (derived query)
    - `ClientRepository.findByUserId()` → table **`clients`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientId": 0,
    "title": "string",
    "scopeDescription": "string",
    "budgetRange": "string",
    "targetBatchId": 0,
    "status": "SUBMITTED",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/clients/projects/{id}/progress`

_Burndown and milestone completion for one client project — never another client's data_

- **Auth:** Role — CLIENT / BUSINESS_ANALYST / ADMIN
- **Path params:** `id` (integer)

**Handler:** `ClientController.progress(…)`
**Call chain:**
- `ClientProjectService.progress(…)`  _[@Transactional]_
    - `ClientProjectRepository.findWithClientById()` → table **`client_projects`** (`SELECT p FROM ClientProject p WHERE p.id = :id`)
    - `SprintService.progressForBatch(…)`  _[@Transactional]_
        - `SprintRepository.findProgressForBatch()` → table **`sprints`** (`SELECT new com.moriah.skillhub.sprint.dto.SprintProgressProjection( s.id, s.sprintNumber, s.status, s.plannedPoints, s.completedPoints) FROM Sprint s WHERE s.ba…`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "clientProjectId": 0,
    "title": "string",
    "targetBatchId": 0,
    "milestoneCompletionFraction": 0.0,
    "burndown": [
      {
        "sprintId": 0,
        "sprintNumber": 0,
        "sprintStatus": "string",
        "plannedPoints": 0,
        "completedPoints": 0
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: HR

- **Package:** `com.moriah.skillhub.hr/`
- **Build-plan:** Feature 19
- **Tables:** `employees, hr_documents, leave_requests, payroll_records`

Employee records, KYC document upload + verification, leave workflow routed to the reporting manager, payroll computation (BigDecimal, unique per employee+month), and letter generation.

### `POST` `/api/v1/hr/documents`

- **Auth:** Authenticated (any logged-in user)
- **Query params:** `documentType`* (string)

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Handler:** `HrDocumentController.upload(…)`
**Call chain:**
- `HrDocumentService.upload(…)`  _[@Transactional, S3]_
    - `HrDocumentRepository.save()` → table **`hr_documents`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
    - `StorageService.upload(…)`
**Side effects:** @Transactional, S3

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/hr/documents/{id}/verify`

- **Auth:** Role — HR_MANAGER / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "decision": "PENDING",
  "rejectionReason": "string"
}
```

**Handler:** `HrDocumentController.verify(…)`
**Call chain:**
- `HrDocumentService.verify(…)`  _[@Transactional, writes audit_logs]_
    - `HrDocumentRepository.findById()` → table **`hr_documents`** (derived query)
    - `HrDocumentRepository.save()` → table **`hr_documents`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/hr/employees`

- **Auth:** Role — HR_MANAGER / ADMIN

**Request body:**

```json
{
  "userUuid": "string",
  "employeeCode": "string",
  "department": "string",
  "designation": "string",
  "employmentType": "FULL_TIME",
  "dateOfJoining": "2026-01-15",
  "baseSalary": 0.0,
  "hourlyRate": 0.0,
  "reportingManagerId": 0
}
```

**Handler:** `EmployeeController.create(…)`
**Call chain:**
- `EmployeeService.create(…)`  _[@Transactional]_
    - `EmployeeRepository.existsByEmployeeCode()` → table **`employees`** (derived query)
    - `EmployeeRepository.existsByUserId()` → table **`employees`** (derived query)
    - `EmployeeRepository.findById()` → table **`employees`** (derived query)
    - `EmployeeRepository.save()` → table **`employees`** (derived query)
    - `UserRepository.findByUuid()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "fullName": "string",
    "employeeCode": "string",
    "department": "string",
    "designation": "string",
    "employmentType": "FULL_TIME",
    "dateOfJoining": "2026-01-15",
    "dateOfExit": "2026-01-15",
    "baseSalary": 0.0,
    "hourlyRate": 0.0,
    "reportingManagerId": 0,
    "status": "ACTIVE"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/hr/leaves`

- **Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "leaveType": "SICK",
  "fromDate": "2026-01-15",
  "toDate": "2026-01-15",
  "reason": "string"
}
```

**Handler:** `LeaveController.create(…)`
**Call chain:**
- `LeaveService.create(…)`  _[@Transactional]_
    - `EmployeeRepository.existsByUserId()` → table **`employees`** (derived query)
    - `LeaveRequestRepository.save()` → table **`leave_requests`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/hr/leaves/{id}/decision`

- **Auth:** Authenticated (any logged-in user)
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "decision": "PENDING"
}
```

**Handler:** `LeaveController.decide(…)`
**Call chain:**
- `LeaveService.decide(…)`  _[@Transactional, writes audit_logs]_
    - `LeaveRequestRepository.existsOverlappingApproved()` → table **`leave_requests`** (`SELECT COUNT(l) > 0 FROM LeaveRequest l WHERE l.user.id = :userId AND l.status = 'APPROVED' AND l.id <> :excludeId AND l.fromDate <= :toDate AND l.toDate >= :fr…`)
    - `LeaveRequestRepository.findById()` → table **`leave_requests`** (derived query)
    - `LeaveRequestRepository.save()` → table **`leave_requests`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/hr/letters/{type}`

- **Auth:** Role — HR_MANAGER / ADMIN
- **Path params:** `type` (string)

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Handler:** `HrLetterController.issue(…)`
**Call chain:**
- `HrLetterService.issue(…)`  _[@Transactional, S3, writes audit_logs]_
    - `UserRepository.findByUuid()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `StorageService.presignedGetUrl(…)`
    - `StorageService.uploadTrusted(…)`
**Side effects:** @Transactional, S3, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "downloadUrl": "string",
    "expiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/hr/payroll`

- **Auth:** Role — HR_MANAGER / ADMIN
- **Query params:** `month`* (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `PayrollController.list(…)`
**Call chain:**
- `PayrollService.list(…)`  _[@Transactional]_
    - `PayrollRecordRepository.findByPeriodMonth()` → table **`payroll_records`** (`SELECT p FROM PayrollRecord p JOIN FETCH p.employee e JOIN FETCH e.user WHERE p.periodMonth = :periodMonth`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "employeeId": 0,
        "employeeCode": "string",
        "employeeFullName": "string",
        "periodMonth": "2026-01-15",
        "workingDays": 0,
        "presentDays": 0,
        "sessionHours": 0.0,
        "grossAmount": 0.0,
        "deductions": 0.0,
        "netAmount": 0.0,
        "payslipDownloadUrl": "string",
        "status": "DRAFT"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/hr/payroll/generate`

- **Auth:** Role — HR_MANAGER / ADMIN

**Request body:**

```json
{
  "periodMonth": "2026-01-15",
  "workingDays": 0,
  "lines": [
    {
      "employeeId": 0,
      "presentDays": 0,
      "sessionHours": 0.0,
      "deductions": 0.0
    }
  ]
}
```

**Handler:** `PayrollController.generate(…)`
**Call chain:**
- `PayrollService.generate(…)`
    - `EmployeeRepository.findAllWithUserByIdIn()` → table **`employees`** (`SELECT e FROM Employee e JOIN FETCH e.user WHERE e.id IN :ids`)
    - `PayrollRecordRepository.findEmployeeIdsAlreadyGenerated()` → table **`payroll_records`** (`SELECT p.employee.id FROM PayrollRecord p WHERE p.periodMonth = :periodMonth AND p.employee.id IN :employeeIds`)
    - `PayrollRecordRepository.save()` → table **`payroll_records`** (derived query)

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "id": 0,
      "employeeId": 0,
      "employeeCode": "string",
      "employeeFullName": "string",
      "periodMonth": "2026-01-15",
      "workingDays": 0,
      "presentDays": 0,
      "sessionHours": 0.0,
      "grossAmount": 0.0,
      "deductions": 0.0,
      "netAmount": 0.0,
      "payslipDownloadUrl": "string",
      "status": "DRAFT"
    }
  ],
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: PIP

- **Package:** `com.moriah.skillhub.pip/, pip/engine/`
- **Build-plan:** Feature 17
- **Tables:** `pip_rules, pip_records, pip_milestones, student_metrics`

The caller's PIP status, PM/Admin PIP browsing, milestone completion, the day-15 exit review (clearance verified server-side against student_metrics), and ADMIN threshold config. PipEvaluationJob runs nightly at 02:00.

### `GET` `/api/v1/pip`

_Browse PIP records_

- **Auth:** Role — TRAINER_PM / HR_MANAGER / ADMIN
- **Query params:** `batchId` (integer), `status` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `PipController.list(…)`
**Call chain:**
- `PipService.list(…)`  _[@Transactional]_
    - `PipMilestoneRepository.findByPipRecordIdIn()` → table **`pip_milestones`** (derived query)
    - `PipRecordRepository.search()` → table **`pip_records`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "studentUuid": "string",
        "studentFullName": "string",
        "batchId": 0,
        "batchName": "string",
        "ruleCode": "ATTENDANCE_LOW",
        "triggerReason": "string",
        "severity": "LOW",
        "triggeredAt": "2026-01-15T10:30:00Z",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "status": "TRIGGERED",
        "blocksTaskPull": true,
        "reviewedByUuid": "string",
        "reviewNotes": "string",
        "outcomeAt": "2026-01-15T10:30:00Z",
        "milestones": [
          {
            "id": 0,
            "title": "string",
            "description": "string",
            "dueDate": "2026-01-15",
            "status": "PENDING",
            "completedAt": "2026-01-15T10:30:00Z",
            "verifiedByUuid": "string"
          }
        ]
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/pip/me`

_The caller's own currently-open PIP record_

- **Auth:** Role — STUDENT

**Handler:** `PipController.me(…)`
**Call chain:**
- `PipService.me(…)`  _[@Transactional]_
    - `PipRecordRepository.findByUserIdAndStatusIn()` → table **`pip_records`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "ruleCode": "ATTENDANCE_LOW",
    "triggerReason": "string",
    "severity": "LOW",
    "triggeredAt": "2026-01-15T10:30:00Z",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "TRIGGERED",
    "blocksTaskPull": true,
    "reviewedByUuid": "string",
    "reviewNotes": "string",
    "outcomeAt": "2026-01-15T10:30:00Z",
    "milestones": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "dueDate": "2026-01-15",
        "status": "PENDING",
        "completedAt": "2026-01-15T10:30:00Z",
        "verifiedByUuid": "string"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/pip/rules`

_The six PIP rules and their current thresholds_

- **Auth:** Role — TRAINER_PM / HR_MANAGER / ADMIN

**Handler:** `PipController.rules(…)`
**Call chain:**
- `PipService.rules(…)`  _[@Transactional]_
    - `PipRuleRepository.findAll()` → table **`pip_rules`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "ruleCode": "ATTENDANCE_LOW",
      "description": "string",
      "thresholdValue": 0.0,
      "windowDays": 0,
      "severity": "LOW",
      "active": true
    }
  ],
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/pip/rules/{code}`

_Update a PIP rule's threshold/window/severity/active flag_

- **Auth:** Role — ADMIN
- **Path params:** `code` (string)

**Request body:**

```json
{
  "thresholdValue": 0.0,
  "severity": "LOW",
  "active": true
}
```

**Handler:** `PipController.updateRule(…)`
**Call chain:**
- `PipService.updateRule(…)`  _[@Transactional, writes audit_logs]_
    - `PipRuleRepository.findByRuleCode()` → table **`pip_rules`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "ruleCode": "ATTENDANCE_LOW",
    "description": "string",
    "thresholdValue": 0.0,
    "windowDays": 0,
    "severity": "LOW",
    "active": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/pip/{id}/milestones/{milestoneId}/complete`

_Mark a PIP milestone complete_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer), `milestoneId` (integer)

**Handler:** `PipController.completeMilestone(…)`
**Call chain:**
- `PipService.completeMilestone(…)`  _[@Transactional, writes audit_logs]_
    - `PipMilestoneRepository.findById()` → table **`pip_milestones`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "dueDate": "2026-01-15",
    "status": "PENDING",
    "completedAt": "2026-01-15T10:30:00Z",
    "verifiedByUuid": "string"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/pip/{id}/review`

_Day-15 review — CLEARED requires task completion >= 85% and no unsatisfactory reviews_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "outcome": "TRIGGERED",
  "reviewNotes": "string"
}
```

**Handler:** `PipController.review(…)`
**Call chain:**
- `PipService.review(…)`  _[@Transactional, writes audit_logs]_
    - `PipMilestoneRepository.findByPipRecordId()` → table **`pip_milestones`** (derived query)
    - `PipMilestoneRepository.saveAll()` → table **`pip_milestones`** (derived query)
    - `UserRepository.getReferenceById()` → table **`users`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
    - `BatchService.updatePipStatus(…)`  _[@Transactional]_
        - `BatchRepository.releaseSeat()` → table **`batches`** (`[native] UPDATE batches SET enrolled_count = enrolled_count - 1 WHERE id = :batchId AND enrolled_count > 0`)
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "ruleCode": "ATTENDANCE_LOW",
    "triggerReason": "string",
    "severity": "LOW",
    "triggeredAt": "2026-01-15T10:30:00Z",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "TRIGGERED",
    "blocksTaskPull": true,
    "reviewedByUuid": "string",
    "reviewNotes": "string",
    "outcomeAt": "2026-01-15T10:30:00Z",
    "milestones": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "dueDate": "2026-01-15",
        "status": "PENDING",
        "completedAt": "2026-01-15T10:30:00Z",
        "verifiedByUuid": "string"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Plans

- **Package:** `com.moriah.skillhub.subscription/`
- **Build-plan:** Feature 07
- **Tables:** `subscription_plans`

Public, Redis-cached catalogue of the 5 subscription tiers.

### `GET` `/api/v1/plans`

_List active subscription plans_

- **Auth:** Public — no token

**Handler:** `PlanController.listPlans(…)`
**Call chain:**
- `EntitlementService.listActivePlans(…)`  _[@Transactional, Redis]_
    - `SubscriptionPlanRepository.findByActiveTrueOrderByTierRankAsc()` → table **`subscription_plans`** (derived query)
**Side effects:** @Transactional, Redis

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "code": "string",
      "name": "string",
      "priceInr": 0.0,
      "tierRank": 0,
      "durationDays": 0,
      "mentorSupport": true,
      "allowsBatch": true,
      "allowsSprints": true,
      "allowsPip": true,
      "allowsInternshipLetter": true,
      "allowsClientProject": true
    }
  ],
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Projects

- **Package:** `com.moriah.skillhub.project/`
- **Build-plan:** Feature 15
- **Tables:** `projects, project_assets, bug_challenges`

DEVELOPER-authored projects, assets (S3 key XOR external URL), DRAFT→PUBLISHED→ARCHIVED. Only PUBLISHED attaches to a task.

### `GET` `/api/v1/projects`

_Browse projects — non-admin callers always see PUBLISHED only_

- **Auth:** Role — DEVELOPER / ADMIN / TRAINER_PM / STUDENT
- **Query params:** `difficulty` (string), `domain` (string), `status` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `ProjectController.list(…)`
**Call chain:**
- `ProjectService.list(…)`  _[@Transactional]_
    - `BugChallengeRepository.findByProjectIdIn()` → table **`bug_challenges`** (derived query)
    - `ProjectRepository.search()` → table **`projects`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "title": "string",
        "slug": "string",
        "description": "string",
        "techStack": [
          "string"
        ],
        "difficulty": "BEGINNER",
        "domain": "string",
        "starterRepoUrl": "string",
        "version": "string",
        "status": "DRAFT",
        "createdByUuid": "string",
        "createdByFullName": "string",
        "assets": [
          {
            "id": 0,
            "assetType": "IMAGE",
            "title": "string",
            "url": "string",
            "sortOrder": 0
          }
        ],
        "challenges": [
          {
            "id": 0,
            "title": "string",
            "expectedBehaviour": "string",
            "brokenCodeUrl": "string",
            "testScriptUrl": "string",
            "difficulty": "BEGINNER"
          }
        ]
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/projects`

_Author a new project (always created DRAFT)_

- **Auth:** Role — DEVELOPER / ADMIN

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "techStack": [
    "string"
  ],
  "difficulty": "BEGINNER",
  "domain": "string",
  "starterRepoUrl": "string",
  "version": "string"
}
```

**Handler:** `ProjectController.create(…)`
**Call chain:**
- `ProjectService.create(…)`  _[@Transactional]_
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/projects/{id}`

_Edit a DRAFT project in place, or bump a PUBLISHED/ARCHIVED one into a new DRAFT version_

- **Auth:** Role — DEVELOPER / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "techStack": [
    "string"
  ],
  "difficulty": "BEGINNER",
  "domain": "string",
  "starterRepoUrl": "string",
  "version": "string"
}
```

**Handler:** `ProjectController.update(…)`
**Call chain:**
- `ProjectService.update(…)`  _[@Transactional]_
    - `BugChallengeRepository.findByProjectId()` → table **`bug_challenges`** (derived query)
    - `ProjectAssetRepository.findByProjectIdOrderBySortOrderAsc()` → table **`project_assets`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/projects/{id}/assets`

_Attach an asset — exactly one of file or externalUrl_

- **Auth:** Role — DEVELOPER / ADMIN
- **Path params:** `id` (integer)
- **Query params:** `assetType`* (string), `title` (string), `externalUrl` (string), `sortOrder` (integer)

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Handler:** `ProjectController.addAsset(…)`
**Call chain:**
- `ProjectService.addAsset(…)`  _[@Transactional, S3]_
    - `ProjectAssetRepository.save()` → table **`project_assets`** (derived query)
    - `StorageService.upload(…)`
**Side effects:** @Transactional, S3

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "assetType": "IMAGE",
    "title": "string",
    "url": "string",
    "sortOrder": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/projects/{id}/publish`

_DRAFT -> PUBLISHED — only a PUBLISHED project can attach to a task_

- **Auth:** Role — DEVELOPER / ADMIN
- **Path params:** `id` (integer)

**Handler:** `ProjectController.publish(…)`
**Call chain:**
- `ProjectService.publish(…)`  _[@Transactional]_
    - `BugChallengeRepository.findByProjectId()` → table **`bug_challenges`** (derived query)
    - `ProjectAssetRepository.findByProjectIdOrderBySortOrderAsc()` → table **`project_assets`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Reviews

- **Package:** `com.moriah.skillhub.submission/`
- **Build-plan:** Feature 12
- **Tables:** `code_reviews, weekly_reviews, task_submissions`

PM review queue, per-submission code review (score 1–10, verdict), and weekly code-defence reviews (the REVIEW_FAILED PIP data source).

### `POST` `/api/v1/reviews`

_Review a submission — score, verdict, comments_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "submissionId": 0,
  "score": 0,
  "verdict": "APPROVED",
  "comments": "string",
  "inlineComments": [
    {
      "filePath": "string",
      "line": 0,
      "comment": "string"
    }
  ]
}
```

**Handler:** `ReviewController.create(…)`
**Call chain:**
- `CodeReviewService.create(…)`  _[@Transactional, writes audit_logs]_
    - `CodeReviewRepository.save()` → table **`code_reviews`** (derived query)
    - `AuditLogService.record(…)`  _[@Transactional]_
        - `AuditLogRepository.save()` → table **`?`** (derived query)
    - `SubmissionService.requireSubmission(…)`
    - `TaskService.completeReview(…)`  _[@Transactional]_
**Side effects:** @Transactional, writes audit_logs

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "submissionId": 0,
    "reviewerUuid": "string",
    "reviewerName": "string",
    "score": 0,
    "verdict": "APPROVED",
    "comments": "string",
    "inlineComments": [
      {
        "filePath": "string",
        "line": 0,
        "comment": "string"
      }
    ],
    "reviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/reviews/queue`

_IN_REVIEW tasks awaiting the caller's review_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `ReviewController.queue(…)`
**Call chain:**
- `TaskService.reviewQueue(…)`  _[@Transactional]_
    - `TaskRepository.findReviewQueue()` → table **`tasks`** (`SELECT t FROM Task t JOIN t.sprint s JOIN s.batch b WHERE t.status = :status AND (:pmId IS NULL OR b.pm.id = :pmId) ORDER BY t.dueAt ASC`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "sprintId": 0,
        "projectId": 0,
        "title": "string",
        "description": "string",
        "taskType": "DAILY",
        "assignedToUuid": "string",
        "assignedToName": "string",
        "storyPoints": 0,
        "dueAt": "2026-01-15T10:30:00Z",
        "status": "BACKLOG",
        "completedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/reviews/weekly`

_Record a student's weekly qualitative rating_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "userUuid": "string",
  "batchId": 0,
  "sprintId": 0,
  "weekStart": "2026-01-15",
  "rating": "SATISFACTORY",
  "notes": "string"
}
```

**Handler:** `ReviewController.createWeekly(…)`
**Call chain:**
- `WeeklyReviewService.create(…)`  _[@Transactional]_
    - `WeeklyReviewRepository.findByUserIdAndWeekStart()` → table **`weekly_reviews`** (derived query)
    - `WeeklyReviewRepository.save()` → table **`weekly_reviews`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentName": "string",
    "batchId": 0,
    "sprintId": 0,
    "weekStart": "2026-01-15",
    "rating": "SATISFACTORY",
    "notes": "string",
    "reviewedByUuid": "string",
    "reviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Sprints

- **Package:** `com.moriah.skillhub.sprint/`
- **Build-plan:** Feature 11
- **Tables:** `sprints, assignment_windows, tasks`

Sprint CRUD + activation (one ACTIVE sprint per batch), and weekly assignment windows.

### `GET` `/api/v1/assignment-windows`

_List assignment windows for a batch_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `batchId`* (integer)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `SprintController.listAssignmentWindows(…)`
**Call chain:**
- `AssignmentWindowService.listByBatch(…)`  _[@Transactional]_
    - `AssignmentWindowRepository.findByBatchIdOrderByWeekStartAsc()` → table **`assignment_windows`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "weekStart": "2026-01-15",
        "weekEnd": "2026-01-15",
        "dueAt": "2026-01-15T10:30:00Z",
        "taskId": 0
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/assignment-windows`

_Create a weekly assignment window for a batch_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "weekStart": "2026-01-15",
  "weekEnd": "2026-01-15",
  "dueAt": "2026-01-15T10:30:00Z",
  "taskId": 0
}
```

**Handler:** `SprintController.createAssignmentWindow(…)`
**Call chain:**
- `AssignmentWindowService.create(…)`  _[@Transactional]_
    - `AssignmentWindowRepository.existsByBatchIdAndWeekStart()` → table **`assignment_windows`** (derived query)
    - `AssignmentWindowRepository.save()` → table **`assignment_windows`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "weekStart": "2026-01-15",
    "weekEnd": "2026-01-15",
    "dueAt": "2026-01-15T10:30:00Z",
    "taskId": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/sprints`

_List sprints for a batch_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `batchId`* (integer)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `SprintController.list(…)`
**Call chain:**
- `SprintService.listByBatch(…)`  _[@Transactional]_
    - `SprintRepository.findByBatchIdOrderBySprintNumberAsc()` → table **`sprints`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "sprintNumber": 0,
        "goal": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "status": "PLANNED",
        "plannedPoints": 0,
        "completedPoints": 0
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/sprints`

_Create a sprint_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "sprintNumber": 0,
  "goal": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "plannedPoints": 0
}
```

**Handler:** `SprintController.create(…)`
**Call chain:**
- `SprintService.create(…)`  _[@Transactional]_
    - `SprintRepository.existsByBatchIdAndSprintNumber()` → table **`sprints`** (derived query)
    - `SprintRepository.existsOverlapping()` → table **`sprints`** (`SELECT COUNT(s) > 0 FROM Sprint s WHERE s.batch.id = :batchId AND (:excludeId IS NULL OR s.id <> :excludeId) AND s.startDate <= :endDate AND s.endDate >= :start…`)
    - `SprintRepository.save()` → table **`sprints`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/sprints/{id}`

_Update a sprint_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "goal": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "plannedPoints": 0,
  "status": "PLANNED"
}
```

**Handler:** `SprintController.update(…)`
**Call chain:**
- `SprintService.update(…)`  _[@Transactional]_
    - `SprintRepository.existsOverlapping()` → table **`sprints`** (`SELECT COUNT(s) > 0 FROM Sprint s WHERE s.batch.id = :batchId AND (:excludeId IS NULL OR s.id <> :excludeId) AND s.startDate <= :endDate AND s.endDate >= :start…`)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/sprints/{id}/activate`

_Activate a sprint (requires the previous sprint COMPLETED)_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Handler:** `SprintController.activate(…)`
**Call chain:**
- `SprintService.activate(…)`  _[@Transactional]_
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Standups

- **Package:** `com.moriah.skillhub.attendance/`
- **Build-plan:** Feature 13
- **Tables:** `standups, attendance`

PM schedules a standup with a late cutoff; students self check-in (PRESENT/LATE); PM can record/override attendance. AttendanceFinalisationJob writes ABSENT rows nightly.

### `GET` `/api/v1/standups`

_List standups for a batch, optionally scoped to one day_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `batchId`* (integer), `date` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `StandupController.list(…)`
**Call chain:**
- `StandupService.list(…)`  _[@Transactional]_
    - `StandupRepository.search()` → table **`standups`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "sprintId": 0,
        "scheduledAt": "2026-01-15T10:30:00Z",
        "lateCutoffMinutes": 0,
        "notes": "string",
        "status": "SCHEDULED",
        "finalisedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/standups`

_Schedule a standup_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "sprintId": 0,
  "scheduledAt": "2026-01-15T10:30:00Z",
  "lateCutoffMinutes": 0,
  "notes": "string"
}
```

**Handler:** `StandupController.create(…)`
**Call chain:**
- `StandupService.create(…)`  _[@Transactional]_
    - `StandupRepository.save()` → table **`standups`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "lateCutoffMinutes": 0,
    "notes": "string",
    "status": "SCHEDULED",
    "finalisedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/standups/{id}`

_Edit or cancel a SCHEDULED standup_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "notes": "string",
  "lateCutoffMinutes": 0,
  "status": "SCHEDULED"
}
```

**Handler:** `StandupController.update(…)`
**Call chain:**
- `StandupService.update(…)`  _[@Transactional]_
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "lateCutoffMinutes": 0,
    "notes": "string",
    "status": "SCHEDULED",
    "finalisedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Submissions

- **Package:** `com.moriah.skillhub.submission/`
- **Build-plan:** Feature 12
- **Tables:** `task_submissions, tasks`

Student GitHub PR submission, GitHub REST verification (PR exists, author matches github_username, state open), commit metadata, retry job on GitHub outage.

### `GET` `/api/v1/submissions`

_List submissions for a task_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `taskId`* (integer), `status` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `SubmissionController.list(…)`
**Call chain:**
- `SubmissionService.list(…)`  _[@Transactional]_
    - `TaskSubmissionRepository.search()` → table **`task_submissions`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "taskId": 0,
        "studentUuid": "string",
        "studentName": "string",
        "attemptNumber": 0,
        "prUrl": "string",
        "repoOwner": "string",
        "repoName": "string",
        "prNumber": 0,
        "prState": "OPEN",
        "commitCount": 0,
        "latestCommitSha": "string",
        "videoUrl": "string",
        "notes": "string",
        "status": "SUBMITTED",
        "submittedAt": "2026-01-15T10:30:00Z",
        "verifiedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/submissions`

_Submit a PR for a task_

- **Auth:** Role — STUDENT

**Request body:**

```json
{
  "taskId": 0,
  "prUrl": "string",
  "videoUrl": "string",
  "notes": "string"
}
```

**Handler:** `SubmissionController.create(…)`
**Call chain:**
- `SubmissionService.create(…)`
    - `GithubVerificationService.verify(…)`  _[external HTTP]_
    - `TaskService.markInReview(…)`  _[@Transactional]_
**Side effects:** @Transactional, external HTTP

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "taskId": 0,
    "studentUuid": "string",
    "studentName": "string",
    "attemptNumber": 0,
    "prUrl": "string",
    "repoOwner": "string",
    "repoName": "string",
    "prNumber": 0,
    "prState": "OPEN",
    "commitCount": 0,
    "latestCommitSha": "string",
    "videoUrl": "string",
    "notes": "string",
    "status": "SUBMITTED",
    "submittedAt": "2026-01-15T10:30:00Z",
    "verifiedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Subscriptions

- **Package:** `com.moriah.skillhub.subscription/`
- **Build-plan:** Features 07, 22
- **Tables:** `user_subscriptions, payments, coupon_redemptions`

Checkout order creation, the caller's subscription history, and the nightly SubscriptionExpiryJob.

### `GET` `/api/v1/subscriptions/me`

_The caller's current active subscription_

- **Auth:** Authenticated (any logged-in user)

**Handler:** `SubscriptionController.me(…)`
**Call chain:**
- `EntitlementService.getCurrentSubscription(…)`  _[@Transactional]_
    - `UserSubscriptionRepository.findByUserIdAndStatus()` → table **`user_subscriptions`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "planCode": "string",
    "planName": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PENDING",
    "autoRenew": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Tasks

- **Package:** `com.moriah.skillhub.sprint/`
- **Build-plan:** Feature 11
- **Tables:** `tasks, sprints`

Task CRUD, PM assignment, student self-pull (blocked by an open PROJECT_DELAY PIP), and the BACKLOG→ASSIGNED→IN_PROGRESS→IN_REVIEW→COMPLETED|REJECTED state machine.

### `GET` `/api/v1/tasks`

_List tasks for a sprint_

- **Auth:** Role — TRAINER_PM / ADMIN / STUDENT
- **Query params:** `sprintId`* (integer), `status` (string), `assignedTo` (string)
- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`

**Handler:** `TaskController.list(…)`
**Call chain:**
- `TaskService.list(…)`  _[@Transactional]_
    - `TaskRepository.search()` → table **`tasks`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "sprintId": 0,
        "projectId": 0,
        "title": "string",
        "description": "string",
        "taskType": "DAILY",
        "assignedToUuid": "string",
        "assignedToName": "string",
        "storyPoints": 0,
        "dueAt": "2026-01-15T10:30:00Z",
        "status": "BACKLOG",
        "completedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/tasks`

_Create a task_

- **Auth:** Role — TRAINER_PM / ADMIN

**Request body:**

```json
{
  "sprintId": 0,
  "projectId": 0,
  "title": "string",
  "description": "string",
  "taskType": "DAILY",
  "storyPoints": 0,
  "dueAt": "2026-01-15T10:30:00Z"
}
```

**Handler:** `TaskController.create(…)`
**Call chain:**
- `TaskService.create(…)`  _[@Transactional]_
    - `TaskRepository.save()` → table **`tasks`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
    - `ProjectService.requirePublished(…)`  _[@Transactional]_
    - `SprintService.requireSprint(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/tasks/{id}`

_Update a task, including driving its status forward_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "taskType": "DAILY",
  "storyPoints": 0,
  "dueAt": "2026-01-15T10:30:00Z",
  "status": "BACKLOG"
}
```

**Handler:** `TaskController.update(…)`
**Call chain:**
- `TaskService.update(…)`  _[@Transactional]_
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/tasks/{id}/assign`

_PM assigns a BACKLOG task to a specific student_

- **Auth:** Role — TRAINER_PM / ADMIN
- **Path params:** `id` (integer)

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Handler:** `TaskController.assign(…)`
**Call chain:**
- `TaskService.assign(…)`  _[@Transactional]_
    - `BatchService.isActiveMember(…)`  _[@Transactional]_
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
    - `BatchService.requireOwnerOrAdmin(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/tasks/{id}/pull`

_Student self-assigns a BACKLOG task_

- **Auth:** Role — STUDENT
- **Path params:** `id` (integer)

**Handler:** `TaskController.pull(…)`
**Call chain:**
- `TaskService.pull(…)`  _[@Transactional]_
    - `BatchService.isActiveMember(…)`  _[@Transactional]_
        - `BatchStudentRepository.findByBatchIdAndUserId()` → table **`batch_students`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Users

- **Package:** `com.moriah.skillhub.user/`
- **Build-plan:** Feature 09
- **Tables:** `users, user_profiles`

Profile fields, server-computed completion %, resume upload/download, and the public portfolio (name/title/skills/certs only — no PII).

### `GET` `/api/v1/portfolio/{slug}`

_A student's public portfolio_

- **Auth:** Public — no token
- **Path params:** `slug` (string)

**Handler:** `UserController.portfolio(…)`
**Call chain:**
- `UserService.getPortfolio(…)`  _[@Transactional]_
    - `UserProfileRepository.findByPortfolioSlug()` → table **`user_profiles`** (derived query)
    - `CertificateService.issuedCertificatesFor(…)`  _[@Transactional]_
        - `CertificateRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc()` → table **`certificates`** (derived query)
    - `ProfileService.fromJsonList(…)`
    - `ProjectService.findTitlesAndSlugs(…)`  _[@Transactional]_
        - `ProjectRepository.findTitlesAndSlugsByIdIn()` → table **`projects`** (`SELECT new com.moriah.skillhub.project.dto.CompletedProjectProjection(p.title, p.slug) FROM Project p WHERE p.id IN :projectIds`)
    - `TaskService.completedProjectIdsFor(…)`  _[@Transactional]_
        - `TaskRepository.findDistinctCompletedProjectIds()` → table **`tasks`** (`SELECT DISTINCT t.projectId FROM Task t WHERE t.assignedTo.id = :userId AND t.status = 'COMPLETED' AND t.projectId IS NOT NULL`)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "fullName": "string",
    "currentTitle": "string",
    "bio": "string",
    "location": "string",
    "skills": [
      "string"
    ],
    "completedProjects": [
      {
        "title": "string",
        "slug": "string"
      }
    ],
    "issuedCertificates": [
      {
        "certificateType": "COMPLETION",
        "issuedAt": "2026-01-15T10:30:00Z",
        "verificationCode": "string"
      }
    ]
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/users/me`

_The caller's own profile_

- **Auth:** Authenticated (any logged-in user)

**Handler:** `UserController.me(…)`
**Call chain:**
- `UserService.getMe(…)`  _[@Transactional]_
    - `ProfileService.getOrCreateProfile(…)`  _[@Transactional]_
        - `UserProfileRepository.findByUserId()` → table **`user_profiles`** (derived query)
        - `UserProfileRepository.save()` → table **`user_profiles`** (derived query)
    - `ProfileService.toResponse(…)`
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `PUT` `/api/v1/users/me/profile`

_Update the caller's profile fields_

- **Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "githubUsername": "string",
  "bio": "string",
  "location": "string",
  "currentTitle": "string",
  "experienceLevel": "string",
  "yearsExperience": 0,
  "skills": [
    "string"
  ],
  "education": [
    {
      "institution": "string",
      "degree": "string",
      "fieldOfStudy": "string",
      "startYear": 0,
      "endYear": 0
    }
  ],
  "workExperience": [
    {
      "company": "string",
      "title": "string",
      "startDate": "2026-01-15",
      "endDate": "2026-01-15",
      "description": "string"
    }
  ]
}
```

**Handler:** `UserController.updateProfile(…)`
**Call chain:**
- `ProfileService.updateProfile(…)`  _[@Transactional]_
    - `UserRepository.findById()` → table **`users`** (derived query)
**Side effects:** @Transactional

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `GET` `/api/v1/users/me/resume`

_A presigned URL for downloading the caller's resume_

- **Auth:** Authenticated (any logged-in user)

**Handler:** `UserController.resumeDownloadUrl(…)`
**Call chain:**
- `ResumeService.getDownloadUrl(…)`  _[@Transactional, S3]_
    - `UserProfileRepository.findByUserId()` → table **`user_profiles`** (derived query)
    - `UserRepository.findById()` → table **`users`** (derived query)
    - `StorageService.presignedGetUrl(…)`
**Side effects:** @Transactional, S3

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "downloadUrl": "string",
    "expiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/users/me/resume`

_Upload (or replace) the caller's resume PDF_

- **Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Handler:** `UserController.uploadResume(…)`
**Call chain:**
- `ResumeService.upload(…)`  _[@Transactional, S3]_
    - `UserRepository.findById()` → table **`users`** (derived query)
    - `ProfileService.getOrCreateProfile(…)`  _[@Transactional]_
        - `UserProfileRepository.findByUserId()` → table **`user_profiles`** (derived query)
        - `UserProfileRepository.save()` → table **`user_profiles`** (derived query)
    - `ProfileService.recalculateCompletion(…)`
    - `ProfileService.toResponse(…)`
    - `StorageService.upload(…)`
**Side effects:** @Transactional, S3

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Errors:** — (envelope `error` on any failure; see §2.3)

---

## Module: Webhooks

- **Package:** `com.moriah.skillhub.payment/, crm/webhook/`
- **Build-plan:** Features 07, 18
- **Tables:** `webhook_events, payments, user_subscriptions, invoices, lead_activities`

Signature-verified, idempotent gateway callbacks: Razorpay/Stripe payment & refund, and WhatsApp inbound messages. Raw body captured before parsing; webhook_events.event_id is the idempotency guarantee.

### `POST` `/api/v1/webhooks/razorpay`

_Razorpay payment/refund webhook — signature-verified, not token-verified_

- **Auth:** Public — no token

**Request body:** raw text (the webhook payload — the HMAC signature is verified against these exact bytes before any parsing).

**Handler:** `PaymentWebhookController.razorpay(…)`
**Call chain:**
- `PaymentWebhookService.handleRazorpayEvent(…)`  _[@Transactional]_
- `RazorpayService.verifySignature(…)`
- `WebhookIdempotencyService.claim(…)`  _[@Transactional]_
    - `JdbcTemplate.update` → `DELETE FROM webhook_events WHERE gateway = ? AND event_id = ? AND status = 'RECEIVED' AND created_at < (NOW(6) - INTERVAL ? MINUTE)`
**Side effects:** @Transactional

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/webhooks/stripe`

_Stripe checkout/refund webhook — signature-verified, not token-verified_

- **Auth:** Public — no token

**Request body:** raw text (the webhook payload — the HMAC signature is verified against these exact bytes before any parsing).

**Handler:** `PaymentWebhookController.stripe(…)`
**Call chain:**
- `PaymentWebhookService.handleStripeEvent(…)`  _[@Transactional]_
- `StripeService.verifyAndParseEvent(…)`
- `WebhookIdempotencyService.claim(…)`  _[@Transactional]_
    - `JdbcTemplate.update` → `DELETE FROM webhook_events WHERE gateway = ? AND event_id = ? AND status = 'RECEIVED' AND created_at < (NOW(6) - INTERVAL ? MINUTE)`
**Side effects:** @Transactional

**Errors:** — (envelope `error` on any failure; see §2.3)

---

### `POST` `/api/v1/webhooks/whatsapp`

_WhatsApp Cloud API inbound message webhook — signature-verified, not token-verified_

- **Auth:** Public — no token

**Request body:** raw text (the webhook payload — the HMAC signature is verified against these exact bytes before any parsing).

**Handler:** `WhatsAppWebhookController.whatsapp(…)`
**Call chain:**
- `WhatsAppWebhookService.handleInbound(…)`

**Errors:** — (envelope `error` on any failure; see §2.3)

---

---

# Part 4 — Issues Found & How They Were Solved

On **2026-08-31** a full-codebase audit ran via four parallel specialist reviews — **security**,
**correctness / bugs**, **database query optimization**, and **backend runtime performance** — plus
the full test suite. The complete report (findings, verification, fix log) is
`docs/audit-2026-08-31.md`; this is the condensed "what broke and how it was fixed" version.

**Result:** all 4 Critical, all 7 High, and 13 Medium/Low items fixed and verified —
`mvn clean verify` (Docker up): **388 unit + 267 integration tests across 37 IT classes, 0
failures**; Flyway applied all 18 migrations against a real MySQL 8 container. Two regressions
introduced by the audit's own changes were caught by `mvn verify` and fixed (see §4.5).

## 4.1 Critical

### C1 — Unauthenticated Java deserialization of the OAuth2 auth-request cookie

- **File:** `common/security/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java`
- **Symptom / risk:** the in-flight `OAuth2AuthorizationRequest` was round-tripped through
  `SerializationUtils.serialize/deserialize` — i.e. `new ObjectInputStream(...).readObject()` on a
  base64 cookie value that is entirely attacker-controlled, on the `permitAll` path
  `/api/v1/auth/oauth2/callback/*`, executed **before any authentication**. CWE-502: a crafted
  payload is a guaranteed unauthenticated DoS and, given the classpath, an RCE-class gadget
  surface. No signature → an attacker who can set the victim's cookie can also inject their own
  `state`/`redirectUri` (OAuth login-CSRF).
- **Fix:** the request is now serialised as a small **non-polymorphic JSON DTO** (`CookiePayload`)
  via the app `ObjectMapper`, base64url-encoded, with an **HMAC-SHA256 tag** appended
  (`base64(json) + "." + base64(hmac)`). The HMAC key is the existing `moriah.jwt.secret` bytes
  prefixed with a fixed domain-separation label so the tag can't be replayed as a JWT. Read path:
  recompute the HMAC, compare with `MessageDigest.isEqual` (constant-time); any mismatch or parse
  failure returns `null` → clean `OAuth2AuthenticationException`. No `ObjectInputStream` anywhere.

### C2 — A student in two batches aborts the entire nightly PIP run

- **File:** `pip/PipEvaluationService.java`
- **Symptom:** `evaluate()` loaded `alreadyOpenUserIds` (users with a pre-existing open PIP record)
  **once**, then looped the cohort. `currentCohortMetrics()` returns one row per
  `(user_id, batch_id)`, and a student enrolled in two batches is legitimate. So a student who
  trips a rule in batch A gets a `pip_records` insert, then the loop reaches their batch-B row —
  `alreadyOpenUserIds` still doesn't contain them — and inserts again → `uq_one_open_pip` violation
  → the whole `@Transactional evaluate()` rolls back → the job is marked FAILED and **every PIP
  trigger that night, across all batches, is lost.** It recurs every night and leaves orphan
  `PIP_TRIGGERED` audit rows.
- **Fix:** one line — `alreadyOpenUserIds.add(metric.userId())` immediately after `fire(...)`
  inside the loop, so the set means "open record **or** already triggered this run". One PIP per
  user regardless of batch count is exactly the `uq_one_open_pip` invariant.

### C3 — A webhook that fails after its idempotency claim is lost permanently and undetectably

- **Files:** `payment/WebhookIdempotencyService.java`, `payment/PaymentWebhookController.java`,
  `crm/webhook/WhatsAppWebhookService.java`, new `common/job/WebhookReconciliationJob.java`
- **Symptom:** `claim()` runs `REQUIRES_NEW` and commits the `webhook_events` row
  (`status='RECEIVED'`, hardcoded) **before** the business handler runs. If the handler then throws
  (`findById().orElseThrow()`, a deadlock, a connection drop), only the business transaction rolls
  back — the claim persists, the gateway retry sees `claim() == false`, and the event is never
  reprocessed. `processed_at` was never set and there was no terminal-status transition, so a
  **lost** event (money captured, subscription never activated) is byte-for-byte identical to a
  processed one.
- **Fix — four parts:**
  1. `runAndMarkProcessed(gateway, eventId, handler)` — a new `@Transactional` method that runs
     the handler (joins the transaction) then `UPDATE webhook_events SET status='PROCESSED',
     processed_at=NOW(6) WHERE … status='RECEIVED'` **atomically** with the business state change.
  2. `releaseClaim(gateway, eventId)` — `REQUIRES_NEW` `DELETE … WHERE status='RECEIVED'`. The
     webhook controllers wrap `runAndMarkProcessed` in `try/catch (RuntimeException)`; on failure
     they call `releaseClaim` and rethrow → 5xx → the gateway redelivers as fresh work.
  3. Stale-claim reclaim inside `claim()` — a row still `RECEIVED` and older than 15 minutes
     (handler process killed mid-delivery) is deleted and re-inserted on the next delivery. The
     `DELETE` runs **only after a confirmed duplicate-key `INSERT` failure** so the happy path is a
     bare `INSERT` (see the regression note in §4.5).
  4. `WebhookReconciliationJob` — a 10-minute sweep that logs `ERROR` for any `webhook_events` row
     stuck in `RECEIVED` > 30 minutes, so a genuinely wedged payment is visible without reading
     gateway dashboards. (`WhatsAppWebhookService.handleInbound` was also restructured so each
     message is its own claim → process → release unit — a bad message no longer rolls back the
     batch and drops the others' claims.)

### C4 — Rate limiter and audit IP collapse behind a load balancer

- **Files:** `common/security/ClientIpResolver.java`, `common/security/RateLimitFilter.java`,
  `application*.yml`, new `common/config/StartupHardeningWarnings.java`
- **Symptom:** `moriah.security.trusted-proxies` is empty in every profile and there is no
  `forward-headers-strategy`, so in the documented deployment shape (TLS terminated upstream)
  `getRemoteAddr()` is the load balancer's IP for **every** request. `RateLimitFilter` keyed on
  that IP, so all users shared **one** 60-req/min bucket (≈1 req/s total platform throughput), the
  per-IP `/auth/**` throttle — the only brute-force control — was effectively disabled, and every
  `audit_logs.ip_address` recorded the proxy.
- **Fix:** (a) `trusted-proxies` entries may now be **CIDR ranges** (`IpAddressMatcher`), so a real
  LB is configurable; (b) a prominent startup `WARN` fires when the list is empty in a non-local
  profile; (c) `RateLimitFilter` buckets a request carrying a bearer token on `tok:<md5(token)>`
  instead of IP, so authenticated traffic no longer collapses; the global limit was raised 60 →
  300 (now a per-user budget). Tokenless `/auth/**` still buckets by IP, so `TRUSTED_PROXIES` must
  still be set at deploy time — account-level lockout (M12) is the complementary defence and is
  deferred.

## 4.2 High

| # | File(s) | Symptom | Fix |
|---|---|---|---|
| **H1** | `common/config/AsyncConfig.java` | `@EnableAsync` only → `@Async` ran on an **unbounded** `SimpleAsyncTaskExecutor` (new virtual thread per task, no queue, no rejection). An export or payment-webhook burst could drain the connection pool. | `AsyncConfigurer` with a bounded `ThreadPoolTaskExecutor` (4/8/queue 100, `CallerRunsPolicy`) as the default, plus a dedicated `exportExecutor` (1/2/queue 10). `ExportGenerationService.generate` → `@Async("exportExecutor")`. |
| **H2** | `admin/ExportGenerationService.java` | Each sheet builder ran the query into a full `List<Object[]>` **before** the first cell — `SXSSFWorkbook`'s streaming only bounded the XML side. `writeAuditSheet` had no `LIMIT` on `audit_logs` → multi-hundred-MB heap spike / OOM. | Rows now stream from a `TYPE_FORWARD_ONLY`, `fetchSize=Integer.MIN_VALUE` cursor straight into the `SXSSFSheet` (~100 rows live). Every query hard-capped at `MAX_EXPORT_ROWS=200_000` with a `WARN` on truncation. (Also L6 — cells starting `= + - @` are apostrophe-prefixed against CSV formula injection.) |
| **H3** | `common/notification/NotificationWorker.java`, `.../dispatch/NotificationClientConfig.java` | `@Scheduled(fixedDelay=100)` never overlaps itself → one logical consumer, ~1 msg per (dispatch latency + 100ms). A nightly PIP run enqueues ~1,500. WhatsApp/SendGrid clients had **no HTTP timeout** — one hung provider froze the pipeline. | `drainBatch()` pops up to 8 messages/tick and dispatches them **concurrently** on the bounded executor (Redis stays a single consumer). Both clients got explicit 3s connect / 8s read timeouts. |
| **H4** | `common/job/*` + new `JobChainGuard.java` | `@SchedulerLock` is per job-name → no interlock. Attendance finalisation running past 01:45 → metrics refresh starts against half-finalised attendance → denominator corruption. | `JobChainGuard.predecessorSucceededRecently(jobName)` — `MetricsRefreshJob` requires an `AttendanceFinalisationJob` SUCCESS run within 6h; `PipEvaluationJob` requires a `MetricsRefreshJob` one. A missing predecessor → the job records a FAILED `job_runs` row and skips. |
| **H5** | `submission/repository/TaskSubmissionRepository.java` + `V17` | `SubmissionVerificationRetryJob` full-scanned `task_submissions` every 15 min via `findByVerifiedAtIsNull()` — `verified_at` was unindexed. | `V17__audit_2026_08_high_indexes.sql` — `idx_task_submissions_verified_at` (`ALGORITHM=INPLACE, LOCK=NONE`). InnoDB indexes NULLs so `IS NULL` is a short range scan. |
| **H6** | `payment/PaymentWebhookService.java` | On a repeat payment, `activateSubscription` caught the `uq_one_active_subscription` violation from `saveAndFlush` but only **logged** it, then fell through to invoice + batch allocation — card charged, no service, no refund. Worse, a caught `saveAndFlush` failure **poisons the Hibernate session**, so the whole webhook tx then fails to commit → the gateway retries forever. | `activateSubscription` now **pre-checks** for an existing ACTIVE row and returns `false` without attempting the insert — no `saveAndFlush`, no poisoned session. On `false`, `capturePayment` marks the payment `CAPTURED`, writes a distinct `PAYMENT_CAPTURED_NO_SUBSCRIPTION` audit action + `ERROR` log for a manual refund, and **skips** invoice / allocation / event. New unit test covers it. |
| **H7** | `common/security/JwtAuthFilter.java` + new `user/repository/AuthUserView.java` | The filter did `userRepository.findByUuid(uuid)` — the whole `User` entity, incl. the `byte[]` TOTP secret — on every authenticated request, to compare one integer. | New `AuthUserView` Spring Data projection (`id`, `uuid`, `status`, `tokenVersion`) and `findAuthViewByUuid`. The fresh per-request `token_version` check is unchanged in behaviour, just cheaper. |

## 4.3 Medium (landed)

| # | Fix |
|---|---|
| **M3 / M19** | `V18__audit_2026_08_medium_indexes.sql` — adds `idx_payments_status_captured`, `idx_leads_created`, `idx_task_submissions_status_submitted`; drops the redundant `idx_pip_records_user` (left-prefix of `idx_pip_records_pull_block`) and `idx_standups_finalised` (left-prefix of V16's `idx_standups_finalisation`). |
| **M4** | `application.yml` — `hibernate.default_batch_fetch_size: 100`, `hibernate.order_updates: true`. |
| **M7** | `SubscriptionExpiryService`, `PipEvaluationService.fire`, `PaymentWebhookService.activateSubscription` now use `LocalDate.now(ZoneId.of(jobsZone))` (`@Value("${moriah.jobs.zone}")`). On a UTC host the old `LocalDate.now()` dated PIP windows and subscription periods a day short. |
| **M11** | JDBC URL `useSSL=true` (== `sslMode=PREFERRED`, silent plaintext fallback) → `sslMode=${DB_SSL_MODE:REQUIRED}`. `VERIFY_CA` + truststore documented in `application-prod.yml`. |
| **M14** | `spring.data.web.pageable.max-page-size: 100` — the built-in cap was 2000. |
| **M15** | `GithubOAuth2UserService` `RestClient` built with 3s/8s timeouts (was `RestClient.create()`, no timeout, on the login thread). `RazorpayService` builds its SDK client once (lazy double-checked singleton) instead of per `createOrder`. |
| **M17** | `CertificateService.issue` rejects a second **live** certificate for the same `(user, batch, type)` via `existsBy…AndRevokedAtIsNull` (re-issue after revoke still allowed). |
| **M20** | New `ExpiredTokenReaperJob` — nightly, deletes `refresh_tokens` / `password_reset_tokens` / `email_verification_tokens` past a 30-day grace, capped 20k/table/run, `@SchedulerLock` + `JobRunTracker`. |
| **M9** (partial) | `HrDocumentService.upload` slug-sanitises `documentType` (`[^A-Za-z0-9_-]` stripped) before it enters the S3 key / DB column / DTO. `ProjectService` asset-filename spots bundled with the deferred M5. |
| **M10** (partial) | New `StartupHardeningWarnings` logs a `WARN` when `spring.data.redis.password` is blank outside a local profile. Serializer & docker-compose `requirepass` left as-is (serializer swap risks breaking cached shapes; local Redis is network-isolated). |
| **L2 / L17** | Primary Hikari pool → fixed-size (`minimum-idle == maximum-pool-size`, removes the cold-ramp TLS-handshake storm behind the load-test p95) + explicit `max-lifetime` / `keepalive-time` / `leak-detection-threshold`. `connection-timeout` is `${DB_CONNECTION_TIMEOUT_MS:30000}` (env-tunable for prod fail-fast; default kept test-safe — see §4.5). Replica pool given the same. |

## 4.4 Low (landed) & non-defects

- **L1** — `WhatsAppSignatureVerifier` now compares digests with `MessageDigest.isEqual` on decoded
  bytes (constant-time); invalid hex short-circuits to `false`.
- **L3** — GitHub PR-URL regex owner/repo groups changed to `[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?`
  so a segment can never be `.` or `..` (`/repos/../../pulls/N`).
- **L9** — `Accumulator.daysSinceLastActivity` clamped with `Math.max(0, …)` against a future-dated
  `lastActivity`.
- **L10** — the invoice-number code was already correct and deliberate (no year segment, per
  `Constants.INVOICE_PREFIX`'s Javadoc); the drift in `architecture.md` was corrected.
- **L12** — removed the no-op `@Transactional` from `NotificationWorker.markSent` /
  `recordFailedAttempt` (self-invoked → proxy never applied it).
- **L19** — `architecture.md`'s nightly-chain description corrected. The `V10` SQL comment was left
  as-is — editing an applied migration changes its Flyway checksum.
- **Not defects:** L5 (`isAuthenticated()` + reporting-manager service check is the intended design
  — a reporting manager has no role to gate on); L11 (the payroll `JOIN FETCH` is on `@ManyToOne`,
  not a collection — no HHH000104). L8 / L15 / L20 are ops/doc notes with no code change.

## 4.5 Two regressions caught by `mvn verify` (and fixed)

1. **`ConnectionPoolLoadIT` / `BatchFlowIT` — 500s under load.** The L2 change had set
   `connection-timeout: 3000` (the "fail fast on saturation" recommendation), but 3s is too
   aggressive for the co-located 150-thread load tests, which are built to prove the pool
   **queues** gracefully — 12 requests hit `HikariPool-1 - Connection is not available, request
   timed out after 3009ms`. Made it `${DB_CONNECTION_TIMEOUT_MS:30000}` (Hikari's own default;
   production can tighten). The fixed-size pool + lifetimes stay.
2. **`BatchFlowIT.concurrentAllocation` — a deadlock 500.** The C3 `claim()` rewrite did an
   **unconditional** `DELETE … WHERE event_id = ? AND status='RECEIVED' AND …` on every claim; on
   a REPEATABLE-READ transaction that takes a gap lock on the unique index, and concurrent
   first-deliveries of lexically-adjacent `event_id`s (`payment.captured:pay_xxx1`, `…xxx2`)
   deadlocked each other → uncaught → 500. Restructured so the **happy path is a bare `INSERT`**
   (exactly as before, no gap locks) and the stale-row `DELETE` runs **only after** a confirmed
   duplicate-key failure.

## 4.6 Deferred — need a Docker-up focused session or a product decision

| Item | Why deferred |
|---|---|
| **M1, M2, M6, M16, M18** | Rewrite nightly-chain core SQL / transaction structure (metrics refresh, PIP eval, attendance finalisation). Only the 269-test integration suite exercises these paths — do as one batch with `mvn verify` green. M1/M2: PIP & metrics read the whole `student_metrics` / all-history and filter the cohort in Java (should push the cohort filter into SQL). M6: `refresh()`/`evaluate()` each span the whole population in one transaction (should chunk-commit). M16/M18: `AttendanceFinalisationService.saveAll(absentRows)` is N single-row inserts with a read-then-write race (should be a batch `INSERT … ON DUPLICATE KEY`). |
| **M5** (+ M9 remainder) | `@Transactional`-wraps-S3-upload refactor across `ResumeService` / `HrDocumentService` / `ProjectService` — detached-entity lifecycle risk that unit tests won't catch. |
| **M8** | `PayrollService.generate` per-line result list is an **API response-shape change** → needs frontend / OpenAPI coordination. |
| **M12** | Account lockout / TOTP brute-force cap — a new security feature on the `@Transactional` login path; needs the auth IT suite. C4's per-token buckets + the 10/min IP limit are the interim mitigation. |
| **M13** | CRM lead visibility: **is the sales pipeline shared team-wide or scoped per agent?** A product decision; the authz model was not changed unilaterally. |
| **L7, L13, L14, L16** | Small features / build-gate additions (2FA-endpoint auth path, OWASP dependency-check plugin, Meta `hub.challenge` handler, `double` → int percentage). |

---

# Part 5 — Running & testing

```bash
# prerequisites: JDK 21, Maven, Docker Desktop running
cp .env.example .env          # dev-only values already present
docker compose up -d          # MySQL 8 (moriah_migrate / moriah_app pre-provisioned), Redis 7, MinIO
mvn spring-boot:run           # http://localhost:8080

# health / docs
GET http://localhost:8080/actuator/health         → {"status":"UP"}
http://localhost:8080/swagger-ui.html             (dev profile only)

# full test gate (unit + Testcontainers integration against real MySQL + Redis)
mvn verify
```

- Sample users / batches / leads live in `db/testdata/R__dev_seed_data.sql` — a **Flyway repeatable
  migration**, run only in the `dev` profile (`application-dev.yml` adds `classpath:db/testdata` to
  `spring.flyway.locations`; `test`/`prod` keep the default and never load it). Reference data
  (roles, plans) is seeded by `V4`/`V5`.
- `context/progress-tracker.md` — the authoritative project-status log (feature checklist,
  migration ledger, decision log, UAT scenarios).
- To regenerate the API docs after an endpoint change: re-export `docs/openapi.json` from
  `GET /v3/api-docs` (dev), then `python scripts/gen-api-doc.py` and
  `python scripts/gen-project-ref.py`.
