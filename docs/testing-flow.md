# Moriah Skill Hub — End-to-End Testing Flow

How to drive the whole platform through the API, in order, showing **what goes in each request
body** and **which field of one response feeds the next request**.

- Base URL (local): `http://localhost:8080` — written `{{baseUrl}}` below.
- Every non-public call needs `Authorization: Bearer «accessToken»`.
- `«name»` = a value you captured from an earlier step.
- **Every step below names its auth explicitly** as `· auth: «token as ROLE»` — meaning "log in
  as the seeded `ROLE` account (Flow 1), take its `data.tokens.accessToken`, and send it as the
  `Authorization: Bearer` header on this call". `· auth: none` = public, send no header.
  If you send a token whose role is not listed, you get `403 INSUFFICIENT_ROLE` (or `404` when the
  route hides existence from non-owners).
- Companion docs: `API-Documentation.md` (every endpoint's shapes + the **Auth** line, which is
  read straight from each controller's `@PreAuthorize` — regenerate with
  `python scripts/gen-api-doc.py` after any controller change), `PROJECT-REFERENCE.md`
  (call chains), `webhooks.md` (gateway signatures), `required-integrations.md` (API keys),
  `postman/` (importable collection — the login request auto-captures the token).

> **Doc-accuracy note (2026-09-03).** `docs/openapi.json` is a static export from a running app and
> currently predates the Flow 12–19 endpoints below — so those ~35 routes are absent from both
> `openapi.json` and the generated `API-Documentation.md`. To refresh: start the app and re-run the
> pipeline in `scripts/README.md` (`GET /v3/api-docs` → `openapi.json` → `gen-api-doc.py`). The
> **Auth** lines for routes that *are* in `API-Documentation.md` were regenerated on 2026-09-03 and
> now match the controllers (this fixed stale lines on `PUT /admin/plans/{id}`, `POST /ba/documents`
> and the two `GET /batches` routes — see the Flow 2 Step 3 note).

---

## 0 · Seeded accounts (from `db/testdata/R__dev_seed_data.sql`, auto-applied by Flyway in the `dev` profile)

All password **`Password123!`**. UUIDs are fixed so you can paste them.

| email | role | uuid | 2FA on login? |
|---|---|---|---|
| `admin@moriah.test` | ADMIN | `11111111-0000-0000-0000-000000000001` | **yes — mandatory** |
| `pm@moriah.test` | TRAINER_PM | `…0002` | no |
| `dev@moriah.test` | DEVELOPER | `…0003` | no |
| `sales@moriah.test` | LEAD_GEN | `…0004` | no |
| `hr@moriah.test` | HR_MANAGER | `…0005` | **yes — mandatory** |
| `ba@moriah.test` | BUSINESS_ANALYST | `…0006` | no |
| `client@moriah.test` | CLIENT | `…0007` | no |
| `student1@moriah.test` | STUDENT | `…0008` | no — **has ACTIVE PROJECT_BASED sub, enrolled in batch `FS-2026-01`** |
| `student2@moriah.test` | STUDENT | `…0009` | no — ACTIVE PROJECT_BASED, enrolled |
| `student3@moriah.test` | STUDENT | `…0010` | no — ACTIVE **STARTER** (use to test `403 ENTITLEMENT_REQUIRED`) |

Also seeded: batch `FS-2026-01` (track `FULL_STACK`, PM = `pm@`), sprint 1 (`ACTIVE`), 3 tasks
(one `BACKLOG` "Implement GET /todos", one `ASSIGNED`, one `IN_REVIEW`), one `SCHEDULED` standup,
a `PUBLISHED` project "Todo API", one `NEW` lead, two `employees`.

### Which token unlocks which flow

| Log in as | Token drives | Flows |
|---|---|---|
| `student1@` (STUDENT) | learning journey, quiz-taking, own interviews, notifications, resource/lesson **browse** | 2, 4 (student half), 12, 13 (browse), 14 (browse + submit), 16 (`/me`) |
| `pm@` (TRAINER_PM) | batches, sprints, tasks, standups, reviews, graduation, interviews, question banks | 3, 6, 7 (browse), 16, 17 |
| `dev@` (DEVELOPER) | projects, bug challenges, requirement-doc **review**, resource & lesson **authoring** | 4, 13 (author), 14 (author), 15 (Dev side) |
| `sales@` (LEAD_GEN) | leads, lead campaigns, talent-pool browse | 8, 18, 20 (browse) |
| `ba@` (BUSINESS_ANALYST) | requirement documents, allocations, BA meetings, resource authoring | 13 (author), 15 (BA side), 19 |
| `hr@` (HR_MANAGER) | employees, payroll, letters, exits, onboarding, disciplinary, recruitment decisions | 9, 20 (decide), 21 |
| `client@` (CLIENT) | talent-pool browse, recruitment requests | 20 |
| `admin@` (ADMIN) | everything above + admin metrics/users/plans/payments/coupons/audit/exports | 10, 22, and any staff route |

> **You do not need the payment flow to test the learning journey** — `student1` / `student2`
> are already enrolled. Payments (Flow 5) need real Razorpay/Stripe **test-mode** keys.

---

## Flow 1 · Authentication

### 1A — Plain login (no 2FA: `pm@`, `dev@`, `sales@`, `ba@`, `client@`, `student1‑3`)

**Step 1** · `POST {{baseUrl}}/api/v1/auth/login` · auth: none
```json
{ "email": "student1@moriah.test", "password": "Password123!" }
```
Response:
```json
{ "success": true, "data": {
    "twoFactorRequired": false, "twoFactorSetupRequired": false, "challengeToken": null,
    "tokens": { "accessToken": "eyJ…", "refreshToken": "…", "expiresInSeconds": 3600 } } }
```
→ **keep `data.tokens.accessToken` as «accessToken»** and `data.tokens.refreshToken` as
«refreshToken». Put `«accessToken»` in the `Authorization: Bearer` header for every step after this.

---

### 1B — First login for an account with mandatory 2FA (`admin@`, `hr@`)

**Step 1** · `POST {{baseUrl}}/api/v1/auth/login` · auth: none
```json
{ "email": "admin@moriah.test", "password": "Password123!" }
```
Response:
```json
{ "success": true, "data": {
    "twoFactorRequired": true, "twoFactorSetupRequired": true,
    "challengeToken": "0t5Ibn…88-char-opaque…", "tokens": null } }
```
→ **keep `data.challengeToken` as «challengeToken»**. It is valid **5 minutes** — the next two
steps must finish inside that window (re-do Step 1 for a fresh one if it lapses).

**Step 2 — get the TOTP secret** · `POST {{baseUrl}}/api/v1/auth/2fa/enable` · auth: none
```json
{ "challengeToken": "«challengeToken»" }
```
Response:
```json
{ "success": true, "data": {
    "secret": "JBSWY3DPEHPK3PXP…",
    "provisioningUri": "otpauth://totp/Moriah%20Skill%20Hub:admin@moriah.test?secret=JBSWY3DP…&issuer=Moriah%20Skill%20Hub&algorithm=SHA1&digits=6&period=30" } }
```
→ **keep `data.secret` as «secret»** (and `data.provisioningUri` as «otpauthUri»).

**Step 3 — turn «secret» into a 6-digit code.** Pick one (details in **Appendix A**):
- **Python (what you used):** `python -c "import pyotp; print(pyotp.TOTP('«secret»').now())"`
- **Google Authenticator, no QR:** app → `＋` → *Enter a setup key* → paste `«secret»`, type *Time based*.
- **Local QR:** `python -c "import segno; segno.make('«otpauthUri»').terminal()"` → scan the terminal QR.

**Step 4 — verify** · `POST {{baseUrl}}/api/v1/auth/2fa/verify` · auth: none · **Content-Type: application/json** (see the note below)
```json
{ "challengeToken": "«challengeToken»", "totpCode": "013015" }
```
Response = a completed `LoginResponse`:
```json
{ "success": true, "data": {
    "twoFactorRequired": false, "twoFactorSetupRequired": false, "challengeToken": null,
    "tokens": { "accessToken": "eyJ…", "refreshToken": "…", "expiresInSeconds": 3600 } } }
```
→ **keep `data.tokens.accessToken` as «accessToken»**. `users.two_factor_enabled` is now `true`.

> ⚠️ **If you get `500 INTERNAL_ERROR` with `Content-Type 'text/plain' is not supported`** — your
> request went out as `text/plain`. In Postman: **Body** tab → the dropdown next to `raw` → change
> **Text → JSON**. `challengeToken`/`totpCode` were never read; fix the Content-Type and resend.
> A wrong TOTP code does **not** consume the challenge — retry within the 5 min.

---

### 1C — Subsequent login for `admin@` / `hr@` (2FA already set up)

**Step 1** · `POST /api/v1/auth/login` with email+password → response has
`twoFactorRequired: true`, `twoFactorSetupRequired: **false**`, `challengeToken` set.
→ keep «challengeToken».

**Step 2** · generate the current 6-digit code from **the same «secret»** as 1B (Appendix A).

**Step 3** · `POST /api/v1/auth/2fa/verify`
```json
{ "challengeToken": "«challengeToken»", "totpCode": "482915" }
```
→ keep `data.tokens.accessToken`. (No `/2fa/enable` step this time.)

---

### 1D — Refresh the access token (rotation)

`POST {{baseUrl}}/api/v1/auth/refresh` · auth: none
```json
{ "refreshToken": "«refreshToken»" }
```
Response `data` = `{ "accessToken": "eyJ…", "refreshToken": "…(new)…", "expiresInSeconds": 3600 }`
→ **replace both** «accessToken» and «refreshToken» with the new values. The old refresh token is
now dead — reusing it revokes the whole chain.

### 1E — Logout

`POST {{baseUrl}}/api/v1/auth/logout` (this device) or `.../logout-all` (every device) · auth: none
```json
{ "refreshToken": "«refreshToken»" }
```
`logout-all` bumps `token_version` → every outstanding access token for that user is rejected on
its next request.

### 1F — Register a brand-new user (optional)

`POST /api/v1/auth/register`
```json
{ "fullName": "New Tester", "email": "new@moriah.test", "phone": "919812345678",
  "password": "Password123!", "githubUsername": "new-tester" }
```
The account is `PENDING_VERIFICATION`; login is blocked until `POST /api/v1/auth/verify-email`
with `{ "token": "«from the email»" }`. The email only sends if `SENDGRID_API_KEY` is real —
otherwise pull the token from the DB:
`docker exec skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub -e "SELECT token_hash FROM email_verification_tokens ORDER BY id DESC LIMIT 1;"` — note that stores the **SHA-256 hash**, not the raw token, so the raw token is only recoverable from the email/log. **Simplest: just use the seeded accounts, which are already ACTIVE + verified.**

---

## Flow 2 · Student learning journey  — login as `student1@moriah.test` (1A)

**Step 1 — my profile** · `GET {{baseUrl}}/api/v1/users/me`
→ keep `data.uuid` as «myUuid» (should be `…0008`).

**Step 2 — update profile** · `PUT {{baseUrl}}/api/v1/users/me/profile`
```json
{ "githubUsername": "sam-s1", "bio": "Learning full-stack.", "location": "Bengaluru",
  "currentTitle": "Trainee", "experienceLevel": "JUNIOR", "yearsExperience": 1,
  "skills": ["Java","Spring","MySQL"],
  "education": [{ "institution": "ABC University", "degree": "B.Tech", "fieldOfStudy": "CSE", "startYear": 2020, "endYear": 2024 }],
  "workExperience": [] }
```
→ response `completionPercent` recalculates server-side.

**Step 3 — my batches** · `GET {{baseUrl}}/api/v1/batches` · auth: «token as STUDENT»
→ from `data.content[]` find `name = "FS-2026-01"`; keep its `id` as «batchId».

> **Auth scoping (fixed 2026-09-03).** This route is `TRAINER_PM` / `ADMIN` / `STUDENT`. A
> STUDENT-only token gets **only the batches they are enrolled in** (any `batch_students` status),
> not the whole list — the same narrowing `GET /sprints`, `GET /tasks` and `GET /standups` already
> do for students. `GET /api/v1/batches/{id}` likewise returns `404 BATCH_NOT_FOUND` to a student
> who is not enrolled in that batch. TRAINER_PM / ADMIN still see every batch. (Before this fix the
> controller was staff-only, contradicting the published API doc — now code and doc agree.)

**Step 4 — sprints in my batch** · `GET {{baseUrl}}/api/v1/sprints?batchId=«batchId»`
→ keep the `ACTIVE` sprint's `id` as «sprintId».

**Step 5 — backlog tasks** · `GET {{baseUrl}}/api/v1/tasks?sprintId=«sprintId»&status=BACKLOG`
→ keep the id of "Implement GET /todos" as «backlogTaskId».

**Step 6 — pull the task** · `POST {{baseUrl}}/api/v1/tasks/«backlogTaskId»/pull` · **no body**
→ response `status` becomes `IN_PROGRESS`, `assignedToUuid` = «myUuid». (Blocked with `403` if
you have an open `PROJECT_DELAY` PIP.)

**Step 7 — submit a GitHub PR** · `POST {{baseUrl}}/api/v1/submissions`
```json
{ "taskId": «backlogTaskId», "prUrl": "https://github.com/sam-s1/todo-api/pull/1",
  "videoUrl": "https://loom.com/share/xyz", "notes": "Implemented the list endpoint." }
```
→ keep `data.id` as «submissionId». `verifiedAt` is `null` unless `GITHUB_API_TOKEN` is real **and**
that PR exists with author = your `githubUsername`. The task moves to `IN_REVIEW`.

**Step 8 — check in to today's standup**
- `GET {{baseUrl}}/api/v1/standups?batchId=«batchId»&date=<today YYYY-MM-DD>` → keep `data[].id` as «standupId».
- `POST {{baseUrl}}/api/v1/standups/«standupId»/checkin`
  ```json
  { "blockerNotes": "None." }
  ```
  On time → `PRESENT`, after `lateCutoffMinutes` → `LATE`. Second check-in is idempotent.

**Step 9 — my attendance** · `GET {{baseUrl}}/api/v1/attendance/me` → your rows (percentages come
from `student_metrics`, refreshed by the nightly job — will be empty/low until that runs).

**Step 10 — my PIP status** · `GET {{baseUrl}}/api/v1/pip/me` → empty unless the nightly PIP job
triggered a record.

**Step 11 — my certificates** · `GET {{baseUrl}}/api/v1/certificates/me` → empty until you're
graduated + issued (Flow 6).

**Entitlement check:** repeat Steps 3–6 logged in as `student3@` (STARTER) → you get
`403 ENTITLEMENT_REQUIRED` on the sprint/task endpoints.

---

## Flow 3 · PM / Trainer journey — login as `pm@moriah.test` (1A)

**Step 1 — create a batch** (or reuse the seeded one) · `POST {{baseUrl}}/api/v1/batches`
```json
{ "name": "FS-2026-02", "trackCode": "FULL_STACK", "planTierMinCode": "PROJECT_BASED",
  "startDate": "2026-09-01", "endDate": "2026-12-01", "capacity": 15 }
```
→ keep `data.id` as «batchId». You (the caller) become `pmUuid`.

**Step 2 — add a student** · `POST {{baseUrl}}/api/v1/batches/«batchId»/students`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000009" }
```
(student2. `409` if the batch is full or the student's tier is below `planTierMinCode`.)

**Step 2b — read the roster** · `GET {{baseUrl}}/api/v1/batches/«batchId»/students` · auth: «token as TRAINER_PM»
→ `data[]` of `{ userUuid, fullName, email, status (ACTIVE｜ON_PIP｜GRADUATED｜TERMINATED｜REASSIGNED),
joinedAt, graduatedAt, finalScore }`. **PM/ADMIN only** and a TRAINER_PM must own the batch (`403
NOT_BATCH_OWNER` otherwise). Keep a `userUuid` as «studentUuid» — it feeds `POST /tasks/{id}/assign`,
`.../students/{userUuid}/graduate`, and `POST /hr/letters/{type}`.

**Step 3 — create sprint 1** · `POST {{baseUrl}}/api/v1/sprints`
```json
{ "batchId": «batchId», "sprintNumber": 1, "goal": "Core CRUD API",
  "startDate": "2026-09-01", "endDate": "2026-09-14", "plannedPoints": 20 }
```
→ keep `data.id` as «sprintId».

**Step 4 — activate the sprint** · `POST {{baseUrl}}/api/v1/sprints/«sprintId»/activate` · no body
(requires the previous sprint, if any, to be `COMPLETED`; one `ACTIVE` sprint per batch).

**Step 5 — create tasks** · `POST {{baseUrl}}/api/v1/tasks`
```json
{ "sprintId": «sprintId», "projectId": null, "title": "Implement GET /todos",
  "description": "Paginated list endpoint.", "taskType": "STORY", "storyPoints": 3,
  "dueAt": "2026-09-10T18:00:00Z" }
```
→ keep `data.id` as «taskId». (Illegal state transitions on `PUT /tasks/{id}` return `409`.)

**Step 6 — assign a task** · `POST {{baseUrl}}/api/v1/tasks/«taskId»/assign`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000009" }
```

**Step 7 — weekly assignment window** · `POST {{baseUrl}}/api/v1/assignment-windows`
```json
{ "batchId": «batchId», "weekStart": "2026-09-01", "weekEnd": "2026-09-07",
  "dueAt": "2026-09-07T18:00:00Z", "taskId": «taskId» }
```

**Step 8 — schedule a standup** · `POST {{baseUrl}}/api/v1/standups`
```json
{ "batchId": «batchId», "sprintId": «sprintId», "scheduledAt": "2026-09-03T10:00:00Z",
  "lateCutoffMinutes": 15, "notes": "Daily sync" }
```
→ keep `data.id` as «standupId».

**Step 9 — record / override attendance** · `POST {{baseUrl}}/api/v1/standups/«standupId»/attendance`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000009", "status": "PRESENT", "blockerNotes": "" }
```
(overrides are audited with old + new value.)

**Step 10 — the review queue** · `GET {{baseUrl}}/api/v1/reviews/queue`
→ from the page, keep a submission's `id` as «submissionId» (put a student through Flow 2 Step 7
first, or use the seeded `IN_REVIEW` task's submission if present).

**Step 11 — review a submission** · `POST {{baseUrl}}/api/v1/reviews`
```json
{ "submissionId": «submissionId», "score": 8, "verdict": "APPROVED",
  "comments": "Clean, add a test for the empty case.",
  "inlineComments": [{ "filePath": "src/TodoController.java", "line": 42, "comment": "extract this" }] }
```
`APPROVED` → the task closes (`COMPLETED`) and feeds sprint velocity.

**Step 12 — weekly code-defence review** (feeds the `REVIEW_FAILED` PIP rule) ·
`POST {{baseUrl}}/api/v1/reviews/weekly`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000009", "batchId": «batchId», "sprintId": «sprintId»,
  "weekStart": "2026-09-01", "rating": "SATISFACTORY", "notes": "On track." }
```

**Step 13 — graduate a student** (precondition for a certificate) ·
`POST {{baseUrl}}/api/v1/batches/«batchId»/students/11111111-0000-0000-0000-000000000009/graduate`
· no body → `batch_students.status = GRADUATED`.

---

## Flow 4 · Developer content — login as `dev@moriah.test` (1A)

**Step 1 — create a project** · `POST {{baseUrl}}/api/v1/projects`
```json
{ "title": "Todo API v2", "description": "REST todo list.", "techStack": ["Java","Spring Boot"],
  "difficulty": "BEGINNER", "domain": "Web", "starterRepoUrl": "https://github.com/moriah/todo-starter",
  "version": "v2" }
```
→ keep `data.id` as «projectId» (status `DRAFT`).

**Step 2 — upload an asset** · `POST {{baseUrl}}/api/v1/projects/«projectId»/assets` ·
`multipart/form-data`, field **`file`** = any file (≤ 10 MB, magic-byte checked).

**Step 3 — bug-fix challenge** · `POST {{baseUrl}}/api/v1/projects/«projectId»/challenges` ·
`multipart/form-data`, fields **`brokenCode`** (file) + **`testScript`** (file).

**Step 4 — publish** · `POST {{baseUrl}}/api/v1/projects/«projectId»/publish` · no body →
`DRAFT → PUBLISHED`. Only `PUBLISHED` projects can attach to a task (`tasks.projectId`).

**Step 5 — author a quiz** · `POST {{baseUrl}}/api/v1/assessments`
```json
{ "batchId": null, "projectId": «projectId», "title": "Spring Basics", "durationMinutes": 20,
  "passPercentage": 60, "maxAttempts": 2,
  "questions": [
    { "questionText": "What annotation marks a REST controller?", "questionType": "MCQ",
      "options": ["@Service","@RestController","@Entity","@Bean"], "correctAnswerIndices": [1],
      "marks": 5, "explanation": "@RestController = @Controller + @ResponseBody" },
    { "questionText": "Pick the JPA annotations", "questionType": "MULTI_SELECT",
      "options": ["@Entity","@Table","@RestController","@Id"], "correctAnswerIndices": [0,1,3],
      "marks": 5, "explanation": "" } ] }
```
→ keep `data.id` as «quizId».

### Student takes the quiz (login as `student1@`)

**Step 6 — list quizzes** · `GET {{baseUrl}}/api/v1/assessments?batchId=«batchId»` → find «quizId».

**Step 7 — start an attempt** · `POST {{baseUrl}}/api/v1/assessments/«quizId»/attempts` · no body
→ keep `data.id` as «attemptId`. Response contains the questions **without** the correct answers.

**Step 8 — submit** · `POST {{baseUrl}}/api/v1/assessments/attempts/«attemptId»/submit`
```json
{ "answers": [
    { "questionId": «q1Id», "selectedOptionIndices": [1], "codeAnswer": null },
    { "questionId": «q2Id», "selectedOptionIndices": [0,1,3], "codeAnswer": null } ] }
```
(`questionId`s come from Step 7's response.) → `percentage` = auto-graded / auto-gradable;
`CODE` answers are excluded from the denominator and the attempt is flagged
`PENDING_MANUAL_GRADING`.

**Step 9 — view a graded attempt** · `GET {{baseUrl}}/api/v1/assessments/attempts/«attemptId»`.

---

## Flow 5 · Payment & enrolment — needs real Razorpay/Stripe **test-mode** keys

> Without real test keys, `POST /subscriptions/checkout` fails at the gateway call
> (`502 PAYMENT_GATEWAY_ERROR`). Set `RAZORPAY_KEY_ID`/`_SECRET`/`_WEBHOOK_SECRET` (or the Stripe
> trio) per `required-integrations.md` §3.4/§3.5. **Or** skip this flow — seeded students are
> already enrolled. **Or** use the manual-payment-row shortcut at the end.

Log in as any student **without** an active sub (register a new one via 1F, or cancel a seed sub).

**Step 1 — plan catalogue** · `GET {{baseUrl}}/api/v1/plans` · auth: none → note `code` values
(`STARTER`…`CORPORATE_PROGRAM`).

**Step 2 — checkout** · `POST {{baseUrl}}/api/v1/subscriptions/checkout`
```json
{ "planCode": "PROJECT_BASED", "gateway": "RAZORPAY", "couponCode": null, "trackCode": "FULL_STACK" }
```
Response:
```json
{ "success": true, "data": {
    "gateway": "RAZORPAY", "paymentId": 101, "amount": 14999.00, "currency": "INR",
    "razorpayOrderId": "order_Pxxxx", "razorpayKeyId": "rzp_test_xxx", "stripeCheckoutUrl": null } }
```
→ keep `data.paymentId` as «paymentId» and `data.razorpayOrderId` as «orderId». (Stripe: keep
`stripeCheckoutUrl`, and `data.paymentId` — Stripe's webhook uses it as `client_reference_id`.)

**Step 3 — simulate the captured-payment webhook** · `POST {{baseUrl}}/api/v1/webhooks/razorpay` ·
auth: none · header `X-Razorpay-Signature: «sig»` (compute per **Appendix B**)
```json
{ "event": "payment.captured",
  "payload": { "payment": { "entity": {
      "id": "pay_TEST_«paymentId»", "order_id": "«orderId»", "amount": 1499900,
      "currency": "INR", "status": "captured" } } } }
```
(`amount` in **paise** = `data.amount × 100`.) → `payments → CAPTURED`, `user_subscriptions → ACTIVE`,
`invoices` row (`PENDING` → `ISSUED` after the async PDF), `BatchAllocationService` places the
student. Returns `200` with an empty body.

**Stripe equivalent** · `POST {{baseUrl}}/api/v1/webhooks/stripe` · header
`Stripe-Signature: t=«ts»,v1=«sig»`
```json
{ "id": "evt_TEST1", "type": "checkout.session.completed",
  "data": { "object": { "object": "checkout.session", "client_reference_id": "«paymentId»",
      "amount_total": 1499900, "currency": "inr", "payment_intent": "pi_TEST1", "payment_status": "paid" } } }
```

**Step 4 — my subscription** · `GET {{baseUrl}}/api/v1/subscriptions/me` (as that student) →
`status: "ACTIVE"`, `planCode: "PROJECT_BASED"`.

**Step 5 — refund** · `POST {{baseUrl}}/api/v1/webhooks/razorpay` with the same signing:
```json
{ "event": "refund.processed",
  "payload": { "payment": { "entity": { "id": "pay_TEST_«paymentId»" } } } }
```
→ `payments → REFUNDED`, subscription `CANCELLED`, student de-allocated (`REASSIGNED`).

**Replay test:** send Step 3's exact body+signature twice → the second returns `200` and creates
**no** second subscription/invoice (idempotency on `webhook_events.event_id`).

**Manual shortcut (no gateway keys):** insert a `payments` row yourself, then fire the webhook:
```sql
INSERT INTO payments (user_id, plan_id, gateway, gateway_order_id, amount, currency, status, track_code)
SELECT u.id, p.id, 'RAZORPAY', 'order_MANUAL_1', p.price_inr, 'INR', 'CREATED', 'FULL_STACK'
FROM users u JOIN subscription_plans p ON p.code='PROJECT_BASED' WHERE u.email='student3@moriah.test';
```
then use `order_id: "order_MANUAL_1"` and `amount` = `price_inr × 100` in the webhook body.

---

## Flow 6 · Certificate — PM graduates, PM/Admin issues, anyone verifies

Preconditions (checked by `CertificateService.issue`): the student is **GRADUATED** from the batch,
has **no open PIP**, and **every sprint in the batch is `COMPLETED`**.

**Step 1 (PM)** — close the sprint · `PUT {{baseUrl}}/api/v1/sprints/«sprintId»`
```json
{ "goal": "Core CRUD API", "startDate": "2026-09-01", "endDate": "2026-09-14",
  "plannedPoints": 20, "status": "COMPLETED" }
```

**Step 2 (PM)** — graduate · `POST {{baseUrl}}/api/v1/batches/«batchId»/students/«studentUuid»/graduate` · no body.

**Step 3 (PM/Admin)** — issue · `POST {{baseUrl}}/api/v1/certificates/issue`
```json
{ "batchId": «batchId», "userUuid": "«studentUuid»", "certificateType": "COMPLETION" }
```
Response:
```json
{ "success": true, "data": {
    "id": 1, "certificateNumber": "MSH-CERT-2026-000001",
    "verificationCode": "A1B2C3D4E5F6", "downloadUrl": "https://…presigned…", "issuedAt": "…" } }
```
→ keep `data.verificationCode` as «code» and `data.id` as «certId».

**Step 4 (student)** — `GET {{baseUrl}}/api/v1/certificates/me` → your certificate + `downloadUrl`.

**Step 5 (anyone, no auth)** — verify · `GET {{baseUrl}}/api/v1/certificates/verify/«code»`
→ `{ valid: true, holderName, batchName, certificateType, issuedAt }` — no email/phone/scores.

**Step 6 (PM/Admin)** — revoke · `POST {{baseUrl}}/api/v1/certificates/«certId»/revoke`
```json
{ "reason": "Issued in error." }
```
→ Step 5 now returns `valid: false` with the revocation date (never a `404`).

---

## Flow 7 · PIP (Performance Improvement Plan)

The **trigger** is the nightly job chain (01:30 → 01:45 → 02:00 IST), not an API call. The API is
for browsing and closing records.

**Browse (PM/Admin)** · `GET {{baseUrl}}/api/v1/pip?batchId=«batchId»&status=IN_PROGRESS`
→ from `data.content[]` keep a record's `id` as «pipId» and a `milestones[].id` as «milestoneId».

**Student view** · `GET {{baseUrl}}/api/v1/pip/me`.

**Complete a milestone (student)** ·
`POST {{baseUrl}}/api/v1/pip/«pipId»/milestones/«milestoneId»/complete` · no body.

**Day-15 exit review (PM)** · `POST {{baseUrl}}/api/v1/pip/«pipId»/review`
```json
{ "outcome": "CLEARED", "reviewNotes": "Task completion back above 85%." }
```
`CLEARED` is **rejected** unless `student_metrics` shows task completion ≥ 85% and a passed review —
verified server-side, the PM can't override it. Other outcomes: `TERMINATED`, `REASSIGNED`.

**Tune thresholds (Admin)** · `PUT {{baseUrl}}/api/v1/pip/rules/ATTENDANCE_LOW`
```json
{ "thresholdValue": 80.00, "severity": "HIGH", "active": true }
```
`GET {{baseUrl}}/api/v1/pip/rules` lists all 6 with current values.

> To make a record appear without waiting for 02:00 IST: set a seeded student's metrics below a
> threshold, e.g.
> `docker exec skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub -e "…"` inserting
> a `student_metrics` row with `attendance_percent = 50`, then invoke the job (there is no API for
> it — restart with the cron near-future, or call the job bean from a test).

---

## Flow 8 · CRM — login as `sales@moriah.test` (1A)

**Step 1 — create a lead** · `POST {{baseUrl}}/api/v1/leads`
```json
{ "name": "Ravi Kumar", "email": "ravi.k@example.com", "phone": "919812300011",
  "source": "LANDING_PAGE", "leadType": "B2C", "institution": "Self", "interestedPlanId": null }
```
→ keep `data.id` as «leadId». (Same email+phone again → updates the existing lead + logs an
activity, never a duplicate.)

**Step 2 — list** · `GET {{baseUrl}}/api/v1/leads?status=NEW&source=LANDING_PAGE`.

**Step 3 — move the pipeline** · `PUT {{baseUrl}}/api/v1/leads/«leadId»/status`
```json
{ "newStatus": "CONTACTED", "reason": "First call done", "lostReason": null, "convertedUserUuid": null }
```
Forward skips → `409`. Backward → allowed **with** `reason`. `LOST` needs `lostReason`.
`ENROLLED` needs `convertedUserUuid`.

**Step 4 — log an activity** · `POST {{baseUrl}}/api/v1/leads/«leadId»/activities`
```json
{ "activityType": "CALL", "outcome": "Interested, sending brochure", "notes": "10-min call",
  "nextFollowUpAt": "2026-09-05T10:00:00Z", "occurredAt": "2026-09-02T11:00:00Z", "templateCode": null }
```
(`activityType: "WHATSAPP"` also fires an outbound template — needs `WHATSAPP_*` keys and a
`templateCode`.)

**Step 5 — my targets** · `GET {{baseUrl}}/api/v1/leads/targets/me`.

---

## Flow 9 · HR — login as `hr@moriah.test` (1B first — HR has mandatory 2FA)

**Step 1 — create an employee** · `POST {{baseUrl}}/api/v1/hr/employees`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000008", "employeeCode": "EMP-1001",
  "department": "Engineering", "designation": "Intern", "employmentType": "INTERN",
  "dateOfJoining": "2026-09-01", "baseSalary": 25000.00, "hourlyRate": null, "reportingManagerId": 1 }
```
Exactly one of `baseSalary` / `hourlyRate` must be set. → keep `data.id` as «employeeId».
(`reportingManagerId` is an **employees.id** — `1` = the seeded HR employee EMP-0001.)

**Step 2 — upload a KYC document** · `POST {{baseUrl}}/api/v1/hr/documents` · `multipart/form-data`,
field **`file`** (PDF; magic-byte checked). Also send a query/param `documentType` if your client
supports it (slug-sanitised server-side). → keep `data.id` as «docId».

**Step 2b — list HR documents** · `GET {{baseUrl}}/api/v1/hr/documents?status=PENDING` · auth: «any token»
→ `data.content[]` of `{ id, userUuid, userFullName, documentType, verificationStatus
(PENDING｜VERIFIED｜REJECTED), verifiedByUuid, verifiedAt, rejectionReason, downloadUrl
(presigned, 15-min TTL), createdAt }`. A plain employee token sees **only its own** rows (the
`userUuid` param is ignored for them); `hr@` / `admin@` see everyone and may add `&userUuid=…` and
`&documentType=…`. `downloadUrl` is `null` on the `POST` / `PUT` responses — re-list to get a link.

**Step 3 — verify the document** · `PUT {{baseUrl}}/api/v1/hr/documents/«docId»/verify`
```json
{ "decision": "VERIFIED", "rejectionReason": null }
```
(`REJECTED` requires `rejectionReason`. You can't verify your own document.)

**Step 4 — apply for leave** (login as the employee) · `POST {{baseUrl}}/api/v1/hr/leaves`
```json
{ "leaveType": "CASUAL", "fromDate": "2026-09-10", "toDate": "2026-09-11", "reason": "Personal" }
```
→ keep `data.id` as «leaveId». Overlapping approved leave → `409`.

**Step 4b — list leave requests** · `GET {{baseUrl}}/api/v1/hr/leaves?status=PENDING` · auth: «any token»
→ `data.content[]` of `{ id, userUuid, userFullName, leaveType, fromDate, toDate, days, reason,
status (PENDING｜APPROVED｜REJECTED), approvedByUuid, decidedAt, createdAt }`. A plain employee
token sees **only its own** rows (the `userUuid` param is ignored for them); `hr@` / `admin@` see
everyone and may add `&userUuid=…`.

**Step 5 — decide the leave** (login as the reporting manager or HR) ·
`PUT {{baseUrl}}/api/v1/hr/leaves/«leaveId»/decision`
```json
{ "decision": "APPROVED" }
```

**Step 6 — run payroll** · `POST {{baseUrl}}/api/v1/hr/payroll/generate`
```json
{ "periodMonth": "2026-09-01", "workingDays": 22,
  "lines": [ { "employeeId": «employeeId», "presentDays": 21, "sessionHours": null, "deductions": 500.00 } ] }
```
Unique per `(employee, periodMonth)` → re-running the same month → `409`. → payslip PDF key on
the record.

**Step 7 — list payroll** · `GET {{baseUrl}}/api/v1/hr/payroll?month=2026-09-01`.

**Step 8 — generate a letter** · `POST {{baseUrl}}/api/v1/hr/letters/experience`
```json
{ "userUuid": "11111111-0000-0000-0000-000000000008" }
```
`type` path values: `offer` / `internship` / `experience` / `relieving`. Experience/relieving
require the student to be `GRADUATED` or cleanly `EXITED` — never terminated.

---

## Flow 10 · Admin — login as `admin@moriah.test` (1B first — mandatory 2FA)

**Step 1 — KPI overview** · `GET {{baseUrl}}/api/v1/admin/metrics/overview` (cached 5 min).

**Step 2 — revenue by range** · `GET {{baseUrl}}/api/v1/admin/metrics/revenue?from=2026-01-01&to=2026-12-31`.

**Step 3 — list users** · `GET {{baseUrl}}/api/v1/admin/users?role=STUDENT&status=ACTIVE`
→ from `data.content[]` keep a `uuid` as «targetUuid».

**Step 4 — suspend a user** · `PUT {{baseUrl}}/api/v1/admin/users/«targetUuid»/status`
```json
{ "status": "SUSPENDED" }
```
→ bumps `token_version`; that user's existing access token is rejected on its **next** request
(verify by calling `GET /users/me` with their old token → `401`).

**Step 5 — change roles** · `PUT {{baseUrl}}/api/v1/admin/users/«targetUuid»/roles`
```json
{ "roles": ["STUDENT", "DEVELOPER"] }
```

**Step 6 — edit a plan's pricing** · `PUT {{baseUrl}}/api/v1/admin/plans/3`
```json
{ "name": "Project Based", "priceInr": 15999.00, "durationDays": 180, "maxProjects": null,
  "mentorSupport": false, "allowsBatch": true, "allowsSprints": true, "allowsPip": true,
  "allowsInternshipLetter": false, "allowsClientProject": false, "active": true }
```
(`3` = the plan's numeric id; get it from `GET /api/v1/plans`. Cache is evicted on write.)

**Step 7 — audit log** · `GET {{baseUrl}}/api/v1/admin/audit?entityType=User&from=2026-09-01`
→ read-only (the DB grant itself blocks `UPDATE`/`DELETE`).

**Step 8 — export** · `POST {{baseUrl}}/api/v1/admin/exports/users` · no body →
`data.downloadUrl` = a presigned XLSX link. `report` path values: `users` / `revenue` / `audit`.

---

## Flow 11 · OAuth2 login (browser only — not a Postman request)

1. Set real `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` in `.env` and register the redirect URI
   `http://localhost:8080/api/v1/auth/oauth2/callback/google` in Google Cloud Console
   (`required-integrations.md` §3.1).
2. Open in a browser: `http://localhost:8080/api/v1/auth/oauth2/authorize/google`
3. Sign in → the callback returns the **same `LoginResponse` JSON** as Flow 1A
   (`data.tokens.accessToken` …). First login for that email creates a `STUDENT`.
4. GitHub is identical with `…/authorize/github` and `…/callback/github`.

---

# Flows 12–22 · Post-launch modules (not yet in `openapi.json` / `API-Documentation.md`)

Every route here is under `/api/v1`. Auth is the controller's `@PreAuthorize` verbatim.
`«…»` values still carry over exactly as in Appendix C.

---

## Flow 12 · Notifications — any logged-in user

**Step 1 — my feed** · `GET {{baseUrl}}/api/v1/notifications?page=0&size=20` · auth: «any token»
(add `&unreadOnly=true` to limit to unread). → `data.content[]` rows are
`{ id, templateCode, payload, read, createdAt }`. Keep one unread row's `id` as «notificationId».

**Step 2 — unread badge count** · `GET {{baseUrl}}/api/v1/notifications/unread-count` · auth: «any token»
→ `data.count`.

**Step 3 — mark one read** · `PUT {{baseUrl}}/api/v1/notifications/«notificationId»/read` · no body ·
auth: «same token as Step 1» → idempotent; `404` if the id is not the caller's own row.

**Step 4 — mark all read** · `PUT {{baseUrl}}/api/v1/notifications/read-all` · no body ·
auth: «any token» → `data.updated` = how many rows flipped.

---

## Flow 13 · Resource library — browse (any user) · author (curators)

Curators = `TRAINER_PM` / `DEVELOPER` / `BUSINESS_ANALYST` / `ADMIN`.

**Step 1 — browse** · `GET {{baseUrl}}/api/v1/resources?page=0&size=20` · auth: «any token»
(optional `&category=ARTICLE` — one of `ARTICLE｜VIDEO｜BOOK｜TOOL｜TEMPLATE｜COURSE｜OTHER` — and
`&search=spring`). → keep a `data.content[].id` as «resourceId».

**Step 2 — one resource** · `GET {{baseUrl}}/api/v1/resources/«resourceId»` · auth: «any token».

**Step 3 — add a resource** · `POST {{baseUrl}}/api/v1/resources` · auth: «token as DEVELOPER»
```json
{ "title": "Spring Data JPA Guide", "description": "Reference for repositories & queries.",
  "category": "ARTICLE", "url": "https://docs.spring.io/spring-data/jpa/reference/",
  "tags": ["spring","jpa","backend"] }
```
→ keep `data.id` as «resourceId». The caller becomes the resource's creator.

**Step 4 — edit** · `PUT {{baseUrl}}/api/v1/resources/«resourceId»` · auth: «creator token or ADMIN»
— same body shape as Step 3. `403` for a curator who is not the creator (and not ADMIN).

**Step 5 — deactivate** · `DELETE {{baseUrl}}/api/v1/resources/«resourceId»` · no body ·
auth: «creator token or ADMIN» → sets `is_active = false`; the row is never deleted.

---

## Flow 14 · Video lessons + per-lesson quiz

Authoring roles = `DEVELOPER` / `TRAINER_PM` / `ADMIN`. Browsing / progress / quiz submit = any user.

### Author side (login as `dev@`)

**Step 1 — create a lesson** · `POST {{baseUrl}}/api/v1/lessons` · auth: «token as DEVELOPER»
```json
{ "title": "Intro to Spring Boot", "description": "Beans, auto-config, starters.",
  "moduleName": "Backend Foundations", "videoUrl": "https://youtu.be/dQw4w9WgXcQ",
  "durationSeconds": 900, "sortOrder": 0, "published": true }
```
→ keep `data.id` as «lessonId». (`published:false` hides it from student browse until
`PUT /lessons/{id}` flips it.)

**Step 2 — add a quiz question** · `POST {{baseUrl}}/api/v1/lessons/«lessonId»/quiz/questions` ·
auth: «creator token or ADMIN»
```json
{ "questionText": "Which annotation bootstraps a Spring Boot app?",
  "options": ["@SpringBootApplication","@EnableAutoConfiguration","@ComponentScan","@Configuration"],
  "correctIndex": 0, "explanation": "@SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan" }
```
Repeat for as many questions as you want. `correctIndex` must be `< options.length` (2–6 options).
→ keep each `data.id` as «quizQuestionId». **`correctIndex` is never returned on read.**

**Step 3 — remove a question** (optional) ·
`DELETE {{baseUrl}}/api/v1/lessons/«lessonId»/quiz/questions/«quizQuestionId»` · no body ·
auth: «creator token or ADMIN».

### Student side (login as `student1@`)

**Step 4 — browse lessons** · `GET {{baseUrl}}/api/v1/lessons?page=0&size=20` · auth: «token as STUDENT»
(optional `&module=Backend%20Foundations`). Each row carries the caller's `progress`.
→ keep a `data.content[].id` as «lessonId». `GET /api/v1/lessons/modules` lists distinct module
names + counts.

**Step 5 — report watch progress** · `POST {{baseUrl}}/api/v1/lessons/«lessonId»/progress` ·
auth: «token as STUDENT»
```json
{ "watchedSeconds": 420, "completed": false }
```
Upsert — `watchedSeconds` never moves backwards; `completed:true` (or crossing the quiz pass mark)
stamps `completedAt` once.

**Step 6 — my progress across all lessons** · `GET {{baseUrl}}/api/v1/lessons/me/progress` ·
auth: «token as STUDENT».

**Step 7 — read the quiz** · `GET {{baseUrl}}/api/v1/lessons/«lessonId»/quiz` · auth: «any token»
→ `data[]` = `{ id, questionText, options, explanation }` (no answer key). Keep the `id`s in order
as «q1Id», «q2Id»…

**Step 8 — submit the quiz** · `POST {{baseUrl}}/api/v1/lessons/«lessonId»/quiz/submit` ·
auth: «token as STUDENT»
```json
{ "answers": [0, 2] }
```
`answers[i]` is the chosen option index for question `i`, **positionally matched** to Step 7's
order. A missing or out-of-range entry counts wrong. Response:
```json
{ "success": true, "data": {
    "score": 1, "total": 2, "passed": false, "passMarkPercent": 60, "submittedAt": "…" } }
```
`passed` = `score*100 >= total*60`. One attempt row per `(lesson, user)` — resubmitting overwrites
it. On the first pass, the lesson's `LessonProgress` is upserted to `COMPLETED`.

---

## Flow 15 · Requirement documents — BA authors, Developer reviews

### BA side (login as `ba@`)

**Step 1 — create a doc** · `POST {{baseUrl}}/api/v1/ba/documents` · auth: «token as BUSINESS_ANALYST»
```json
{ "clientProjectId": 1, "docType": "BRD", "title": "Todo API — Business Requirements",
  "content": "## Goals\n- CRUD todos\n- Auth\n" }
```
`docType`: `BRD｜SRS｜FRS｜USER_STORY`. → lands `IN_REVIEW`. Keep `data.id` as «docId».

**Step 2 — approve it** · `PUT {{baseUrl}}/api/v1/ba/documents/«docId»/approve` · no body ·
auth: «token as BUSINESS_ANALYST» → `IN_REVIEW → APPROVED`.

**Step 3 — list (BA view)** · `GET {{baseUrl}}/api/v1/ba/documents?status=APPROVED` ·
auth: «token as BUSINESS_ANALYST» (optional `&clientProjectId=1`).

### Developer side (login as `dev@`)

**Step 4 — list docs to review** · `GET {{baseUrl}}/api/v1/dev/requirement-documents?status=APPROVED` ·
auth: «token as DEVELOPER» (optional `&clientProjectId=1`). → keep a `data.content[].id` as «docId».

**Step 5 — read full content** · `GET {{baseUrl}}/api/v1/dev/requirement-documents/«docId»` ·
auth: «token as DEVELOPER» → `data.content` is the full markdown.

**Step 6 — acknowledge** · `POST {{baseUrl}}/api/v1/dev/requirement-documents/«docId»/acknowledge` ·
no body · auth: «token as DEVELOPER» → stamps `dev_reviewed_at` / `dev_reviewed_by` for the caller;
idempotent; **does not** change the document status.

---

## Flow 16 · Student interviews — PM schedules, student views

**Step 1 (PM) — schedule** · `POST {{baseUrl}}/api/v1/interviews` · auth: «token as TRAINER_PM»
```json
{ "studentUuid": "11111111-0000-0000-0000-000000000008",
  "interviewType": "MOCK", "scheduledAt": "2026-09-20T10:00:00Z", "durationMinutes": 45,
  "mode": "ONLINE", "location": null, "interviewerName": "Priya (PM)",
  "meetingLink": "https://meet.google.com/abc-defg-hij" }
```
`interviewType`: `MOCK｜TECHNICAL｜HR｜PLACEMENT`. `mode`: `ONLINE｜ONSITE`.
→ starts `SCHEDULED`; keep `data.id` as «interviewId».

**Step 2 (PM) — list** · `GET {{baseUrl}}/api/v1/interviews?status=SCHEDULED` · auth: «token as TRAINER_PM»
(optional `&type=MOCK`, `&studentUuid=…`).

**Step 3 (student) — my interviews** · `GET {{baseUrl}}/api/v1/interviews/me` · auth: «token as STUDENT».

**Step 4 (PM) — update / add feedback** · `PUT {{baseUrl}}/api/v1/interviews/«interviewId»` ·
auth: «token as TRAINER_PM»
```json
{ "interviewType": "MOCK", "scheduledAt": "2026-09-20T10:00:00Z", "durationMinutes": 45,
  "mode": "ONLINE", "location": null, "interviewerName": "Priya (PM)",
  "meetingLink": "https://meet.google.com/abc-defg-hij",
  "status": "COMPLETED", "feedback": "Strong on data structures; revise system design.", "rating": 4 }
```

**Step 5 (PM) — cancel** · `DELETE {{baseUrl}}/api/v1/interviews/«interviewId»` · no body ·
auth: «token as TRAINER_PM» → `status = CANCELLED`, never row-deleted.

---

## Flow 17 · Question banks — assessment authoring (`pm@` or `admin@`)

**Step 1 — create a bank** · `POST {{baseUrl}}/api/v1/assessments/banks` · auth: «token as TRAINER_PM»
```json
{ "name": "Java Core — Set A", "topic": "Java", "description": "Reusable MCQ/CODE pool." }
```
→ keep `data.id` as «bankId».

**Step 2 — list banks** · `GET {{baseUrl}}/api/v1/assessments/banks?active=true` ·
auth: «token as TRAINER_PM» (optional `&topic=Java`).

**Step 3 — add a question** · `POST {{baseUrl}}/api/v1/assessments/banks/«bankId»/questions` ·
auth: «token as TRAINER_PM»
```json
{ "questionText": "What is the default value of a boolean field?",
  "questionType": "MCQ", "options": ["true","false","0","null"],
  "correctAnswerIndices": [1], "marks": 2, "explanation": "Primitive boolean defaults to false.",
  "difficulty": "EASY" }
```
`questionType`: `MCQ｜MULTI_SELECT｜CODE`. A `CODE` question must have **no** `options` and **no**
`correctAnswerIndices`; MCQ/MULTI_SELECT need ≥ 2 options. → keep `data.id` as «bankQuestionId».
**Answer keys are never returned** on `GET .../questions`.

**Step 4 — list questions** · `GET {{baseUrl}}/api/v1/assessments/banks/«bankId»/questions` ·
auth: «token as TRAINER_PM».

**Step 5 — rename / toggle active** · `PUT {{baseUrl}}/api/v1/assessments/banks/«bankId»` ·
auth: «token as TRAINER_PM»
```json
{ "name": "Java Core — Set A (2026)", "topic": "Java", "description": "…", "active": true }
```

**Step 6 — remove a question** ·
`DELETE {{baseUrl}}/api/v1/assessments/banks/«bankId»/questions/«bankQuestionId»` · no body ·
auth: «token as TRAINER_PM».

**Step 7 — deactivate the bank** · `DELETE {{baseUrl}}/api/v1/assessments/banks/«bankId»` · no body ·
auth: «token as TRAINER_PM» → `is_active = false`; never row-deletes.

---

## Flow 18 · Lead campaigns — login as `sales@moriah.test` (1A)

**Step 1 — create** · `POST {{baseUrl}}/api/v1/leads/campaigns` · auth: «token as LEAD_GEN»
```json
{ "name": "Sep 2026 LinkedIn Push", "channel": "SOCIAL", "description": "Target final-year CS.",
  "startDate": "2026-09-01", "endDate": "2026-09-30", "budget": 50000.00, "targetLeads": 200 }
```
`channel`: `EMAIL｜SOCIAL｜EVENT｜REFERRAL｜PAID_ADS｜WEBINAR`. → starts `PLANNED`; keep `data.id` as
«campaignId».

**Step 2 — list** · `GET {{baseUrl}}/api/v1/leads/campaigns?status=PLANNED` · auth: «token as LEAD_GEN».

**Step 3 — update (incl. status)** · `PUT {{baseUrl}}/api/v1/leads/campaigns/«campaignId»` ·
auth: «token as LEAD_GEN» — full body as Step 1 plus `"status": "ACTIVE"`
(`PLANNED｜ACTIVE｜COMPLETED｜CANCELLED`).

**Step 4 — cancel** · `DELETE {{baseUrl}}/api/v1/leads/campaigns/«campaignId»` · no body ·
auth: «token as LEAD_GEN» → `status = CANCELLED`, never row-deleted.

---

## Flow 19 · BA meetings — login as `ba@moriah.test` (1A)

**Step 1 — schedule** · `POST {{baseUrl}}/api/v1/ba/meetings` · auth: «token as BUSINESS_ANALYST»
```json
{ "title": "Kickoff — Todo API", "agenda": "Scope, timeline, stakeholders.",
  "clientProjectId": 1, "scheduledAt": "2026-09-10T09:30:00Z", "durationMinutes": 60,
  "location": "Google Meet" }
```
→ starts `SCHEDULED`; keep `data.id` as «meetingId».

**Step 2 — list** · `GET {{baseUrl}}/api/v1/ba/meetings?status=SCHEDULED` ·
auth: «token as BUSINESS_ANALYST» (optional `&clientProjectId=1`).

**Step 3 — update / add minutes** · `PUT {{baseUrl}}/api/v1/ba/meetings/«meetingId»` ·
auth: «token as BUSINESS_ANALYST» — full body as Step 1 plus `"status": "COMPLETED"` and
`"minutes": "Agreed on v1 scope; BA to draft BRD by 2026-09-15."`.

**Step 4 — cancel** · `DELETE {{baseUrl}}/api/v1/ba/meetings/«meetingId»` · no body ·
auth: «token as BUSINESS_ANALYST» → `status = CANCELLED`.

---

## Flow 20 · Talent pool & recruitment requests

Browse talent = `CLIENT` / `ADMIN` / `HR_MANAGER` / `LEAD_GEN`. Raise a request = `CLIENT`.
Decide = `ADMIN` / `HR_MANAGER`.

**Step 1 (client) — browse candidates** · `GET {{baseUrl}}/api/v1/talent-pool?search=java&skill=spring` ·
auth: «token as CLIENT» → `data.content[]` candidate profiles; keep a `data.content[].uuid` as
«candidateUuid».

**Step 2 (client) — request to recruit** · `POST {{baseUrl}}/api/v1/recruitment-requests` ·
auth: «token as CLIENT»
```json
{ "candidateUuid": "«candidateUuid»", "roleTitle": "Junior Backend Engineer",
  "engagementType": "FULL_TIME", "message": "6-month contract, remote." }
```
`engagementType`: `FULL_TIME｜CONTRACT｜INTERNSHIP`. → lands `PENDING`; keep `data.id` as «requestId».

**Step 3 (client) — list my requests** · `GET {{baseUrl}}/api/v1/recruitment-requests` ·
auth: «token as CLIENT» (a CLIENT sees only their own; ADMIN / HR see all).

**Step 4 (HR/Admin) — decide** · `PUT {{baseUrl}}/api/v1/recruitment-requests/«requestId»/status` ·
auth: «token as HR_MANAGER»
```json
{ "status": "APPROVED", "decisionNote": "Cleared with the batch PM." }
```
`status` must be `APPROVED` or `REJECTED` (anything else → `400`).

---

## Flow 21 · HR employee lifecycle — login as `hr@moriah.test` (1B — HR has mandatory 2FA)

Uses «employeeId» from **Flow 9 Step 1** (`POST /hr/employees` → `data.id`).
List: `GET {{baseUrl}}/api/v1/hr/employees?status=ACTIVE&department=Engineering&search=EMP-1001`.

### Onboarding

**Step 1 — start** · `POST {{baseUrl}}/api/v1/hr/onboardings` · auth: «token as HR_MANAGER»
```json
{ "employeeId": «employeeId», "startDate": "2026-09-01",
  "buddyUuid": "11111111-0000-0000-0000-000000000005" }
```
→ starts `NOT_STARTED`; keep `data.id` as «onboardingId».

**Step 2 — list / get** · `GET {{baseUrl}}/api/v1/hr/onboardings?status=NOT_STARTED` /
`GET {{baseUrl}}/api/v1/hr/onboardings/«onboardingId»` · auth: «token as HR_MANAGER».

**Step 3 — progress it** · `PUT {{baseUrl}}/api/v1/hr/onboardings/«onboardingId»` ·
auth: «token as HR_MANAGER»
```json
{ "startDate": "2026-09-01", "buddyUuid": "11111111-0000-0000-0000-000000000005",
  "status": "COMPLETED",
  "checklist": [{ "label": "Laptop issued", "done": true }, { "label": "Email set up", "done": true }],
  "notes": "All set." }
```
`status`: `NOT_STARTED｜IN_PROGRESS｜COMPLETED｜CANCELLED`; `COMPLETED` stamps `completedAt`.

### Disciplinary

**Step 4 — raise** · `POST {{baseUrl}}/api/v1/hr/disciplinary` · auth: «token as HR_MANAGER»
```json
{ "employeeId": «employeeId», "actionType": "WRITTEN_WARNING", "severity": "MEDIUM",
  "incidentDate": "2026-09-05", "description": "Repeated late logins despite a verbal warning." }
```
`actionType`: `VERBAL_WARNING｜WRITTEN_WARNING｜PIP｜SUSPENSION｜TERMINATION_RECOMMENDATION｜OTHER`.
`severity`: `LOW｜MEDIUM｜HIGH`. → starts `OPEN`; keep `data.id` as «disciplinaryId».

**Step 5 — list / get** · `GET {{baseUrl}}/api/v1/hr/disciplinary?status=OPEN&severity=MEDIUM` /
`GET {{baseUrl}}/api/v1/hr/disciplinary/«disciplinaryId»` · auth: «token as HR_MANAGER».

**Step 6 — update** · `PUT {{baseUrl}}/api/v1/hr/disciplinary/«disciplinaryId»` ·
auth: «token as HR_MANAGER» — the four create fields are re-sent, plus `status` and the outcome text:
```json
{ "actionType": "WRITTEN_WARNING", "severity": "MEDIUM", "incidentDate": "2026-09-05",
  "description": "Repeated late logins despite a verbal warning.",
  "status": "RESOLVED", "actionTaken": "Written warning issued and acknowledged.",
  "resolutionNotes": "No further incidents in 30 days." }
```
`status`: `OPEN｜ACKNOWLEDGED｜RESOLVED｜ESCALATED` — `ACKNOWLEDGED` / `RESOLVED` stamp their own timestamps.

### Exit / offboarding

**Step 7 — initiate** · `POST {{baseUrl}}/api/v1/hr/exits` · auth: «token as HR_MANAGER»
```json
{ "employeeId": «employeeId», "exitType": "RESIGNATION", "lastWorkingDay": "2026-10-31",
  "reason": "Higher studies.", "noticePeriodDays": 30 }
```
`exitType`: `RESIGNATION｜TERMINATION｜RETIREMENT｜CONTRACT_END`. → starts `INITIATED`; keep
`data.id` as «exitId».

**Step 8 — list** · `GET {{baseUrl}}/api/v1/hr/exits?status=INITIATED` · auth: «token as HR_MANAGER»
(optional `&employeeId=«employeeId»`).

**Step 9 — progress the checklist** · `PUT {{baseUrl}}/api/v1/hr/exits/«exitId»` ·
auth: «token as HR_MANAGER»
```json
{ "exitType": "RESIGNATION", "lastWorkingDay": "2026-10-31", "reason": "Higher studies.",
  "noticePeriodDays": 30, "status": "IN_PROGRESS",
  "clearanceChecklist": [{ "label": "Assets returned", "done": true },
                         { "label": "Access revoked", "done": false }],
  "exitInterviewNotes": "Positive; would rejoin." }
```
`status` here may be `INITIATED` or `IN_PROGRESS` only — sending `COMPLETED` is rejected (use
Step 10).

**Step 10 — finalise** · `POST {{baseUrl}}/api/v1/hr/exits/«exitId»/complete` · no body ·
auth: «token as HR_MANAGER» → flips `employees.status` to `EXITED` (or `TERMINATED` when
`exitType = TERMINATION`) and stamps `date_of_exit`. After this the experience/relieving letter in
**Flow 9 Step 8** becomes available for that user.

---

## Flow 22 · Admin payments, coupons & runtime plans — login as `admin@moriah.test` (1B — mandatory 2FA)

### Payments

**Step 1 — list** · `GET {{baseUrl}}/api/v1/admin/payments?status=CAPTURED&gateway=RAZORPAY` ·
auth: «token as ADMIN» (optional `&userUuid=…`). → keep a row's `id` as «paymentId» and its
`gatewayOrderId` as «gatewayOrderId».

**Step 2 — summary** · `GET {{baseUrl}}/api/v1/admin/payments/summary` · auth: «token as ADMIN»
→ per-status counts + totals, plus total captured / refunded.

**Step 3 — one payment** · `GET {{baseUrl}}/api/v1/admin/payments/«gatewayOrderId»` · auth: «token as ADMIN».

**Step 4 — refund** · `POST {{baseUrl}}/api/v1/admin/payments/«gatewayOrderId»/refund` · auth: «token as ADMIN»
```json
{ "reason": "Customer requested within 7 days." }
```
Only a `CAPTURED` payment, once — otherwise `409 PAYMENT_NOT_REFUNDABLE`. Sets status `REFUNDED`
optimistically; the gateway's own refund webhook then no-ops.

### Coupons

**Step 5 — create** · `POST {{baseUrl}}/api/v1/admin/coupons` · auth: «token as ADMIN»
```json
{ "code": "DIWALI25", "discountType": "PERCENTAGE", "discountValue": 25.0,
  "validFrom": "2026-10-01", "validUntil": "2026-11-15", "maxRedemptions": 500 }
```
`discountType`: `PERCENTAGE｜FLAT`. `code` must match `^[A-Z0-9][A-Z0-9_-]*$`; a taken code →
`409 COUPON_CODE_TAKEN`. → the `code` **is** the id for the next calls.

**Step 6 — list** · `GET {{baseUrl}}/api/v1/admin/coupons` · auth: «token as ADMIN».

**Step 7 — update** · `PUT {{baseUrl}}/api/v1/admin/coupons/DIWALI25` · auth: «token as ADMIN» —
same body minus `code`, plus `"active": true`.

**Step 8 — deactivate** · `DELETE {{baseUrl}}/api/v1/admin/coupons/DIWALI25` · no body ·
auth: «token as ADMIN» → `active = false`; redemption history is kept.

Use a live coupon in **Flow 5 Step 2** — `{ "couponCode": "DIWALI25", … }` on
`POST /subscriptions/checkout`.

### Runtime plans (create / retire — editing pricing is Flow 10 Step 6)

**Step 9 — create a plan** · `POST {{baseUrl}}/api/v1/admin/plans` · auth: «token as ADMIN» — plan
body as Flow 10 Step 6 plus a `"code"` (e.g. `"WEEKEND_TRACK"`; a taken code → `409 PLAN_CODE_TAKEN`).
The plan cache is evicted on write.

**Step 10 — retire a plan** · `DELETE {{baseUrl}}/api/v1/admin/plans/«planId»` · no body ·
auth: «token as ADMIN» → `active = false`.

---

## Appendix A · Getting a TOTP code from «secret» (5 ways)

The secret is Base32; TOTP is **HMAC-SHA1, 6 digits, 30-second step**, ±1 step drift tolerated.

| Method | Command / steps |
|---|---|
| **Python `pyotp`** | `pip install pyotp` then `python -c "import pyotp; print(pyotp.TOTP('«secret»').now())"` |
| **Python, no install** (oathtool-style) | see the snippet below |
| **oathtool** (Linux/Git-Bash pkg) | `oathtool --totp -b "«secret»"` |
| **Google / Microsoft Authenticator** | app → add account → **Enter a setup key** → paste `«secret»`, type = *Time based* → reads the code |
| **Local QR to scan** | `pip install segno` then `python -c "import segno; segno.make('«otpauthUri»').terminal()"` (ASCII QR in the terminal) or `.save('totp.png', scale=8)` |

Pure-Python TOTP (no dependency):
```python
import base64, hmac, hashlib, struct, time
secret = "«secret»"
key = base64.b32decode(secret + "=" * (-len(secret) % 8), casefold=True)
msg = struct.pack(">Q", int(time.time()) // 30)
h = hmac.new(key, msg, hashlib.sha1).digest()
o = h[-1] & 0x0F
code = (struct.unpack(">I", h[o:o+4])[0] & 0x7FFFFFFF) % 1_000_000
print(f"{code:06d}")
```

Node one-liner (if you have `otplib`): `node -e "console.log(require('otplib').authenticator.generate('«secret»'))"`

---

## Appendix B · Signing a webhook request

`webhookSecret` = your `.env` `RAZORPAY_WEBHOOK_SECRET` / `STRIPE_WEBHOOK_SECRET` (any string
locally, must match what you sign with).

**Razorpay** — `X-Razorpay-Signature` = hex HMAC-SHA256 of the **raw body**:
```bash
BODY='{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TEST1","order_id":"order_TEST1","amount":1499900,"currency":"INR","status":"captured"}}}}'
printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$RAZORPAY_WEBHOOK_SECRET" -hex | sed 's/^.* //'
```

**Stripe** — `Stripe-Signature: t=<ts>,v1=<hmac>` where the HMAC is over `"<ts>.<rawBody>"`:
```bash
BODY='{"id":"evt_TEST1","type":"checkout.session.completed","data":{"object":{"object":"checkout.session","client_reference_id":"101","amount_total":1499900,"currency":"inr","payment_intent":"pi_TEST1","payment_status":"paid"}}}'
T=$(date +%s)
SIG=$(printf '%s' "$T.$BODY" | openssl dgst -sha256 -hmac "$STRIPE_WEBHOOK_SECRET" -hex | sed 's/^.* //')
echo "t=$T,v1=$SIG"
```

**In Postman:** the imported collection's Razorpay/Stripe requests already carry a **pre-request
script** that reads the request body, signs it with `{{webhookSecret}}`, and sets the header —
just set the `webhookSecret` collection/environment variable and hit Send.

**Stripe CLI (easiest):** `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`
prints a `whsec_…` (put it in `.env`), then `stripe trigger checkout.session.completed`.

---

## Appendix C · Value carry-over cheat-sheet

| Produced by | Field | Consumed by |
|---|---|---|
| `POST /auth/login` | `data.tokens.accessToken` | `Authorization: Bearer` on every authed call |
| `POST /auth/login` | `data.tokens.refreshToken` | `POST /auth/refresh`, `/auth/logout` |
| `POST /auth/login` (2FA acct) | `data.challengeToken` | `POST /auth/2fa/enable`, `POST /auth/2fa/verify` |
| `POST /auth/2fa/enable` | `data.secret` | Appendix A → 6-digit `totpCode` |
| `POST /auth/2fa/verify` | `data.tokens.accessToken` | as above |
| `POST /auth/refresh` | `data.accessToken` / `data.refreshToken` | replace both stored values |
| `GET /users/me` | `data.uuid` | any `userUuid` body field for your own account |
| `GET /batches` | `data.content[].id` | `«batchId»` — `sprints?batchId=`, `standups?batchId=`, `/batches/{id}/…` |
| `GET /sprints?batchId=` | `[].id` | `«sprintId»` — `tasks?sprintId=`, `/sprints/{id}/activate` |
| `GET /tasks?sprintId=&status=BACKLOG` | `[].id` | `«taskId»` — `/tasks/{id}/pull`, `POST /submissions` |
| `POST /submissions` | `data.id` | `«submissionId»` — `POST /reviews` |
| `GET /standups?batchId=&date=` | `[].id` | `«standupId»` — `/standups/{id}/checkin`, `/attendance` |
| `POST /assessments` | `data.id` | `«quizId»` — `GET /assessments`, `/assessments/{id}/attempts` |
| `POST /assessments/{id}/attempts` | `data.id`, `data.questions[].id` | `«attemptId»`, `questionId` in the submit body |
| `POST /subscriptions/checkout` | `data.paymentId`, `data.razorpayOrderId` | webhook body `client_reference_id` / `order_id` |
| `POST /certificates/issue` | `data.verificationCode`, `data.id` | `GET /certificates/verify/{code}`, `/certificates/{id}/revoke` |
| `POST /leads` | `data.id` | `«leadId»` — `/leads/{id}/status`, `/leads/{id}/activities` |
| `POST /hr/employees` | `data.id` | `«employeeId»` — `payroll/generate` `lines[].employeeId` |
| `GET /admin/users` | `data.content[].uuid` | `/admin/users/{userUuid}/status`, `/roles` |
| `GET /notifications` | `data.content[].id` | `«notificationId»` — `PUT /notifications/{id}/read` |
| `POST /resources` | `data.id` | `«resourceId»` — `PUT` / `DELETE /resources/{id}` |
| `POST /lessons` | `data.id` | `«lessonId»` — `/lessons/{id}/quiz/**`, `/lessons/{id}/progress` |
| `POST /lessons/{id}/quiz/questions` | `data.id` | `«quizQuestionId»` — `DELETE …/quiz/questions/{questionId}` |
| `GET /lessons/{id}/quiz` | `data[].id` (in order) | positional index in `POST …/quiz/submit` `answers[]` |
| `POST /ba/documents` | `data.id` | `«docId»` — `/ba/documents/{id}/approve`, then `/dev/requirement-documents/{id}` |
| `GET /dev/requirement-documents` | `data.content[].id` | `«docId»` — `/dev/requirement-documents/{id}/acknowledge` |
| `POST /interviews` | `data.id` | `«interviewId»` — `PUT` / `DELETE /interviews/{id}` |
| `POST /assessments/banks` | `data.id` | `«bankId»` — `/assessments/banks/{id}/questions`, `PUT` / `DELETE` |
| `POST /assessments/banks/{id}/questions` | `data.id` | `«bankQuestionId»` — `DELETE …/questions/{questionId}` |
| `POST /leads/campaigns` | `data.id` | `«campaignId»` — `PUT` / `DELETE /leads/campaigns/{id}` |
| `POST /ba/meetings` | `data.id` | `«meetingId»` — `PUT` / `DELETE /ba/meetings/{id}` |
| `GET /talent-pool` | `data.content[].uuid` | `«candidateUuid»` — `POST /recruitment-requests` body |
| `POST /recruitment-requests` | `data.id` | `«requestId»` — `PUT /recruitment-requests/{id}/status` |
| `POST /hr/employees` | `data.id` | `«employeeId»` — `/hr/onboardings`, `/hr/disciplinary`, `/hr/exits` bodies |
| `POST /hr/onboardings` | `data.id` | `«onboardingId»` — `PUT /hr/onboardings/{id}` |
| `POST /hr/disciplinary` | `data.id` | `«disciplinaryId»` — `PUT /hr/disciplinary/{id}` |
| `POST /hr/exits` | `data.id` | `«exitId»` — `PUT /hr/exits/{id}`, `POST /hr/exits/{id}/complete` |
| `GET /admin/payments` | `data.content[].gatewayOrderId` | `«gatewayOrderId»` — `/admin/payments/{id}`, `…/refund` |
| `POST /admin/coupons` | `data.code` | coupon `code` — `PUT` / `DELETE /admin/coupons/{code}`, `checkout` `couponCode` |
