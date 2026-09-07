# CLAUDE.md — repo map & operating notes

## Layout

- `backend/` — Spring Boot 3.5 / Java 21. Flyway migrations in
  `backend/src/main/resources/db/migration` (`V*.sql`); dev-only seed data in
  `backend/src/main/resources/db/testdata/R__dev_seed_data.sql` (repeatable, `dev`
  profile only). MapStruct + Lombok annotation processing.
- `frontend/` — React 19 / Vite. API layer in `frontend/src/services/*`,
  pages in `frontend/src/pages/<role>/*`.

Each subtree was brought in with `git subtree`, so `git log -- backend/` and
`git log -- frontend/` both show full prior history.

## Running the stack

1. `cd backend && docker compose up -d` — MySQL (`:3306`), MySQL replica (`:3307`),
   Redis (`:6379`), MinIO (`:9000` API / `:9001` console). Containers are named
   `skillhub-*`.
2. `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` — app on
   `:8080`. The `dev` profile loads the seed data; `test` / `prod` do not.
3. `cd frontend && npm install && npm run dev` — app on `:5173`, proxies `/api` to
   `:8080` (see `frontend/vite.config.js`).

`.env` for each side is copied from that side's `.env.example` and is gitignored.
See the root `README.md` for which keys actually need real values in dev
(`JWT_SECRET`, `TOTP_ENCRYPTION_KEY`) versus which can stay `change_me`.

## Gotchas

- **Restart the backend after any backend edit** — recompiling while the JVM is
  live corrupts its classloader (anonymous-inner-class numbering drift →
  `NoClassDefFoundError` on unrelated requests).
- `[error/RESOURCE_NOT_FOUND] No static resource api/v1/...` in the log ==
  that route isn't registered in the running JVM — the app is stale, restart it.
- Docker Desktop on Windows: port-forwarding can go stale after a container
  restart (raw TCP connects but the MySQL/Redis handshake fails). Fix with
  `docker restart <container>` — never change app code for it. `docker exec
  skillhub-mysql mysql -uroot -p<MYSQL_ROOT_PASSWORD> moriah_skillhub` always works.
- Backend log file: `backend/logs/skillhub.log` (large — grep it).
- Jobs/cron zone: `Asia/Kolkata`.
- **`STORAGE_UPLOAD_FAILED`** on a resume/invoice/certificate upload → object storage
  problem, not app code. Check `[storage] upload failed` in the log for the real S3
  error. Usual causes: the `moriah-skillhub` bucket is missing (the `minio-init`
  compose service creates it — `docker compose logs minio-init`), `S3_ENDPOINT` unset
  in `.env`, or `S3_ACCESS_KEY`/`SECRET` ≠ `MINIO_ROOT_USER`/`PASSWORD`. The
  `InvoiceGenerationJob` has **no retry** — a failed invoice stays PENDING; re-drive it
  with `POST /api/v1/dev/jobs/invoice-generation?paymentId=<id>` (ADMIN, dev profile).

## Build / test

- Backend: `cd backend && ./mvnw -q test` (unit + integration; ITs use Testcontainers
  or the `test` profile, never the dev seed).
- Frontend: `cd frontend && npm run build`.
