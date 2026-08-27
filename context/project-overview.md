# Project Overview

## About the Project

Moriah Skill Hub is an enterprise EdTech and Project-Based Learning platform that replaces passive video-watching with a task-driven, sprint-oriented training model. Students subscribe to a plan, get placed into a batch, work real client-simulated projects in 1–2 week agile sprints, submit code through GitHub, get reviewed by a Project Manager, and graduate with a verifiable certificate.

Around that learning core sit three operational systems: an agile project tracker for sprint execution, a fully automated Performance Improvement Plan (PIP) engine that detects underperformance from hard thresholds and drives a 15-day remediation cycle without human bias, and a combined CRM + HR suite covering lead acquisition, onboarding, payroll, and exit management.

**This context covers the backend and database only.** Spring Boot 3.5 on Java 21, MySQL 8.0, Redis. The API is consumed by a separate React frontend built by a different track — no UI, templating, or view logic lives in this codebase.

---

## The Problem It Solves

Bootcamps and colleges produce graduates who have watched hundreds of hours of video and never opened a pull request. There is no attendance discipline, no code review, no deadline pressure, and no early-warning system when a student silently falls behind — by the time anyone notices, the cohort has ended.

Moriah Skill Hub makes the learning process operationally identical to a real engineering job. Students attend standups, pull tasks from a sprint backlog, commit code, and defend it in weekly reviews. The PIP engine watches attendance, delivery, and assessment scores continuously and intervenes automatically at fixed thresholds, so a struggling student is caught in days rather than months.

---

## Scope of This Codebase

| In this codebase                                  | Not in this codebase                      |
| ------------------------------------------------- | ----------------------------------------- |
| REST API for all 8 portals                        | React SPA, components, styling            |
| MySQL schema, migrations, seeders                 | Any HTML, CSS, or JSX                     |
| Business logic, PIP rule engine, scoring          | Client-side routing or state management   |
| Auth, JWT issuance, RBAC enforcement              | Browser session handling                  |
| Payment webhooks, invoice generation              | Checkout UI                               |
| Third-party integrations (GitHub, WhatsApp, S3)   | Frontend API client code                  |
| Scheduled jobs, notifications, audit logging      | Design tokens, UI registry                |
| Certificate PDF generation + public verification  | Certificate viewer screen                 |

The backend is the single source of truth for all business rules. The frontend never computes a match, a threshold, an attendance percentage, or a PIP decision — it only renders what the API returns.

---

## User Roles

Eight personas, enforced by RBAC on every endpoint.

| Role          | Category            | Core Backend Capabilities                                                                              |
| ------------- | ------------------- | ------------------------------------------------------------------------------------------------------ |
| `STUDENT`     | Learner / Intern    | Subscribe, enrol in batch, check in to standups, pull tasks, submit GitHub PRs, take quizzes, view PIP status, download certificate |
| `TRAINER_PM`  | Instructional Lead  | Create batches, plan sprints, assign tasks, log attendance, review code and score 1–10, approve/reject PIP, sign off graduation |
| `DEVELOPER`   | Content Author      | Author projects, upload starter repos and docs, create bug-fix challenges, build question banks       |
| `LEAD_GEN`    | Sales & Marketing   | Ingest leads, move pipeline stages, log calls, dispatch WhatsApp/email, track targets                 |
| `HR_MANAGER`  | Talent & Operations | Onboard staff and students, verify documents, manage leave, run payroll, issue letters                |
| `BUSINESS_ANALYST` | Product & Delivery | Author BRD/SRS/FRS records, map client scope to sprints, plan resource allocation                 |
| `ADMIN`       | Platform Governance | Global user management, plan and pricing config, revenue metrics, audit trails, permission overrides  |
| `CLIENT`      | External Partner    | Submit project requirements, view batch progress on their project, review sprint demos, browse talent |

`ADMIN` bypasses ownership checks but never bypasses audit logging.

---

## Subscription Tiers

Five plans. Prices and entitlements live in the `subscription_plans` table and are configurable at runtime by `ADMIN` — never hardcoded in Java.

| Tier               | Price (INR) | Entitlement Model  | Backend-Relevant Entitlements                                       |
| ------------------ | ----------- | ------------------ | ------------------------------------------------------------------- |
| Starter            | 3,999       | Self-paced         | Video + quiz access only. No batch allocation, no sprints, no PIP.  |
| Professional       | 7,999       | Guided             | Adds assignments, task submission, auto-graded assessments.         |
| Project Based      | 14,999      | Agile Simulation   | Adds batch allocation, sprints, GitHub PR review, standups, PIP.    |
| Internship         | 19,999      | Live Apprenticeship| Adds 1:1 mentor assignment, HR onboarding, experience letter.       |
| Corporate Program  | 29,999      | Executive Fast-Track| Adds client project assignment, placement referral records.        |

Entitlement flags drive authorization. A `STARTER` student calling a sprint endpoint gets `403`, not an empty list.

---

## Core Backend Flows

### Enrolment

```
POST /api/v1/subscriptions/checkout
        ↓
Razorpay/Stripe order created, payment_id stored as PENDING
        ↓
Gateway webhook → POST /api/v1/webhooks/{gateway}
        ↓
Signature verified, idempotency key checked
        ↓
user_subscriptions row activated, invoice PDF generated to S3
        ↓
BatchAllocationService assigns student to open batch matching track + tier
        ↓
Welcome notification dispatched (email + WhatsApp)
```

### Daily Sprint Cycle

```
Student checks in           → attendance row, status PRESENT/LATE by cutoff time
PM opens standup            → standup row, blockers logged per student
Student pulls task          → task.assigned_to set, status IN_PROGRESS
Student submits GitHub PR   → GitHub API verifies branch + PR exists and is open
PM reviews                  → code_reviews row, score 1-10, APPROVED or CHANGES_REQUESTED
Task closes                 → task.status COMPLETED, feeds sprint velocity
```

### PIP Engine

Three nightly jobs in a fixed order — never synchronously inside a request. The ordering is load-bearing: metrics computed before absences are written are wrong.

```
01:30  AttendanceFinalisationJob
         Any enrolled student with no attendance row for a conducted
         standup gets an ABSENT row. Without this the attendance
         denominator only counts students who showed up, and a student
         who never attends reads as 100%.
        ↓
01:45  MetricsRefreshJob
         Recomputes student_metrics for every active student —
         attendance, tasks, quizzes, missed assignment windows,
         unsatisfactory weekly reviews. Upserted in batches of 500.
        ↓
02:00  PipEvaluationJob  (ShedLock-guarded)
         Loads the whole cohort from student_metrics in ONE query,
         runs six rule evaluators in memory
        ↓
Any rule breached → pip_records row created (status TRIGGERED)
  One open record per user, enforced by a generated column
        ↓
Notification dispatched to student + HR + PM
        ↓
15-calendar-day remediation window opens with generated milestones
        ↓
Day 15 → PM records exit review outcome
  Clearance requires ≥85% task completion, verified server-side —
  the PM cannot clear a student who does not meet criteria
        ↓
CLEARED (reinstate) | TERMINATED | REASSIGNED
```

### PIP Trigger Rules

| Rule Code           | Condition                                                   | Severity |
| ------------------- | ----------------------------------------------------------- | -------- |
| `ATTENDANCE_LOW`    | Attendance < 75% over rolling 14-day window                 | HIGH     |
| `PROJECT_DELAY`     | Committed sprint story > 48h past deadline                  | CRITICAL |
| `ASSIGNMENT_MISSED` | 2+ consecutive weekly assignment windows missed             | MEDIUM   |
| `QUIZ_FAILURE`      | Cumulative assessment average < 60%                         | MEDIUM   |
| `REVIEW_FAILED`     | PM records UNSATISFACTORY on weekly code defence            | HIGH     |
| `TASK_ABANDONED`    | No standup log and no task progress for 3 consecutive days  | HIGH     |

Clearance criteria: task completion ≥ 85% and exit review passed. Thresholds live in `pip_rules` config table, not in code.

---

## API Surface

Versioned under `/api/v1`. Grouped by module, not by role — RBAC decides who may call what.

```
/api/v1/auth/*              → register, login, refresh, logout, oauth callback, 2fa
/api/v1/users/*             → profile, portfolio, resume
/api/v1/plans/*             → plan catalogue (public read)
/api/v1/subscriptions/*     → checkout, history, upgrade
/api/v1/webhooks/*          → razorpay, stripe, github, whatsapp
/api/v1/batches/*           → batch CRUD, student allocation
/api/v1/sprints/*           → sprint CRUD, backlog, velocity
/api/v1/tasks/*             → task CRUD, assignment, submission
/api/v1/submissions/*       → GitHub PR submission + verification
/api/v1/reviews/*           → code review, scoring, approval
/api/v1/standups/*          → standup session, attendance check-in
/api/v1/assessments/*       → quiz delivery, attempt, auto-grading
/api/v1/projects/*          → developer-authored projects, assets, challenges
/api/v1/pip/*               → PIP records, milestones, exit review
/api/v1/certificates/*      → issuance + public verification (unauthenticated GET)
/api/v1/leads/*             → CRM pipeline, activities, targets
/api/v1/hr/*                → onboarding, leave, payroll, documents
/api/v1/ba/*                → requirement documents, resource plans
/api/v1/clients/*           → client project submission, progress view
/api/v1/admin/*             → metrics, user management, pricing, audit logs
```

---

## Features In Scope

- JWT auth with refresh rotation, Google + GitHub OAuth2, TOTP 2FA
- RBAC across 8 roles with entitlement checks per subscription tier
- Full MySQL schema with Flyway migrations, reference-data seeders, and a nightly-refreshed `student_metrics` table
- Backup, binlog-based point-in-time recovery, a read replica, and a verified restore drill
- Razorpay + Stripe checkout, webhook verification, idempotent processing, invoice PDF
- Batch and sprint lifecycle management with story points and velocity calculation
- Task assignment, GitHub PR verification via GitHub REST API, code review scoring
- Standup sessions with attendance check-in and configurable late cutoff
- Quiz engine with timed attempts, auto-grading, and 60% pass baseline
- Developer content authoring — projects, starter repos, docs, bug-fix challenges, question banks
- Automated PIP rule engine on an ordered nightly job chain with a 15-day remediation lifecycle
- QR-verifiable certificate generation with a public unauthenticated verification endpoint
- CRM lead ingestion with deduplication, pipeline stages, activity logging, target tracking
- HR onboarding, document verification, leave workflow, payroll computation, letter generation
- BA requirement document repository and resource allocation planning
- Client portal API for project submission and sprint progress visibility
- Admin metrics, pricing configuration, user management, immutable audit log
- Notification dispatch via email (SendGrid/SES) and WhatsApp Cloud API, queued through Redis
- S3-compatible object storage for resumes, assets, certificates, payslips
- OpenAPI 3 spec auto-published via springdoc
- Export endpoints producing XLSX, CSV, and PDF

---

## Features Out of Scope

- Any frontend code, component, or styling
- Live video streaming infrastructure — videos are external links (YouTube unlisted, Vimeo, Loom) or S3 objects
- In-browser code execution sandbox — quiz code answers are stored and graded by unit-test scripts, not executed live in Phase 1–3
- Real-time WebSocket presence or live collaborative editing
- Mobile push notifications
- Multi-tenant / white-label deployment
- Automated placement matching or job board
- AI-generated feedback on student code
- Microservice decomposition — this is a modular monolith
- Elasticsearch or full-text search infrastructure — MySQL `LIKE` and indexed filters only
- GraphQL — REST only despite the FRS mentioning it

---

## Target Users of the API

The React SPA built by the frontend track (Sangeetha, Sai Reddy, Praveen), the payment gateways calling webhooks, GitHub calling PR webhooks, and internal scheduled jobs. There is no public developer API and no third-party API consumer in Phase 1–3.

---

## Success Criteria

- A test payment completes end to end: order created, webhook verified, subscription activated, invoice generated, student allocated to a batch — all within one request cycle plus webhook latency
- Replaying the same gateway webhook twice produces exactly one subscription and one invoice
- A student submitting a GitHub PR URL results in a verified submission appearing on the PM review queue with commit count and PR state populated
- Simulating attendance below 75% causes the nightly PIP job to create a `pip_records` row and dispatch notifications on the next run
- A student who stops attending is auto-marked ABSENT overnight, drops below 75%, and is triggered into PIP on the next nightly run — verifiable end to end
- The metrics refresh plus PIP evaluation for a 5,000-student cohort completes in under 90 seconds, reading a refreshed `student_metrics` table rather than recomputing aggregates per student
- Suspending a user invalidates their access token within one second, not after the 60-minute expiry
- A full dump plus binlogs restores to a chosen timestamp with matching row counts
- Scanning a certificate QR code resolves to a public endpoint returning valid issuance data without authentication
- 95% of read endpoints respond in ≤ 200ms under normal load, with a connection pool size set from load-test evidence rather than assumption
- Every role-restricted endpoint returns `403` for the wrong role, verified by an integration test per endpoint group
- Every financial and grade-affecting mutation writes an `audit_logs` row
- `mvn verify` passes with Testcontainers-backed integration tests against real MySQL
