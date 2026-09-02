# Moriah Skill Hub — Backend

Spring Boot 3.5 · Java 21 · MySQL 8.0 · Redis 7 · Maven. Backend and database only — see
[`context/AGENTS.md`](context/AGENTS.md) for the full project brief, build order, and rules.

## Local development

Requires: JDK 21, Maven, Docker Desktop.

```bash
cp .env.example .env   # already present with dev-only values; only needed if you deleted it
docker compose up -d
mvn spring-boot:run
```

This starts MySQL 8 (with the `moriah_migrate` / `moriah_app` users from
[`docker/mysql-init/01-users.sql`](docker/mysql-init/01-users.sql) pre-provisioned), Redis 7,
and a MinIO instance standing in for S3 (wired starting feature 08).

Once running:

- `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`
- `http://localhost:8080/swagger-ui.html` — Swagger UI (dev profile only)

## Tests

```bash
mvn verify
```

Integration tests use Testcontainers (`org.testcontainers:mysql`, pinned to the 1.20.x line —
see the comment in `pom.xml`) and spin up their own disposable MySQL container; Docker Desktop
must be running.

## Project state

Build order, what's done, and what's next live in
[`context/progress-tracker.md`](context/progress-tracker.md) — that file, not this README, is
the source of truth for project status.

Seed data

For Linux
docker exec -i skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub < src/main/resources/db/testdata/dev-seed.sql

For Windows
cmd /c "docker exec -i skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub < src/main/resources/db/testdata/dev-seed.sql"


MiniIO Bucket

docker exec skillhub-minio mc alias set local http://localhost:9000 skillhub_minio minio_dev_only
docker exec skillhub-minio mc mb --ignore-existing local/moriah-skillhub
