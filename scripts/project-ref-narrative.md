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
