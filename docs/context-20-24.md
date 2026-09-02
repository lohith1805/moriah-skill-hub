# Context — Features 20–24

Reference document for the final five features of the build (Phase 3 — Completion, Integration and UAT). Built 2026-08-27 → 2026-08-28, each `/architect`-equivalent decision resolved up front, each `/review`'d against the real `git diff`, each committed separately. For the full blow-by-blow decision log, see `progress-tracker.md`'s "Decisions Made During Build" section — this file is the condensed, feature-by-feature reference.

Git commits: `da141bc` (20) → `acf46dd` (21) → `e9aa922` (22) → `0a17350` (23) → `bd2b977` (24).

---

## Feature 20 — Certificate Engine, Graduation and Public Verification (V14)

**Package:** `com.moriah.skillhub.certificate` — `Certificate`/`CertificateType` entity, `CertificateRepository`, `CertificateService`, `GraduationService`, `QrCodeService`, `CertificateController`, `VerificationController`, `CertificateProperties`.

**Endpoints:**
```
POST /api/v1/batches/{id}/students/{userUuid}/graduate   TRAINER_PM (own batch) / ADMIN
POST /api/v1/certificates/issue                            TRAINER_PM (own batch) / ADMIN
GET  /api/v1/certificates/me                                any authenticated user (own certificates)
GET  /api/v1/certificates/verify/{code}                     public, no auth
POST /api/v1/certificates/{id}/revoke                       TRAINER_PM (own batch) / ADMIN
```

**Key decisions:**
- Graduation (`BatchService.graduate`) is `ACTIVE`-only — an `ON_PIP` student must be cleared back to `ACTIVE` through the normal PIP review flow first, never bypassed. Releases the batch seat, same as `TERMINATED`/`REASSIGNED`.
- Certificate issuance eligibility is three independent cross-package service calls: `BatchService.hasGraduatedFromBatch` (batch-scoped, unlike the any-batch `hasGraduated`), `PipService.hasOpenPip`, `SprintService.allSprintsClosed` (a batch with zero sprints passes vacuously — no guard invented for an unspec'd edge case).
- `certificate_number = MSH-CERT-{year}-{id, zero-padded to 6}` — collision-free by construction from the row's own post-insert `IDENTITY` id (two saves: insert, then update with the computed number).
- `verification_code`: 12 chars, `SecureRandom`, alphabet excludes `0/O/1/I/L` (manual-transcription safety), retried on collision up to `Constants.MAX_CODE_GENERATION_ATTEMPTS` (5).
- QR encodes only the public verify URL, never certificate data. PDF rendered once at issuance, uploaded to `certificates/{certificateNumber}.pdf`, never regenerated.
- Public verification: unknown code → real 404; revoked certificate → 200 with `valid: false` and a revocation date, never a 404. Returns holder name, batch, type, issue date, validity only — never email/phone/scores/PIP history.
- Clears the Open Stub tracked since feature 09: `PortfolioResponse` gains `completedProjects` (derived from `Task.projectId` + `TaskStatus.COMPLETED` — no per-student "project completion" table exists) and `issuedCertificates` (non-revoked only).
- `OwnershipGuard` gains a `certificates/` namespace: owner or `TRAINER_PM`/`ADMIN`.

**Findings from `/review`:** 0 critical. One item deferred to feature 23 (resolved there): `CertificateService.issue` uploaded to S3 inside `@Transactional`, matching pre-existing debt in Invoice/Payroll/HrLetter.

---

## Feature 21 — BA and Client Portal (V15)

**Package:** `com.moriah.skillhub.client` — `Client`/`ClientStatus`/`ClientProject`/`ClientProjectStatus`/`RequirementDocument`/`RequirementDocumentStatus`/`RequirementDocumentType`/`ResourceAllocation` entities, `ClientController` (`/clients/**`), `BaController` (`/ba/**`).

**Endpoints:**
```
POST /api/v1/clients                          ADMIN only
POST /api/v1/clients/projects                  CLIENT only (own company)
GET  /api/v1/clients/projects/{id}/progress    CLIENT (own project) / BUSINESS_ANALYST / ADMIN
POST /api/v1/ba/documents                      BUSINESS_ANALYST / ADMIN
PUT  /api/v1/ba/documents/{id}/approve         BUSINESS_ANALYST / ADMIN
POST /api/v1/ba/allocations                    BUSINESS_ANALYST / ADMIN
```

**Key decisions:**
- Clients cannot self-register. `POST /clients` optionally provisions the portal login in the same call, reusing `AuthService.forgotPassword` for the "set your password" link — never a bespoke token mechanism, never a generated/logged plaintext password.
- `POST /clients/projects` resolves the caller's own `client_id` via `clients.user_id = callerUserId`.
- Progress endpoint: owning `CLIENT` or `BUSINESS_ANALYST`/`ADMIN` staff oversight (hand-rolled "owner or staff" check, same shape as `BatchService.requireOwnerOrAdmin`). `target_batch_id IS NULL` → zeroed progress shape, not an error. Burndown/milestone data derived from real `Sprint`/`Task` data via `SprintService.progressForBatch` — no dedicated milestone table exists.
- Requirement documents land directly in `IN_REVIEW` on creation (never `DRAFT` — no submit-for-review or edit-draft endpoint exists in this feature's scope, same "unreachable schema state, documented why" precedent as `PayrollStatus`). Versioned per `(clientProjectId, docType)`: `version = max existing + 1`, enforced in application code (not a DB constraint, since `client_project_id` is nullable and MySQL treats NULL as distinct in a unique index).
- `resource_allocations` is create-only — no status column, no update/list/delete endpoint in scope.

**Findings from `/review`:** 0 critical, 0 important. One trivial, not fixed: `CreateResourceAllocationRequest` doesn't enforce `fromDate ≤ toDate`.

---

## Feature 22 — Admin Metrics and Exports

**Package:** `com.moriah.skillhub.admin` — `AdminMetricsController`, `AdminUserController`, `AdminPlanController`, `AuditController`, `ExportController`, backed by `MetricsService`, `AdminUserService`, `AuditQueryService`, `ExportService`/`ExportGenerationService`.

**Endpoints:** all `ADMIN` only.
```
GET  /api/v1/admin/metrics/overview          cached 5 min
GET  /api/v1/admin/metrics/revenue?from=&to=
GET  /api/v1/admin/users?role=&status=
PUT  /api/v1/admin/users/{userUuid}/status   bumps token_version
PUT  /api/v1/admin/users/{userUuid}/roles    bumps token_version
PUT  /api/v1/admin/plans/{id}                cache evicted on write
GET  /api/v1/admin/audit?entityType=&userUuid=&from=
POST /api/v1/admin/exports/{report}          users | revenue | audit
```

**No new migration** — every view/table read (`v_revenue_monthly`, `v_batch_velocity`, `v_lead_funnel`, `student_metrics`, `audit_logs`) already existed from earlier features.

**Key decisions:**
- `ReplicaDataSourceConfig` wires the previously-unused `mysql-replica` docker service for real — a second, separately-qualified `JdbcTemplate` (`@Qualifier("replicaJdbcTemplate")`), not a full JPA routing split, since every read this feature does is already raw SQL. Used for metrics/audit/export reads only; user list/status/role stays on the primary.
- **Two real Spring auto-config traps hit and fixed** (see `ReplicaDataSourceConfig`/`ExportGenerationService` Javadoc for full detail): (1) a second `DataSource`/`JdbcTemplate` bean silently suppresses the primary pool — `@ConditionalOnMissingBean` matches by *type*, not qualifier — fixed by never registering the replica pool as its own bean and explicitly re-declaring `@Primary` on the default `JdbcTemplate`. (2) `@Async` self-invocation within one class bypasses the proxy and runs synchronously — fixed by splitting export generation into its own bean.
- Suspend/role-change both bump `token_version` for immediate revocation, proven by a real "old token still fails immediately after" integration test.
- Exports always deliver via presigned URL (no raw-bytes-below-1000-rows path — no precedent anywhere in this codebase for returning file bytes in JSON).
- Audit query response never exposes `users.id` — `entity_id` is nulled and replaced with `entity_uuid` specifically when `entity_type = 'User'`.

**Findings from `/review`:** 0 critical, 0 important.

---

## Feature 23 — Audit, Hardening and Performance (V16)

Cross-cutting correctness/security/performance pass over features 01–22, not new functionality. No `/architect` flag.

**What changed:**
- **V16 migration**: 1 CHECK constraint gap closed (`roles.code` — the only enum-backed column in the whole schema missing one), 8 indexes added, each tied to a real unindexed hot-path query (notably `batches(track_code, status)` — the allocation hot path — and `tasks(status, due_at)` — the PM review queue, previously a full scan). MySQL rejects an explicit `ALGORITHM` clause on `ADD CONSTRAINT ... CHECK` — confirmed the hard way, that one statement ships with no algorithm clause; every index `ALTER` uses `ALGORITHM=INPLACE, LOCK=NONE`.
- **Audit logging gaps closed**: `payment/` had zero coverage anywhere (capture/refund/failure/invoice-issued added); `CodeReviewService.create` (a grade mutation) was unaudited. (`PipService`/`PipEvaluationService`/`QuizService`, initially suspected, turned out already fully audited.)
- **One real N+1 fixed**: `TaskSubmissionRepository.search` was missing `@EntityGraph`.
- **Security headers added** (none existed before): HSTS (1 year, includeSubDomains, unconditional `AnyRequestMatcher` — the default writer would never fire given this project's TLS-terminated-upstream topology, no `server.ssl.*` configured anywhere), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, CSP `default-src 'self'; frame-ancestors 'none'`.
- **The `@Transactional`-wraps-outbound-call fix** (deferred from feature 20): `CertificateService.issue`, `InvoiceService.renderAndUpload`, `PayrollService.generateOne` all restructured so the S3 upload happens outside any open transaction (`HrLetterService` was already correct). Each repository call now runs in its own short transaction via Spring Data's per-call default — same precedent `SubmissionVerificationRetryJob.retry()` already established.
- OWASP pass and rate-limiting coverage: confirmed clean, nothing to fix.

**Honestly-scoped, not fully met (documented explicitly, not faked):**
- V16 migration tested against real Testcontainers MySQL only — **not** a production-sized dump (none exists in this build).
- Connection pool load test: 150 concurrent callers, real HTTP, real MySQL — p95 ≈ 6s. Explicitly **not** a claim that build-plan's literal 5,000-concurrent/≤200ms target was met — `DB_POOL_MAX` left at 50, evidence-based reasoning documented in `progress-tracker.md`, not the literal spec'd number.
- PIP pipeline profiling: 1,500-student synthetic cohort, 300 triggered — `evaluate()` completed in ~29.5s, well within the real 15-minute nightly budget.

**Findings from `/review`:** 0 critical, 0 important.

---

## Feature 24 — Integration Testing and UAT (final feature)

Verification/completion pass over the 255 pre-existing integration tests from features 01–23. No `/architect` flag. **This is the last feature in the 24-feature build.**

**Gaps found and closed:**
- Zero HTTP coverage existed for `CheckoutController`/`SubscriptionController` (new `CheckoutFlowIT`), `PipController.list`/`.rules` (extended `PipFlowIT`), `StandupController.list` (extended `AttendanceFlowIT`).
- Three role-groups had no genuine wrong-role 403 test — closed.
- `QuizFailureRule`'s exact-threshold boundary was untested — closed.
- No test proved the full nightly chain (attendance → metrics → PIP) end to end from real data — added `NightlyChainUatFlowIT`.
- No test measured the *combined* metrics-refresh + PIP-evaluation pipeline (only PIP-evaluation alone existed) — added `MetricsAndPipChainProfilingIT` (measured: refresh 7.8s + evaluate 24.9s = 32.7s combined, well under the 90s target).

**UAT Scenarios table (`progress-tracker.md`) — 9 of 10 rows closed with real proof pointers:**
- Rows 1–8, 10: checked, each backed by a specific test class/method.
- **Row 9 (p95 ≤ 200ms): deliberately left unchecked** — feature 23's real measured number (~6s) doesn't meet the target, and this build never checks a box a real number doesn't support.

**Restore drill: actually re-executed**, not just referenced — fresh throwaway containers (default ports were occupied locally, so alternate ports were used), fresh checkpoint dump, real `mysqlbinlog` replay to a chosen timestamp. Result: 3 rows, exact match to the original 2026-08-24 drill. Full transcript in `docs/runbook-dr.md`.

**Genuinely out of scope for this environment, stated plainly:** staging deploy, a production-scale migration dry run — no deployment target has ever existed anywhere in this build. "Confirmed by the frontend track" for the exported `docs/openapi.json` also cannot happen — no external frontend team exists in this build (same fact every prior API Contract Change row already states).

**A real flake found and fixed while getting the final suite green**: `NotificationQueueIT`'s 10s poll budget wasn't always enough once this feature's own new tests added real extra Redis-queue traffic (300 PIP notification enqueues from `MetricsAndPipChainProfilingIT`). Confirmed not a logic bug via isolated re-runs; widened to 30s.

**Findings from `/review`:** 0 critical, 0 important.

---

## What a future session should know before touching any of this

1. **The `@Transactional`-wraps-outbound-call rule is easy to violate again** — it was violated independently four separate times before feature 23 caught them all at once. Check any new "render a file, then upload it" service against this.
2. **A second `DataSource`/`JdbcTemplate` bean is dangerous** — see feature 22's findings above before adding a third connection pool anywhere.
3. **The load test / production-migration-dry-run / staging-deploy gaps are real and still open.** Don't assume build-plan.md's literal performance targets were met just because the corresponding UAT row looks checked — read the actual numbers in `progress-tracker.md` first.
4. **`docs/openapi.json` is a one-time static snapshot** (feature 24) — it will drift the moment any endpoint changes, and nothing regenerates it automatically.
