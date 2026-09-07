# Testing scheduled jobs without waiting for the clock

Several features only run on a cron — the biggest is the **nightly chain at 01:30–02:00 IST**
that ends in PIP evaluation. You don't have to wait until tomorrow. Two ways:

1. **Fire it now** via the dev-only endpoint (recommended).
2. **Retime the cron** via env vars + restart (if you want it to keep firing on a short loop).

---

## The jobs

| Job | Default cron (IST) | What it does | Reads / depends on |
|---|---|---|---|
| `AttendanceFinalisationJob` | `0 30 1 * * *` (01:30) | marks every enrolled student with no check-in row `ABSENT` for each held standup | standups + check-ins |
| `MetricsRefreshJob` | `0 45 1 * * *` (01:45) | recomputes `student_metrics` (attendance %, task completion %, quiz avg, overdue tasks, …) per (student, batch) | attendance, tasks, quiz attempts, reviews |
| `PipEvaluationJob` | `0 0 2 * * *` (02:00) | reads `student_metrics`, and for any student breaching an active `pip_rules` row with **no open PIP**, opens a `pip_records` row + notifies the student, their PM and HR | `student_metrics` (so **run metrics-refresh first**) |
| `SubscriptionExpiryJob` | `0 0 3 * * *` (03:00) | flips `ACTIVE` subscriptions past `end_date` to `EXPIRED` | `user_subscriptions.end_date` |
| `QuizAttemptExpiryJob` | `0 */15 * * * *` | auto-submits assessment attempts past their time limit | `quiz_attempts` |
| `WebhookReconciliationJob` | `0 */10 * * * *` | reconciles captured payments whose webhook never landed | `payments` / `webhook_events` |
| `NotificationReaperJob` | `0 * * * * *` | re-queues notifications stuck "processing" | `notifications` |
| `SubmissionVerificationRetryJob` | `0 */15 * * * *` | retries GitHub PR verification that failed transiently | `submissions` |
| `ExpiredTokenReaperJob` | `0 20 3 * * *` | deletes used/expired auth tokens | token tables |

Invoice-PDF generation is **not** a cron job — it's an async listener that fires the instant the
payment webhook commits. If an invoice shows up late it's the payment gateway *redelivering* the
webhook, not a schedule. See `docs/webhooks.md`.

---

## Option 1 — fire a job now (dev profile)

`DevJobController` is registered **only** under `SPRING_PROFILES_ACTIVE=dev` and is ADMIN-gated.
It calls the exact service method the job's `@Scheduled` method calls, synchronously, and returns
the row count.

```
POST /api/v1/dev/jobs/{job}/run        Authorization: Bearer <ADMIN token>
```

`{job}` ∈ `attendance-finalisation` · `metrics-refresh` · `pip-evaluation` · `subscription-expiry` · `quiz-attempt-expiry`

### Full PIP dry-run

```bash
ADMIN=<admin access token>          # login as admin@moriah.test (+ 2FA), grab accessToken

curl -sS -X POST localhost:8080/api/v1/dev/jobs/attendance-finalisation/run -H "Authorization: Bearer $ADMIN"
curl -sS -X POST localhost:8080/api/v1/dev/jobs/metrics-refresh/run          -H "Authorization: Bearer $ADMIN"
curl -sS -X POST localhost:8080/api/v1/dev/jobs/pip-evaluation/run           -H "Authorization: Bearer $ADMIN"
# -> {"success":true,"data":{"job":"pip-evaluation","processed":N}}   N = PIPs opened this run
```

Then check the result:

```bash
# as the affected student:
curl -sS localhost:8080/api/v1/pip/me -H "Authorization: Bearer $STUDENT"
# as PM/ADMIN — the whole list:
curl -sS localhost:8080/api/v1/pip     -H "Authorization: Bearer $ADMIN"
```

### Making a student actually breach a rule

`pip-evaluation` only opens a PIP for a student whose refreshed metrics cross an **active**
`pip_rules` threshold (seeded in `V11__pip.sql`):

| `rule_code` | Trips when… | Seed threshold |
|---|---|---|
| `ATTENDANCE_LOW` | attendance % over the trailing 14 days `< 75` | 75.00 / 14d |
| `PROJECT_DELAY` | ≥ 1 committed task overdue past the 2-day window | 1.00 / 2d |
| `ASSIGNMENT_MISSED` | ≥ 2 consecutive weekly assignments missed | 2.00 |
| `QUIZ_FAILURE` | quiz average `< 60` | 60.00 |
| `REVIEW_FAILED` | ≥ 1 unsatisfactory weekly review | 1.00 |
| `TASK_ABANDONED` | no recorded activity for ≥ 3 consecutive days | 3.00 |

The seed batches don't breach anything by default. Force one — e.g. tank a student's attendance —
with SQL, then run the chain:

```sql
-- student1@ is enrolled in FS-2026-01 (batch id 1). Give them absences for every standup held.
INSERT IGNORE INTO attendance (standup_id, user_id, status, marked_at)
SELECT s.id, u.id, 'ABSENT', NOW()
  FROM standups s
  JOIN users u ON u.email = 'student1@moriah.test'
 WHERE s.batch_id = 1;
```

```bash
docker exec -i skillhub-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" moriah_skillhub < that.sql
curl -sS -X POST localhost:8080/api/v1/dev/jobs/metrics-refresh/run -H "Authorization: Bearer $ADMIN"
curl -sS -X POST localhost:8080/api/v1/dev/jobs/pip-evaluation/run  -H "Authorization: Bearer $ADMIN"
```

A PIP fires once per student — clear it (`POST /api/v1/pip/{id}/review`) or
`DELETE FROM pip_records WHERE user_id = …` before re-running.

---

## Option 2 — retime the cron

Every job's cron is an env var. Point the ones you care about at "every minute" (or a minute from
now) in `.env`, then restart the app. Spring cron is 6-field `sec min hour dom mon dow`.

```dotenv
# nightly chain -> every minute, staggered so metrics runs after finalisation
ATTENDANCE_FINALISATION_CRON=0 * * * * *
METRICS_REFRESH_CRON=20 * * * * *
PIP_EVALUATION_CRON=40 * * * * *

# others
SUBSCRIPTION_EXPIRY_CRON=0 * * * * *
QUIZ_ATTEMPT_EXPIRY_CRON=0 * * * * *
```

Then `mvn -o spring-boot:run` (dev profile loads `.env`). Watch the log:

```
tail -f logs/skillhub.log | grep -E "PipEvaluationJob|MetricsRefreshJob|AttendanceFinalisationJob"
```

**Revert these before any real run** — you don't want attendance finalised every minute.
Option 1 leaves the schedule untouched, which is why it's preferred.
