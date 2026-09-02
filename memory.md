# Memory — Moriah Skill Hub Backend

Last updated: 2026-08-28 (Features 20–24 complete. **The full 24-feature build is done**, 8 days ahead of the Sep 11 target. Session ran fully autonomously per standing instruction and stopped after feature 24 as directed.)

## What was built

**Features 01–19.** ✅ Complete. (See prior memory entries / `context/progress-tracker.md` decision log.)

**Feature 20 — Certificate Engine, Graduation and Public Verification (V14).** ✅ Complete, `/review`'d, committed `da141bc`. New `certificate/` package: `Certificate`/`CertificateType` entity, `CertificateService`, `GraduationService`, `QrCodeService`, `CertificateController`, `VerificationController` (public, unauthenticated verify endpoint). `BatchService.graduate`/`hasGraduatedFromBatch` added. Clears the long-standing portfolio Open Stub (`completedProjects`/`issuedCertificates` on `GET /portfolio/{slug}`).

**Feature 21 — BA and Client Portal (V15).** ✅ Complete, `/review`'d (0 findings), committed `acf46dd`. New `client/` package: `Client`/`ClientProject`/`RequirementDocument`/`ResourceAllocation`, `ClientController` (`/clients/**`) + `BaController` (`/ba/**`). Client portal logins are ADMIN-provisioned only, reusing the existing password-reset flow (never a bespoke token/plaintext password).

**Feature 22 — Admin Metrics and Exports.** ✅ Complete, `/review`'d (0 findings), committed `e9aa922`. New `admin/` package (metrics overview, revenue, user/role/status management, audit query, async XLSX exports via SXSSFWorkbook). New `ReplicaDataSourceConfig` wires the previously-unused `mysql-replica` docker service for real. No new migration — every underlying view/table already existed.

**Feature 23 — Audit, Hardening and Performance (V16).** ✅ Complete, `/review`'d (0 findings), committed `0a17350`. Cross-cutting pass, not new functionality: V16 migration (1 CHECK gap + 8 real indexes), closed real audit-logging gaps in `payment/` (was fully unaudited) and `CodeReviewService.create`, fixed a real N+1 (`TaskSubmissionRepository.search`), added security headers (HSTS/nosniff/frame-deny/CSP — none existed before), resolved the deferred `@Transactional`-wraps-S3-upload issue across `CertificateService`/`InvoiceService`/`PayrollService`.

**Feature 24 — Integration Testing and UAT.** ✅ Complete, `/review`'d (0 findings), committed `bd2b977`. Closed real test-coverage gaps (Checkout/Subscription/Pip/Standup controllers had zero HTTP coverage; 3 role-groups had no wrong-role 403 test; one PIP rule boundary untested). Added `NightlyChainUatFlowIT` (genuine end-to-end 01:30→01:45→02:00 chain) and `MetricsAndPipChainProfilingIT`. Actually re-ran the local restore drill (fresh containers, fresh dump/binlog replay — 3 rows, matched). Exported `docs/openapi.json`.

**`mvn verify`: 385 unit tests + 269 integration tests, 0 failures, genuinely green** as of the end of this session.

## Decisions made

- **Standing instruction governing this entire session**: decide autonomously per `context/build-plan.md`/`context/AGENTS.md` without asking design questions; run `/review` after each feature; **build through feature 24, then stop.** This instruction is now fully discharged — a fresh session needs new explicit direction to do anything further on this project.
- **Git repository initialized this session** (none existed before, at all). `.env` correctly excluded. `/review` now uses real `git diff` for every feature instead of the old parallel-agent-file-list workaround — that workaround is obsolete, don't reintroduce it.
- **Workflow pattern**: the orchestrating session did all `/architect`-equivalent research/decision-making itself (reading build-plan.md/architecture.md/existing conventions, resolving every open design question up front), then delegated the actual implementation + `mvn verify` to a background general-purpose Agent per feature, with a long, fully-decided, self-contained prompt. After each feature: reviewed the real `git diff` directly, decided any fixes autonomously, committed. This worked well — every feature shipped with 0-1 findings on review, and several real bugs were caught by the delegated agents themselves before ever reaching review (see Problems solved).
- **Honesty standard held throughout, deliberately, on every "can't fully test this locally" item** (load testing, production-scale migration dry run, staging deploy, restore drill scope) — never fabricated a passing number or a claim that couldn't be backed by a real local test. UAT Scenario row 9 (p95 ≤ 200ms) is explicitly left unchecked in `progress-tracker.md` because the real measured number (~6s under local load) doesn't meet it. This precedent should hold for any future work on this project too.
- **`@Transactional` must never wrap an outbound call** (S3, HTTP) — this was violated 4 times independently across features 07/08/19/20 before feature 23 caught and fixed all of them at once. Watch for this in any new service that renders-then-uploads a file.

## Problems solved

- **Two real Spring auto-configuration traps (feature 22)**: (1) a second `DataSource`/`JdbcTemplate` `@Bean` silently suppresses the primary connection pool — `@ConditionalOnMissingBean` matches by type, not qualifier. Fixed by never registering the replica pool as its own bean, and explicitly re-declaring `@Primary` on the default `JdbcTemplate`. (2) `@Async` self-invocation within the same class silently runs synchronously (proxy bypass) — fixed by splitting into a separate bean (`ExportGenerationService`). Both documented in that code's own Javadoc — read it before adding a third connection pool or another `@Async` method to this codebase.
- **A subtle HSTS bug (feature 23)**: Spring Security's default HSTS header writer only fires when `HttpServletRequest.isSecure()` is true, but this project has no `server.ssl.*` configured anywhere — meaning the realistic deployment shape is TLS terminated upstream, so the default would have silently never sent the header. Fixed with an unconditional `requestMatcher(AnyRequestMatcher.INSTANCE)`.
- **A real cross-test-class pollution bug (feature 20)**: two `CertificateFlowIT` fixtures left permanently-`ACTIVE` `batch_students` rows that corrupted `MetricsRefreshFlowIT`'s deliberately-unscoped whole-platform cohort scan, since Testcontainers MySQL is a single static-singleton shared across the whole `mvn verify` run. Fixed by not inserting the row when the test doesn't actually need a specific status. Any future `*FlowIT` fixture that sets `batch_students` to `ACTIVE`/`ON_PIP` and doesn't transition it away by test end will reproduce this.
- **A genuine test-timing flake (feature 24)**: `NotificationQueueIT`'s 10-second poll budget wasn't always enough once feature 24's own new tests (300 PIP-triggered notification enqueues in `MetricsAndPipChainProfilingIT`) added real extra traffic to the shared Redis queue. Confirmed via isolated re-runs it wasn't a logic bug; fixed by widening the poll budget to 30s.
- **Recurring build-agent friction**: background agents kept ending their turn while `mvn verify` was still running in a background shell (or a self-imposed wait-loop), producing incomplete reports — happened at least once on every one of features 20-24. Fix was always the same manual resume message: tell the agent explicitly to call Bash for `mvn verify` with `run_in_background` omitted/false and an explicit `timeout` ≥ 480000ms. Twice this also coincided with a genuine session usage-limit interrupt mid-build; in both cases work-in-progress survived on disk and resuming the same agent picked up cleanly.

## Current state

**All 24 features complete, committed, and `/review`'d clean.** `context/progress-tracker.md` is fully up to date — Current Status, full Progress checklist, Migration Ledger through V16, Open Stubs table (all 5 rows cleared), UAT Scenarios table (9/10 rows checked with real proof pointers, row 9 honestly unchecked), Database Resilience Checklist, and a complete decision log for every feature this session touched.

Git log: `bfa045b` (01-19 snapshot) → `da141bc` (20) → `acf46dd` (21) → `e9aa922` (22) → `0a17350` (23) → `bd2b977` (24). Nothing uncommitted. No PR/push happened — everything is local commits on `main`.

## Next session starts with

**Nothing is queued.** The build is complete. If the user wants to continue working on this project, likely directions:
1. A genuinely new feature beyond the original 24-feature plan.
2. Addressing one of the honestly-flagged gaps: real load testing against the p95 target, a production-scale migration dry run, or an actual staging deployment — all three need infrastructure that doesn't exist yet in this project.
3. A bug fix or refinement to something already shipped.

In any of these cases: run `/remember restore` first, then read `context/progress-tracker.md`'s Current Status and (for gap-related work) its UAT Scenarios table and feature 23/24 decision-log entries before doing anything.

## Open questions

- `docs/openapi.json` (feature 24) is a one-time static export — it will drift from the live `/v3/api-docs` the moment any endpoint changes, and nothing regenerates it automatically. Worth wiring into a build step if this project ever gets real CI.
- `project-overview.md` still says `/api/v1/webhooks/*` includes `github` — build-plan.md's real spec never built one. Noted across several prior sessions, still open, not blocking.
- No staging environment or deployment target has ever been chosen for this project. Every "verified locally, not against production scale" caveat (V16 migration, load test, restore drill) traces back to this one root fact.
