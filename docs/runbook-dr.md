# Disaster Recovery Runbook

Feature 02. Targets from the SRS: **RTO ≤ 2 hours, RPO ≤ 1 hour** (architecture.md, "Backup, PITR
and Disaster Recovery"). RPO is bounded by binlog shipping interval (5 minutes), not the nightly
dump.

This runbook has been exercised locally end to end — see "Restore drill — what was actually run"
below, including three real gotchas found by doing it, not by writing the procedure and assuming
it works.

---

## Prerequisites

- `log_bin` ON, `binlog_format = ROW`, `sync_binlog = 1` — configured on the `mysql` service in
  `docker-compose.yml`, and required in any real deployment target. Without this, PITR is
  impossible regardless of backup frequency.
- **A host or sidecar with full MySQL client tools, separate from the database server itself.**
  The official `mysql:8.0` server image does **not** ship `mysqlbinlog` — only `mysql`,
  `mysqldump`, `mysqladmin`, and a few others. Binlog shipping and replay must run from
  somewhere with the full `mysql-client`/`mysql-community-client` package (or equivalent)
  installed. `scripts/backup/ship-binlogs.sh` uses `mysqlbinlog --read-from-remote-server`
  specifically so it never needs filesystem access to the server's binlog directory — it only
  needs that full client package wherever *it* runs, and network access to the database.
- A dedicated backup/replication database user in real deployments — `REPLICATION SLAVE,
  REPLICATION CLIENT ON *.*` — never `moriah_migrate` or `moriah_app` (neither holds global
  privileges; see architecture.md "Database Users"). Locally, the scripts default to `root` since
  that separation doesn't matter in dev.

---

## Nightly full logical dump

`scripts/backup/nightly-dump.sh` — `mysqldump --single-transaction --source-data=2`, gzipped,
uploaded to `s3://$BACKUP_S3_BUCKET/full-dumps/` (falls back to `./backups/full-dumps/` locally
if `BACKUP_S3_BUCKET` is unset). `--source-data=2` records the binlog file+position as a comment
in the dump — this is what makes PITR possible from it, not just a point restore.

Schedule nightly in the real environment (cron, a Kubernetes CronJob, or the deployment
platform's scheduled task equivalent — no such scheduler exists in this repo yet, since none has
been chosen). 30-day retention and encryption are S3 bucket configuration (lifecycle rule +
versioning + SSE-KMS on the bucket), not this script's job.

## Binlog shipping

`scripts/backup/ship-binlogs.sh` — every 5 minutes (this is what bounds RPO, not the nightly
dump), ships every fully-rotated binlog file to `s3://$BACKUP_S3_BUCKET/binlogs/` (or
`./backups/binlogs/` locally). Tracks the last-shipped file in a local state file so re-running
after a failure never re-uploads or skips a file.

---

## Restore drill — what was actually run

Performed 2026-08-24 against a local `docker-compose` MySQL (not a simulation described in
prose — the actual commands below were run and the result checked).

1. Started `mysql` (primary) with the binlog config from `docker-compose.yml`, applied V1–V5.
2. Seeded 2 rows in `users` ("Drill User One/Two").
3. Took a checkpoint dump: `mysqldump --single-transaction --source-data=2 moriah_skillhub` →
   captured binlog coordinates `mysql-bin.000003`, position `23666`.
4. Inserted "Post-Dump Batch A" (committed 10:00:09 UTC), then "Post-Dump Batch B" (committed
   10:00:20 UTC) — two changes after the dump, far enough apart to target a point *between* them.
5. Copied `mysql-bin.000003` out of the primary container.
6. Started a scratch MySQL instance (`mysql-scratch`), restored the checkpoint dump into it —
   confirmed 2 rows, matching the dump's snapshot exactly.
7. Replayed the binlog from the dump's captured position, stopping at a chosen timestamp between
   Batch A and Batch B: `mysqlbinlog --start-position=23666 --stop-datetime="<chosen time>"
   mysql-bin.000003 | mysql moriah_skillhub`.
8. Result: **3 rows** — Drill User One, Drill User Two, and Post-Dump Batch A. Batch B correctly
   absent. This is an exact match to what the primary looked like at that instant, not "restore
   everything" — the actual point-in-time guarantee, proven, not assumed.

### Three things this drill caught that a paper procedure would have missed

1. **`mysqlbinlog` isn't in the `mysql:8.0` image** (see Prerequisites above) — discovered when
   `docker exec <server-container> mysqlbinlog ...` failed with "executable file not found."
   Real binlog tooling needs to run from somewhere else.
2. **`--stop-datetime` is interpreted in mysqlbinlog's local system timezone, not UTC.** The
   drill's database session reported timestamps in UTC (`10:00:09`), but `mysqlbinlog` running on
   a host set to IST displayed and expected times in `15:30:09` (UTC+5:30) terms. Passing the raw
   UTC timestamp as `--stop-datetime` silently matched zero events (the window was entirely in
   the past relative to local time) rather than erroring — the kind of mistake that looks like a
   successful, empty restore instead of an obvious failure. **Always convert to the timezone of
   the machine running `mysqlbinlog` before setting `--start-datetime`/`--stop-datetime`, or
   restore by `--start-position`/`--stop-position` instead where the exact position is known.**
3. **The restore target must have the same `gtid_mode` as the source.** The primary runs with
   `--gtid-mode=ON` (see `docker-compose.yml`); a scratch instance started without it rejected
   the replay with `@@SESSION.GTID_NEXT cannot be set to UUID:NUMBER when @@GLOBAL.GTID_MODE =
   OFF`. Any scratch/restore instance must be started with the same GTID configuration as the
   primary.

### Running it again

The exact sequence above is reproducible with `docker-compose.yml`'s `mysql` service, standard
`mysqldump`/`mysqlbinlog`, and a scratch `mysql:8.0` container started with matching
`--gtid-mode=ON --enforce-gtid-consistency=ON`. Re-run monthly per architecture.md, and once
during feature 24.

---

## Setting up the local replica

`docker-compose.yml`'s `mysql-replica` service is the same image and binlog configuration as the
primary, on a different `server-id`, standing in for "one async read replica in a second AZ."
Not wired to the application — feature 22 is where admin/export reads targeting a replica
actually get built. Manual setup (docker-compose has no native "wait for primary, then configure
replication" step):

```bash
# 1. Create a replication user on the primary (once)
docker exec skillhub-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY '<replication-password>';
   GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';"

# 2. Point the replica at the primary (GTID-based — no file/position tracking needed)
docker exec skillhub-mysql-replica mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "CHANGE REPLICATION SOURCE TO
     SOURCE_HOST='mysql', SOURCE_USER='repl', SOURCE_PASSWORD='<replication-password>',
     SOURCE_AUTO_POSITION=1;
   START REPLICA;"

# 3. Confirm
docker exec skillhub-mysql-replica mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "SHOW REPLICA STATUS\G" | grep -E "Replica_IO_Running|Replica_SQL_Running"
# both should read Yes
```

---

## Promotion (primary failure)

1. Stop application traffic to the failed primary.
2. On the replica: `STOP REPLICA; RESET REPLICA ALL;` — detaches it from the old source.
3. Repoint the application's `DB_HOST` (and `MIGRATE_DB_*` if applicable) at the promoted
   instance.
4. **Verify `flyway_schema_history` matches the deployed application artifact** — the promoted
   replica must have applied exactly the migrations this version of the app expects. A mismatch
   here means the promotion target is not actually equivalent to the failed primary; do not
   proceed until it is confirmed.
5. **Reconcile `webhook_events`** against Razorpay/Stripe's own event logs for the failover
   window — replication lag (however small) means the last few seconds of webhook processing
   before failure may not have replicated. Any gateway event present at the source but missing
   here needs manual replay against the promoted instance.
6. Resume application traffic.
7. Provision a new replica against the promoted primary at the next opportunity — a single
   database with no replica is a compounding risk, not a stable end state.

---

## Checklist reference

The Database Resilience Checklist in `progress-tracker.md` tracks completion status for every
item in this runbook — update it, not just this file, when something here changes.
