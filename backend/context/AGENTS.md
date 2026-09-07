# AGENTS.md

Moriah Skill Hub — Backend and Database.
Spring Boot 3.5 · Java 21 · MySQL 8.0 · Redis 7 · Maven.

This is a **backend-only repository**. The React frontend lives in a separate repo and is built by a separate track. There is no UI code here, and none should ever be added. If a task seems to require a component, a template, or a stylesheet, the task belongs in the other repo.

---

<!-- BEGIN:spring-agent-rules -->
# This is NOT the Spring Boot you know

Spring Security 6, Spring Data JPA 3, and AWS SDK v2 all contain breaking changes from the versions most training data covers. `WebSecurityConfigurerAdapter`, `antMatchers()`, `authorizeRequests()`, `@EnableGlobalMethodSecurity`, `RestTemplate`, and `AmazonS3ClientBuilder` are removed, deprecated, or not on this classpath.

Never write Spring Security, Spring Data, JJWT, or AWS SDK code from memory. Read `context/library-docs.md` first — it documents the exact API shapes this project uses. If it is not covered there, read the official documentation before writing the code.
<!-- END:spring-agent-rules -->

---

## Read Before Anything Else

Read in this exact order before any implementation:

1. `context/project-overview.md` — what is being built, roles, tiers, scope boundaries
2. `context/architecture.md` — stack, package structure, layer boundaries, full DB schema, invariants
3. `context/code-standards.md` — Java 21 and Spring Boot conventions, layer rules, error handling
4. `context/library-docs.md` — project-specific patterns for every library and external API
5. `context/build-plan.md` — the 24 features in sequence, with owners and verification criteria
6. `context/progress-tracker.md` — what is done, what is next, what is blocked

If `memory.md` exists in the project root and this session continues previous work, run `/remember restore` instead of re-reading everything manually.

---

## Rules That Never Change

**Schema**

- Flyway owns the schema. `ddl-auto` is `validate` and never changes.
- Every entity change ships with a versioned migration in the same commit.
- Never edit a migration that has been applied anywhere. Add the next version.
- **Migration versions ascend with feature numbers.** A new migration takes the next unused number — never one slotted between existing versions.
- **Forward-only. Expand/contract for anything destructive.** Flyway Community has no undo scripts; reverting a deploy reverts the app, never the schema. Never `DROP`/`RENAME` a column in the same release as the code change.
- **MySQL has no partial unique indexes.** Conditional uniqueness uses a `STORED` generated column plus a unique key. Two exist: one active subscription per user, one open PIP record per user.
- Flyway runs as `moriah_migrate` (DDL). The runtime pool is `moriah_app` (no DDL, and no `UPDATE`/`DELETE` on `audit_logs`). Never merge them.
- Update the Migration Ledger in `progress-tracker.md` when a migration is added.

**Layers**

- Controllers never inject a repository. Services never return an entity past the controller boundary.
- `@Transactional` goes on service methods only — never on a controller, never on a repository.
- A feature module may call another module's service interface. It may never touch another module's repository or entity.
- `common/` never imports from a feature package.

**Contracts**

- Every endpoint returns `ApiResponse<T>`. Never a bare object, never a bare list.
- Every list endpoint is paginated. No endpoint returns an unbounded collection.
- Every non-public controller method has `@PreAuthorize`. A missing one is a defect, not an oversight.
- The frontend track codes against the OpenAPI spec. Log any request or response shape change in `progress-tracker.md` and notify them before merging.

**Correctness**

- PIP thresholds are rows in `pip_rules`. A numeric threshold hardcoded in Java is a defect.
- Money is `BigDecimal` and `DECIMAL(12,2)`. `double` and `float` never appear in a money code path.
- Every webhook verifies its signature against the raw body, then deduplicates via `webhook_events.event_id`, before any state changes. **PDF rendering happens after the response, never inside the gateway's timeout.**
- Never make an outbound HTTP call inside a transaction.
- Every `@ManyToOne` and `@OneToOne` is `FetchType.LAZY`. Every enum is `EnumType.STRING`.
- Never expose `users.id` — the public identifier is `users.uuid`.
- Never log a token, password, OTP, webhook secret, or card detail.
- **Batch capacity uses a conditional atomic `UPDATE`**, never read-then-write and never `@Version` retry on a hot counter.
- **The nightly chain is ordered: attendance finalisation (01:30) → metrics refresh (01:45) → PIP evaluation (02:00).** Reordering silently corrupts the attendance denominator. Every job writes a `job_runs` row.
- **The PIP engine reads `student_metrics` only, in one query.** A repository call inside the per-student loop is a defect.
- **Ungraded `CODE` quiz answers are excluded from the percentage denominator**, never scored as zero.
- **Every presigned URL passes `OwnershipGuard.canAccessKey()` before signing.** Signing a key because the caller asked for it is an IDOR.
- `tier` is never a JWT claim. Entitlements are read from the DB per check.
- **RBAC is role-based only.** `permissions`/`role_permissions` exist in the schema (V1) but are seeded empty and read by nothing across all 24 features. Never add a permission-code check, seed a permission row, or treat the empty table as a gap to fill — it's a deliberate deferral, recorded in `progress-tracker.md`. If a real need for fine-grained permissions shows up, that's a new feature: define the specific codes an actual endpoint needs, don't backfill a hypothetical catalogue.

**Process**

- Update `progress-tracker.md` after every completed feature. A feature is complete only when its migration is applied, its endpoints return documented shapes, its tests pass, and `mvn verify` is green.
- Never add a dependency that is not in the approved list in `code-standards.md`.
- Never leave a `TODO` in committed code. Unfinished work goes in `progress-tracker.md`.
- Three stubs are left deliberately (webhook → allocation, invoice PDF → S3, task-pull PIP block). They exist because feature order is fixed. They are tracked in the Open Stubs table in `progress-tracker.md` and must all be cleared before feature 24. Never add a fourth without adding a row.
- Before using any third-party library, load its installed skill if one exists, then read `context/library-docs.md` for the project-specific rules that override it.
- **If the same problem persists after one corrective prompt — stop immediately and run `/recover`.**

---

## Available Skills

Four skills are installed. Each one exists because a specific failure mode kept repeating.

### `/architect` — before you build

Reads the context files, asks focused questions one at a time, surfaces decisions not yet made, and produces an implementation plan you confirm before any code is written.

**Run it before:**

- Any feature in `build-plan.md` that touches more than one module
- Anything involving money, auth, or PIP status — these are the hard-to-reverse decisions
- Any feature where the schema is not already fully specified in `architecture.md`

**Specifically on this project, always run it before:** feature 07 (payments and webhook idempotency), feature 10 (batch allocation under concurrency), feature 12 (GitHub submission and review), feature 17 (the PIP rule engine), and feature 20 (certificate issuance and public verification). These five carry the most irreversible decisions in the build, and they are flagged in `build-plan.md` and `progress-tracker.md` too.

```
/architect feature 17

Check @context/architecture.md for the student_metrics table, then /architect feature 17.

/architect feature 10 — I need to decide how concurrent allocation to the last seat in a batch behaves.
```

**Skip cost:** You start typing a prompt, the agent builds, and halfway through you realise nobody decided whether a `STARTER` subscriber gets allocated to a batch. The agent guessed. You are now refactoring. This is the single biggest source of wasted sessions.

**After it produces the plan, review it before implementing.** Add decisions the agent missed. Correct assumptions that are wrong. You are still in control.

---

### `/review` — after you build, before you move on

Checks whether the implementation matched the plan, whether it respects the layer boundaries in `architecture.md`, and whether it is production ready. Returns issues by severity: critical, important, minor. **It never auto-fixes.**

**Run it after:**

- Any feature that writes to the database
- Anything touching auth, RBAC, entitlements, or payments
- Any feature with the kind of logic that is easy to get subtly wrong — the PIP rules, task state transitions, payroll computation
- Any time something feels off and you cannot pinpoint why

```
/review

/review Feature 11 — a student on a PROJECT_DELAY PIP can still pull new tasks. The block is not firing.

/review Feature 17 — the PIP job is issuing one query per student. Check the loop in PipEvaluationJob.
```

**Skip cost:** The agent finishes a feature, you skim the code, you commit. Three days later you find the bug in production, traced to an assumption you did not catch.

**A plain `/review` is good. A `/review` with a specific observation is better.** The more precisely you describe what feels wrong, the more targeted the diagnosis.

**Worth reviewing on this project regardless of how it looks:** anything that computes attendance or task percentages in Java rather than reading a view, any list endpoint added without `Pageable`, any controller method added without `@PreAuthorize`, and any repository call that appears inside a loop.

---

### `/recover` — when something breaks

Diagnoses which failure mode you are in — a targeted bug, a polluted context, or a wrong assumption made earlier — and gives the right corrective response. One prompt instead of an afternoon of spiralling.

**Run it when:**

- Something is broken and `/review` did not fix it
- You tried one corrective prompt and it did not work
- You have a specific stack trace or terminal error to diagnose

```
/recover [paste the exact stack trace here]

/recover Feature 07 — replaying the Razorpay webhook creates a second user_subscriptions row. The unique constraint on webhook_events.event_id is in V2 and applied.

/recover Flyway fails on startup: checksum mismatch on V3.
```

**Paste raw terminal output directly. Never paraphrase it.** The exact message — the full stack trace, the Hibernate SQL, the constraint name — is what makes a one-shot diagnosis possible. Paraphrasing throws away the detail that matters.

**Common failure modes on this stack, worth naming in the prompt if you recognise them:**

- `LazyInitializationException` — the query was wrong. Do not touch `open-in-view`.
- Flyway checksum mismatch — a migration was edited after being applied. It needs a new version, not a repair.
- `ddl-auto: validate` failing at startup — the entity and the migration disagree.
- Silent N+1 — enable `hibernate.generate_statistics` and count the queries before guessing.

**Skip cost:** You spiral. Four hours in, increasingly desperate prompts, the agent stuck in a loop. `/recover` is the circuit breaker — run it before you lose the afternoon.

---

### `/remember` — between sessions

Two modes. `/remember save` compresses the current session — decisions made, patterns established, progress completed — into `memory.md` in the project root. `/remember restore` loads it at the start of the next session so work picks up exactly where it stopped.

**Run `/remember save`:** at the end of every session. At 1.4 features per day there is no session that is not carrying state into the next one. Features 07 (payments), 17 (PIP engine), 19 (HR module), and 22 (admin metrics) are the ones most likely to span multiple sessions outright.

**Run `/remember restore`:** at the start of any session continuing previous work.

```
/remember save
/remember restore
```

**The rule:** `/remember restore` replaces reading this file and the context files manually. Do not do both — it is redundant. Starting a fresh feature with no previous save: read AGENTS.md. Continuing after a save: `/remember restore`.

**`memory.md` is committed on this repo**, so it travels across machines. The skill filters secrets before writing, but that filter is a judgment call, not a scanner. **Read the file before every commit** — it is short by design. Features 07 and 18 involve pasting gateway payloads and webhook headers into the session, which is exactly where a `key_secret` or signature header can slip through looking like ordinary config.

**Skip cost:** Every new session starts from zero. You spend ten minutes re-explaining what was decided yesterday — or you do not, and the agent contradicts itself by lunch.

---

## Session Workflow

```
NEW FEATURE          → read AGENTS.md + context files
                       /architect feature [N]
                       review the plan, correct it, then implement

CONTINUING WORK      → /remember restore
                       implement

AFTER IMPLEMENTING   → /review
                       fix what it flags
                       update progress-tracker.md
                       mvn verify

SOMETHING BROKE      → one corrective prompt
                       still broken? /recover [paste raw error]

END OF SESSION       → /remember save
                       check the Schedule Checkpoints table in progress-tracker.md
```

**One developer, 24 features, 17 days, no buffer.** The Schedule Checkpoints table in `progress-tracker.md` is the early-warning system — check it against the calendar at the end of each session, not against how much code exists. If a checkpoint slips, the cut order is in the Risk section of `build-plan.md`. Slipping two checkpoints without cutting scope means missing Sep 11.

---

## What "Done" Means

A feature is not complete until all of the following are true:

- [ ] Its Flyway migration is applied and recorded in the Migration Ledger
- [ ] `ddl-auto: validate` passes against the migrated schema
- [ ] Every endpoint returns the `ApiResponse` envelope and is paginated where it returns a list
- [ ] Every non-public method has `@PreAuthorize`, with a test asserting `403` for a wrong role
- [ ] The verification criteria in `build-plan.md` for that feature pass
- [ ] `mvn verify` is green, including Testcontainers integration tests
- [ ] `progress-tracker.md` is updated — checkbox, migration ledger, and any decision or API contract change

Skipping the tracker update is how the next session loses the thread. It is the cheapest step and the one most often dropped.
