# Context — Features 15–19

Scope: Developer Content Portal (15), Student Metrics (16), PIP Rule Engine (17), CRM Module (18), HR Module (19). All built 2026-08-26/27, all `/review`'d, all committed to `mvn verify` green before moving to the next.

This is a standalone excerpt of `context/progress-tracker.md`'s decision log for just this feature range — see that file for the full 24-feature history, migration ledger, and open stubs.

---

## Feature 15 — Developer Content Portal

Built directly (no `/architect` flag). `/review`'d. `mvn verify` green (150 integration tests).

**New code:** top-level `project/` package — `Project`/`ProjectAsset`/`BugChallenge` entities, `ProjectRepository`/`ProjectAssetRepository`/`BugChallengeRepository`, `ProjectService`/`ProjectWriter`, `ProjectController`/`ChallengeController`. No new migration (V8 already has all three tables).

**Key decisions:**
- Asset/challenge uploads are one-step multipart endpoints (`POST .../assets`, `POST .../challenges` take `MultipartFile` directly), not two-step JSON-DTO-references-a-key — matches `ResumeService.upload`'s precedent, not the `resumes/{uuid}/...` pre-existing-key pattern.
- Storage keys are UUID-based (`projects/{projectId}/assets/{uuid}-{filename}`) specifically so the full key is known *before* any DB row exists, since `chk_project_assets_source`/`broken_code_key NOT NULL` demand the key at insert time.
- `PUT /projects/{id}` does double duty: in-place edit for `DRAFT`, version-bump-and-archive-original for `PUBLISHED` (clones into a new versioned-slug `DRAFT` row).
- `list()` forces `status=PUBLISHED` for everyone except `ADMIN` — only `ADMIN` can browse drafts across the catalogue.
- `OwnershipGuard.canAccessKey` extended with a `projects/` namespace (raw `JdbcTemplate`, `common/` can't import `project/` entities) — signable if `PUBLISHED`, creator, or `ADMIN`.

**Bugs found and fixed before `/review`:**
1. CHECK-constraint violation on first insert (save-then-update-key ordering) — fixed by the UUID-key-first design above.
2. MySQL reserved word: `AS accessible` → `AS is_accessible` (`ACCESSIBLE` is reserved in MySQL 8).
3. RestAssured/`RestClient.uri(String)` double-encoding on presigned downloads — same fix as features 08/12 (`RestClient.create().get().uri(URI.create(url))`).

**Cross-test-pollution bug (most involved investigation this feature):** `LearningSchemaIT` (feature 06) left an un-cleaned `project_assets` fixture row with a malformed key that leaked into `ProjectFlowIT`'s ADMIN-listing test via the shared static Testcontainers MySQL instance. Root-caused and fixed with `@Transactional` on `LearningSchemaIT` (matching `EntitlementGuardIT`'s precedent), not a defensive try/catch band-aid.

**`/review`: 10 findings, 9 fixed, 1 no-change.** Notable fixes: a slug-generation race (new `ProjectWriter` bean, `REQUIRES_NEW` retry with numeric suffix), `BugChallenge.createdBy` misattribution (was crediting the project owner instead of the actual caller), missing `ARCHIVED`-project mutability guard, missing wrong-role 403 tests. One deferred (per-item presign in `list()` isn't batched — real but lower priority, needs an interface change judged out of scope).

---

## Feature 16 — Student Metrics (V10)

Built directly (schema fully specified in architecture.md V9). `/review`'d. `mvn verify` green (157 integration tests).

**New code:** top-level `metrics/` package — `StudentMetric` entity, `StudentMetricRepository` (empty until feature 17 reads it), `StudentMetricsService`, `MetricsRefreshJob`. New migration `V10__student_metrics.sql`: `student_metrics` table + `v_batch_velocity`/`v_revenue_monthly` views (`v_lead_funnel` deferred to V12/feature 18, source table didn't exist yet).

**Key decisions:**
- Cohort roster reads through a new `BatchService.activeAndOnPipEnrollments()` — a real cross-package service call, not raw SQL, matching precedent.
- Cohort includes `ON_PIP` alongside `ACTIVE` — excluding it would make feature 17's PIP clearance mathematically impossible to grant.
- The other four aggregate reads (attendance, tasks, quizzes, assignment misses+reviews) stay raw `JdbcTemplate` deliberately — no existing bulk-service method covers those joins; `StudentMetricsService`'s Javadoc now says explicitly this job is meant to be the *one* place this rollup shape happens.
- `tasks_overdue_48h`'s window is schema-fixed via `Constants.STUDENT_METRICS_TASK_OVERDUE_HOURS`, not read from `pip_rules` (which doesn't exist until V11) — a measurement window, not a decision threshold.
- `EXCUSED` attendance status excluded from both numerator and denominator (inferred interpretation, not spec'd — flagged for product sign-off if it needs to change).
- `student_metrics` rows for departed students are never deleted/flagged stale (matches V9's schema, no soft-delete column) — feature 17 must filter its own cohort read the same way, not trust every row is live.

**`/review`: 9 real bugs fixed** (full list was in that session's `ReportFindings` call). Most consequential: `applyAssignmentWindows` counting not-yet-due windows as misses, and ignoring `batch_students.joined_at` (blaming reassigned students for pre-enrollment weeks); `applyQuizzes` wrongly excluding graded `EXPIRED` attempts and never feeding `days_since_last_activity`; missing indexes on 4 hot columns; `Instant.now()` read per-student inside the upsert loop instead of once (non-determinism risk at a day boundary). One test-fixture bug surfaced by the `joined_at` fix, fixed with a backdatable `insertBatchStudent` overload.

---

## Feature 17 — PIP Rule Engine (V11)

`/architect`'d (mandatory per AGENTS.md — money/threshold-shaped decision). Built. `/review`'d (8-angle, high effort). Unit suite green (34/34).

**New code:** top-level `pip/` package — `PipRule`/`PipRecord`/`PipMilestone` entities, `PipRuleCode`/`PipStatus`/`PipSeverity` enums, 3 repositories, a strategy-pattern rule engine (`PipRuleEvaluator` interface + 6 concrete rules: `AttendanceRule`, `ProjectDelayRule`, `AssignmentMissedRule`, `QuizFailureRule`, `ReviewFailedRule`, `TaskAbandonedRule`), `PipEvaluationJob`/`PipEvaluationService` (job/service split), human-facing `PipService`/`PipController`. New migration `V11__pip.sql`: `pip_rules` (seeded), `pip_records` (one-open-record-per-user via `STORED` generated column + unique constraint), `pip_milestones`.

**Key decisions:**
- Evaluator ordering is severity-based, not enum-declaration-order — a student tripping two rules in one run must have the more severe one win.
- `pip_rules.window_days` is admin-visible but NOT evaluator-readable and not admin-settable via `PUT /pip/rules/{code}` — every rule computes its own window internally (schema-fixed measurement window, same as feature 16's `tasks_overdue_48h`). Removed `windowDays` from the update request DTO since it silently did nothing.
- `PIP_CLEARANCE_MIN_TASK_COMPLETION_PERCENT` moved from hardcoded `Constants.java` to a new `@ConfigurationProperties` (`PipClearanceProperties`) — not made a `pip_rules` row since no seventh rule code exists to hang it off.
- `TaskPullGuard` calls `PipService.blocksPull(userId)` — a real service method, not raw JDBC (unlike `EntitlementGuard`/`OwnershipGuard`, which live in `common/` and genuinely can't import feature packages; `pip/` has a full service layer so there's no excuse).
- `TaskService.assign()` (PM-driven) is deliberately exempt from `TaskPullGuard`; `POST /tasks/{id}/pull` (student self-service) is not — documented in Javadoc so it isn't "fixed" as an oversight later.
- `BatchService.removeStudent()` stays `ACTIVE`-only — an `ON_PIP` student must be offboarded through `PipService.review()`, which has real PIP-outcome semantics.
- No `@Version` optimistic locking anywhere in `pip/` — confirmed pre-existing, codebase-wide gap, not unique to this feature. Left as a candidate for feature 23.

**One severe bug — the most consequential finding up to that point in the build:** `StudentMetricsService.currentCohortMetrics()` had no filter against the live cohort, so a student who left it (`CLEARED`/`TERMINATED`/`REASSIGNED`) kept a stale `student_metrics` row forever. Since a departed student has no open `pip_records` row, the nightly job would re-evaluate their months-old metrics and could silently flip an already-`TERMINATED` row back to `ON_PIP` — undoing a real HR/PM decision with no human in the loop. **Fixed** by filtering against `batchService.activeAndOnPipEnrollments()` before returning, plus a defense-in-depth guard in `BatchService.updatePipStatus()` that only an `ACTIVE` student can transition to `ON_PIP`.

**`/review`: 12 findings, 10 fixed, 1 deferred, 1 no-change.** Notable fixes: `ReviewFailedRule`'s clearance check used lifetime-cumulative unsatisfactory-review count, making `CLEARED` permanently unreachable for anyone with even one bad review ever (fixed to window against the PIP record's own start date); HR-manager roster re-queried per triggered student instead of once per job run; `AttendanceFinalisationJob` silently skipped `ON_PIP` students; `BatchAllocationService.deallocate()` silently no-op'd for `ON_PIP` students; missing index on the hottest path in this feature (`TaskPullGuard.blocksPull()`); N+1 on `PipService.list()`; a PIP status mutation that wasn't audited.

---

## Feature 18 — CRM Module (V12)

Built directly (schema fully specified in architecture.md V11). `/review`'d — **first feature where the 5-parallel-agent workaround for the missing git repo was used** (documented as a standing environment fact, applies to every subsequent `/review` in this project). `mvn verify` green (285 unit + 189 integration tests).

**New code:** top-level `crm/` package — `Lead`/`LeadActivity`/`SalesTarget` entities, `LeadStatus`/`LeadSource`/`LeadActivityType` enums, 3 repositories, `LeadDedupeHasher`/`LeadWriter`/`LeadWhatsAppSender`/`LeadService`/`LeadController`, `crm/webhook/` (`WhatsAppSignatureVerifier`, `WhatsAppWebhookService`, `WhatsAppWebhookController`). New migration `V12__crm.sql`: `leads`/`lead_activities`/`sales_targets` + `v_lead_funnel` view (deferred from V10).

**Key decisions:**
- Pipeline transitions use `LeadStatus`'s enum-declaration order as the ordinal sequence, `LOST` handled as a terminal exit from any non-terminal status (mirrors `PipRuleCode`'s severity-rank precedent).
- `interested_plan_id` is a bare FK `Long`, not `@ManyToOne` — shared-kernel exception only covers `User`/`Batch`; existence validated via direct `SubscriptionPlanRepository.existsById`.
- The creating agent is self-assigned to `Lead.assignedAgent` at creation — no separate "assign lead" endpoint exists in build-plan.md's 6-endpoint list.
- `sales_targets` has no create/update endpoint — `GET /leads/targets/me` is deliberately read-only, matching the "table exists in schema, not yet API-managed" treatment.
- Outbound WhatsApp triggered by `POST /leads/{id}/activities` with `activityType=WHATSAPP` + required `templateCode` — extracted `WhatsAppDispatcher.sendTemplate(...)` and a new `LeadWhatsAppSender` bean deferring via `TransactionSynchronizationManager.afterCommit()` (separate bean specifically so `LeadService`'s own unit tests never touch a real Spring transaction).
- `leads.phone`/`leads.email` normalized before storage (not just for the dedupe hash) — the inbound WhatsApp webhook's `from` field is always digits-only, so unnormalized stored phones would never match.
- `users.id` never exposed by any CRM DTO — switched every leaking field (`assignedAgentId`→`assignedAgentUuid`, etc.) to `uuid`, including the `GET /leads?agentId=` query param becoming `agentUuid` (a deliberate deviation from build-plan.md's literal param name, logged in the API Contract Changes table).

**One severe bug:** the dedup create-race fallback re-read `findByDedupeHash` inside the *same* outer transaction as the original check — MySQL/InnoDB's REPEATABLE READ snapshot is fixed at first read, so a concurrent duplicate's loser never sees the winner's commit and 500s instead of merging into the existing lead. **Fixed** with a second `REQUIRES_NEW` method on `LeadWriter` (fresh transaction = fresh snapshot), same shape as `QuizAttemptWriter.findExisting`'s feature-12/13 precedent.

**`/review`: 18 findings, 15 fixed, 3 no-change.** Notable fixes: `users.id` leaks (above); WhatsApp webhook missing signature header fell through to 500 instead of 400 (new `GlobalExceptionHandler` handler, also silently fixed the same latent gap in Razorpay/Stripe webhooks); build-plan.md's own Verify line unmet (dedup produced 1 activity not 2 — fixed, and the test that had been asserting the wrong number corrected); N+1 on `GET /leads`; 3 missing FK indexes; missing wrong-role 403 tests; `Lead.assignedAgent` was a dead field; duplicate-construction blocks collapsed into one `recordActivity()` helper; missing `.env`/`.env.example` var that would have broken startup.

---

## Feature 19 — HR Module (V13)

Built directly (schema fully specified in architecture.md V13). `/review`'d (5-angle, same no-git-repo workaround as feature 18). `mvn verify` green (323 unit + 209 integration tests). **This was the last feature covered by that session's standing autonomous-run instruction.**

**New code:** top-level `hr/` package — `Employee`/`HrDocument`/`LeaveRequest`/`PayrollRecord` entities, `EmploymentType`/`EmployeeStatus`/`LeaveType`/`LeaveStatus`/`HrDocumentStatus`/`PayrollStatus`/`LetterType` enums, 4 repositories, 5 services (`EmployeeService`, `HrDocumentService`, `LeaveService`, `PayrollService`, `HrLetterService`) each with its own controller. New migration `V13__hr.sql`. Two new shared-infra pieces: `common/security/CurrentUserUuid` (mirrors `@CurrentUser`, resolves `.uuid()` instead of `.userId()`, avoids a DB round trip) and two new `OwnershipGuard` namespaces (`payslips/{employeeCode}/...`, `hr-letters/{userUuid}/...`, both "owner OR HR_MANAGER/ADMIN"). One new cross-package read: `BatchService.hasGraduated(Long userId)`.

**Key decisions:**
- No `hr_letters` table exists — letter issuance is deliberately ephemeral. `HrLetterService.issue` is NOT `@Transactional` (nothing writes to the DB): generate PDF → `uploadTrusted` → `presignedGetUrl` → return the URL.
- `PayrollStatus.DRAFT`/`PAID` are schema-valid but unreachable through this feature's endpoints (`generate()` always produces `FINALISED` directly) — documented in the enum's own Javadoc.
- Letter eligibility for EXPERIENCE/RELIEVING is dual-track: `BatchService.hasGraduated(userId)` (student) OR `Employee.status == EXITED` (staff), never `TERMINATED`. The staff track is currently unreachable in practice — no endpoint in this feature's 8-endpoint list ever transitions an `Employee` to `EXITED`. Left as-is deliberately: inventing an offboarding endpoint now would be scope creep past build-plan.md's literal spec. Will resolve itself once a future feature adds staff offboarding.
- `Employee.id`/`PayrollLineRequest.employeeId`/`CreateEmployeeRequest.reportingManagerId` expose the raw PK — confirmed fine, since `employees.id` is the resource's own PK (like `Lead.id`/`Project.id`), not `users.id`. Every actual `users.id` reference in HR DTOs already uses `uuid`.

**Two genuine authorization bugs — the most severe findings this feature produced:**
1. `LeaveService.requireReportingManagerOrHr`'s HR-override short-circuit never checked the caller wasn't the requester — an HR_MANAGER could submit and then approve their own leave. Fixed by checking self-decision first, regardless of role. New `ErrorCode.SELF_DECISION_NOT_ALLOWED` (403).
2. `HrDocumentService.verify` had no check against self-verification — an HR_MANAGER could upload their own KYC document and verify it themselves. Same fix. Both covered by new `HrFlowIT` tests (`decideLeave_hrManagerOwnRequest_returns403`, `verifyDocument_hrManagerOwnDocument_returns403`) plus unit tests.

**`/review`: 15 findings, 13 fixed, 1 deferred, 1 no-change (details of no-change items above).** Other fixes: dead `@NotBlank` on a `@RequestParam` (no `@Validated` on the controller — Bean Validation on `@RequestParam` silently does nothing without it, a general gotcha worth remembering for future `@RequestParam` validation anywhere in this codebase); `BigDecimal` scale mismatch in payroll gross-amount calc (violated the literal "DECIMAL(12,2)" build-plan line); unbounded negative net pay; N+1 in payroll generation (2 new batch repository methods); payslip S3 key format deviating from architecture.md's `yyyy-MM` template; duplicated "is caller HR staff" JDBC subquery in `OwnershipGuard` (extracted to shared `isHrStaff` helper); overly strict `@PastOrPresent` blocking legitimate future hire dates; missing audit logging on HR mutations (added to match PIP/Attendance precedent); untested `LetterType.INTERNSHIP`; a TOCTOU race with no `DataIntegrityViolationException` handler (fixed as a codebase-wide `GlobalExceptionHandler` backstop, not HR-specific).

**Deferred to a spawned background task** (not fixed inline): PDF-rendering boilerplate is now duplicated a third time across `InvoiceService`/`PayrollService`/`HrLetterService`. Fixing it means touching the already-shipped `InvoiceService` (feature 07/08) — judged out of proportion for feature 19's own review budget. Chip: "Extract shared PDF-rendering helper" (`task_168cd7c1`, may since have been actioned or gone stale).

---

## Cross-cutting patterns established or reinforced across 15–19

- **No git repo existed in this working directory through at least feature 19** — `/review`'s `git diff`-based Phase 0 was worked around with 5 parallel Agent-tool calls per feature, each briefed with the explicit new/modified file list. (A later memory snapshot suggests a git repo was initialized in a subsequent session — verify current state before assuming this workaround is still needed.)
- **`users.id` must never be exposed in a response DTO** — always the resource's own `uuid`. A resource's own PK (`Lead.id`, `Project.id`, `Employee.id`) is fine. This was a real `/review` finding in both feature 18 and (checked, confirmed clean) feature 19.
- **Mandatory 2FA login flow for `ADMIN`/`HR_MANAGER` in integration tests** — established in `PipFlowIT` (feature 17), had to be replicated in `ProjectFlowIT` (15, via the ADMIN-2FA bug found mid-investigation) and `HrFlowIT` (19). Any new `*FlowIT` logging in as one of these roles needs the same `completeMandatoryTwoFactorSetupAndLogin`/`currentTotpCode` helper pair or every subsequent call 401s.
- **"Config that looks live but isn't" anti-pattern** — flagged twice (feature 16's `tasks_overdue_48h`, feature 17's `pip_rules.window_days`): a DB column or request field that appears to configure behavior but nothing actually reads it. Worth checking for in any new admin-configurable-looking field.
- **Job/Writer-bean split for `REQUIRES_NEW` race handling** — `ProjectWriter` (15), `LeadWriter` (18) both follow the `QuizAttemptWriter` (12/13) shape: a separate `@Service` bean for both the racy write and, critically, the *post-race-loss re-fetch* (MySQL REPEATABLE READ needs a fresh transaction to see a concurrent winner's commit).
- **Cross-module cohort/status reads go through the owning module's real service method, not raw JDBC**, once that service layer exists — `StudentMetricsService`/`PipService`/`HrLetterService` all had to be corrected onto this pattern during review (`BatchService.activeAndOnPipEnrollments()`, `PipService.blocksPull()`, `BatchService.hasGraduated()`). Raw `JdbcTemplate` from `common/` remains correct only when the caller genuinely cannot import the target package's entities.
- **Every feature's `/review` catches missing wrong-role 403 tests and N+1 queries** — treat both as a standing checklist item before considering any new feature done, not just a possibility.
