#!/usr/bin/env bash
# Restore drill — architecture.md: "monthly, and once during feature 24. Restore the latest full
# dump plus binlogs to a target timestamp on a scratch instance, then diff row counts against the
# primary. A backup that has never been restored is not a backup."
#
# Automates the exact procedure manually run and proven 2026-08-24 (see docs/runbook-dr.md
# "Restore drill — what was actually run") — takes a fresh checkpoint dump, makes a marker
# change, replays the binlog to a point before that marker, and confirms the marker is absent
# from the restored instance. Requires: docker, and a full MySQL client toolset on the machine
# running this script (mysqlbinlog — see docs/runbook-dr.md's Prerequisites for why that can't
# be the `mysql:8.0` server image itself).
#
# Env vars (all have local-dev defaults matching .env):
#   DB_HOST, DB_PORT, DB_NAME, MIGRATE_DB_USERNAME, MIGRATE_DB_PASSWORD
#   MYSQLBINLOG_BIN     — path to mysqlbinlog; default assumes it's on PATH
#   SCRATCH_CONTAINER    — name for the scratch container this script creates and removes
#   SCRATCH_PORT         — host port for the scratch container
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-moriah_skillhub}"
DB_USER="${MIGRATE_DB_USERNAME:-moriah_migrate}"
DB_PASSWORD="${MIGRATE_DB_PASSWORD:-migrate_dev_only}"
MYSQLBINLOG_BIN="${MYSQLBINLOG_BIN:-mysqlbinlog}"
SCRATCH_CONTAINER="${SCRATCH_CONTAINER:-skillhub-restore-drill-scratch}"
SCRATCH_PORT="${SCRATCH_PORT:-13399}"

work_dir="$(mktemp -d)"
marker_table="restore_drill_marker_$(date +%s)"

cleanup() {
  echo "[restore-drill] cleaning up"
  docker rm -f "${SCRATCH_CONTAINER}" >/dev/null 2>&1 || true
  rm -rf "${work_dir}"
}
trap cleanup EXIT

echo "[restore-drill] 1/6 — checking mysqlbinlog is available (not part of the mysql:8.0 server image)"
if ! command -v "${MYSQLBINLOG_BIN}" >/dev/null 2>&1; then
  echo "[restore-drill] FAIL: '${MYSQLBINLOG_BIN}' not found. Run this from a host with full" \
       "MySQL client tools installed — see docs/runbook-dr.md Prerequisites." >&2
  exit 1
fi

echo "[restore-drill] 2/6 — taking checkpoint dump (captures binlog position via --source-data=2)"
mysqldump --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASSWORD}" \
  --single-transaction --source-data=2 "${DB_NAME}" > "${work_dir}/checkpoint.sql"

binlog_file=$(grep -oP "(?<=MASTER_LOG_FILE=')[^']+" "${work_dir}/checkpoint.sql")
binlog_pos=$(grep -oP "(?<=MASTER_LOG_POS=)[0-9]+" "${work_dir}/checkpoint.sql")
echo "[restore-drill] checkpoint at ${binlog_file}:${binlog_pos}"

echo "[restore-drill] 3/6 — creating a marker table (the change the drill proves is excludable)"
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASSWORD}" "${DB_NAME}" \
  -e "CREATE TABLE ${marker_table} (id INT);"
target_time_utc=$(mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASSWORD}" \
  --silent --skip-column-names -e "SELECT UTC_TIMESTAMP();")
# mysqlbinlog's --stop-datetime is interpreted in the *local* timezone of the machine running
# it, not UTC — confirmed the hard way during the manual drill (see docs/runbook-dr.md gotcha
# #2). MySQL's UTC_TIMESTAMP() gives an unambiguous UTC value; converting it here rather than
# just documenting the gotcha is what actually makes this script correct on any host.
target_time=$(date -d "${target_time_utc} UTC" +"%Y-%m-%d %H:%M:%S")
echo "[restore-drill] target restore point: ${target_time_utc} UTC (${target_time} local) — before the marker table exists"

echo "[restore-drill] 4/6 — copying the binlog for replay (mysqlbinlog --read-from-remote-server needs" \
     "REPLICATION SLAVE/CLIENT; falls back to a direct file copy for the local Docker case)"
if ! docker cp "$(docker ps --filter "publish=${DB_PORT}" --format '{{.Names}}' | head -1)":/var/lib/mysql/"${binlog_file}" \
     "${work_dir}/${binlog_file}" 2>/dev/null; then
  "${MYSQLBINLOG_BIN}" --read-from-remote-server --host="${DB_HOST}" --port="${DB_PORT}" \
    --user="${DB_USER}" --password="${DB_PASSWORD}" --raw --result-file="${work_dir}/" "${binlog_file}"
fi

echo "[restore-drill] 5/6 — restoring into a scratch instance"
docker run -d --name "${SCRATCH_CONTAINER}" -e MYSQL_ROOT_PASSWORD=root_dev_only -p "${SCRATCH_PORT}:3306" \
  mysql:8.0 --gtid-mode=ON --enforce-gtid-consistency=ON >/dev/null
for _ in $(seq 1 30); do
  if docker exec "${SCRATCH_CONTAINER}" mysqladmin ping -uroot -proot_dev_only --silent 2>/dev/null; then break; fi
  sleep 2
done

docker exec "${SCRATCH_CONTAINER}" mysql -uroot -proot_dev_only \
  -e "CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
docker exec -i "${SCRATCH_CONTAINER}" mysql -uroot -proot_dev_only "${DB_NAME}" < "${work_dir}/checkpoint.sql"

"${MYSQLBINLOG_BIN}" --start-position="${binlog_pos}" --stop-datetime="${target_time}" \
  "${work_dir}/${binlog_file}" > "${work_dir}/replay.sql"
docker cp "${work_dir}/replay.sql" "${SCRATCH_CONTAINER}":/tmp/replay.sql
docker exec "${SCRATCH_CONTAINER}" mysql -uroot -proot_dev_only "${DB_NAME}" -e "source /tmp/replay.sql"

echo "[restore-drill] 6/6 — verifying the marker table is absent from the restored instance"
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --password="${DB_PASSWORD}" "${DB_NAME}" \
  -e "DROP TABLE ${marker_table};"

marker_present=$(docker exec "${SCRATCH_CONTAINER}" mysql -uroot -proot_dev_only "${DB_NAME}" --silent \
  --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='${marker_table}';")

if [ "${marker_present}" != "0" ]; then
  echo "[restore-drill] FAIL: marker table present in restored instance — point-in-time replay did not stop where expected" >&2
  exit 1
fi

echo "[restore-drill] PASS — restored to ${target_time_utc} UTC, marker table correctly absent"
