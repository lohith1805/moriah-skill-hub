# Session context — 2026-09-02

Short log of what changed in this working session, on top of the earlier
audit-2026-08-31 hardening work. All of it landed in one branch:

- **Branch:** `chore/audit-hardening-docs-logging`
- **Commit:** `27d5e50` (hardening + docs + seed data + logging) — then merged to `main`
- Repo is **local only** (no git remote).

---

## 1. Role-aware Postman collection

`scripts/gen-postman.py` was rewritten and `docs/postman/moriah-skillhub-postman.zip`
regenerated.

- **Auth folder split by role.** One `Login — <role>` request per seeded account
  (`student1`, `student3`, `pm`, `dev`, `sales`, `ba`, `client`), each storing a
  role-specific token: `{{studentAccessToken}}`, `{{pmAccessToken}}`, …
- **Admin / HR 2FA sub-folders** with 3 steps (login → enable → verify). Step 3 has a
  pre-request script that computes the current TOTP from `{{adminTotpSecret}}` using
  Postman's built-in CryptoJS, so the flow is one click. Manual `pyotp` fallback still
  works if the secret var is empty.
- **Every module request pins the matching role token** via a request-level Bearer
  override, derived from the controller `@PreAuthorize`. Public paths stay `noauth`.
- Seeded user UUIDs are pre-filled collection variables (`{{student1Uuid}}` … `{{adminUuid}}`).
- All variables live on the collection, so it works with no environment selected.

## 2. Logging

**Correlation ids**
- `common/security/MdcLoggingFilter.java` (new) — puts a per-request `X-Request-Id`
  into the SLF4J MDC (`requestId`), echoes it as a response header, `MDC.clear()` in
  `finally`. Ordered ahead of the Spring Security chain.
- `common/security/JwtAuthFilter.java` — adds `MDC.put("userId", …)` once a token is
  fully validated.

**`application-prod.yml`**
- Console: ECS-format JSON, one object per line (`logging.structured.format.console: ecs`).
- File: rotating `logs/skillhub.log` (`LOG_FILE` env overrides the path). Rolls at 50 MB
  or daily; keeps 30 days / 3 GB of gzip archives; cleans on start. Readable pattern
  with `[req=…,user=…]`.
- Levels: `root: WARN`, `com.moriah.skillhub: INFO`, `org.flywaydb` + `com.zaxxer.hikari`
  kept at INFO for startup forensics.
- Also removed the childless `spring.datasource:` key (was a YAML-LS error).

**`application-dev.yml`**
- `spring.jpa.show-sql` → `false` (it duplicated `org.hibernate.SQL: DEBUG` and bypassed
  the log framework).
- Added `org.hibernate.orm.jdbc.bind: TRACE` for bind-param values.

**`SecurityConfig.java`**
- `PUBLIC_PATHS` now also lists the bare `/v3/api-docs`, `/v3/api-docs.yaml`,
  `/swagger-ui` — see issue #3.

> `application-preview.yml` / `.env.preview` were discussed but **not** changed here —
> they live on another machine. Guidance: it's a dev-like profile, so use readable
> verbose logging (not the prod ECS block), and fix `hibernate[format_sql]` →
> `hibernate.format_sql`.

## 3. Diagnosed: `GET /v3/api-docs` → 401

The 401 body was Spring Security's `authenticationEntryPoint` (charset ISO-8859-1), not
a 404 from `GlobalExceptionHandler` — so the path wasn't matching `permitAll()` in the
running app. Two causes, both covered:
1. The running copy's `PUBLIC_PATHS` didn't cover the bare `/v3/api-docs` → added it.
2. `springdoc.api-docs.enabled: false` (which `application-prod.yml` sets) means no
   handler → servlet ERROR-dispatch to `/error` → not public → same 401. So don't run
   the `prod` profile if you need the spec/UI.

## 4. Verified

`mvn -o spring-boot:run` with `SPRING_PROFILES_ACTIVE=prod` against local Docker infra
(`DB_SSL_MODE=DISABLED`, `CORS_ALLOWED_ORIGINS=…` supplied since prod ignores `.env`):

- `Started SkillHubApplication in 36.323 seconds`, Flyway validated 18 migrations.
- `logs/skillhub.log` created, readable lines with timestamps + `[req=,user=]`.
- Console emitted ECS JSON (`{"@timestamp":…,"log":{"level":…},"service":{"name":"skillhub"},"ecs":{"version":"8.11"}}`).
- Expected prod WARNs: empty `trusted-proxies`, blank `REDIS_PASSWORD`.
- Stopped cleanly; port 8080 free.

`mvn -o compile` → BUILD SUCCESS after all Java changes.

---

## 5. Dev seed data → Flyway repeatable migration

`db/testdata/dev-seed.sql` + `DevDataLoader.java` (an `ApplicationRunner` that swallowed errors
into a WARN) are **removed**. Replaced by:

- `db/testdata/R__dev_seed_data.sql` — a Flyway **repeatable** migration.
- `application-dev.yml` → `spring.flyway.locations: classpath:db/migration,classpath:db/testdata`.
  `application.yml` (prod) and `application-test.yml` keep the default `classpath:db/migration`,
  so the sample data never reaches the `test` or `prod` databases.

Now `mvn spring-boot:run` seeds the 10 test accounts + batch/sprint/tasks/lead/employees as part
of `flyway:migrate` on any fresh machine — no separate script. Re-runs are idempotent
(`INSERT IGNORE` warns on rows that already exist; a fresh DB inserts silently). Verified: Flyway
logged `Successfully applied 1 migration ... "dev seed data"`.

## 6. Frontend-integration Decision 1 → Option A (staff invite + client approval)

Implements the FE gap report's B1.1–B1.3. See `frontend-backend-gap-report.md` for the three
decisions (1 → Option A, 2 → Hybrid/FE-only, 3 → drop multi-role/FE-only).

**Migration** `V19__staff_invite_and_client_approval.sql`
- extends `chk_users_status` with `INVITED`, `PENDING_APPROVAL`, `REJECTED`
- new `staff_invite_tokens` table (mirrors `email_verification_tokens`)
- header note: deliberately re-opens build-plan feature 21's "a client cannot self-register"

**New endpoints**

| Method | Path | Auth | What |
|---|---|---|---|
| POST | `/api/v1/admin/users` | ADMIN | create staff → `INVITED`, email accept-invite link (7-day TTL). Rejects `STUDENT`/`CLIENT` roles. |
| POST | `/api/v1/admin/users/{userUuid}/resend-invite` | ADMIN | burn the old link, issue + email a new one (only while `INVITED`) |
| POST | `/api/v1/auth/accept-invite` | public | `{token,password}` → set password, `INVITED → ACTIVE`, auto-login (2FA challenge for an invited ADMIN/HR_MANAGER) |
| POST | `/api/v1/auth/register/client` | public | `{fullName,email,phone,password,companyName,industry?}` → `PENDING_APPROVAL` user + `CLIENT` role + `INACTIVE` clients row; "application received" email |
| GET | `/api/v1/admin/client-requests?status=` | ADMIN | paged queue; status ∈ {PENDING_APPROVAL (default), REJECTED} |
| POST | `/api/v1/admin/client-requests/{userUuid}/approve` | ADMIN | `PENDING_APPROVAL → ACTIVE`, clients row `→ ACTIVE`, "approved" email |
| POST | `/api/v1/admin/client-requests/{userUuid}/reject` | ADMIN | `{reason}` → `REJECTED`, "not approved" email with reason |

`AuthService.login` now rejects the three new statuses with distinct 403s:
`ACCOUNT_INVITE_PENDING`, `ACCOUNT_PENDING_APPROVAL`, `ACCOUNT_REGISTRATION_REJECTED`.

**New files:** `auth/entity/StaffInviteToken`, `auth/repository/StaffInviteTokenRepository`,
`auth/dto/{AcceptInviteRequest,ClientRegisterRequest}`,
`admin/dto/{CreateStaffRequest,ClientRequestResponse,RejectClientRequest}`,
`client/ClientRegistrationService`, `admin/ClientApprovalService`, `admin/ClientRequestController`,
+ additions to `AuthService`, `AdminUserService`, `AdminUserController`, `AuthController`,
`ErrorCode`, `UserStatus`, `Constants`, `AuthLinkProperties`, `application*.yml`.
`STAFF_INVITE_URL_TEMPLATE` added to the `.env` template (default
`http://localhost:3000/accept-invite?token={token}`).

**Tests:** `ClientRegistrationServiceTest` (2), `ClientApprovalServiceTest` (6), `AdminUserServiceTest`
+3 → 17 unit tests green. Full `mvn verify` + `docs/openapi.json` re-export + `gen-*` doc/Postman
regen are the remaining follow-ups.
