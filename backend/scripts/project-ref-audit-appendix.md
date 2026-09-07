---

# Part 4 — Issues Found & How They Were Solved

On **2026-08-31** a full-codebase audit ran via four parallel specialist reviews — **security**,
**correctness / bugs**, **database query optimization**, and **backend runtime performance** — plus
the full test suite. The complete report (findings, verification, fix log) is
`docs/audit-2026-08-31.md`; this is the condensed "what broke and how it was fixed" version.

**Result:** all 4 Critical, all 7 High, and 13 Medium/Low items fixed and verified —
`mvn clean verify` (Docker up): **388 unit + 267 integration tests across 37 IT classes, 0
failures**; Flyway applied all 18 migrations against a real MySQL 8 container. Two regressions
introduced by the audit's own changes were caught by `mvn verify` and fixed (see §4.5).

## 4.1 Critical

### C1 — Unauthenticated Java deserialization of the OAuth2 auth-request cookie

- **File:** `common/security/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java`
- **Symptom / risk:** the in-flight `OAuth2AuthorizationRequest` was round-tripped through
  `SerializationUtils.serialize/deserialize` — i.e. `new ObjectInputStream(...).readObject()` on a
  base64 cookie value that is entirely attacker-controlled, on the `permitAll` path
  `/api/v1/auth/oauth2/callback/*`, executed **before any authentication**. CWE-502: a crafted
  payload is a guaranteed unauthenticated DoS and, given the classpath, an RCE-class gadget
  surface. No signature → an attacker who can set the victim's cookie can also inject their own
  `state`/`redirectUri` (OAuth login-CSRF).
- **Fix:** the request is now serialised as a small **non-polymorphic JSON DTO** (`CookiePayload`)
  via the app `ObjectMapper`, base64url-encoded, with an **HMAC-SHA256 tag** appended
  (`base64(json) + "." + base64(hmac)`). The HMAC key is the existing `moriah.jwt.secret` bytes
  prefixed with a fixed domain-separation label so the tag can't be replayed as a JWT. Read path:
  recompute the HMAC, compare with `MessageDigest.isEqual` (constant-time); any mismatch or parse
  failure returns `null` → clean `OAuth2AuthenticationException`. No `ObjectInputStream` anywhere.

### C2 — A student in two batches aborts the entire nightly PIP run

- **File:** `pip/PipEvaluationService.java`
- **Symptom:** `evaluate()` loaded `alreadyOpenUserIds` (users with a pre-existing open PIP record)
  **once**, then looped the cohort. `currentCohortMetrics()` returns one row per
  `(user_id, batch_id)`, and a student enrolled in two batches is legitimate. So a student who
  trips a rule in batch A gets a `pip_records` insert, then the loop reaches their batch-B row —
  `alreadyOpenUserIds` still doesn't contain them — and inserts again → `uq_one_open_pip` violation
  → the whole `@Transactional evaluate()` rolls back → the job is marked FAILED and **every PIP
  trigger that night, across all batches, is lost.** It recurs every night and leaves orphan
  `PIP_TRIGGERED` audit rows.
- **Fix:** one line — `alreadyOpenUserIds.add(metric.userId())` immediately after `fire(...)`
  inside the loop, so the set means "open record **or** already triggered this run". One PIP per
  user regardless of batch count is exactly the `uq_one_open_pip` invariant.

### C3 — A webhook that fails after its idempotency claim is lost permanently and undetectably

- **Files:** `payment/WebhookIdempotencyService.java`, `payment/PaymentWebhookController.java`,
  `crm/webhook/WhatsAppWebhookService.java`, new `common/job/WebhookReconciliationJob.java`
- **Symptom:** `claim()` runs `REQUIRES_NEW` and commits the `webhook_events` row
  (`status='RECEIVED'`, hardcoded) **before** the business handler runs. If the handler then throws
  (`findById().orElseThrow()`, a deadlock, a connection drop), only the business transaction rolls
  back — the claim persists, the gateway retry sees `claim() == false`, and the event is never
  reprocessed. `processed_at` was never set and there was no terminal-status transition, so a
  **lost** event (money captured, subscription never activated) is byte-for-byte identical to a
  processed one.
- **Fix — four parts:**
  1. `runAndMarkProcessed(gateway, eventId, handler)` — a new `@Transactional` method that runs
     the handler (joins the transaction) then `UPDATE webhook_events SET status='PROCESSED',
     processed_at=NOW(6) WHERE … status='RECEIVED'` **atomically** with the business state change.
  2. `releaseClaim(gateway, eventId)` — `REQUIRES_NEW` `DELETE … WHERE status='RECEIVED'`. The
     webhook controllers wrap `runAndMarkProcessed` in `try/catch (RuntimeException)`; on failure
     they call `releaseClaim` and rethrow → 5xx → the gateway redelivers as fresh work.
  3. Stale-claim reclaim inside `claim()` — a row still `RECEIVED` and older than 15 minutes
     (handler process killed mid-delivery) is deleted and re-inserted on the next delivery. The
     `DELETE` runs **only after a confirmed duplicate-key `INSERT` failure** so the happy path is a
     bare `INSERT` (see the regression note in §4.5).
  4. `WebhookReconciliationJob` — a 10-minute sweep that logs `ERROR` for any `webhook_events` row
     stuck in `RECEIVED` > 30 minutes, so a genuinely wedged payment is visible without reading
     gateway dashboards. (`WhatsAppWebhookService.handleInbound` was also restructured so each
     message is its own claim → process → release unit — a bad message no longer rolls back the
     batch and drops the others' claims.)

### C4 — Rate limiter and audit IP collapse behind a load balancer

- **Files:** `common/security/ClientIpResolver.java`, `common/security/RateLimitFilter.java`,
  `application*.yml`, new `common/config/StartupHardeningWarnings.java`
- **Symptom:** `moriah.security.trusted-proxies` is empty in every profile and there is no
  `forward-headers-strategy`, so in the documented deployment shape (TLS terminated upstream)
  `getRemoteAddr()` is the load balancer's IP for **every** request. `RateLimitFilter` keyed on
  that IP, so all users shared **one** 60-req/min bucket (≈1 req/s total platform throughput), the
  per-IP `/auth/**` throttle — the only brute-force control — was effectively disabled, and every
  `audit_logs.ip_address` recorded the proxy.
- **Fix:** (a) `trusted-proxies` entries may now be **CIDR ranges** (`IpAddressMatcher`), so a real
  LB is configurable; (b) a prominent startup `WARN` fires when the list is empty in a non-local
  profile; (c) `RateLimitFilter` buckets a request carrying a bearer token on `tok:<md5(token)>`
  instead of IP, so authenticated traffic no longer collapses; the global limit was raised 60 →
  300 (now a per-user budget). Tokenless `/auth/**` still buckets by IP, so `TRUSTED_PROXIES` must
  still be set at deploy time — account-level lockout (M12) is the complementary defence and is
  deferred.

## 4.2 High

| # | File(s) | Symptom | Fix |
|---|---|---|---|
| **H1** | `common/config/AsyncConfig.java` | `@EnableAsync` only → `@Async` ran on an **unbounded** `SimpleAsyncTaskExecutor` (new virtual thread per task, no queue, no rejection). An export or payment-webhook burst could drain the connection pool. | `AsyncConfigurer` with a bounded `ThreadPoolTaskExecutor` (4/8/queue 100, `CallerRunsPolicy`) as the default, plus a dedicated `exportExecutor` (1/2/queue 10). `ExportGenerationService.generate` → `@Async("exportExecutor")`. |
| **H2** | `admin/ExportGenerationService.java` | Each sheet builder ran the query into a full `List<Object[]>` **before** the first cell — `SXSSFWorkbook`'s streaming only bounded the XML side. `writeAuditSheet` had no `LIMIT` on `audit_logs` → multi-hundred-MB heap spike / OOM. | Rows now stream from a `TYPE_FORWARD_ONLY`, `fetchSize=Integer.MIN_VALUE` cursor straight into the `SXSSFSheet` (~100 rows live). Every query hard-capped at `MAX_EXPORT_ROWS=200_000` with a `WARN` on truncation. (Also L6 — cells starting `= + - @` are apostrophe-prefixed against CSV formula injection.) |
| **H3** | `common/notification/NotificationWorker.java`, `.../dispatch/NotificationClientConfig.java` | `@Scheduled(fixedDelay=100)` never overlaps itself → one logical consumer, ~1 msg per (dispatch latency + 100ms). A nightly PIP run enqueues ~1,500. WhatsApp/SendGrid clients had **no HTTP timeout** — one hung provider froze the pipeline. | `drainBatch()` pops up to 8 messages/tick and dispatches them **concurrently** on the bounded executor (Redis stays a single consumer). Both clients got explicit 3s connect / 8s read timeouts. |
| **H4** | `common/job/*` + new `JobChainGuard.java` | `@SchedulerLock` is per job-name → no interlock. Attendance finalisation running past 01:45 → metrics refresh starts against half-finalised attendance → denominator corruption. | `JobChainGuard.predecessorSucceededRecently(jobName)` — `MetricsRefreshJob` requires an `AttendanceFinalisationJob` SUCCESS run within 6h; `PipEvaluationJob` requires a `MetricsRefreshJob` one. A missing predecessor → the job records a FAILED `job_runs` row and skips. |
| **H5** | `submission/repository/TaskSubmissionRepository.java` + `V17` | `SubmissionVerificationRetryJob` full-scanned `task_submissions` every 15 min via `findByVerifiedAtIsNull()` — `verified_at` was unindexed. | `V17__audit_2026_08_high_indexes.sql` — `idx_task_submissions_verified_at` (`ALGORITHM=INPLACE, LOCK=NONE`). InnoDB indexes NULLs so `IS NULL` is a short range scan. |
| **H6** | `payment/PaymentWebhookService.java` | On a repeat payment, `activateSubscription` caught the `uq_one_active_subscription` violation from `saveAndFlush` but only **logged** it, then fell through to invoice + batch allocation — card charged, no service, no refund. Worse, a caught `saveAndFlush` failure **poisons the Hibernate session**, so the whole webhook tx then fails to commit → the gateway retries forever. | `activateSubscription` now **pre-checks** for an existing ACTIVE row and returns `false` without attempting the insert — no `saveAndFlush`, no poisoned session. On `false`, `capturePayment` marks the payment `CAPTURED`, writes a distinct `PAYMENT_CAPTURED_NO_SUBSCRIPTION` audit action + `ERROR` log for a manual refund, and **skips** invoice / allocation / event. New unit test covers it. |
| **H7** | `common/security/JwtAuthFilter.java` + new `user/repository/AuthUserView.java` | The filter did `userRepository.findByUuid(uuid)` — the whole `User` entity, incl. the `byte[]` TOTP secret — on every authenticated request, to compare one integer. | New `AuthUserView` Spring Data projection (`id`, `uuid`, `status`, `tokenVersion`) and `findAuthViewByUuid`. The fresh per-request `token_version` check is unchanged in behaviour, just cheaper. |

## 4.3 Medium (landed)

| # | Fix |
|---|---|
| **M3 / M19** | `V18__audit_2026_08_medium_indexes.sql` — adds `idx_payments_status_captured`, `idx_leads_created`, `idx_task_submissions_status_submitted`; drops the redundant `idx_pip_records_user` (left-prefix of `idx_pip_records_pull_block`) and `idx_standups_finalised` (left-prefix of V16's `idx_standups_finalisation`). |
| **M4** | `application.yml` — `hibernate.default_batch_fetch_size: 100`, `hibernate.order_updates: true`. |
| **M7** | `SubscriptionExpiryService`, `PipEvaluationService.fire`, `PaymentWebhookService.activateSubscription` now use `LocalDate.now(ZoneId.of(jobsZone))` (`@Value("${moriah.jobs.zone}")`). On a UTC host the old `LocalDate.now()` dated PIP windows and subscription periods a day short. |
| **M11** | JDBC URL `useSSL=true` (== `sslMode=PREFERRED`, silent plaintext fallback) → `sslMode=${DB_SSL_MODE:REQUIRED}`. `VERIFY_CA` + truststore documented in `application-prod.yml`. |
| **M14** | `spring.data.web.pageable.max-page-size: 100` — the built-in cap was 2000. |
| **M15** | `GithubOAuth2UserService` `RestClient` built with 3s/8s timeouts (was `RestClient.create()`, no timeout, on the login thread). `RazorpayService` builds its SDK client once (lazy double-checked singleton) instead of per `createOrder`. |
| **M17** | `CertificateService.issue` rejects a second **live** certificate for the same `(user, batch, type)` via `existsBy…AndRevokedAtIsNull` (re-issue after revoke still allowed). |
| **M20** | New `ExpiredTokenReaperJob` — nightly, deletes `refresh_tokens` / `password_reset_tokens` / `email_verification_tokens` past a 30-day grace, capped 20k/table/run, `@SchedulerLock` + `JobRunTracker`. |
| **M9** (partial) | `HrDocumentService.upload` slug-sanitises `documentType` (`[^A-Za-z0-9_-]` stripped) before it enters the S3 key / DB column / DTO. `ProjectService` asset-filename spots bundled with the deferred M5. |
| **M10** (partial) | New `StartupHardeningWarnings` logs a `WARN` when `spring.data.redis.password` is blank outside a local profile. Serializer & docker-compose `requirepass` left as-is (serializer swap risks breaking cached shapes; local Redis is network-isolated). |
| **L2 / L17** | Primary Hikari pool → fixed-size (`minimum-idle == maximum-pool-size`, removes the cold-ramp TLS-handshake storm behind the load-test p95) + explicit `max-lifetime` / `keepalive-time` / `leak-detection-threshold`. `connection-timeout` is `${DB_CONNECTION_TIMEOUT_MS:30000}` (env-tunable for prod fail-fast; default kept test-safe — see §4.5). Replica pool given the same. |

## 4.4 Low (landed) & non-defects

- **L1** — `WhatsAppSignatureVerifier` now compares digests with `MessageDigest.isEqual` on decoded
  bytes (constant-time); invalid hex short-circuits to `false`.
- **L3** — GitHub PR-URL regex owner/repo groups changed to `[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?`
  so a segment can never be `.` or `..` (`/repos/../../pulls/N`).
- **L9** — `Accumulator.daysSinceLastActivity` clamped with `Math.max(0, …)` against a future-dated
  `lastActivity`.
- **L10** — the invoice-number code was already correct and deliberate (no year segment, per
  `Constants.INVOICE_PREFIX`'s Javadoc); the drift in `architecture.md` was corrected.
- **L12** — removed the no-op `@Transactional` from `NotificationWorker.markSent` /
  `recordFailedAttempt` (self-invoked → proxy never applied it).
- **L19** — `architecture.md`'s nightly-chain description corrected. The `V10` SQL comment was left
  as-is — editing an applied migration changes its Flyway checksum.
- **Not defects:** L5 (`isAuthenticated()` + reporting-manager service check is the intended design
  — a reporting manager has no role to gate on); L11 (the payroll `JOIN FETCH` is on `@ManyToOne`,
  not a collection — no HHH000104). L8 / L15 / L20 are ops/doc notes with no code change.

## 4.5 Two regressions caught by `mvn verify` (and fixed)

1. **`ConnectionPoolLoadIT` / `BatchFlowIT` — 500s under load.** The L2 change had set
   `connection-timeout: 3000` (the "fail fast on saturation" recommendation), but 3s is too
   aggressive for the co-located 150-thread load tests, which are built to prove the pool
   **queues** gracefully — 12 requests hit `HikariPool-1 - Connection is not available, request
   timed out after 3009ms`. Made it `${DB_CONNECTION_TIMEOUT_MS:30000}` (Hikari's own default;
   production can tighten). The fixed-size pool + lifetimes stay.
2. **`BatchFlowIT.concurrentAllocation` — a deadlock 500.** The C3 `claim()` rewrite did an
   **unconditional** `DELETE … WHERE event_id = ? AND status='RECEIVED' AND …` on every claim; on
   a REPEATABLE-READ transaction that takes a gap lock on the unique index, and concurrent
   first-deliveries of lexically-adjacent `event_id`s (`payment.captured:pay_xxx1`, `…xxx2`)
   deadlocked each other → uncaught → 500. Restructured so the **happy path is a bare `INSERT`**
   (exactly as before, no gap locks) and the stale-row `DELETE` runs **only after** a confirmed
   duplicate-key failure.

## 4.6 Deferred — need a Docker-up focused session or a product decision

| Item | Why deferred |
|---|---|
| **M1, M2, M6, M16, M18** | Rewrite nightly-chain core SQL / transaction structure (metrics refresh, PIP eval, attendance finalisation). Only the 269-test integration suite exercises these paths — do as one batch with `mvn verify` green. M1/M2: PIP & metrics read the whole `student_metrics` / all-history and filter the cohort in Java (should push the cohort filter into SQL). M6: `refresh()`/`evaluate()` each span the whole population in one transaction (should chunk-commit). M16/M18: `AttendanceFinalisationService.saveAll(absentRows)` is N single-row inserts with a read-then-write race (should be a batch `INSERT … ON DUPLICATE KEY`). |
| **M5** (+ M9 remainder) | `@Transactional`-wraps-S3-upload refactor across `ResumeService` / `HrDocumentService` / `ProjectService` — detached-entity lifecycle risk that unit tests won't catch. |
| **M8** | `PayrollService.generate` per-line result list is an **API response-shape change** → needs frontend / OpenAPI coordination. |
| **M12** | Account lockout / TOTP brute-force cap — a new security feature on the `@Transactional` login path; needs the auth IT suite. C4's per-token buckets + the 10/min IP limit are the interim mitigation. |
| **M13** | CRM lead visibility: **is the sales pipeline shared team-wide or scoped per agent?** A product decision; the authz model was not changed unilaterally. |
| **L7, L13, L14, L16** | Small features / build-gate additions (2FA-endpoint auth path, OWASP dependency-check plugin, Meta `hub.challenge` handler, `double` → int percentage). |

---

# Part 5 — Running & testing

```bash
# prerequisites: JDK 21, Maven, Docker Desktop running
cp .env.example .env          # dev-only values already present
docker compose up -d          # MySQL 8 (moriah_migrate / moriah_app pre-provisioned), Redis 7, MinIO
mvn spring-boot:run           # http://localhost:8080

# health / docs
GET http://localhost:8080/actuator/health         → {"status":"UP"}
http://localhost:8080/swagger-ui.html             (dev profile only)

# full test gate (unit + Testcontainers integration against real MySQL + Redis)
mvn verify
```

- Sample users / batches / leads live in `db/testdata/R__dev_seed_data.sql` — a **Flyway repeatable
  migration**, run only in the `dev` profile (`application-dev.yml` adds `classpath:db/testdata` to
  `spring.flyway.locations`; `test`/`prod` keep the default and never load it). Reference data
  (roles, plans) is seeded by `V4`/`V5`.
- `context/progress-tracker.md` — the authoritative project-status log (feature checklist,
  migration ledger, decision log, UAT scenarios).
- To regenerate the API docs after an endpoint change: re-export `docs/openapi.json` from
  `GET /v3/api-docs` (dev), then `python scripts/gen-api-doc.py` and
  `python scripts/gen-project-ref.py`.
