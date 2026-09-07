# Moriah Skill Hub

Monorepo for the Moriah Skill Hub platform — an LMS / agile-training product with
client project delivery, HR, CRM, and payments.

```
moriah-skill-hub/
├── backend/    Spring Boot 3.5 · Java 21 · MySQL 8 · Redis 7 · MinIO (S3)
└── frontend/   React 19 · Vite · Tailwind · Recharts
```

Each subtree keeps its own full git history and its own `README.md`, `.gitignore`,
and `.env.example`.

## Prerequisites

- Docker (Desktop or Engine) — for MySQL, the read replica, Redis, MinIO
- JDK 21
- Node 20+
- Maven is vendored (`backend/mvnw`)

## Run it (local dev)

### 1. Backend infrastructure (Docker)

```bash
cd backend
cp .env.example .env          # then edit — see "Filling .env" below
docker compose up -d          # mysql, mysql-replica, redis, minio
```

Wait for `docker ps` to show `skillhub-mysql` healthy.

### 2. Backend app

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Serves on **http://localhost:8080** (`/swagger-ui.html` for the API).
- The `dev` profile applies `src/main/resources/db/testdata/R__dev_seed_data.sql`
  on top of the Flyway migrations — seed users, a batch, sprints, a CRM pipeline,
  employee records, etc. The `test` and `prod` profiles never load it.

### 3. Frontend

```bash
cd frontend
cp .env.example .env          # VITE_API_BASE_URL can stay blank — Vite proxies /api to :8080
npm install
npm run dev
```

- Serves on **http://localhost:5173**.

## Seed accounts (dev profile)

All use password `Password123!`:

| Email | Role |
|---|---|
| `admin@moriah.test` | ADMIN |
| `hr@moriah.test` | HR_MANAGER |
| `pm@moriah.test` | TRAINER_PM |
| `dev@moriah.test` | DEVELOPER |
| `ba@moriah.test` | BUSINESS_ANALYST |
| `sales@moriah.test` | LEAD_GEN |
| `client@moriah.test` | CLIENT |
| `student1@moriah.test` … `student9@moriah.test` | STUDENT |

`admin@` and `hr@` have **mandatory 2FA** — first login walks through TOTP setup.

## Filling `.env` (backend)

`backend/.env.example` ships every key with `change_me`. For local dev:

- **Database / Redis / MinIO** — use the values from `backend/docker-compose.yml`
  (the compose file and the app read the same `.env`). Keep `DB_HOST=localhost`,
  `DB_PORT=3306`, `DB_REPLICA_PORT=3307`, `REDIS_PORT=6379`.
- **`JWT_SECRET`** — generate a real one: `openssl rand -base64 64` (HS512 needs ≥64 bytes).
- **`TOTP_ENCRYPTION_KEY`** — `openssl rand -base64 32` (32-byte AES key, base64).
- **Everything else** (`GOOGLE_*`, `GITHUB_*`, `RAZORPAY_*`, `STRIPE_*`, `SENDGRID_*`,
  `WHATSAPP_*`, `GITHUB_API_TOKEN`) can stay `change_me` — those integrations are
  exercised only by unit tests against mocked clients and aren't hit by normal
  local use. Real values are only needed to test that specific integration end to end.

`.env` is gitignored in both subtrees — never commit real values.

## Notes

- Backend logs: `backend/logs/skillhub.log`.
- After editing backend Java while the app is running, restart it — hot classloading
  is not reliable for this codebase.
- Jobs / cron zone is `Asia/Kolkata` (`JOBS_ZONE`).
