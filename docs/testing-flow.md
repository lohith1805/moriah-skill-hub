# Moriah Skill Hub — End-to-End Testing Flow

How to drive the whole platform through the API, in order, showing **what goes in each request
body** and **which field of one response feeds the next request**.

- Base URL (local): `http://localhost:8080` — written `{{baseUrl}}` below.
- Every non-public call needs `Authorization: Bearer «accessToken»`.
- `«name»` = a value you captured from an earlier step.
- Companion docs: `API-Documentation.md` (every endpoint's shapes), `PROJECT-REFERENCE.md`
  (call chains), `webhooks.md` (gateway signatures), `required-integrations.md` (API keys),
  `postman/` (importable collection — the login request auto-captures the token).

---

## 0 · Seeded accounts (from `db/testdata/dev-seed.sql`)

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

**Step 3 — my batches** · `GET {{baseUrl}}/api/v1/batches`
→ from `data.content[]` find `name = "FS-2026-01"`; keep its `id` as «batchId».

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
