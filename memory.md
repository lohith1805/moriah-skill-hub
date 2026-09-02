# Memory — Moriah Skill Hub Backend

Last updated: 2026-09-02 (session 3: filling frontend↔backend **Part B backend gaps**, 4-endpoint
test cadence. Branch `fe-integration-invite-approval` was merged to `main` (`80c4884`, ff-only)
at the start. 28 new endpoints across 7 gap items shipped and committed to `main`, each its own
commit, `mvn verify` GREEN at two checkpoints — 444 unit + 267 integration, 0 failures.)

## Session 3 — Part B gaps done (all on `main`, all with unit tests)

- **B1.5 notifications feed** (`466ff3f`) — `GET/PUT /api/v1/notifications` (+`/unread-count`,
  `/read-all`). `V20` adds `notifications.read_at`. Feed scoped to `IN_APP` channel.
  `NotificationFeedService` in `common/notification/`.
- **B1.12 admin coupons** (`765a7ec`) — `GET/POST/PUT/DELETE /api/v1/admin/coupons`. New
  `payment/CouponAdminService` (separate from checkout-path `CouponService`). DELETE = deactivate.
  New `ErrorCode.COUPON_CODE_TAKEN`.
- **B1.13 plan create/delete** + **B1.17 admin user detail/edit** (`8e1afff`) —
  `POST/DELETE /api/v1/admin/plans` (via `EntitlementService`, evicts `plans`/`planCodesById`
  caches), `GET/PUT /api/v1/admin/users/{userUuid}` (`AdminUserDetailResponse`; profile fields
  only, no token_version bump). New `ErrorCode.PLAN_CODE_TAKEN`.
- **B1.11 admin transactions + refunds** (`fb2aa5f`) — `GET /api/v1/admin/payments` (+`/summary`,
  `/{gatewayOrderId}`, `POST /{gatewayOrderId}/refund`). Added `RazorpayService.refund` /
  `StripeService.refund`. `AdminPaymentService.refund` is **not `@Transactional`** (outbound call
  rule), sets `REFUNDED` optimistically — the existing refund webhook no-ops on a already-REFUNDED
  row. New `ErrorCode.PAYMENT_NOT_REFUNDABLE`.
- **B1.6 Resource Library** (`f7c5250`) — new `resource/` module, `V21` `learning_resources`
  (link-only). `GET/POST/PUT/DELETE /api/v1/resources`. Curator roles = TRAINER_PM/DEVELOPER/
  BUSINESS_ANALYST/ADMIN; edit requires creator-or-ADMIN.
- **B1.4 Video Lessons** (`541c072`) — new `learning/` module, `V22` `video_lessons` +
  `lesson_progress`. 8 endpoints under `/api/v1/lessons` (CRUD + `/modules`, `/me/progress`,
  `POST /{id}/progress`). Curator roles = DEVELOPER/TRAINER_PM/ADMIN. `watchedSeconds` never
  rewinds; `completedAt` stamped once. Link-only; **per-lesson quiz still TODO**.

- **B1.15 part 2 — bug-challenge list/detail/update/delete** (`c25465f`'s predecessor, actually
  its own commit) — `GET /api/v1/projects/{id}/challenges`, `GET/PUT/DELETE /api/v1/challenges/{id}`
  on `ChallengeController`. DEVELOPER/ADMIN; edit/delete need creator-or-ADMIN + non-archived
  project. New `ErrorCode.BUG_CHALLENGE_NOT_FOUND`. **Question-bank half of B1.15 NOT done** (new
  tables, deferred).
- **B1.16 — developer client-requirements review** (`c25465f`) — `V23` adds
  `requirement_documents.dev_reviewed_at`/`dev_reviewed_by`. `GET /api/v1/ba/documents` (BA list,
  new), `GET /api/v1/dev/requirement-documents` + `/{id}` + `POST /{id}/acknowledge`
  (`DevRequirementController`). New `RequirementDocumentDetailResponse` (adds `content`).
- **B1.7 — Lead-Gen Campaigns** (committed after clean-verify recovery) — `V24` `lead_campaigns`.
  `GET/POST/PUT/DELETE /api/v1/leads/campaigns` (`LeadCampaignController`, LEAD_GEN/ADMIN). DELETE
  → status CANCELLED. New `ErrorCode.LEAD_CAMPAIGN_NOT_FOUND`.
- **B1.14 — BA Meetings** — `V25` `ba_meetings` (title, agenda, optional client_project_id,
  scheduled_at, duration, location, status, minutes). `GET/POST/PUT/DELETE /api/v1/ba/meetings`
  (`BaMeetingController`, BUSINESS_ANALYST/ADMIN). DELETE → status CANCELLED.

- **B1.8 — Student Interviews** (`ac13b26`) — `V26` `student_interviews`. `POST /api/v1/interviews`
  + `GET` (staff, `?status/?type/?studentUuid`) + `GET /me` (STUDENT) + `PUT /{id}` +
  `DELETE /{id}` (→ CANCELLED). New `interview/` module. `ErrorCode.INTERVIEW_NOT_FOUND`.
- **B1.9 — Client Talent Pool + recruitment requests** (`18ff04b`) — `V27` `recruitment_requests`.
  `GET /api/v1/talent-pool` (browse profiles; new `UserProfileRepository.searchTalentPool`),
  `POST /api/v1/recruitment-requests` (CLIENT → PENDING), `GET /api/v1/recruitment-requests`
  (CLIENT own / ADMIN+HR all — privilege from JWT roles), `PUT /{id}/status` (ADMIN/HR
  approve/reject). New `talent/` module. Resume access deliberately NOT wired (needs an
  OwnershipGuard branch). `ErrorCode.RECRUITMENT_REQUEST_NOT_FOUND`.
- **B1.10 slice 1 — HR Exit Management** (`64a5257`) — `V28` `employee_exits`.
  `POST /api/v1/hr/exits` + `GET` + `PUT /{id}` + `POST /{id}/complete` (HR_MANAGER/ADMIN).
  `complete` flips `employees.status` (EXITED / TERMINATED for `ExitType.TERMINATION`) + stamps
  `date_of_exit`. **Onboarding + disciplinary (rest of B1.10) NOT done.**
  `ErrorCode.EMPLOYEE_EXIT_NOT_FOUND`.

**Migrations added this session: V20–V28.** Next unused = **V29**.
**Verify status:** last FULL green `mvn clean verify` was at **B1.14** (`ef5c437` state): 465 unit
+ 267 integration, 0 failures. B1.8/B1.9/B1.10 (V26/V27/V28) are unit-tested only (488 unit
tests green) — a full verify was attempted but the **integration phase hung ~22 min with no
output and had to be killed** (host resource exhaustion — orphan JVMs, thrashing; NOT a code
defect). The three new migrations are plain additive `CREATE TABLE` in the exact shape of
V21/V24/V25 which passed. **A clean `mvn clean verify` still needs to be run for B1.8–B1.10 when
the machine is quiet** (close the VS Code Java language server + other JVMs first).

## `taskkill //F //IM java.exe` is TOO BROAD — it also kills the user's VS Code redhat.java
language server (it auto-restarts, but rude). Kill maven JVMs by PID / by `CommandLine -like
'*Adoptium*'` instead.
(One scare mid-session: a background `verify` collapsed with a flood of "connection closed" +
`bash fork: Resource temporarily unavailable` + my log file hitting a size cap — purely host
resource exhaustion, NOT a code bug. Re-run clean = green. Lesson: run `verify` ALONE, filter
its log with `grep --line-buffered`, never unbounded `tee`.)

**`README.md` has UNCOMMITTED local edits (not from this session's commits) that paste Razorpay
TEST api keys + seeded-user rows.** Left untouched. Should be moved out of the tracked file.

**Part B gaps still open:** B1.10 onboarding + disciplinary (exit done), B1.15-part1 assessment
question bank (needs new tables), B1.18 installment plans (likely skip), B1.4 per-lesson quiz.
Also noticed in passing: `GET /api/v1/hr/employees` (list) does not exist — `EmployeeController`
has only `POST`. Small gap worth a follow-up.

**Session-3 endpoint tally: ~53 across 14 gap items** (B1.4, B1.5, B1.6, B1.7, B1.8, B1.9,
B1.10-exit, B1.11, B1.12, B1.13, B1.14, B1.15-part2, B1.16, B1.17), each its own commit on
`main`, all unit-tested (488 green). Frontend integration (Part A) still not started.

## What was built

Backend = Spring Boot 3.5 / Java 21 / MySQL 8 / Redis 7, modular monolith at
`C:\Users\ADMIN\Desktop\Moraih Backend\Moraih Backend` (**local-only git repo, no remote**).
The parent folder `C:\Users\ADMIN\Desktop\Moraih Backend\` also contains
`moriah-skill-hub-updated/…` (the React 19 + Vite frontend) and `frontend-backend-gap-report.md`
— **those two live outside the repo**.

**On `main` (commits `27d5e50` then `77e1b70`, already merged):**
- **2026-08-31 audit** — 4 parallel sub-agents; fixed every Critical + High + 13 Medium/Low.
  Migrations `V17`/`V18` (indexes). Notable: OAuth2 authz cookie → signed JSON (not Java
  serialization); webhook idempotency `claim()` gap-lock-free + reconciliation job; `JobChainGuard`
  interlock for the nightly metrics/PIP chain; CIDR-aware `ClientIpResolver` + per-bearer-token
  rate-limit buckets; bounded async executors + client timeouts; `JwtAuthFilter` reads a
  lightweight `AuthUserView` projection; export streaming + CSV-injection sanitisation; expired-
  token reaper; `application.yml` hardening. Full log in `docs/audit-2026-08-31.md`.
- **Doc pipeline** — `docs/openapi.json` (exported from a running app's `/v3/api-docs`) is the
  single source of truth for `scripts/gen-api-doc.py`, `gen-project-ref.py`, `gen-postman.py`;
  `gen-callgraph.py` statically scans `src/`. Prose lives in `scripts/project-ref-narrative.md`
  + `scripts/project-ref-audit-appendix.md`. Regen order + caveats in `scripts/README.md`.
  Generated: `docs/API-Documentation.md`, `docs/PROJECT-REFERENCE.md`,
  `docs/postman/moriah-skillhub-postman.zip` (role-aware: per-role login requests each storing a
  `{{<role>AccessToken}}`, per-request auth pinned by `@PreAuthorize`, admin/HR 2FA sub-folders
  with a CryptoJS TOTP pre-request script). Hand-written docs (no generator): `webhooks.md`,
  `testing-flow.md`, `required-integrations.md`, `audit-2026-08-31.md`, `runbook-dr.md`.
- **Observability** — `MdcLoggingFilter` (per-request `X-Request-Id` → MDC), `JwtAuthFilter` adds
  `userId` to MDC. `application-prod.yml`: ECS-JSON console + rotating file `logs/skillhub.log`
  (50 MB/daily, 30-day/3 GB cap). `application-dev.yml`: dropped `show-sql` (dup of the
  `org.hibernate.SQL` logger), added bind-param TRACE. `SecurityConfig.PUBLIC_PATHS` also lists
  the bare `/v3/api-docs` + `/swagger-ui`.
- Context file `docs/context-2026-09-02-logging-and-postman.md` records all of the above.

**On branch `fe-integration-invite-approval` (4 commits, `mvn clean verify` GREEN, NOT merged):**
- `ced9ed0` **fix: restore `docker/mysql-init/01-users.sql`** — it was wrongly deleted in
  `27d5e50` ("superseded"); it is still mounted by `docker-compose.yml:27` AND
  `IntegrationTestBase` to create `moriah_app` and give `moriah_migrate` `GRANT OPTION`. Its
  absence broke all 267 ITs (context load) and a fresh `docker compose up`.
- `0b47bc5` **chore(dev): dev seed → Flyway repeatable migration** —
  `db/testdata/R__dev_seed_data.sql` replaces `DevDataLoader.java` (an `ApplicationRunner` that
  swallowed errors into one WARN). `application-dev.yml` adds `classpath:db/testdata` to
  `spring.flyway.locations`; prod/test keep the default so sample data never reaches them. Seeds
  10 accounts (`admin@ pm@ dev@ sales@ hr@ ba@ client@ student1@ student2@ student3@ moriah.test`,
  password `Password123!`) + one batch/sprint/tasks/lead/employees, all idempotent.
- `70dd6b5` **feat: staff invite + client self-registration approval** (FE gap B1.1–B1.3).
  Migration `V19` adds `UserStatus` `INVITED` / `PENDING_APPROVAL` / `REJECTED` (extends
  `chk_users_status`) + `staff_invite_tokens` table (mirrors `email_verification_tokens`).
  7 new endpoints:
  `POST /api/v1/admin/users` (invite staff → `INVITED`, emails link, 7-day TTL, rejects
  STUDENT/CLIENT roles), `POST /api/v1/admin/users/{uuid}/resend-invite`,
  `POST /api/v1/auth/accept-invite` (`{token,password}` → set password, `INVITED→ACTIVE`,
  auto-login; a 2FA challenge for an invited ADMIN/HR_MANAGER),
  `POST /api/v1/auth/register/client` (`→ PENDING_APPROVAL` + CLIENT role + `INACTIVE` clients
  row), `GET /api/v1/admin/client-requests?status=PENDING_APPROVAL|REJECTED`,
  `POST /api/v1/admin/client-requests/{uuid}/approve|reject`.
  `AuthService.login` rejects the 3 new statuses with `ACCOUNT_INVITE_PENDING` /
  `ACCOUNT_PENDING_APPROVAL` / `ACCOUNT_REGISTRATION_REJECTED`. New: `StaffInviteToken` + repo,
  `ClientRegistrationService`, `ClientApprovalService` (in `admin/`), `ClientRequestController`,
  5 DTOs. `STAFF_INVITE_URL_TEMPLATE` added (`AuthLinkProperties`, `application*.yml`,
  `required-integrations.md`). 11 new unit tests. Docs regenerated (openapi.json now 107
  endpoints).
- `0ab563a` **test: relative dates in `BatchFlowIT` / `SprintTaskFlowIT`** — see Problems solved.

## Decisions made

- **Frontend↔backend integration — 3 decisions locked** (see `frontend-backend-gap-report.md`):
  1. **Option A — build it in the backend.** Staff invite + client self-register + ADMIN-only
     approval queue. Done (branch above). Deliberately re-opens `build-plan.md` feature 21's
     "a client cannot self-register" — noted in `V19` header + Javadoc. `POST /api/v1/clients`
     (ADMIN-provisioned client) is unchanged and still works.
  2. **Hybrid (FE-only, no backend change).** Keep `register → verify-email → login`; the FE
     adds a route guard forcing `/student/subscription` (checkout) before the dashboard unlocks.
     Do NOT build a combined register-with-payment endpoint.
  3. **Drop multi-role (FE-only).** Backend keeps emitting the JWT `roles` array; the FE
     collapses to one primary role by priority
     `ADMIN > TRAINER_PM > BUSINESS_ANALYST > HR_MANAGER > LEAD_GEN > DEVELOPER > CLIENT > STUDENT`.
     No backend endpoint requires two roles at once; multi-role is admin-assign-only.
- **The frontend is a 100% mock prototype.** `src/services/apiClient.js` has `USE_MOCKS = true`;
  all ~13 `*Service.js` use `mockRequest()` against `mockData.js` + ~40 `localStorage` keys.
  Zero backend endpoints wired. The FE `LoginResponse` has NO user object — must call
  `GET /users/me` after login. Role codes differ (`trainer`↔`TRAINER_PM`,
  `lead_generator`↔`LEAD_GEN`, `hr`↔`HR_MANAGER`); plan `corporate`↔`CORPORATE_PROGRAM`.
- **Dev seed is a Flyway repeatable migration, never a versioned one and never a runner.** The
  `test`/`prod` profiles must keep `spring.flyway.locations` at the default so they never load it.
- **`@Transactional` must never wrap an outbound call** (S3, HTTP) — held from prior sessions.
- **Honesty standard**: never fabricate a passing test number or a claim not backed by a real
  local run (held throughout; e.g. UAT p95 row stays honestly unchecked).

## Problems solved

- **`docker/mysql-init/01-users.sql` deletion broke every integration test.** Restored from the
  initial commit. If ITs ever fail suite-wide with `GRANT command denied to 'moriah_migrate'` in
  `afterMigrate.sql`, this file is missing again.
- **`@FutureOrPresent` date time-bomb.** `BatchFlowIT` / `SprintTaskFlowIT` hardcoded
  `startDate: "2026-09-01"`; `CreateBatchRequest` / `CreateSprintRequest` mark `startDate`
  `@FutureOrPresent`, so every batch/sprint create returned 400 (before the role check) once the
  clock passed that date — 18 failures on 2026-09-02. Fixed: dates now `LocalDate.now().plusDays(…)`
  as consecutive relative weeks. Watch for other hardcoded future-dates elsewhere in the ITs.
- **`GET /v3/api-docs` → 401** — no handler (prod disables springdoc, or the path wasn't in
  `PUBLIC_PATHS`) → servlet ERROR-dispatch to `/error` → not public → the `UNAUTHENTICATED`
  envelope (charset ISO-8859-1). Real 404s come from `GlobalExceptionHandler` (charset UTF-8).
- **`mvn spring-boot:run` failed on `${DB_HOST}`** — nothing loads `.env` for a host-run JVM.
  Fixed with `spring.config.import: "optional:file:./.env[.properties]"` in `application-dev.yml`
  (dev only; `test` uses Testcontainers, `prod` uses real env).
- **`POST /api/v1/auth/2fa/verify` 500** — request sent as `Content-Type: text/plain`. Not a 2FA
  bug; the body was never read. Postman: Body → raw → JSON.
- **springdoc path matching** — `/v3/api-docs/**` did not reliably cover the bare `/v3/api-docs`;
  `PUBLIC_PATHS` now lists both forms + `.yaml` + bare `/swagger-ui`.
- **Machine is heavily load-sensitive** — running 2+ `mvn verify` concurrently makes perf-budget
  ITs (`PipEvaluationProfilingIT`) flake and slows every IT ~5×. Run one build at a time.

## Current state

- **`main` = `77e1b70`** — audit + doc pipeline + logging. Green.
- **Branch `fe-integration-invite-approval`** (4 commits above) — Option A + the two fixes.
  **`mvn clean verify` = BUILD SUCCESS, 399 unit + 267 integration, 0 failures.** NOT merged.
- Working tree: only `memory.md` (this file) is uncommitted.
- `openapi.json` / `API-Documentation.md` / `PROJECT-REFERENCE.md` / Postman collection are all
  regenerated and include the 7 new endpoints.

## Next session starts with

1. **Merge the branch:** `git checkout main && git merge --ff-only fe-integration-invite-approval`
   (then optionally `git branch -d fe-integration-invite-approval`).
2. Then the actual **frontend↔backend integration**, driven by
   `C:\Users\ADMIN\Desktop\Moraih Backend\frontend-backend-gap-report.md`:
   - **Part A (frontend):** flip `USE_MOCKS=false`, add `VITE_API_BASE_URL`, rewrite every
     `*Service.js` to call `apiClient.request`; unwrap the `ApiResponse`/`PageResponse` envelope;
     real auth (access+refresh + 401-refresh interceptor, email-only login, `GET /users/me` after
     login, real 2FA challenge flow, OAuth redirect); role/status/plan enum mapping; multipart
     uploads; Vite dev proxy.
   - **Part B (backend gaps still open):** in-app notifications feed, resource library, video
     lessons / self-paced learning, lead-gen campaigns, student interviews, client talent pool +
     recruitment requests, HR exit/onboarding/disciplinary, admin transactions+refunds list,
     admin coupon CRUD (`CouponService` exists, no controller), plan create/delete, BA meetings,
     assessment question bank + bug-challenge list/update/delete.

## Open questions

- Merge the branch now, or keep iterating on it first?
- Part B priority order — which missing endpoints does the frontend need for its first
  shippable screens? (gap report suggests notifications → resource library → video lessons →
  transactions list, then the rest.)
- `frontend-backend-gap-report.md` lives outside the repo (workspace parent). Should it (and the
  frontend) be brought under version control?
