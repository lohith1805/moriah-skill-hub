# Moriah Skill Hub Backend — Context (Features 01–09)

Project: `C:\Users\ADMIN\Desktop\Moraih Skill Hub Backend` (git repo, branch `master`)
Governing files: `context/AGENTS.md`, `context/project-overview.md`, `context/architecture.md`,
`context/code-standards.md`, `context/library-docs.md`, `context/build-plan.md`,
`context/progress-tracker.md`. Also `memory.md` at the project root (session handoff notes).

Single developer, backend + database, Spring Boot 3.5 / Java 21 / MySQL 8 / Redis 7. 24 features
built strictly in order, Aug 20 – Sep 11. This file is a standalone snapshot for starting a new
chat session — `context/progress-tracker.md` and `memory.md` remain the authoritative, living
records inside the repo; re-read them first if this file and the repo ever disagree.

---

## Status as of 2026-08-25

**Completed and committed:** Features 01 through 08, all `mvn verify`-green, all `/review`'d where
required, all decisions logged in `context/progress-tracker.md`.

- 01 Project Skeleton and Configuration — `f0d3ec3`, `6a380de`
- 02 Core Schema and Database Resilience — `ab31fb0`, `b771f32`
- 03+04 Authentication, RBAC and Entitlements — `cb39a1e`
- 05+06 OAuth2/2FA, Learning Schema — `84e8f3d`
- 07 Payment Integration — `451cfc5`
- 08 Storage and Notification Infrastructure — `17190b7`
- `/review` fixes on the 07+08 pair — `6c10d13`
- `memory.md` refresh — `32b35b0`

**Next:** Feature 09 — User Profile and Portfolio. Not in `AGENTS.md`'s mandatory-`/architect`
five; check `build-plan.md`'s feature 09 section to decide whether it's fully spec'd enough to
build directly (like 01/02/06) or needs `/architect` first (like 05/07/08's "touches more than
one module" judgment call).

**Testing cadence reminder:** test after every 2 features. 09+10 is the next checkpoint pair —
and **feature 10 is one of `AGENTS.md`'s mandatory-five `/architect` features** (batch allocation
under concurrency), so run `/architect` before building 10, even though 09 itself may not need it.

**Standing workflow contract (unchanged, still in force):**
- Build features in strict order.
- `/architect` before any feature touching money/auth/PIP status, or per AGENTS.md's explicit
  five (07, 10, 12, 17, 20), or when a feature spans multiple modules with real undecided design.
- `/review` after every 2-feature checkpoint; fix what it flags or explicitly ratify a deviation.
- Update `context/progress-tracker.md` after every completed feature/pair.
- Commit per checkpoint, `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- `/remember save` after building.
- Never guess on genuinely unspecified data — ask, unless the user has given standing permission
  to just take the recommended option (as they did in this session — confirm this still applies
  in the new chat, or ask again if starting fresh with no such context).
- Never log a token, password, OTP, webhook secret, or card detail.
- A feature is not confirmed done until a real `mvn verify` passes — not just targeted
  `mvn -Dtest=SomeIT test` runs. This project's `*Test`/`*IT` split binds `*IT` classes to the
  **failsafe** plugin (`verify` lifecycle), not surefire (`test` lifecycle) — a targeted `-Dtest`
  run exercises the class outside failsafe's ordering/load conditions and can miss real bugs that
  only surface under the full suite (this happened twice in the 07+08 segment).

---

## What exists in the codebase right now

**Schema (Flyway):** V1–V8 applied. V1 users/roles, V2 audit_logs/notifications/shedlock/job_runs,
V3 subscriptions/payments, V4 seed roles, V5 seed plans, V6 batches/sprints/tasks,
V7 submissions/reviews/attendance, V8 assessments/projects. V9 (student_metrics, feature 16)
onward not yet applied.

**Packages built:**
- `auth/` — JWT + refresh rotation, OAuth2 (Google/GitHub), TOTP 2FA (mandatory for
  ADMIN/HR_MANAGER), email verification / password reset (now actually dispatched via
  `NotificationService`, feature 08).
- `user/` — Role/User/UserRole entities, repositories (no profile/portfolio endpoints yet — that's
  feature 09).
- `common/security/` — `JwtAuthFilter`, `RateLimitFilter`, `OwnershipGuard`, `EntitlementGuard`,
  `TokenRevocationService`, `ClientIpResolver`, OAuth2 support classes.
- `common/audit/`, `common/job/` (JobRun tracking, reused by every nightly job),
  `common/storage/` (S3-compatible, real MinIO in tests), `common/notification/` (reliable Redis
  queue, three-bean write path — see below), `common/config/`, `common/exception/`, `common/util/`.
- `subscription/` — plans, entitlements, subscription expiry job.
- `payment/` — checkout, webhooks (Razorpay/Stripe), coupons, invoices.

**Notification write path (feature 08, worth knowing before touching it again):**
`NotificationService` (public API, channel-support check) → `NotificationWriter` (orchestrates
save-then-push, not itself `@Transactional`) → `NotificationRowWriter` (the only place that's
actually `@Transactional`, two entry points: `save()`/`saveInNewTransaction()`). This three-way
split exists because of two real, hard-won bugs — self-invocation silently bypassing Spring's
transactional proxy, and a Redis push happening before its own transaction had committed. Do not
collapse these back into fewer classes without re-reading `progress-tracker.md`'s 2026-08-25
entries first.

**Scheduled jobs pattern:** every job splits a `@Scheduled` + `@SchedulerLock`-guarded wrapper from
a plain, public, directly-testable method (`SubscriptionExpiryJob.expireOverdueSubscriptions()` →
`runExpiry()`; `NotificationReaperJob.sweepScheduled()` → `sweep()`). Follow this for every future
nightly job (13/16/17) — putting the lock directly on a method tests also call risks losing the
ShedLock race against the real cron tick.

**Recurring bug classes to watch for in new code:**
1. Hibernate's default `Integer`/`int` mapping is plain `INTEGER` — any `TINYINT/SMALLINT/INT
   UNSIGNED` column needs an explicit `columnDefinition` override or `ddl-auto: validate` fails.
2. Raw-JDBC `Instant`/`Timestamp` parameters can disagree with Hibernate's own read of the same
   `DATETIME` column on timezone handling — prefer writing through Hibernate/JPA, or a relative
   SQL interval, over binding an absolute Java timestamp when a test needs to backdate a row.
3. A same-class method call (`this.foo()`) never goes through Spring's AOP proxy — `@Transactional`,
   `@Cacheable`, etc. on such a call are silently ignored. Every fix so far has been "extract to a
   genuinely separate bean," not a self-injection trick — keep following that precedent.
4. A blocking Redis command's own requested wait must stay under `spring.data.redis.timeout`
   (2000ms project-wide) — Lettuce applies the client-side timeout uniformly regardless of what
   the blocking command itself asked for.
5. `RestClient.uri(String)` re-encodes its argument as a template — pass a pre-parsed `URI` for
   anything already percent-encoded (presigned URLs, etc.).

**Open Stubs remaining** (see `progress-tracker.md`'s table for the authoritative list):
- `BatchAllocationService.allocate()` from the payment webhook — cleared at feature 10.
- PIP `blocks_task_pull` check on `POST /tasks/{id}/pull` — left in 11, cleared at 17.

---

## Files to read first in a new chat

1. `context/progress-tracker.md` — Current Status section, then the full decision log for
   whichever features are most recent (currently 07/08 and the review-fix entry).
2. `memory.md` — shorter, session-oriented version of the same.
3. `context/build-plan.md`'s section for whatever feature is next.
4. This file, for the cross-feature patterns/gotchas above that aren't feature-specific enough to
   live in progress-tracker.md's per-feature entries.
