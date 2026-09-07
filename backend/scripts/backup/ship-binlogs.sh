#!/usr/bin/env bash
# Binlog shipping — architecture.md "Backup, PITR and Disaster Recovery": every 5 minutes,
# 7-day retention, same bucket as the nightly dump, separate prefix. This is what bounds RPO to
# 5 minutes, not the nightly dump — intended to run on a 5-minute cron/scheduled task.
#
# Uses `mysqlbinlog --read-from-remote-server`, not direct filesystem access to the binlog
# directory: works identically against a local container, a VM, or a managed database, and needs
# only network access plus a user with REPLICATION CLIENT/SLAVE — the same global-only privilege
# a read replica's connection needs, which `moriah_migrate`/`moriah_app` deliberately don't have
# (see architecture.md "Database Users" — neither is meant to hold global privileges). Point
# BACKUP_DB_USERNAME/PASSWORD at a dedicated user granted only:
#   GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'moriah_backup'@'%';
# Defaults to root for local dev, where that separation doesn't matter.
#
# Idempotent: tracks the last-shipped binlog file in a local state file so re-running (e.g. after
# a failed run) never re-uploads a file already shipped, and only ships files fully rotated by
# the server, never the currently-active one.
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
BACKUP_DB_USERNAME="${BACKUP_DB_USERNAME:-root}"
BACKUP_DB_PASSWORD="${BACKUP_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-root_dev_only}}"
S3_BUCKET="${BACKUP_S3_BUCKET:-}"
S3_PREFIX="${BACKUP_S3_BINLOG_PREFIX:-binlogs}"
STATE_FILE="${BINLOG_SHIP_STATE_FILE:-./backups/.last-shipped-binlog}"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT
mkdir -p "$(dirname "${STATE_FILE}")"

echo "[backup/ship-binlogs] listing binlogs on ${DB_HOST}:${DB_PORT}"

# All binlog files except the last (currently being written) one are safe to ship.
mapfile -t all_binlogs < <(
  mysql --host="${DB_HOST}" --port="${DB_PORT}" \
        --user="${BACKUP_DB_USERNAME}" --password="${BACKUP_DB_PASSWORD}" \
        --silent --skip-column-names \
        -e "SHOW BINARY LOGS;" | awk '{print $1}'
)

if [ "${#all_binlogs[@]}" -le 1 ]; then
  echo "[backup/ship-binlogs] nothing rotated yet, nothing to ship"
  exit 0
fi

shippable=("${all_binlogs[@]:0:${#all_binlogs[@]}-1}")
last_shipped="$(cat "${STATE_FILE}" 2>/dev/null || echo '')"

for binlog in "${shippable[@]}"; do
  if [[ "${binlog}" < "${last_shipped}" || "${binlog}" == "${last_shipped}" ]]; then
    continue
  fi

  echo "[backup/ship-binlogs] shipping ${binlog}"
  mysqlbinlog \
    --read-from-remote-server \
    --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${BACKUP_DB_USERNAME}" --password="${BACKUP_DB_PASSWORD}" \
    --raw --result-file="${work_dir}/" \
    "${binlog}"

  if [ -n "${S3_BUCKET}" ]; then
    aws s3 cp "${work_dir}/${binlog}" "s3://${S3_BUCKET}/${S3_PREFIX}/${binlog}"
  else
    mkdir -p "./backups/${S3_PREFIX}"
    cp "${work_dir}/${binlog}" "./backups/${S3_PREFIX}/${binlog}"
    echo "[backup/ship-binlogs] BACKUP_S3_BUCKET not set — wrote locally to ./backups/${S3_PREFIX}/${binlog}"
  fi

  echo "${binlog}" > "${STATE_FILE}"
done

echo "[backup/ship-binlogs] done, last shipped: $(cat "${STATE_FILE}")"
