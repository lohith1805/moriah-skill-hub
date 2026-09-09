# Testing scheduled jobs (dev profile)

The nightly job chain (attendance finalisation → metrics refresh → PIP evaluation →
subscription/quiz expiry → invoice generation) runs on cron between ~01:30 and 03:00 IST.
The `dev` profile exposes on-demand triggers so a job can be exercised without waiting for
its cron, plus a couple of PIP fixtures so the 15-day recovery window doesn't have to elapse
in real time.

Everything here is **dev-profile only** (`@Profile("dev")`) and **ADMIN-gated**. None of it
exists in `test` or `prod`.

## Get an admin token

The dev endpoints need a bearer token for an ADMIN user. `admin@moriah.test` has TOTP
enabled in local dev, so login is the two-step challenge flow:

```bash
# 1. email + password -> challengeToken
CT=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@moriah.test","password":"Password123!"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['challengeToken'])")

# 2. challengeToken + 6-digit code from your enrolled authenticator -> access token
TOK=$(curl -s -X POST http://localhost:8080/api/v1/auth/2fa/verify \
  -H 'Content-Type: application/json' \
  -d "{\"challengeToken\":\"$CT\",\"totpCode\":\"<123456>\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['data']['tokens']['accessToken'])")
```

`pm@moriah.test` has no 2FA (single-step login) but is **not** an ADMIN, so it can't call
`/dev/**` — use it only for the PM-facing UI parts of a demo.

## Run a scheduled job now

```
POST /api/v1/dev/jobs/{job}/run        (ADMIN)
```

`job` ∈ `attendance-finalisation`, `metrics-refresh`, `pip-evaluation`,
`subscription-expiry`, `quiz-attempt-expiry`, `invoice-generation`.

For a realistic PIP test run them in that order — each just calls the same service method
its `@Scheduled` method calls, in the request thread, and returns a row count.
`invoice-generation` additionally needs `?paymentId=` and re-renders the PDF + resends the
confirmation email.

```bash
curl -s -X POST http://localhost:8080/api/v1/dev/jobs/metrics-refresh/run  -H "Authorization: Bearer $TOK"
curl -s -X POST http://localhost:8080/api/v1/dev/jobs/pip-evaluation/run   -H "Authorization: Bearer $TOK"
```

`pip-evaluation` runs all four passes of `PipEvaluationService`:
`evaluate()` (open new PIPs) → `reconcileSeededMilestones()` (sync the auto-seeded milestone
to its trigger metric) → `autoResolveElapsed()` (auto-clear / escalate elapsed windows) →
`nudgeEarlyRecoveries()` (one-time "ready to clear early" PM nudge). `processed` is the sum.

## PIP fixtures

```
POST /api/v1/dev/pip/{id}/elapse-window   (ADMIN)
```

Backdates a PIP record's window so `end_date` is yesterday (window length preserved) and
clears `early_clear_nudged_at`. The next `pip-evaluation` run then treats it as elapsed, so
`autoResolveElapsed()`'s auto-clear / escalation path fires without a 15-day wait.

To raise a PIP for one specific student regardless of their metrics, use the real manual
endpoint (ADMIN or the batch's PM):

```bash
curl -s -X POST http://localhost:8080/api/v1/pip -H "Authorization: Bearer $TOK" \
  -H 'Content-Type: application/json' -d '{
    "studentUuid":"11111111-0000-0000-0000-000000000009",
    "batchId":1, "ruleCode":"ATTENDANCE_LOW",
    "reason":"manual raise for testing", "severity":"MEDIUM"}'
```

`ruleCode` ∈ `ATTENDANCE_LOW｜PROJECT_DELAY｜ASSIGNMENT_MISSED｜QUIZ_FAILURE｜REVIEW_FAILED｜TASK_ABANDONED`.
The student must be enrolled in the batch; a second open PIP for the same student is `409
PIP_ALREADY_OPEN`.

## PIP feature-17 demo recipes

The rule metrics (`student_metrics`) are normally recomputed nightly by `MetricsRefreshJob`
from real attendance / task / quiz / review rows. To fast-forward to a given end state in
one sitting, write the number straight into `student_metrics` then run the job. The PIP
logic itself runs unmodified.

```bash
DB="docker exec skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub -e"
```

### 1. Clear a PIP (happy path)

```bash
$DB "UPDATE student_metrics SET attendance_present=attendance_total, attendance_percent=100.00
     WHERE user_id=9 AND batch_id=1;"
```
Then in the UI: **Verify** the recovery milestone → **Clear PIP** (criteria met) → student ACTIVE.
No job run needed for the manual clear.

### 2. System catches a premature "Verify"

Leave attendance failing. In the UI, **Verify** the seeded milestone anyway, then:
```bash
curl -s -X POST http://localhost:8080/api/v1/dev/jobs/pip-evaluation/run -H "Authorization: Bearer $TOK"
```
The seeded milestone flips **back to PENDING** and the PM gets a `PIP_MILESTONE_AUTO_REVERTED`
notice — the metric behind it hasn't recovered, so verifying it early can't clear the PIP.

### 3. Metric recovers → seeded milestone auto-completes + early-clear nudge

```bash
$DB "UPDATE student_metrics SET attendance_present=attendance_total, attendance_percent=100.00
     WHERE user_id=9 AND batch_id=1;"
curl -s -X POST http://localhost:8080/api/v1/dev/jobs/pip-evaluation/run -H "Authorization: Bearer $TOK"
```
The seeded milestone is auto-completed by the system (`verified_by` NULL). If every clearance
criterion is now met and the window hasn't elapsed, the PM gets a one-time `PIP_READY_TO_CLEAR`
nudge (guarded by `pip_records.early_clear_nudged_at`).

### 4. Window elapses without recovery → no auto-clear

```bash
curl -s -X POST http://localhost:8080/api/v1/dev/pip/12/elapse-window        -H "Authorization: Bearer $TOK"
curl -s -X POST http://localhost:8080/api/v1/dev/jobs/pip-evaluation/run     -H "Authorization: Bearer $TOK"
```
With the trigger metric still failing the record stays open (PM must TERMINATE / REASSIGN)
and gets one `PIP_WINDOW_ELAPSED` escalation. Fix the metric, re-elapse, re-run → auto-CLEARED.

## Test-data state after a demo — and how to reset

`R__dev_seed_data.sql` is a Flyway **repeatable** migration built entirely from
`INSERT IGNORE` / `INSERT ... WHERE NOT EXISTS`. It **adds** missing seed rows on every boot;
it does **not** reset rows a test has already mutated. So after running the recipes above the
dev DB carries leftover state:

| Table | Typical leftovers from a PIP demo |
| --- | --- |
| `student_metrics` | `attendance_percent` / `task_completion_percent` forced for the demo student(s); recomputed on the next real `metrics-refresh` from the (sparse) underlying rows, so values may shift again |
| `pip_records` | extra rows in `CLEARED` / `TERMINATED` / `REASSIGNED`; `early_clear_nudged_at` set on nudged rows; `start_date` / `end_date` backdated by `elapse-window` |
| `pip_milestones` | seeded milestones auto-completed (`verified_by` NULL) or reverted; PM-added rows |
| `weekly_reviews` | ratings saved during the demo (upsert-keyed by `(user, week_start)`) |
| `batch_students` | `status` toggled ACTIVE ⇄ ON_PIP; demo enrolments |
| `notifications` | `PIP_*` rows for the PM / student / HR |

### Targeted reset (keep the schema, undo PIP demo state)

```sql
-- close out every non-seed PIP and its milestones
DELETE m FROM pip_milestones m
  JOIN pip_records r ON r.id = m.pip_record_id
 WHERE r.id > 7;                       -- ids 1..7 are the R__ seed set
DELETE FROM pip_records WHERE id > 7;

-- put demo students back to ACTIVE
UPDATE batch_students SET status = 'ACTIVE'
 WHERE user_id IN (9, 10) AND status = 'ON_PIP';

-- drop demo weekly reviews (seed has none)
DELETE FROM weekly_reviews;

-- clear demo notifications
DELETE FROM notifications WHERE template_code LIKE 'PIP\_%';
```
Then `POST /api/v1/dev/jobs/metrics-refresh/run` to rebuild `student_metrics` from the real
underlying rows.

This removes the *added* PIPs but won't restore a seed PIP (ids 1–7) whose `status` a demo
changed (e.g. one you REASSIGNED). For that, use the full reset.

### Full reset (fresh DB)

```bash
docker compose down -v && docker compose up -d      # drops the MySQL volume
# then start the backend — Flyway replays V1..V55 + R__dev_seed_data.sql
```

## Runtime-only MySQL workarounds

Unrelated to the jobs, but they reset on every MySQL container restart and are worth
re-applying if `GET /api/v1/placements` throws `ER_OUT_OF_SORTMEMORY` on a RAM-starved host:

```sql
SET GLOBAL sort_buffer_size = 67108864;
CREATE INDEX idx_placements_updated           ON placements (updated_at);
CREATE INDEX idx_placements_client_updated    ON placements (client_id, updated_at);
CREATE INDEX idx_placements_candidate_updated ON placements (candidate_id, updated_at);
```
