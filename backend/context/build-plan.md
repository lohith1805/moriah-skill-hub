# Build Plan

## Core Principle

Schema first, then endpoints, then automation. Every feature is verifiable the moment it is finished — by an HTTP call against a running instance or by an integration test.

**One developer, executing strictly in order 01 → 24.** No feature is reordered by difficulty, dependency convenience, or anything else. Where an early feature needs something a later one provides, a **tracked stub** is left and recorded in the Open Stubs table in `progress-tracker.md`. Stubs are the mechanism for keeping the order fixed; they are not technical debt as long as every one is cleared before feature 24.

**Migration versions ascend with feature numbers.** V1–V5 at feature 02, V6–V8 at 06, V9 at 16, V10 at 17, V11 at 18, V12 at 19, V13 at 20, V14 at 21, V15 at 23. Nothing out of sequence, so Flyway applies them in exactly the order you wrote them.

The frontend track codes against the OpenAPI spec in parallel. Contract changes after Sep 4 must be communicated before merge.

Delivery: **Aug 20 – Sep 11, 2026, 17 working days, 24 features.** Roughly 1.4 features/day with no buffer. Read the Risk section before starting.

---

## Phase 1 — Foundation

**Aug 20 – Aug 26 · 5 days · 8 features**

---

### 01 Project Skeleton and Configuration

**Logic:**

- Maven, Java 21, Spring Boot 3.5, package root `com.moriah.skillhub`
- Dependencies exactly as listed in code-standards.md
- `application.yml` with `dev` and `prod` profiles, every secret an env placeholder
- `ddl-auto: validate`, `open-in-view: false`, virtual threads enabled
- **Two datasources:** the Hikari runtime pool on `moriah_app`, and a separate short-lived Flyway datasource on `moriah_migrate`
- `common/dto/ApiResponse`, `PageResponse`, `ErrorDetail` records
- `GlobalExceptionHandler` with the `ErrorCode` enum
- `BaseEntity` with JPA auditing
- springdoc-openapi, Swagger UI on `dev` only
- Actuator health public, everything else secured

**Verify:** Starts against local MySQL, `/actuator/health` returns `UP`, Swagger loads, and the runtime user provably cannot run DDL.

---

### 02 Core Schema and Database Resilience

Migrations V1–V5, the two database users, and the backup/PITR configuration. **This is the largest single feature in Phase 1 and the one most worth not rushing.**

**Schema:**

- `V1__core_users_roles.sql` — `users` (including `token_version`), `roles`, `permissions`, `role_permissions`, `user_roles`, `user_profiles`, `refresh_tokens`, `password_reset_tokens`, `email_verification_tokens`. **`permissions`/`role_permissions` are created here and never populated or read by any of the 24 features** — RBAC is role-based (`hasRole`) throughout. The tables exist so a permission-code layer can be added later without a breaking migration; they are not a Phase 1 deliverable.
- `V2__system_audit_notifications.sql` — `audit_logs`, `notifications`, `shedlock`, `job_runs`. **These land here, not at the end.** Feature 03 writes failed logins to `audit_logs`; feature 08 writes to `notifications`. Both fail `ddl-auto: validate` if these tables arrive later.
- `V3__subscriptions_payments.sql` — `subscription_plans`, `user_subscriptions` (with the `active_user_id` generated column for one-active-subscription uniqueness), `payments`, `invoices`, `webhook_events`, `coupons`, `coupon_redemptions`
- `V4__seed_roles_permissions.sql` — **despite the filename, this seeds the 8 roles only.** No permission codes are inserted and `role_permissions` stays empty. Idempotent. (Filename kept as-is to avoid renumbering every migration after it — see the decision note in `progress-tracker.md`.)
- `V5__seed_plans.sql` — 5 tiers with entitlement flags. Idempotent.
- All FKs explicitly named; all status columns with `CHECK` constraints matching the Java enums exactly
- **No partial unique indexes** — MySQL does not have them. Conditional uniqueness uses `STORED` generated columns.

**Database users:**

- `moriah_migrate` — `ALL PRIVILEGES` on the schema, used only by Flyway at startup
- `moriah_app` — DML on all tables except `audit_logs`, where it holds `SELECT, INSERT` only. No DDL.

**Resilience:**

- `log_bin` ON, `binlog_format = ROW`, 7-day retention, `sync_binlog = 1` — the precondition for PITR
- Nightly full logical dump to S3, encrypted and versioned, 30-day retention
- Binlog shipping to S3 every 5 minutes — this is what bounds RPO to 5 minutes, not the nightly dump
- One async read replica in a second AZ, for standby promotion and for admin/export reads
- `docs/runbook-dr.md` — promote replica, repoint app, verify `flyway_schema_history`, reconcile `webhook_events` against the gateways

**Verify:** Flyway migrates cleanly from empty. `ddl-auto: validate` passes. Re-running seeders changes nothing. `moriah_app` fails on `UPDATE audit_logs`. **A restore drill succeeds: full dump plus binlogs replayed to a chosen timestamp on a scratch instance, row counts diffed against the primary.**

---

### 03 Authentication

**Endpoints:**

```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
POST /api/v1/auth/verify-email
POST /api/v1/auth/password/forgot
POST /api/v1/auth/password/reset
```

**Logic:**

- BCrypt cost 12
- JWT access token, 60 minutes, carrying `sub` (uuid), `roles`, `jti`, `tv` (token_version), and a `kid` header for key rotation
- **`tier` is deliberately not a claim** — entitlements are read from the DB per check so an upgrade takes effect immediately
- Refresh token: opaque 512-bit, SHA-256 hashed, 30 days, **rotated on every use**. Reuse of a consumed token revokes the whole chain.
- **Revocation:** `JwtAuthFilter` compares the `tv` claim against `users.token_version`, read directly from the database on every request — deliberately **not** cached in Redis (see `architecture.md` "Authentication" for why: the per-request `User` lookup by uuid already has the freshest value, so a cache would only add staleness). Password reset and `logout-all` increment it. `jti` goes on a Redis denylist on explicit logout.
- Email verification tokens issued on register
- Rate limit 10 req/min/IP on all auth endpoints
- Failed logins written to `audit_logs` with IP

**Verify:** Register → verify email → login → protected call → refresh → old refresh token rejected → `logout-all` → **every previously issued access token rejected within a second**, not after 60 minutes.

---

### 04 RBAC and Entitlements

**Logic:**

- `@EnableMethodSecurity`, roles as `ROLE_*` authorities from `user_roles`. **No permission-code check anywhere** — `permissions`/`role_permissions` from V1 stay empty and unread. `hasRole(...)` is the entire authorization mechanism.
- `EntitlementGuard.require(userId, Entitlement.X)` — reads the active subscription from the DB, cached 60s. Never from a token claim.
- `Entitlement` enum: `BATCH`, `SPRINTS`, `PIP`, `MENTOR`, `INTERNSHIP_LETTER`, `CLIENT_PROJECT`
- No active subscription → `403 ENTITLEMENT_REQUIRED`, never `401`
- `OwnershipGuard` — resource ownership, batch-PM check, and **storage key ownership** (`canAccessKey(userId, key)`), used by every presigned URL request from feature 08 onward
- Redis rate limiting, 60 req/min/IP globally
- Role change increments `token_version`

**Verify:** Per-role `403` tests. A `STARTER` gets `403` on a sprint endpoint, `PROJECT_BASED` gets `200`. An upgraded user gains entitlement without re-login.

---

### 05 OAuth2 and 2FA

**Endpoints:**

```
GET  /api/v1/auth/oauth2/authorize/{provider}
GET  /api/v1/auth/oauth2/callback/{provider}
POST /api/v1/auth/2fa/enable
POST /api/v1/auth/2fa/verify
POST /api/v1/auth/2fa/disable
```

**Logic:**

- Google and GitHub OAuth2 via Spring Security OAuth2 Client
- First OAuth login creates a `STUDENT` with `email_verified_at` set and no password hash
- Existing email → link the provider, never create a duplicate user
- **GitHub login captures `github_username`** — feature 12 depends on it
- TOTP 2FA, secret AES-GCM encrypted at rest with a key from the secret store (not a bare env var), mandatory for `ADMIN` and `HR_MANAGER`
- Issues the same JWT pair as password login — one token contract

**Verify:** OAuth round trip issues a valid token. A second login with the same Google account does not create a second user. An `ADMIN` without 2FA cannot complete login.

---

### 06 Learning Schema

Migrations V6–V8.

**Schema:**

- `V6__batches_sprints_tasks.sql` — `batches`, `batch_students` (with `graduated_at`, `graduated_by`), `sprints`, `tasks`. **No `version` column on `batches`** — capacity uses a conditional atomic update, not optimistic locking.
- `V7__submissions_reviews_attendance.sql` — `task_submissions` (unique on `task_id, user_id, attempt_number`), `code_reviews`, **`weekly_reviews`**, `standups` (with `late_cutoff_minutes`, `finalised_at`), `attendance` (with `is_auto_marked`)
- `V8__assessments_projects.sql` — `quizzes`, `quiz_questions`, `quiz_attempts` (with `auto_graded_marks` / `auto_gradable_marks`), `quiz_answers`, **`assignment_windows`**, `projects`, `project_assets`, `bug_challenges`

`weekly_reviews` and `assignment_windows` exist because two PIP rules read them. Without these tables, `REVIEW_FAILED` and `ASSIGNMENT_MISSED` have no data source and cannot be implemented at feature 17.

Composite indexes: `tasks(sprint_id, status, due_at)`, `tasks(assigned_to, status, due_at)`, `attendance(user_id, status, created_at)`

**Verify:** Flyway clean, `validate` passes, `EXPLAIN` on the PM review-queue query uses an index.

---

### 07 Payment Integration

**Run `/architect` before this one.**

**Endpoints:**

```
GET  /api/v1/plans
POST /api/v1/subscriptions/checkout
GET  /api/v1/subscriptions/me
POST /api/v1/webhooks/razorpay
POST /api/v1/webhooks/stripe
```

**Logic:**

- `GET /plans` public, cached in Redis
- Checkout creates a `payments` row `CREATED`, returns the gateway order. Coupon redemption recorded in `coupon_redemptions`.
- Amount always re-read from `subscription_plans` server-side — never trust the client
- Razorpay in paise, Stripe in smallest currency unit
- Webhook: raw body → HMAC verify → `claim()` on `webhook_events.event_id` → duplicate returns `200` and stops
- **Inline work is bounded:** `payments` → `CAPTURED`, `user_subscriptions` → `ACTIVE`, `invoices` row written with status `PENDING`. Then return `200`. **PDF rendering and S3 upload are enqueued for `InvoiceGenerationJob`, not done inside the gateway's ~5s timeout.**
- **Stub:** `BatchAllocationService.allocate()` — implemented at feature 10
- **Stub:** `InvoiceGenerationJob` writes the PDF locally and logs. S3 upload is wired at feature 08.
- Refunds flip `payments` → `REFUNDED`, `user_subscriptions` → `CANCELLED`, and **de-allocate the student from their batch** (`batch_students.status` → `REASSIGNED`). A refunded student must not keep pulling tasks.
- `SubscriptionExpiryJob` — nightly, transitions `ACTIVE` subscriptions past `end_date` to `EXPIRED`. Without it every subscription is perpetual.
- Webhook endpoints rate-limited despite being `permitAll()` — HMAC verification is CPU work and is otherwise a free DoS surface

**Verify:** Test payment activates a subscription and produces one invoice. Replaying the identical webhook produces no second subscription and no second invoice. A tampered signature returns `400`. The webhook responds in well under 1s. A refund de-allocates.

---

### 08 Storage and Notification Infrastructure

**Logic:**

- `S3Client` with `endpointOverride` when `S3_ENDPOINT` is set, so R2 works with no code change
- Private bucket, presigned GET with 15-minute TTL, **every request authorized by `OwnershipGuard.canAccessKey()` before signing**. DB stores the key, never the URL.
- Upload validation: content type, magic bytes, 10MB cap, server-generated filename
- Clears the feature 07 stub: `InvoiceGenerationJob` now uploads to S3 and flips `invoices.status` to `ISSUED`
- `NotificationService.enqueue()` writes a `notifications` row, then pushes to Redis
- **`NotificationWorker` uses a reliable queue** — `RPOPLPUSH` into a processing list, ack on success, requeue on worker death. A plain `LPOP` loses the message if the worker dies mid-dispatch.
- Retries 3×, then marks `FAILED`. Spaced by `NotificationReaperJob`'s fixed staleness-sweep interval (5 minutes), not literal exponential backoff — ratified on `/review` of features 07+08 (progress-tracker.md), since the reliable-queue mechanism's own redelivery cadence already governs retry spacing and a separate backoff schedule would need its own timer nothing else here maintains.
- All enqueueing in `afterCommit`
- ShedLock against the `shedlock` table from V2

**Verify:** Upload, retrieve via presigned URL, confirm expiry. Requesting another user's resume key returns `403`. Kill the worker mid-dispatch and confirm the message is redelivered, not lost.

---

## Phase 2 — Core Feature Implementation

**Aug 27 – Sep 04 · 7 days · 11 features**

---

### 09 User Profile and Portfolio

**Endpoints:**

```
GET   /api/v1/users/me
PUT   /api/v1/users/me/profile
POST  /api/v1/users/me/resume
GET   /api/v1/users/me/resume
GET   /api/v1/portfolio/{slug}
```

**Logic:**

- Profile fields; `github_username` editable here as a fallback for password-registered users
- `completion_percent` computed server-side; `is_complete` derived, never client-supplied
- Resume to `resumes/{userUuid}/resume.pdf`, key stored on the profile
- `portfolio_slug` unique, name plus random suffix
- `GET /portfolio/{slug}` public — name, title, skills, completed projects, issued certificates only. Never email, phone, scores, or PIP status.

**Verify:** Completion recalculates on save. Public portfolio contains no PII beyond name and title.

---

### 10 Batch Management and Allocation

**Run `/architect` before this one.**

**Endpoints:**

```
POST   /api/v1/batches
GET    /api/v1/batches
GET    /api/v1/batches/{id}
PUT    /api/v1/batches/{id}
POST   /api/v1/batches/{id}/students
DELETE /api/v1/batches/{id}/students/{userId}
```

**Logic:**

- Creation restricted to `TRAINER_PM` and `ADMIN`; creator becomes `pm_id`
- Clears the feature 07 stub. `BatchAllocationService.allocate(userId)` finds an `ACTIVE`/`PLANNED` batch matching track and minimum tier with free capacity.
- **Capacity via conditional atomic update:** `UPDATE batches SET enrolled_count = enrolled_count + 1 WHERE id = ? AND enrolled_count < capacity`. Zero rows affected means full. No read-then-write, no retry loop, no race under launch-day load.
- No matching batch → pending queue, PM notified. Never silently unallocated.
- Only `PROJECT_BASED` and above allocated. `STARTER` and `PROFESSIONAL` never enter a batch.
- Removal sets `batch_students.status` to `REASSIGNED` — never deletes, history is needed for PIP and certificates

**Verify:** Concurrent allocation to a one-seat batch yields exactly one enrolment. A `STARTER` is never allocated. Payment → allocation now works end to end.

---

### 11 Sprint and Task Management

**Endpoints:**

```
POST /api/v1/sprints
GET  /api/v1/sprints?batchId=
PUT  /api/v1/sprints/{id}
POST /api/v1/sprints/{id}/activate
POST /api/v1/tasks
GET  /api/v1/tasks?sprintId=&status=&assignedTo=
PUT  /api/v1/tasks/{id}
POST /api/v1/tasks/{id}/assign
POST /api/v1/tasks/{id}/pull
POST /api/v1/assignment-windows
```

**Logic:**

- Sprints 1–2 weeks, unique per `(batch_id, sprint_number)`, non-overlapping within a batch
- One `ACTIVE` sprint per batch; activating requires the previous `COMPLETED`
- Tasks: `DAILY`, `ASSIGNMENT`, `STORY`, `BUGFIX`
- **`assignment_windows` created here** — the PM defines weekly windows with a due date and an optional linked task. Feature 17's `ASSIGNMENT_MISSED` rule counts consecutive windows with no submission.
- `POST /tasks/{id}/pull` — student self-assigns a `BACKLOG` task. **Stub:** blocked if an open `pip_records` row has `blocks_task_pull = true`. Wire the hook now; the record is created at feature 17.
- State machine `BACKLOG → ASSIGNED → IN_PROGRESS → IN_REVIEW → COMPLETED | REJECTED`. Illegal transitions `409`.
- `completed_points` recalculated on completion

**Verify:** Illegal transition returns `409`. Velocity is accurate. Assignment windows can be created and listed per batch.

---

### 12 GitHub Submission and Code Review

**Run `/architect` before this one.**

**Endpoints:**

```
POST /api/v1/submissions
GET  /api/v1/submissions?taskId=&status=
GET  /api/v1/reviews/queue
POST /api/v1/reviews
POST /api/v1/reviews/weekly
```

**Logic:**

- Student submits `{ taskId, prUrl, videoUrl?, notes? }`
- **Idempotent:** unique `(task_id, user_id, attempt_number)`. A double-POST does not create two submissions.
- URL parsed by regex; malformed → `400 INVALID_PR_URL`
- GitHub REST verifies the PR exists, author matches `users.github_username`, state is open. **Author mismatch → `403`** — without this a student submits someone else's PR.
- Commit count and latest SHA persisted
- Verification cached in Redis 5 min keyed `owner/repo/pr` — a batch refreshing must not burn the 5,000/hr limit
- GitHub 5xx → persist `verified_at = null`, status `SUBMITTED`, retry job verifies later. An outage never blocks a student.
- Task → `IN_REVIEW`, appears on `GET /reviews/queue`
- PM review: score 1–10, verdict `APPROVED` / `CHANGES_REQUESTED`, comments, inline comments JSON
- **`POST /reviews/weekly`** writes a `weekly_reviews` row with rating `SATISFACTORY` / `NEEDS_IMPROVEMENT` / `UNSATISFACTORY`. This is the data source for the `REVIEW_FAILED` PIP rule — the rule is unimplementable without it.

**Verify:** Submitting another user's PR returns `403`. Malformed URL returns `400`. Double-POST creates one row. A weekly `UNSATISFACTORY` is queryable per student per week.

---

### 13 Standups and Attendance

**Endpoints:**

```
POST /api/v1/standups
GET  /api/v1/standups?batchId=&date=
POST /api/v1/standups/{id}/checkin
POST /api/v1/standups/{id}/attendance
GET  /api/v1/attendance/me
GET  /api/v1/attendance/batch/{batchId}
```

**Logic:**

- PM schedules a standup with a `late_cutoff_minutes` window
- Self check-in: on time → `PRESENT`, after cutoff → `LATE`
- **`AttendanceFinalisationJob`, nightly at 01:30 IST.** For every `CONDUCTED` standup not yet `finalised_at`, write an `ABSENT` row with `is_auto_marked = true` for every enrolled student with no attendance row, then set `finalised_at`.

  This job is not optional. Attendance percentage is present ÷ total; with no row for a no-show, the denominator shrinks and a student who never attends shows 100%. **Without this job the 75% rule — the most important rule in the product — can never fire.**
- PM override on any status, audited with old and new value
- Blocker notes captured at check-in
- Unique `(standup_id, user_id)` — double check-in is idempotent, not an error
- Attendance percentage is **never computed in Java** — it comes from `student_metrics` (feature 16)

**Verify:** Check-in after cutoff records `LATE`. Double check-in creates one row. **A student who never checks in has an `ABSENT` row the next morning and an attendance percentage below 100.** Re-running the job does not double-write.

---

### 14 Assessment Engine

**Endpoints:**

```
POST /api/v1/assessments
GET  /api/v1/assessments?batchId=
POST /api/v1/assessments/{id}/attempts
GET  /api/v1/assessments/attempts/{id}
POST /api/v1/assessments/attempts/{id}/submit
```

**Logic:**

- `MCQ`, `MULTI_SELECT`, `CODE` questions
- Server enforces `duration_minutes`, never the client
- **Correct answers never sent to the client** during an attempt
- Auto-grades `MCQ` and `MULTI_SELECT`. **`CODE` answers are stored ungraded and excluded from the percentage denominator** — `percentage = auto_graded_marks / auto_gradable_marks`. Scoring them zero would fail students on unmarked work and fire `QUIZ_FAILURE` against them.
- An attempt containing `CODE` questions is flagged `PENDING_MANUAL_GRADING`; feature 17's rule ignores those attempts
- Pass baseline from `quizzes.pass_percentage`, default `Constants.QUIZ_PASS_PERCENTAGE`
- `max_attempts` enforced; exceeding returns `409`
- Unsubmitted attempts swept to `EXPIRED` and scored on answers given

**Verify:** Attempt payload contains no correct answers. A quiz that is half `CODE` questions yields a percentage based only on the auto-gradable half, and the attempt is flagged for manual grading.

---

### 15 Developer Content Portal

**Endpoints:**

```
POST /api/v1/projects
GET  /api/v1/projects?difficulty=&domain=&status=
PUT  /api/v1/projects/{id}
POST /api/v1/projects/{id}/assets
POST /api/v1/projects/{id}/challenges
POST /api/v1/projects/{id}/publish
```

**Logic:**

- `DEVELOPER` authors projects with tech stack, difficulty, domain, starter repo, version
- Assets: either an S3 key or an external URL, never both — enforced by `CHECK`
- Bug-fix challenges with broken code, expected behaviour, test script
- `DRAFT → PUBLISHED → ARCHIVED`. Only `PUBLISHED` attaches to a task.
- Version bump creates a new row; previous archived, never overwritten

**Verify:** A `DRAFT` cannot attach to a task. Assets return working presigned URLs subject to ownership checks.

---

### 16 Student Metrics

Migration V9. **This replaces the nested reporting views from the earlier draft of this plan.**

**Schema:**

- `V9__student_metrics.sql` — the `student_metrics` table, unique on `(user_id, batch_id)`, with attendance, task, quiz, assignment-miss, and unsatisfactory-review columns
- Genuine views retained for small admin-facing reads only: `v_batch_velocity`, `v_revenue_monthly`, `v_lead_funnel`

**Logic:**

- `MetricsRefreshJob`, nightly at 01:45 IST — **after** `AttendanceFinalisationJob`, **before** `PipEvaluationJob`. The ordering is load-bearing: metrics computed before absences are written are wrong.
- Three aggregate queries (attendance, tasks, quizzes) plus assignment-window and weekly-review counts, upserted in batches of 500
- Writes a `job_runs` row

**Why a table and not views:** MySQL 8 does not materialize views. Three stacked aggregate views would re-scan `attendance`, `tasks`, and `quiz_attempts` in full on every PIP run, and degrade linearly as history accumulates. A refreshed table turns the nightly cohort read into one indexed query.

**Verify:** A full 5,000-student refresh completes in under 30 seconds. Reading the whole cohort from `student_metrics` is a single indexed query. Job ordering is enforced by cron, and running metrics before finalisation is visibly detectable in the data.

---

### 17 PIP Rule Engine

Migration V10. The core differentiator. **Run `/architect` before this one — it is the most complex feature in the build.**

**Schema:** `V10__pip.sql` — `pip_rules`, `pip_records` (with the `open_user_id` generated column and `blocks_task_pull`), `pip_milestones`

**Endpoints:**

```
GET  /api/v1/pip/me
GET  /api/v1/pip?batchId=&status=
POST /api/v1/pip/{id}/milestones/{milestoneId}/complete
POST /api/v1/pip/{id}/review
GET  /api/v1/pip/rules
PUT  /api/v1/pip/rules/{code}
```

**Logic:**

- `PipEvaluationJob` at 02:00 IST, ShedLock-guarded, writes a `job_runs` row
- **Loads the whole active cohort from `student_metrics` in one query.** A repository call inside the per-student loop is a defect.
- Six evaluators, thresholds read from `pip_rules`, each mapped to a concrete metrics column:

| Rule                | Reads from `student_metrics`         | Default threshold |
| ------------------- | ------------------------------------ | ----------------- |
| `ATTENDANCE_LOW`    | `attendance_percent`                 | < 75%             |
| `PROJECT_DELAY`     | `tasks_overdue_48h`                  | ≥ 1               |
| `ASSIGNMENT_MISSED` | `consecutive_assignments_missed`     | ≥ 2               |
| `QUIZ_FAILURE`      | `quiz_average_percent`               | < 60%             |
| `REVIEW_FAILED`     | `unsatisfactory_reviews`             | ≥ 1               |
| `TASK_ABANDONED`    | `days_since_last_activity`           | ≥ 3               |

- Clears the feature 11 stub: `PROJECT_DELAY` sets `blocks_task_pull = true`
- One open record per user, enforced by the `open_user_id` generated column. A second run does not re-trigger.
- On trigger: `pip_records` (start today, end +15 days), generated `pip_milestones`, notifications to student, PM and HR in `afterCommit`
- `POST /pip/{id}/review` at day 15: clearance requires task completion ≥ 85% and a passed review, **both verified server-side against `student_metrics`** — the PM cannot clear a student who does not meet criteria
- Outcomes `CLEARED` / `TERMINATED` / `REASSIGNED`, all audited
- `PUT /pip/rules/{code}` is `ADMIN` only and audited — thresholds are config, not a deployment

**Verify:** A fixture per rule sitting just above and just below the threshold triggers exactly as expected — including `REVIEW_FAILED` and `ASSIGNMENT_MISSED`, which now have real tables behind them. A second run creates no duplicate. Clearing at 70% completion is rejected. Full cohort evaluation under 60 seconds.

---

### 18 CRM Module

Migration V11.

**Endpoints:**

```
POST /api/v1/leads
GET  /api/v1/leads?status=&agentId=&source=
PUT  /api/v1/leads/{id}/status
POST /api/v1/leads/{id}/activities
GET  /api/v1/leads/targets/me
POST /api/v1/webhooks/whatsapp
```

**Logic:**

- Ingestion from landing page, college, corporate, referral, walk-in
- Dedup via `dedupe_hash` = SHA-256 of normalised email + phone, unique. A duplicate updates the existing lead and logs an activity — never a second row.
- Pipeline `NEW → CONTACTED → DEMO_SCHEDULED → COUNSELLING_DONE → PAYMENT_PENDING → ENROLLED | LOST`. Backward transitions allowed with a logged reason; forward skips are not.
- WhatsApp outbound uses pre-approved template codes outside the 24h session window
- `LOST` requires `lost_reason`; `ENROLLED` links `converted_user_id`
- Targets tracked monthly against `v_lead_funnel`

**Verify:** Same email and phone twice yields one lead and two activities. Skipping a stage returns `409`.

---

### 19 HR Module

Migration V12.

**Endpoints:**

```
POST /api/v1/hr/employees
POST /api/v1/hr/documents
PUT  /api/v1/hr/documents/{id}/verify
POST /api/v1/hr/leaves
PUT  /api/v1/hr/leaves/{id}/decision
POST /api/v1/hr/payroll/generate
GET  /api/v1/hr/payroll?month=
POST /api/v1/hr/letters/{type}
```

**Logic:**

- Employee records for internal staff and internship-track students
- Document verification `PENDING → VERIFIED | REJECTED`, rejection requires a reason. **Uploads scanned for content-type and magic-byte mismatch** — these are KYC and ID documents.
- Leave routed to `reporting_manager_id`; overlapping approved leave returns `409`
- Payroll: base salary, or `hourly_rate × session_hours` for trainers, minus deductions. Unique `(employee_id, period_month)` — a month cannot be generated twice.
- All money `BigDecimal` / `DECIMAL(12,2)`
- Payslip PDF to S3, key on the record
- Letters: offer, internship, experience, relieving. Experience and relieving require `GRADUATED` or a clean `EXITED` — never a terminated student.

**Verify:** Regenerating a month returns `409`. Overlapping leave rejected. A terminated student cannot receive an experience letter.

---

## Phase 3 — Completion, Integration and UAT

**Sep 05 – Sep 11 · 5 days · 5 features**

---

### 20 Certificate Engine, Graduation and Public Verification

Migration V13. **Run `/architect` before this one.**

**Endpoints:**

```
POST /api/v1/batches/{id}/students/{userId}/graduate
POST /api/v1/certificates/issue
GET  /api/v1/certificates/me
GET  /api/v1/certificates/verify/{code}     ← public, unauthenticated
POST /api/v1/certificates/{id}/revoke
```

**Logic:**

- **Graduation sign-off (FRS `MSH-FR-PM-08`) is implemented here.** The PM sets `batch_students.status` to `GRADUATED` with `graduated_at` and `graduated_by`. Nothing else in the system sets this status, and certificate issuance requires it — without this endpoint no certificate can ever be issued.
- Issuance requires `GRADUATED`, no open `pip_records` row, all sprints closed
- `certificate_number` = `MSH-CERT-{YYYY}-{seq}`, unique
- `verification_code` = 12-char cryptographically random, unique
- QR encodes `{APP_BASE_URL}/verify/{verification_code}` — never the certificate data
- PDF generated once, uploaded to S3, key persisted. Never regenerated on download.
- Public verification returns only holder name, batch, type, issue date, validity. **Never email, phone, scores, or PIP history.**
- Revoked → `valid: false` with revocation date, never a `404`

**Verify:** QR resolves publicly with no auth. A student with an open PIP cannot be issued. A student never graduated cannot be issued. Revoked reports invalid.

---

### 21 BA and Client Portal

Migration V14.

**Endpoints:**

```
POST /api/v1/clients
POST /api/v1/clients/projects
GET  /api/v1/clients/projects/{id}/progress
POST /api/v1/ba/documents
PUT  /api/v1/ba/documents/{id}/approve
POST /api/v1/ba/allocations
```

**Logic:**

- Clients submit scope; BA converts to `requirement_documents` (BRD/SRS/FRS/user story) with versioning
- `DRAFT → IN_REVIEW → APPROVED`, approval restricted to `BUSINESS_ANALYST` and `ADMIN`
- CLIENT users are provisioned by `ADMIN` — a client cannot self-register
- Resource allocation maps students and batches to a client project
- `GET /clients/projects/{id}/progress` returns burndown and milestone completion for **that client's project only** — never another client's data, another batch, or any individual student's PIP status or scores

**Verify:** A client requesting another client's project gets `403`. Progress contains no student names or scores.

---

### 22 Admin Metrics and Exports

**Endpoints:**

```
GET  /api/v1/admin/metrics/overview
GET  /api/v1/admin/metrics/revenue?from=&to=
GET  /api/v1/admin/users?role=&status=
PUT  /api/v1/admin/users/{id}/status
PUT  /api/v1/admin/users/{id}/roles
PUT  /api/v1/admin/plans/{id}
GET  /api/v1/admin/audit?entityType=&userId=&from=
POST /api/v1/admin/exports/{report}
```

**Logic:**

- KPIs read from `student_metrics`, `v_revenue_monthly`, `v_lead_funnel`, `v_batch_velocity` — never recomputed in Java
- Overview cached 5 minutes
- **Suspension and role change increment `token_version`**, so revocation is immediate rather than nominal
- Plan and pricing configuration at runtime, cache evicted on write
- Audit log read-only through the API, and read-only at the grant level
- **Exports and heavy metrics reads target the read replica**, so a 50,000-row XLSX never touches the primary
- Exports run `@Async` via `SXSSFWorkbook`, delivered by presigned URL above 1,000 rows

**Verify:** Non-admin gets `403` everywhere. **Suspending a user invalidates their token within a second, verified by an immediate second request with the old token.** A 50,000-row export completes without exhausting heap and issues no queries against the primary.

---

### 23 Audit, Hardening and Performance

Migration V15.

**Logic:**

- `AuditLogService` wired into every financial, grade, role, and PIP status mutation
- `V15__indexes_constraints.sql` — remaining `CHECK` constraints and indexes. **This migration runs against populated tables and is the one most likely to lock in production.** Test it against a restored production-sized dump, with `ALGORITHM=INPLACE, LOCK=NONE`, not against an empty Testcontainer.
- OWASP pass: parameterised queries verified, output encoding, rate limiting confirmed
- Security headers: HSTS, `X-Content-Type-Options`, `X-Frame-Options`, CSP
- **Connection pool load-tested and `DB_POOL_MAX` set from evidence.** Virtual threads let thousands of requests queue on the pool without raising throughput — the pool is the ceiling, not the thread model.
- N+1 audit across every list endpoint with `hibernate.generate_statistics`
- Slow query log reviewed, missing indexes added
- Load test 5,000 concurrent reads, target p95 ≤ 200ms
- PIP pipeline profiled end to end against a full cohort

**Verify:** No endpoint issues more than one query per logical read. `UPDATE audit_logs` fails at the database level. Load test meets the target with a documented pool size. V15 applies to a production-sized dataset without a blocking lock.

---

### 24 Integration Testing and UAT

**Logic:**

- Testcontainers MySQL + Redis suite covering every controller
- One `403` test per role-restricted endpoint group
- Webhook idempotency: identical event twice → one subscription, one invoice
- PIP rule test per rule, at and around each threshold
- **Every row in the Open Stubs table confirmed cleared**
- End-to-end UAT scenarios:
  1. Test payment enrols, invoices, and allocates a batch
  2. GitHub PR submission reaches the PM review queue with verified metadata
  3. A student who stops attending is auto-marked `ABSENT`, drops below 75%, and is triggered into PIP on the next nightly run
  4. Certificate QR resolves to a valid public verification response
- OpenAPI spec published and confirmed by the frontend track
- Staging deploy, production migration dry run against a restored dump, **restore drill repeated**

**Verify:** `mvn verify` green. All four UAT scenarios pass on staging. Restore drill recovers to a chosen timestamp.

---

## Feature Count

| Phase                                     | Days   | Features |
| ----------------------------------------- | ------ | -------- |
| Phase 1 — Foundation                      | 5      | 8        |
| Phase 2 — Core Feature Implementation     | 7      | 11       |
| Phase 3 — Completion, Integration and UAT | 5      | 5        |
| **Total**                                 | **17** | **24**   |

---

## Tracked Stubs

Order is fixed, so some features need something a later one provides. Each is a deliberate stub, tracked in `progress-tracker.md`, and **all must be cleared before feature 24.**

| Stub                                          | Left in | Cleared in |
| --------------------------------------------- | ------- | ---------- |
| `BatchAllocationService.allocate()` from webhook | 07    | 10         |
| Invoice PDF written locally, not to S3        | 07      | 08         |
| PIP `blocks_task_pull` check on task pull      | 11      | 17         |

---

## Risk

24 features, 17 days, one developer, no slack. Risk accepted explicitly.

**Most likely to overrun:** 17 (PIP engine — six rules, scheduler, 15-day lifecycle), 07 (payments — two gateways, sandboxes fail slowly), 02 (schema plus DR — larger than it looks), 19 (HR — four features wearing one number).

**Leading indicators you are behind:**

- Phase 1 not complete by Aug 26 → behind on day 5, not day 12
- Feature 16 not started by Sep 1 → the PIP engine gets rushed, and it is the differentiator
- Any feature over 2 days → stop and run `/architect`; the blocker is usually an undecided design question

**Cut order, least damage first:** 21 (BA/client) → 15 (developer portal; seed projects directly) → 22 exports only, keep metrics → 14 reduced to MCQ only → 05 (OAuth; password auth works, but `github_username` must then be entered manually in feature 09).

**Never cut:** 02 (schema + DR), 07 (payments), 10 (allocation), 12 (submission), 13 (attendance finalisation), 16 (metrics), 17 (PIP), 20 (certificates), 23 (hardening), 24 (UAT).

**Non-negotiable under any time pressure:** every migration, `@PreAuthorize` on every endpoint, webhook signature verification and idempotency, the attendance finalisation job, and the backup configuration in feature 02. Skipping these does not save time — it moves the cost somewhere more expensive.
