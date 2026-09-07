# Required Integrations, Infrastructure & Secrets

Everything the backend needs in `.env`, split into three kinds:

1. **Local infrastructure** — services `docker compose` runs for you; you only point the app at them.
2. **Secrets you generate yourself** — no third party involved.
3. **Third-party API keys** — obtained from Google, GitHub, Razorpay, Stripe, SendGrid, Meta.

> **The one rule:** every `${VAR}` in `src/main/resources/application*.yml` that has **no**
> `:default` must be present in `.env` or the app fails to start (placeholder resolution error).
> Most of them can be any dummy string until you actually want to exercise that integration —
> only the items in parts 2 and (per feature) 3 need real values.

`.env` is gitignored. In a deployed environment these come from the platform's secret store,
never a file.

- Related: `docs/webhooks.md` (payment webhook setup in detail), `.env.example` (canonical list),
  `docs/PROJECT-REFERENCE.md` §2.4 / §2.10 (how auth and the DB are wired).

---

## Part 1 — Local infrastructure (`docker compose up -d`)

`docker-compose.yml` provides five things. Start them, then the app connects over the values below.

```bash
cp .env.example .env      # if you haven't already
docker compose up -d      # mysql, mysql-replica, redis, minio
```

| Var | Local value | Service |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `moriah_skillhub` | `skillhub-mysql` |
| `DB_USERNAME` / `DB_PASSWORD` | `moriah_app` / `app_dev_only` | **must match** `docker/mysql-init/01-users.sql` |
| `MIGRATE_DB_USERNAME` / `MIGRATE_DB_PASSWORD` | `moriah_migrate` / `migrate_dev_only` | same file (Flyway user) |
| `DB_REPLICA_HOST` / `DB_REPLICA_PORT` | `localhost` / `3307` | `skillhub-mysql-replica` (admin exports / heavy reads) |
| `DB_POOL_MAX` / `DB_REPLICA_POOL_MAX` | `10` / `10` | Hikari sizing (defaults are higher — keep small locally) |
| `DB_SSL_MODE` | `REQUIRED` works against the docker image (encrypts, doesn't verify). Set `DISABLED` if you hit a TLS handshake error locally. | — |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / *(blank)* | `skillhub-redis` |
| `MYSQL_ROOT_PASSWORD` | `root_dev_only` | docker-compose only — **not read by the app** |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | `skillhub_minio` / `minio_dev_only` | docker-compose only |
| `MINIO_API_PORT` / `MINIO_CONSOLE_PORT` | `9000` / `9001` | docker-compose only |
| `S3_ENDPOINT` | `http://localhost:9000` | **must set** — blank means the SDK talks to real AWS |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `skillhub_minio` / `minio_dev_only` | = your MinIO root creds |
| `S3_BUCKET` / `S3_REGION` | `moriah-skillhub` / `us-east-1` | create the bucket once ↓ |

### One-time: create the MinIO bucket

MinIO starts with no buckets; the app does not create them.

```bash
docker exec skillhub-minio mc alias set local http://localhost:9000 skillhub_minio minio_dev_only
docker exec skillhub-minio mc mb --ignore-existing local/moriah-skillhub
```

MinIO console for browsing uploads: <http://localhost:9001> (login = `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`).

---

## Part 2 — Secrets you generate yourself

No third party. These **must** be real (correct length/entropy) even locally, or auth breaks at
first use.

| Var | What | Generate it |
|---|---|---|
| `JWT_SECRET` | HMAC key for signing access tokens (HS512). **Rejected if < 64 bytes** — `change_me` breaks the first login. | `openssl rand -base64 64` |
| `TOTP_ENCRYPTION_KEY` | AES-256-GCM key that encrypts each user's TOTP secret at rest. Must be a valid **base64 of 32 bytes**. | `openssl rand -base64 32` |
| `JWT_KEY_ID` | just a label for future key rotation | any string, e.g. `v1` |
| `DB_PASSWORD` / `MIGRATE_DB_PASSWORD` | local DB passwords | any value **that matches `docker/mysql-init/01-users.sql`** — for the shipped init that's `app_dev_only` / `migrate_dev_only` |

`openssl` is on your PATH (mingw64), so run the commands above in the Git Bash terminal.

**PowerShell alternative** (no openssl):

```powershell
# JWT_SECRET  → 64 bytes
$b = [byte[]]::new(64); [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
# TOTP_ENCRYPTION_KEY  → 32 bytes
$b = [byte[]]::new(32); [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

---

## Part 3 — Third-party API keys

The dispatch/gateway code is unit-tested against mocks and degrades gracefully, so to just
**boot the app, run migrations, and click around Swagger** you can leave every value in this part
as `dummy`. Get real keys per feature you want to test end-to-end.

The OAuth redirect URI the app expects is always:

```
{baseUrl}/api/v1/auth/oauth2/callback/{google|github}
```

Local `baseUrl` = `http://localhost:8080`.

`application.yml` pins each registration's `redirect-uri` to this path. If you instead register
the Spring-default `{baseUrl}/login/oauth2/code/{google|github}` in the provider console, the
provider bounces the browser to a path the login filter no longer listens on —
`OAuth2DefaultCallbackFallbackController` catches it and redirects to the SPA sign-in screen with
an `oauth_callback_misrouted` error instead of completing the login. Fix the registered URI to the
`/api/v1/auth/oauth2/callback/...` form above.

---

### 3.1 Google OAuth2 — "Sign in with Google"

**Vars:** `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
**Console:** <https://console.cloud.google.com/apis/credentials>

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create (or pick) a project.
2. **APIs & Services → OAuth consent screen**: choose **External**, fill app name + support email,
   add scopes `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`. While the app is
   in **Testing**, add your Google account under **Test users**.
3. **APIs & Services → Credentials → Create Credentials → OAuth client ID.**
4. **Application type: Web application.** Under **Authorized redirect URIs** add:
   `http://localhost:8080/api/v1/auth/oauth2/callback/google`
   (add the production URL later as a second entry).
5. Create → copy **Client ID** → `GOOGLE_CLIENT_ID`, **Client secret** → `GOOGLE_CLIENT_SECRET`.

---

### 3.2 GitHub OAuth App — "Sign in with GitHub" (also captures `github_username`)

**Vars:** `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
**Settings:** <https://github.com/settings/developers>

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
   ([direct link](https://github.com/settings/developers)). For an org app use
   *Organization settings → Developer settings*.
2. **Application name:** anything. **Homepage URL:** `http://localhost:8080`.
3. **Authorization callback URL:** `http://localhost:8080/api/v1/auth/oauth2/callback/github`
4. **Register application** → copy **Client ID** → `GITHUB_CLIENT_ID`.
5. Click **Generate a new client secret** → copy it → `GITHUB_CLIENT_SECRET` (shown once).

The app requests the `read:user, user:email` scope automatically — you don't configure scopes on
the app itself.

---

### 3.3 GitHub Personal Access Token — student PR verification

**Var:** `GITHUB_API_TOKEN` — a **server-side** token, *different* from the OAuth app above.
Used by `SubmissionVerificationRetryJob` / `GithubVerificationService` to check that a submitted
PR exists, its author matches `users.github_username`, and its state is open.
**Tokens:** <https://github.com/settings/tokens>

**Fine-grained token (recommended):**
1. <https://github.com/settings/personal-access-tokens/new>
2. **Resource owner:** you (or the org that owns the repos students fork).
3. **Repository access:** *Public repositories (read-only)* is enough if students always use
   public forks; otherwise *Only select repositories*.
4. **Permissions → Repository:** set **Pull requests: Read-only** (and **Metadata: Read-only**,
   which is required and auto-selected).
5. Generate → copy `github_pat_…` → `GITHUB_API_TOKEN`.

**Classic token (simpler):**
1. <https://github.com/settings/tokens/new>
2. Scope: **`repo`** (or **`public_repo`** if only public repos are involved).
3. Generate → copy `ghp_…` → `GITHUB_API_TOKEN`.

---

### 3.4 Razorpay — checkout + payment/refund webhook

**Vars:** `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`
**Dashboard:** <https://dashboard.razorpay.com>

1. Sign up / log in, then toggle to **Test Mode** (top of the dashboard).
2. **Settings → API Keys → Generate Test Key**
   ([direct link](https://dashboard.razorpay.com/app/keys)).
   Copy **Key Id** (`rzp_test_…`) → `RAZORPAY_KEY_ID` and **Key Secret** → `RAZORPAY_KEY_SECRET`
   (the secret is shown **once**).
3. **Settings → Webhooks → Add New Webhook**
   ([direct link](https://dashboard.razorpay.com/app/webhooks)):
   - **Webhook URL:** `https://<your-host>/api/v1/webhooks/razorpay`
   - **Secret:** type any string → put the **same** value in `.env` as `RAZORPAY_WEBHOOK_SECRET`
     (HMAC is symmetric; for local testing it just has to match what you sign with).
   - **Active events:** tick **`payment.captured`** and **`refund.processed`**.
4. Local testing without a public URL: see `docs/webhooks.md` → *Local test with curl* (compute
   the `X-Razorpay-Signature` yourself with `openssl`).

---

### 3.5 Stripe — checkout + checkout/refund webhook

**Vars:** `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
(`STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL` are frontend routes — leave the localhost:3000 defaults.)
**Dashboard:** <https://dashboard.stripe.com>

1. Sign up / log in, then switch to **Test mode** (toggle, top right).
2. **Developers → API keys** ([direct link](https://dashboard.stripe.com/test/apikeys)): copy the
   **Secret key** (`sk_test_…`) → `STRIPE_SECRET_KEY`.
3. **Developers → Webhooks → Add endpoint**
   ([direct link](https://dashboard.stripe.com/test/webhooks)):
   - **Endpoint URL:** `https://<your-host>/api/v1/webhooks/stripe`
   - **Events to send:** **`checkout.session.completed`** and **`charge.refunded`**
   - After creating it, reveal the **Signing secret** (`whsec_…`) → `STRIPE_WEBHOOK_SECRET`.
4. **Local testing (easiest):** install the [Stripe CLI](https://stripe.com/docs/stripe-cli), then:
   ```bash
   stripe login
   stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe   # prints a whsec_… → put in .env, restart the app
   stripe trigger checkout.session.completed
   ```
   The CLI signs the forwarded events, so no manual signature needed. Details in `docs/webhooks.md`.

---

### 3.6 SendGrid — transactional email (verification + password-reset links)

**Var:** `SENDGRID_API_KEY` (`SENDGRID_FROM_ADDRESS` / `SENDGRID_FROM_NAME` have defaults but the
from-address must be a verified sender).
**Dashboard:** <https://app.sendgrid.com>

1. Create a free account at <https://signup.sendgrid.com/> (100 emails/day free).
2. **Settings → API Keys → Create API Key**
   ([direct link](https://app.sendgrid.com/settings/api_keys)): name it, choose **Restricted
   Access** with **Mail Send: Full Access** (or Full Access). Copy the key (`SG.…`, shown once)
   → `SENDGRID_API_KEY`.
3. **Settings → Sender Authentication → Verify a Single Sender**
   ([direct link](https://app.sendgrid.com/settings/sender_auth)): verify the address you'll put
   in `SENDGRID_FROM_ADDRESS` (or authenticate a whole domain). SendGrid rejects mail from an
   unverified sender.

---

### 3.7 Meta WhatsApp Cloud API — WhatsApp notifications + inbound webhook

**Vars:** `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`
(`WHATSAPP_API_BASE_URL` default `https://graph.facebook.com/v18.0` is fine.)
**Portal:** <https://developers.facebook.com/apps>

1. Log in at [Meta for Developers](https://developers.facebook.com/) → **My Apps → Create App**
   → type **Business**.
2. On the app dashboard, **Add product → WhatsApp → Set up**.
3. **WhatsApp → API Setup**: Meta gives you a **test phone number** and a **temporary access
   token** (valid 24 h). Copy:
   - **Phone number ID** → `WHATSAPP_PHONE_NUMBER_ID`
   - **Temporary access token** → `WHATSAPP_ACCESS_TOKEN`
     For a non-expiring token, create a **System User** in
     [Business Settings](https://business.facebook.com/settings/system-users) and generate a token
     with the `whatsapp_business_messaging` and `whatsapp_business_management` permissions.
4. **App dashboard → App settings → Basic → App Secret → Show** → `WHATSAPP_APP_SECRET`
   (used to verify the `X-Hub-Signature-256` HMAC on inbound `POST /api/v1/webhooks/whatsapp`).
5. **WhatsApp → Configuration → Webhook**: Callback URL
   `https://<your-host>/api/v1/webhooks/whatsapp`, plus a **verify token** of your choice.
   > ⚠️ **Known gap (audit item L14):** the backend has **no `GET /api/v1/webhooks/whatsapp`
   > handler** for Meta's `hub.challenge` verification handshake, so completing webhook
   > registration through Meta's UI needs that endpoint added first. Outbound WhatsApp
   > (notifications) works without it.

`get-started` reference: <https://developers.facebook.com/docs/whatsapp/cloud-api/get-started>

---

## Part 4 — Plain config (no secret; override only if you need to)

All of these have working defaults. `SERVER_PORT` (8080), `SPRING_PROFILES_ACTIVE` (dev),
`JOBS_ZONE` (Asia/Kolkata), `AUTH_RATE_LIMIT_PER_MIN` (10), `GLOBAL_RATE_LIMIT_PER_MIN` (300),
`TRUSTED_PROXIES` (blank — set to the load-balancer IP/CIDR in production),
`CORS_ALLOWED_ORIGINS` (**prod profile only** — dev uses the hardcoded localhost list in
`application-dev.yml`), and every `*_CRON` value
(`ATTENDANCE_FINALISATION_CRON`, `METRICS_REFRESH_CRON`, `PIP_EVALUATION_CRON`,
`SUBSCRIPTION_EXPIRY_CRON`, `SUBMISSION_VERIFICATION_RETRY_CRON`, `QUIZ_ATTEMPT_EXPIRY_CRON`,
`NOTIFICATION_REAPER_CRON`, `WEBHOOK_RECONCILIATION_CRON`; the token-reaper job's schedule is the
property `moriah.token-reaper.cron`, default `0 20 3 * * *`).

`EMAIL_VERIFICATION_URL_TEMPLATE` / `PASSWORD_RESET_URL_TEMPLATE` / `STAFF_INVITE_URL_TEMPLATE` /
`STRIPE_SUCCESS_URL` / `STRIPE_CANCEL_URL` / `CERTIFICATE_VERIFY_URL_TEMPLATE` are **frontend
routes** — the backend never serves them; leave the localhost:3000 defaults.
(`STAFF_INVITE_URL_TEMPLATE` default `http://localhost:3000/accept-invite?token={token}` — the
page a staff invitee lands on to set their password; see `POST /api/v1/admin/users`.)

---

## Part 5 — Which lines must be real, per feature

| To test… | Needs real values for |
|---|---|
| App boots + Flyway migrates + Swagger | Part 1 (all), Part 2 `JWT_SECRET` + `TOTP_ENCRYPTION_KEY`; everything else can be `dummy` |
| Register / login / refresh / password reset | + nothing extra (email just won't send until SendGrid is set) |
| Email actually delivered | + `SENDGRID_API_KEY` + a verified `SENDGRID_FROM_ADDRESS` |
| "Sign in with Google" | + `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` |
| "Sign in with GitHub" | + `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` |
| Student GitHub PR submission verifies | + `GITHUB_API_TOKEN` |
| Checkout + payment webhook (Razorpay) | + `RAZORPAY_KEY_ID` / `_KEY_SECRET` / `_WEBHOOK_SECRET` |
| Checkout + payment webhook (Stripe) | + `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` |
| Outbound WhatsApp notifications | + `WHATSAPP_PHONE_NUMBER_ID` / `_ACCESS_TOKEN` |
| Inbound WhatsApp webhook | + `WHATSAPP_APP_SECRET` (and the missing `hub.challenge` handler — L14) |

---

## Part 6 — Complete `.env` template (local development)

Copy this over `.env`, then fill the two `<GENERATE>` lines (Part 2) and replace `dummy` for
whatever integration you're testing.

```dotenv
# ── Database (moriah_app — runtime pool, no DDL) ──────────────────────────────
DB_HOST=localhost
DB_PORT=3306
DB_NAME=moriah_skillhub
DB_USERNAME=moriah_app
DB_PASSWORD=app_dev_only
DB_POOL_MAX=10
DB_POOL_MIN=10
DB_SSL_MODE=REQUIRED          # set DISABLED if the docker MySQL TLS handshake fails locally

# ── Read replica (admin exports / heavy metrics reads) ───────────────────────
DB_REPLICA_HOST=localhost
DB_REPLICA_PORT=3307
DB_REPLICA_POOL_MAX=10

# ── Flyway (moriah_migrate — DDL, migrations only) ───────────────────────────
MIGRATE_DB_USERNAME=moriah_migrate
MIGRATE_DB_PASSWORD=migrate_dev_only

# ── App ─────────────────────────────────────────────────────────────────────
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
JOBS_ZONE=Asia/Kolkata

# ── Redis ───────────────────────────────────────────────────────────────────
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ── JWT + 2FA — GENERATE THESE (Part 2) ─────────────────────────────────────
JWT_SECRET=<GENERATE: openssl rand -base64 64>
JWT_KEY_ID=v1
TOTP_ENCRYPTION_KEY=<GENERATE: openssl rand -base64 32>

# ── MinIO / S3 (docker-compose) ─────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=root_dev_only
MINIO_ROOT_USER=skillhub_minio
MINIO_ROOT_PASSWORD=minio_dev_only
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
S3_BUCKET=moriah-skillhub
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=skillhub_minio
S3_SECRET_KEY=minio_dev_only

# ── OAuth2 (Part 3.1 / 3.2) — dummy until you register real apps ────────────
GOOGLE_CLIENT_ID=dummy
GOOGLE_CLIENT_SECRET=dummy
GITHUB_CLIENT_ID=dummy
GITHUB_CLIENT_SECRET=dummy

# ── GitHub REST PR verification (Part 3.3) ──────────────────────────────────
GITHUB_API_TOKEN=dummy

# ── Razorpay (Part 3.4) ────────────────────────────────────────────────────
RAZORPAY_KEY_ID=dummy
RAZORPAY_KEY_SECRET=dummy
RAZORPAY_WEBHOOK_SECRET=local_test_secret

# ── Stripe (Part 3.5) ─────────────────────────────────────────────────────
STRIPE_SECRET_KEY=dummy
STRIPE_WEBHOOK_SECRET=local_test_secret
STRIPE_SUCCESS_URL=http://localhost:3000/checkout/success
STRIPE_CANCEL_URL=http://localhost:3000/checkout/cancelled

# ── SendGrid (Part 3.6) ───────────────────────────────────────────────────
SENDGRID_API_KEY=dummy
SENDGRID_FROM_ADDRESS=no-reply@moriahskillhub.example
SENDGRID_FROM_NAME=Moriah Skill Hub

# ── WhatsApp / Meta (Part 3.7) ───────────────────────────────────────────
WHATSAPP_API_BASE_URL=https://graph.facebook.com/v18.0
WHATSAPP_PHONE_NUMBER_ID=dummy
WHATSAPP_ACCESS_TOKEN=dummy
WHATSAPP_APP_SECRET=dummy

# ── Frontend routes (backend only substitutes {token}/{code}) ────────────
EMAIL_VERIFICATION_URL_TEMPLATE=http://localhost:3000/verify-email?token={token}
PASSWORD_RESET_URL_TEMPLATE=http://localhost:3000/reset-password?token={token}
STAFF_INVITE_URL_TEMPLATE=http://localhost:3000/accept-invite?token={token}
CERTIFICATE_VERIFY_URL_TEMPLATE=http://localhost:3000/verify/{code}
```

> `docker compose` and the app both read this same `.env` (the app via the
> `spring.config.import` line in `application-dev.yml`).
