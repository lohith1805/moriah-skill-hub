-- Local development only. Creates the two database users described in context/architecture.md
-- ("Database Users") so the application's two-datasource setup has something to connect to
-- before any Flyway migration exists. The audit_logs-specific SELECT+INSERT-only restriction on
-- moriah_app is applied by src/main/resources/db/migration/afterMigrate.sql, once that table
-- exists (feature 02) — this script only establishes the general DDL/no-DDL split, plus the
-- GRANT OPTION moriah_migrate needs to narrow moriah_app's grants itself in that callback.
--
-- These are throwaway local-dev credentials, not secrets — they never leave this machine's
-- docker network. Anything resembling a real secret stays out of this file.

CREATE DATABASE IF NOT EXISTS moriah_skillhub
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Database name escaped as `moriah\_skillhub` in every GRANT below — deliberate, not a typo.
-- In MySQL's GRANT syntax, an unescaped "_" in the database-name position of "db.*" is a
-- wildcard (matches any single character), not a literal underscore. In local dev, Testcontainers
-- sets MYSQL_USER=moriah_migrate, which makes the official MySQL image pre-create that user with
-- its own escaped, literal-underscore grant on `moriah\_skillhub` *before* this script runs. An
-- unescaped GRANT here then targets a technically different (wildcard-pattern) privilege-table
-- row than that one, so a later change like WITH GRANT OPTION silently doesn't apply to the row
-- MySQL actually uses — confirmed the hard way; see the recover-skill diagnosis in
-- progress-tracker.md. Escaping avoids the split entirely, matching what the official image
-- itself does.

-- moriah_migrate: Flyway only. Full DDL rights, used nowhere else.
-- WITH GRANT OPTION: needed so afterMigrate.sql can narrow moriah_app's own grants on
-- audit_logs once it exists — that's a smaller privilege escalation than the DROP TABLE rights
-- moriah_migrate already holds on the same schema, and it keeps the audit_logs restriction
-- entirely inside Flyway's migration flow instead of a separate manual/root-run step.
CREATE USER IF NOT EXISTS 'moriah_migrate'@'%' IDENTIFIED BY 'migrate_dev_only';
GRANT ALL PRIVILEGES ON `moriah\_skillhub`.* TO 'moriah_migrate'@'%' WITH GRANT OPTION;

-- moriah_app: the HikariCP runtime pool. DML only — no CREATE, ALTER, DROP, INDEX.
--
-- SELECT, INSERT only at the schema level — deliberately not UPDATE/DELETE. MySQL privileges
-- are additive across levels (global, database, table): a table-level REVOKE can never cancel a
-- database-level GRANT, so UPDATE/DELETE can't be granted schema-wide and then narrowed away for
-- audit_logs specifically — confirmed the hard way; see the recover-skill diagnosis in
-- progress-tracker.md. UPDATE/DELETE are instead granted per-table (every table except
-- audit_logs) by afterMigrate.sql, which re-derives the table list from information_schema on
-- every migrate so newly-added tables in later migrations are covered automatically.
CREATE USER IF NOT EXISTS 'moriah_app'@'%' IDENTIFIED BY 'app_dev_only';
GRANT SELECT, INSERT ON `moriah\_skillhub`.* TO 'moriah_app'@'%';

FLUSH PRIVILEGES;
