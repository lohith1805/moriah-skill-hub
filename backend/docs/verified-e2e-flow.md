# Verified end-to-end flow

A single continuous run that boots the whole stack and walks one path through every
major module against **real APIs and a real database** — no mocks, no stubbed gateways
beyond the payment provider's own webhook (which cannot reach `localhost`).

- **Verified:** 2026-09-03, **44/44 steps green** (`ALL STEPS PASSED`).
- **Backend build:** `dev` profile, Flyway `V1`–`V34` + `R__dev_seed_data.sql`.
- **Harness:** `scripts/e2e-flow.py` (see [§7](#7-re-running-it)).
- Two defects were found and fixed during this verification — see [§6](#6-defects-found--fixed).

---

## 1. Prerequisites

| Need | Detail |
|---|---|
| Docker Desktop | MySQL, MySQL replica, Redis, MinIO containers |
| Java 21, Maven | backend |
| Node 20+, npm | frontend |
| Python 3.11+ | the flow harness only (stdlib + `docker exec mysql`) |
| Free ports | `8080` backend, `5173` frontend, `3316` MySQL, `3307` replica, `6379` Redis, `9000/9001` MinIO |

> Windows note: a native `MySQL97` service squats `3306`. Docker MySQL is therefore
> published on **3316** (`DB_PORT=3316 docker compose up -d`), and the backend is
> started with `DB_PORT=3316` / `DB_REPLICA_PORT=3316`.

---

## 2. Step 0 — infrastructure

```bash
cd "Moraih Backend"
DB_PORT=3316 docker compose up -d mysql mysql-replica redis minio
docker ps            # all four healthy
```

---

## 3. Step 1 — backend

```bash
cd "Moraih Backend"
DB_HOST=localhost DB_PORT=3316 \
DB_REPLICA_HOST=localhost DB_REPLICA_PORT=3316 \
REDIS_HOST=localhost REDIS_PORT=6379 \
S3_ENDPOINT=http://localhost:9000 \
SPRING_PROFILES_ACTIVE=dev \
mvn -o spring-boot:run
```

Wait for `Started SkillHubApplication`, then:

```bash
curl -s http://localhost:8080/actuator/health      # {"status":"UP"}
```

Flyway applies all migrations and re-runs `R__dev_seed_data.sql` (test accounts, 4
batches, sprints, lessons/question-banks, a CRM pipeline, one graduate + certificate).

**Seeded logins** (all `Password123!`): `admin@moriah.test`, `pm@moriah.test`,
`sales@moriah.test`, `hr@moriah.test`, `ba@moriah.test`, `dev@moriah.test`,
`client@moriah.test`, `student1@`…`student9@moriah.test`.

> The harness raises `AUTH_RATE_LIMIT_PER_MIN` (default **10/min**, IP-scoped, fixed
> window) because it issues ~13 `/auth/*` calls in a burst. For a manual walk-through
> the default is fine.

---

## 4. Step 2 — frontend

```bash
cd moriah-skill-hub-updated
npm install
npm run dev            # http://localhost:5173
```

`.env` leaves `VITE_API_BASE_URL` empty → the SPA calls the **relative** path
`/api/v1/...`, so the browser Network tab shows `localhost:5173/api/v1/...`. Vite's
dev proxy (`vite.config.js`) transparently forwards `/api` → `http://localhost:8080`.
That is expected — the request is served by `:8080`. The only call that hits `:8080`
directly is the OAuth provider callback (absolute redirect URI).

---

## 5. The verified flow

Every row below was executed in one run, in order. `→` is the observed result.

### Part A · Public / unauthenticated

| # | Call | Result |
|---|---|---|
| A1 | `GET /api/v1/plans` | 200 · 5 plans (`STARTER, PROFESSIONAL, PROJECT_BASED, INTERNSHIP, CORPORATE_PROGRAM`) — Redis-cached, self-heals a poison entry |
| A2 | `GET /api/v1/public/stats` | 200 · `{graduates, activeLearners, activeBatches, placements, certificatesIssued, hiringPartners}` |

### Part B · New student — register → resend → verify → login

| # | Call | Result |
|---|---|---|
| B1 | `POST /api/v1/auth/register` `{fullName,email,phone,password}` | 201 · `{uuid, fullName, email}`, account `PENDING_VERIFICATION` |
| B2 | `POST /api/v1/auth/resend-verification` `{email}` | 200 · non-enumerating (same response for any address) |
| B3 | *(read verification token from `notifications.payload`)* | token extracted |
| B4 | `POST /api/v1/auth/verify-email` `{token}` | 200 · account → `ACTIVE` |
| B5 | `POST /api/v1/auth/login` `{email,password}` | 200 · access + refresh token pair |

### Part C · New student session — no subscription = limited access

| # | Call | Result |
|---|---|---|
| C1 | `GET /api/v1/users/me` | 200 · profile incl. `twoFactorEnabled`, `isComplete` |
| C2 | `GET /api/v1/subscriptions/me` | **404 `SUBSCRIPTION_NOT_FOUND`** — dashboard renders but entitlement-gated features are locked |
| C3 | `GET /api/v1/subscriptions/me/invoices` | 200 · `[]` |
| C4 | `GET /api/v1/batches` (student-scoped) | 200 · 0 rows (none enrolled) |
| C5 | `GET /api/v1/lessons` | 200 · catalogue |
| C6 | `GET /api/v1/notifications/unread-count` | 200 · `{unreadCount:0}` |
| C7 | `POST /api/v1/subscriptions/checkout` `{planCode:PROJECT_BASED, gateway:RAZORPAY, trackCode:FULL_STACK}` | 200 · Razorpay `order_…` created (subscription is **not** activated here — the signed webhook does that) |

### Part D · Seeded student with an ACTIVE `PROJECT_BASED` subscription (`student1@`)

| # | Call | Result |
|---|---|---|
| D1 | `POST /api/v1/auth/login` | 200 |
| D2 | `GET /api/v1/subscriptions/me` | 200 · `PROJECT_BASED` / `ACTIVE` / end date |
| D3 | `GET /api/v1/subscriptions/me/invoices` | 200 · list (rows appear once a payment is captured) |
| D4 | `GET /api/v1/batches` | 200 · 1 enrolled batch |
| D5 | `GET /api/v1/certificates/me` | 200 |
| D6 | `GET /api/v1/attendance/me` | 200 |
| D7 | `GET /api/v1/pip/me` | 404 (no open PIP — expected) |

### Part E · Trainer / PM (`pm@`)

| # | Call | Result |
|---|---|---|
| E1 | `POST /api/v1/auth/login` | 200 |
| E2 | `GET /api/v1/batches` | 200 · 4 batches (full list) |
| E3 | `GET /api/v1/batches/1` | 200 · `FS-2026-01` |
| E4 | `GET /api/v1/batches/1/students` | 200 · roster (2) |
| E5 | `GET /api/v1/sprints?batchId=1` | 200 · 1 sprint |
| E6 | `GET /api/v1/standups?batchId=1` | 200 · 3 standups |
| E6b | `GET /api/v1/standups` *(no `batchId`)* | **400 `VALIDATION_FAILED`** ("Required request parameter 'batchId' is missing.") — was 500 before the fix |
| E7 | `GET /api/v1/reviews/queue` | 200 |

### Part F · Lead generation (`sales@`)

| # | Call | Result |
|---|---|---|
| F1 | `POST /api/v1/auth/login` | 200 |
| F2 | `GET /api/v1/leads` | 200 · pipeline |
| F3 | `GET /api/v1/leads/targets/leaderboard` | 200 |
| F4 | `GET /api/v1/leads/targets/me` | 200 |
| F5 | `GET /api/v1/leads/campaigns` | 200 |
| F6 | `POST /api/v1/leads/inbound` `{name,email,phone,message,leadType}` *(public, unauthenticated)* | 201 |

### Part G · Admin (`admin@`, mandatory 2FA)

| # | Call | Result |
|---|---|---|
| G1 | `POST /auth/login` → `POST /auth/2fa/enable` → TOTP → `POST /auth/2fa/verify` | 200 · session issued (first admin login forces 2FA setup) |
| G2 | `GET /api/v1/admin/metrics/overview` | 200 (replica pool falls back to primary in dev) |
| G3 | `GET /api/v1/admin/metrics/revenue` | 200 |
| G4 | `GET /api/v1/admin/users` | 200 |
| G5 | `GET /api/v1/admin/payments/summary` | 200 |
| G6 | `GET /api/v1/admin/coupons` | 200 |
| G7 | `GET /api/v1/admin/client-requests` | 200 |
| G8 | `GET /api/v1/admin/audit` | 200 |
| G9 | `GET /api/v1/admin/plans` *(this path is `POST`/`PUT`/`DELETE`-only; the admin UI lists plans via `GET /plans`)* | **405 `METHOD_NOT_ALLOWED`** — was 500 before the fix |

---

## 6. Defects found & fixed

Both were in `GlobalExceptionHandler` — client mistakes surfacing as a misleading `500`.

| Was | Now | Fix |
|---|---|---|
| Omitting a required `@RequestParam` (`GET /standups` with no `batchId`) → **500 `INTERNAL_ERROR`** | **400 `VALIDATION_FAILED`**, names the parameter | `@ExceptionHandler({MissingServletRequestParameterException, MethodArgumentTypeMismatchException})` |
| Hitting a path with the wrong verb (`GET /admin/plans`) → **500 `INTERNAL_ERROR`** | **405 `METHOD_NOT_ALLOWED`** | `@ExceptionHandler(HttpRequestMethodNotSupportedException)` + `ErrorCode.METHOD_NOT_ALLOWED` |

---

## 7. Re-running it

```bash
# infra + backend up (raise the auth limit for the burst of logins)
AUTH_RATE_LIMIT_PER_MIN=500 GLOBAL_RATE_LIMIT_PER_MIN=2000 \
DB_HOST=localhost DB_PORT=3316 DB_REPLICA_HOST=localhost DB_REPLICA_PORT=3316 \
REDIS_HOST=localhost REDIS_PORT=6379 S3_ENDPOINT=http://localhost:9000 \
SPRING_PROFILES_ACTIVE=dev mvn -o spring-boot:run

# then, in another shell:
python scripts/e2e-flow.py      # prints PASS/FAIL per step, exits non-zero on any failure
```

The harness is idempotent: it registers a fresh `e2e.student.<timestamp>@example.com`
(unique email **and** phone — `users.phone` is `UNIQUE`), and resets `admin@`'s 2FA to
setup-required so Part G can run head-lessly.

---

## 8. Known caveats (not defects)

- **Razorpay / Stripe webhooks cannot reach `localhost`.** `POST /subscriptions/checkout`
  creates the order, but the subscription only activates — and the `invoices` row + PDF
  are only produced — when the signed `payment.captured` webhook arrives. Locally that
  needs a tunnel (e.g. Cloudflare) registered as the provider's webhook URL. Without it,
  Parts C7 / D3 show "order created" / an empty invoice list, which is correct.
- **`AUTH_RATE_LIMIT_PER_MIN`** default is 10/min per IP. A rapid scripted walk trips it;
  a human walking the UI never will.
- **Mocked feature areas** (no backend yet, frontend falls back to local data, not
  covered here): BA client docs / resource plans, client project briefs, developer
  projects / bug challenges, HR biometric attendance, trainer cross-batch student
  management, student performance-summary / auto-PIP.
