# Context 09–12

Condensed reference for Features 09–12, distilled from `progress-tracker.md`'s decision log so a
new session/agent doesn't have to read that entire log to pick up this stretch of the build. For
full narrative detail (exact bug traces, code snippets, reasoning), see the dated entries in
`progress-tracker.md` — this file is a compressed index into it, not a replacement.

All four features: `mvn verify` green, committed to `master`, pushed to
`https://github.com/lohith1805/moriah-skillhub-backend.git`.

---

## 09 — User Profile and Portfolio

Built directly (not `/architect`'d — not one of the mandatory five). No new migration —
`user_profiles` already existed from V1.

**Package:** `user/` — `ProfileService` (lazy get-or-create, slug generation with collision retry,
completion-percent calc), `UserService` (self/public reads), `ResumeService` (S3 upload/presigned
download), `UserController` (`/api/v1/users/**` + public `/api/v1/portfolio/**`).

**Decisions:**
- `skills`/`education`/`workExperience` are pre-serialized JSON text (Jackson), same treatment as
  `Notification.payload` — not a typed-collection JSON mapping.
- `GET /portfolio/{slug}` omits `completedProjects`/`issuedCertificates` — Open Stub, cleared at
  feature 20 (`project`/`certificate` don't exist as features yet).

**Bugs fixed:** `EducationEntry.@Min(1950)` too strict for real historical data → widened to 1900.
RestAssured double-encodes a presigned URL the same way `RestClient.uri(String)` does → switched
that one download call to plain `RestClient`.

---

## 10 — Batch Management and Allocation

`/architect`'d (mandatory-five — "batch allocation under concurrency"). New migration **V9**
(`batch_allocation.sql`).

**Package:** `batch/` — `Batch`/`BatchStudent`/`PendingBatchAllocation` entities, `BatchService`,
`BatchAllocationService` (clears the feature-07 stub), `BatchController`.

**Decisions:**
- **Track comes from checkout, not the profile.** `CheckoutRequest.trackCode` (**frontend contract
  change**) → `payments.track_code` → read at webhook time by `BatchAllocationService.allocate()`.
- **`pending_batch_allocations` (V9) is the real "pending queue"** — one open row per user
  (conditional-unique generated column), resolved (never deleted) by a later `allocate()` success
  or a manual PM add.
- V9 claimed the version library-docs.md's original outline reserved for feature 16 — every
  placeholder from the old V9 onward shifted down one version in the Migration Ledger.
- `Batch` promoted to a **shared-kernel entity** alongside `User` — other packages hold a real
  `@ManyToOne Batch`, not a bare id.
- `TRAINER_PM` scoped to own batches via `BatchService.requireOwnerOrAdmin(callerUserId, batch)`;
  `ADMIN` bypasses. This exact method is the established pattern every later batch-scoped write
  (features 11, 12) must call.
- `EntitlementFlagsLoader` gained `tierRank` + `evict(userId)`, called before `allocate()`'s own
  `load()` — the normal 60s cache staleness isn't acceptable for a webhook that needs the plan just
  paid for, immediately.

**Bugs fixed:** `PaymentWebhookFlowIT`'s unscoped `webhook_events` count assertion broke once
`BatchFlowIT` started hitting the same shared container — scoped the count to the test's own
marker (same fix pattern used repeatedly across this build for shared-container test pollution).

---

## 11 — Sprint and Task Management

Built directly (build-plan.md carries no `/architect` flag for this one, unlike 10/12). No new
migration — `sprints`/`tasks` (V6) and `assignment_windows` (V8) already existed.

**Package:** `sprint/` — `Sprint`/`Task`/`AssignmentWindow` entities, `SprintService`/
`TaskService`/`AssignmentWindowService`, `SprintController`/`TaskController`, `TaskPullGuard`.

**Task state machine** (extended again in feature 12 — see below):
```
BACKLOG → ASSIGNED → IN_PROGRESS → IN_REVIEW → COMPLETED | REJECTED
```
`PUT /tasks/{id}` cannot move a task out of `BACKLOG` — reserved for `/assign` (PM) and `/pull`
(student self-assign). `sprintId`/`batchId` immutable on every PUT.

**Decisions:**
- `POST /tasks/{id}/pull` is `STUDENT`-only, requires active batch membership
  (`BatchService.isActiveMember`). `TaskPullGuard` wires the PIP `blocks_task_pull` Open Stub
  (currently a permissive no-op — cleared at feature 17).
- Velocity (`SprintService.totalVelocity`) is a projection-only sum, never a loaded-entity sum.

**Bugs fixed (both in the sprint state machine):**
1. `transitionStatus`'s `PLANNED → ACTIVE` branch checked "no other active" before "previous
   completed" — wrong error surfaced first for the ordinary case. Swapped order.
2. `POST /sprints/{id}/activate` silently let you re-activate an already-`ACTIVE` sprint (inherited
   `transitionStatus`'s same-status no-op, meant for `update()`'s convenience). Fixed with an
   explicit `PLANNED`-only guard in `activate()` itself.

**Infra notes:** Mockito inline mock maker needs `-Djdk.attach.allowAttachSelf=true` (test JVM
self-attach permission, not a memory issue as it first appeared). Forked-JVM heap/metaspace/code-
cache now explicitly capped in `pom.xml`'s `test.jvm.argLine` for this resource-constrained host.

---

## 12 — GitHub Submission and Code Review

`/architect`'d (mandatory-five). No new migration — `task_submissions`/`code_reviews`/
`weekly_reviews` already existed from V7. `/review`'d after building; all findings fixed (below).

**Package:** `submission/` — `TaskSubmission`/`CodeReview`/`WeeklyReview` entities,
`GithubVerificationService`/`GithubPrFetcher`/`GithubClientConfig` (server PAT), `SubmissionService`/
`CodeReviewService`/`WeeklyReviewService`, `SubmissionController`/`ReviewController`,
`SubmissionVerificationRetryJob`, `TaskSubmissionWriter`.

`TaskService` gained `markInReview`/`completeReview`/`reviewQueue` — narrow cross-package entry
points (different authorization each: "you're this task's `assignedTo`" vs. batch-PM ownership).
`ALLOWED_TRANSITIONS` extended: `IN_REVIEW → IN_PROGRESS` (for `CHANGES_REQUESTED`).

**Decisions:**
- `CHANGES_REQUESTED` → `IN_PROGRESS`, never terminal `REJECTED` — `attempt_number` presupposes
  resubmission. `REJECTED` stays reachable only via a PM's direct `PUT /tasks/{id}`.
- No GitHub webhook receiver — `build-plan.md`'s real spec is pull-based only (`project-
  overview.md`'s stray webhook mention is an unreconciled doc inconsistency, not a gap).
- `GithubPrFetcher` is a separate `public` bean — `@Cacheable` self-invocation avoidance +
  `@MockitoBean`-replaceable from `SubmissionFlowIT` (no real GitHub PAT in CI).
- **`SubmissionService.create()` and `SubmissionVerificationRetryJob.retry()` are deliberately NOT
  `@Transactional`** (a `/review` fix — see below) — every DB step opens its own short transaction
  via its own bean instead.

**Idempotent double-post handling (`TaskSubmissionWriter`), four escalating bugs, all fixed:**
1. MySQL/InnoDB can genuinely deadlock two concurrent inserts on the same unique key
   (`CannotAcquireLockException`), not just cleanly reject one (`DataIntegrityViolationException`)
   — both must be caught.
2. Catching either inside the same Hibernate session that just failed a flush still poisons that
   session (same bug class as `WebhookIdempotencyService`) — fixed with a separate `REQUIRES_NEW`
   bean, `TaskSubmissionWriter.tryInsert`.
3. The recovery *read* after catching also needs its own `REQUIRES_NEW` transaction — MySQL
   REPEATABLE READ's snapshot is fixed at the first read in a transaction, so even a real-time read
   in the old outer transaction could miss the winner's already-committed row.
   `TaskSubmissionWriter.findExisting`, also `REQUIRES_NEW`.
4. Once that recovery read started finding a row, `submission.getUser()` threw
   `LazyInitializationException` (that row's own short session had already closed) — fixed with
   `JOIN FETCH ts.user` on the repository query backing `findExisting`.

**`/review` findings, all fixed same day:**
1. **Critical** — both `create()` and `retry()` made outbound GitHub HTTP calls inside an open
   `@Transactional` method (violates AGENTS.md), with no timeout on the GitHub `RestClient` —
   real connection-pool-exhaustion risk under a slow (not just erroring) GitHub response. Fixed:
   removed `@Transactional` from both; `create()` reordered so `verify()` runs *before* any DB
   write (no compensating rollback needed); `GithubClientConfig` gained a 5s connect / 8s read
   timeout.
2. **Important** — `WeeklyReviewService.create()` had no batch-ownership check (unlike
   `SprintService.create`/`TaskService.completeReview`) — any PM could rate a student outside their
   own batch, feeding straight into the feature-17 `REVIEW_FAILED` PIP rule. Fixed by calling
   `BatchService.requireOwnerOrAdmin`.
3. **Important** — none of the four role-restricted endpoints had a wrong-role `403` test. Added
   `create_asTrainerPm_returns403`, `reviewQueue_asStudent_returns403`, `review_asStudent_returns403`,
   `weeklyReview_asStudent_returns403` to `SubmissionFlowIT`.

**Also fixed, pre-existing and unrelated to feature 12's own code:** `SprintTaskFlowIT.createBatch`
hardcoded a `startDate` that rolled into the past mid-build (`@FutureOrPresent`), cascading into 11
test failures. Switched to `LocalDate.now()`.

---

## Cross-cutting patterns established across 09–12

- **`BatchService.requireOwnerOrAdmin(callerUserId, batch)`** is the one call every batch-scoped
  write across every package uses — check any new batch-scoped write calls it.
- **A same-class call to another `@Cacheable`/`@Transactional(REQUIRES_NEW)` method on `this`
  bypasses Spring's AOP proxy** — every instance of this bug fixed so far was fixed by splitting
  into a separate bean (`EntitlementFlagsLoader`, `NotificationRowWriter`, `GithubPrFetcher`,
  `TaskSubmissionWriter`). Check for this whenever a new self-invocation-shaped bug appears.
- **Hibernate session poisoning**: catching a translated exception from a failed `save()`/flush and
  continuing in the *same* session still leaves it unusable. Fix is either plain `JdbcTemplate`
  (`WebhookIdempotencyService`) or an isolated `REQUIRES_NEW` bean (`TaskSubmissionWriter`) —
  depends on whether the recovery path needs normal JPA machinery afterward.
- **MySQL REPEATABLE READ**: a transaction's snapshot is fixed at its *first* read, not per-query.
  A recovery read issued later in an already-active transaction can miss data committed by another
  transaction after that first read — needs its own fresh (`REQUIRES_NEW`) transaction.
- **Never make an outbound HTTP call inside `@Transactional`** (AGENTS.md, hard rule) — the feature
  12 `/review` finding. Worth checking any future feature that calls an external gateway
  (Razorpay/Stripe/SendGrid/WhatsApp) from inside a service method that also writes to the DB.
- **Hardcoded future-looking test dates rot** — this build has hit it twice now
  (`SprintTaskFlowIT` in feature 12, likely elsewhere). Prefer `LocalDate.now()`/`.plusDays(n)` in
  test fixtures over a literal date string where the constraint is `@FutureOrPresent`.
