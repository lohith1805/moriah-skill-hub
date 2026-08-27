#!/usr/bin/env bash
# Nightly full logical dump — architecture.md "Backup, PITR and Disaster Recovery":
# nightly, 30-day retention, S3, server-side encrypted, versioned.
#
# Retention and versioning are bucket configuration (S3 lifecycle rule + versioning enabled on
# the bucket), not this script's job — this script only produces and uploads the dump.
#
# Runs against `moriah_migrate` (read access is all a dump needs) or a dedicated read-only
# backup user in production; never against the primary's connection pool.
#
# Env vars (all have local-dev defaults matching .env):
#   DB_HOST, DB_PORT, DB_NAME, MIGRATE_DB_USERNAME, MIGRATE_DB_PASSWORD
#   BACKUP_S3_BUCKET   — if unset, falls back to writing under ./backups/ locally (dev/CI use)
#   BACKUP_S3_PREFIX   — default "full-dumps"
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-moriah_skillhub}"
DB_USER="${MIGRATE_DB_USERNAME:-moriah_migrate}"
DB_PASSWORD="${MIGRATE_DB_PASSWORD:-migrate_dev_only}"
S3_BUCKET="${BACKUP_S3_BUCKET:-}"
S3_PREFIX="${BACKUP_S3_PREFIX:-full-dumps}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump_file="moriah_skillhub-${timestamp}.sql.gz"
work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

echo "[backup/nightly-dump] starting dump of ${DB_NAME}@${DB_HOST}:${DB_PORT} at ${timestamp}"

# --single-transaction: consistent snapshot without locking InnoDB tables.
# --source-data=2: records the binlog file+position as a comment in the dump, so a restore knows
# exactly where to resume replaying binlogs from — this is what makes PITR possible, not just
# "restore the dump."
mysqldump \
  --host="${DB_HOST}" --port="${DB_PORT}" \
  --user="${DB_USER}" --password="${DB_PASSWORD}" \
  --single-transaction --routines --triggers --source-data=2 \
  "${DB_NAME}" | gzip > "${work_dir}/${dump_file}"

echo "[backup/nightly-dump] dump complete: $(du -h "${work_dir}/${dump_file}" | cut -f1)"

if [ -n "${S3_BUCKET}" ]; then
  aws s3 cp "${work_dir}/${dump_file}" "s3://${S3_BUCKET}/${S3_PREFIX}/${dump_file}" \
    --sse aws:kms
  echo "[backup/nightly-dump] uploaded to s3://${S3_BUCKET}/${S3_PREFIX}/${dump_file}"
else
  mkdir -p "./backups/${S3_PREFIX}"
  cp "${work_dir}/${dump_file}" "./backups/${S3_PREFIX}/${dump_file}"
  echo "[backup/nightly-dump] BACKUP_S3_BUCKET not set — wrote locally to ./backups/${S3_PREFIX}/${dump_file}"
fi
