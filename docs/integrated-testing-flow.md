# Moriah Skill Hub — Integrated (UI + API) Testing Flow

Companion to `testing-flow.md`. That doc drives the **backend API directly** (Postman/curl).
**This doc drives the React app in a browser** and tells you, per screen: which page, what to
click, which backend endpoint it calls, what you should see, and how to cross-check the result
against the API or DB.

- Frontend repo: `moriah-skill-hub-updated` (React 19 + Vite). Backend: this repo.
- Last updated **2026-09-03**. Covers only the screens whose services are wired to the backend
  (see the matrix in §2) — everything else is still the localStorage mock and is called out so
  you don't file bugs against it.

---

## 1 · Setup

### 1.1 Start the backend (`dev` profile — auto-seeds 10 accounts)

```
# in this repo
docker compose up -d          # MySQL + Redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# -> http://localhost:8080 , /v3/api-docs for the live OpenAPI
```

`R__dev_seed_data.sql` seeds the accounts below (all password **`Password123!`**), plus batch
`FS-2026-01`, one active sprint + 3 tasks, a published project, one lead, two employees.

| email | role | 2FA on login? |
|---|---|---|
| `admin@moriah.test` | ADMIN | **yes — mandatory** |
| `pm@moriah.test` | TRAINER_PM | no |
| `dev@moriah.test` | DEVELOPER | no |
| `sales@moriah.test` | LEAD_GEN | no |
| `hr@moriah.test` | HR_MANAGER | **yes — mandatory** |
| `ba@moriah.test` | BUSINESS_ANALYST | no |
| `client@moriah.test` | CLIENT | no |
| `student1@moriah.test` / `student2` | STUDENT | no — enrolled in `FS-2026-01` |
| `student3@moriah.test` | STUDENT | no — STARTER plan (entitlement-denied) |

### 1.2 Start the frontend

```
# in moriah-skill-hub-updated
cp .env.example .env           # leave VITE_API_BASE_URL blank -> use the dev proxy
npm install
npm run dev                    # -> http://localhost:5173
```

`vite.config.js` proxies `/api` → `http://localhost:8080`, so the browser talks to the real
backend with no CORS setup. To point at a deployed backend instead, set
`VITE_API_BASE_URL=https://…/api/v1` in `.env` and restart.

### 1.3 How auth works in the app

- `POST /api/v1/auth/login` on the Login page. The access + refresh tokens are stored in
  `localStorage` (`msh_access_token` / `msh_refresh_token` / `msh_token_expiry`). Every API call
  sends `Authorization: Bearer <access>`; a 401 triggers one transparent `POST /auth/refresh`.
- The FE `user` object = `GET /users/me` **+ the `roles` claim decoded from the JWT** (`/users/me`
  carries no roles). Primary role → FE role via `primaryFeRole()` decides which dashboard you land on.
- **2FA (admin@ / hr@):** first login returns a `challengeToken` + `twoFactorSetupRequired:true`.
  The Login page shows the TOTP secret + `otpauth://` URI. Generate a code
  (`python -c "import pyotp; print(pyotp.TOTP('«secret»').now())"` — see `testing-flow.md`
  Appendix A) and submit it. Later logins just ask for the 6-digit code.

### 1.4 Cross-checking

- **API:** open `http://localhost:8080/swagger-ui.html`, or paste the `msh_access_token` from
  the browser's devtools → Application → Local Storage into a Postman `Bearer` and hit the same
  endpoint.
- **DB:** `docker exec -it skillhub-mysql mysql -umoriah_app -papp_dev_only moriah_skillhub`.
- **Network tab:** every wired action below fires exactly the request named — watch it in
  devtools → Network, filtered to `Fetch/XHR`.

---

## 2 · Wired vs. mock — per screen

| Area | Screen (route) | Backend? | Endpoint(s) |
|---|---|---|---|
| Auth | `/login`, `/register`, `/reset-password`, `/verify-email`, `/auth/oauth/callback` | ✅ wired | `/api/v1/auth/**`, `/users/me` |
| Notifications | header bell (all roles) | ✅ wired | `/api/v1/notifications**` |
| Admin | `/admin/users` | ✅ wired | `/admin/users**`, `/admin/metrics/overview` |
| Admin | `/admin/plans` | ✅ wired | `/plans`, `/admin/plans**` |
| Admin | `/admin/transactions` | ✅ wired | `/admin/payments**` |
| Admin | `/admin/audit-logs`, `/admin/reports` | ✅ wired | `/admin/audit`, `/admin/exports/*` |
| Admin | coupons (inside Plans/Transactions UI) | ✅ wired | `/admin/coupons**` |
| Developer | `/developer/resources` (+ `/student/resources`, `/trainer/resources`) | ✅ wired | `/api/v1/resources**` |
| Developer | `/developer/video-lessons` | ✅ wired | `/api/v1/lessons**` (+ `/quiz`) |
| Developer | `/developer/assessment-bank` | ✅ wired | `/api/v1/assessments/banks**` |
| Developer | `/developer/client-requirements` | ✅ wired | `/api/v1/dev/requirement-documents**` |
| Lead-gen | `/leads/campaigns` | ✅ wired | `/api/v1/leads/campaigns**` |
| BA | `/ba/meetings` | ✅ wired | `/api/v1/ba/meetings**` |
| Client | `/client/talent-pool` (candidate list + "recruit") | ✅ wired (list + request) | `/api/v1/talent-pool`, `/api/v1/recruitment-requests` |
| HR | `/hr/onboarding` | ✅ wired | `/api/v1/hr/onboardings**`, `/hr/employees` |
| HR | `/hr/exit` (Exit + Disciplinary tabs) | ✅ wired | `/api/v1/hr/exits**`, `/hr/disciplinary**`, `/hr/employees` |
| Student | `/student/learning` (video lessons + quiz) | ✅ wired | `/api/v1/lessons**` (+ `/quiz`, `/progress`, `/quiz/submit`) |
| Student | `/student/certificates` | ✅ wired | `GET /api/v1/certificates/me` |
| Student | `/student/pip-status` | ✅ wired | `GET /api/v1/pip/me` |
| Student | `/student/subscription` (plan list only) | ✅ partial | `GET /api/v1/plans`, `GET /api/v1/subscriptions/me` (checkout still mock) |
| Trainer | `/trainer/batches` (list + create) | ✅ wired | `GET`/`POST /api/v1/batches` |
| — | **everything below is still localStorage mock** | ❌ | — |
| Lead-gen | `/leads/pipeline`, `/leads/targets`, campaign "Send" helper | ❌ mock | `/leads` has no per-lead detail / activity list / delete yet |
| BA | `/ba/documents`, `/ba/resource-planning`, `/ba/client-review` | ❌ mock | doc API is text-only (no file upload); no resource-plan endpoint |
| Client | `/client/talent-pool` pipeline (shortlist→offer→sign→placed), `/client/projects`, `/client/demos` | ❌ mock | no backend for the placement state machine |
| HR | `/hr/attendance`, `/hr/payroll`, `/hr/documents` (letters), Exit page's **PIP tab** | ❌ mock | different backend shapes / no endpoint yet |
| Developer | `/developer/projects`, `/developer/assessments` (publish), bug challenges | ❌ mock | project publish + in-browser bug runner unmigrated |
| Student | `/student/tasks`, `/student/submissions`, `/student/assessments`, `/student/interviews` (placement), `/student/projects` | ❌ mock | no "my tasks across sprints" endpoint; assessments = dev in-browser runner |
| Trainer | `/trainer/sprints`, `/trainer/sprint-planning`, `/trainer/code-review`, `/trainer/analytics`, `/trainer/pip-management`, `/trainer/graduation` | ❌ mock | interlocked sprint↔task rewrite + name→uuid remapping still pending |

> If a screen is in the ❌ half, its data lives in `localStorage` keys like `msh_*` and never
> reaches the backend. Clearing site data resets it.

---

## 3 · Flow A — Auth (any role)

**A1 · Plain login.** Go to `http://localhost:5173/login`. Enter `student1@moriah.test` /
`Password123!` → **Sign in**.
- Network: `POST /api/v1/auth/login` → 200, body `data.tokens.accessToken` set.
- Verify: you land on `/student/dashboard`; devtools → Application → Local Storage has
  `msh_access_token`. A follow-up `GET /api/v1/users/me` fired to hydrate the profile.

**A2 · 2FA login (`admin@`).** Login with `admin@moriah.test` / `Password123!`.
- Network: `POST /auth/login` → `data.twoFactorRequired:true`, `twoFactorSetupRequired:true`.
- The page shows a **secret** + QR URI. Generate a code from the secret, type it → **Verify**.
- Network: `POST /auth/2fa/enable` (once) then `POST /auth/2fa/verify` → 200 with `tokens`.
- Verify: you land on `/admin/dashboard`. Next login only asks for the 6-digit code.

**A3 · Refresh + logout.** Leave a tab open ~60 min (or set the token expiry back in
localStorage) and click around → a transparent `POST /auth/refresh` fires and both stored tokens
rotate. **Logout** (sidebar) → `POST /auth/logout` with the refresh token; you're bounced to
`/login` and the `msh_*` keys are cleared.

**A4 · Session survives reload.** While logged in, hard-refresh (Ctrl-Shift-R). The app shows a
spinner, fires `GET /users/me`, and restores you to the same dashboard — no bounce to `/login`.

**A5 · Register (student).** `/register` → fill the form → submit.
- Network: `POST /api/v1/auth/register` → `data.needsEmailVerification:true`. **No auto-login.**
- The account is `PENDING_VERIFICATION`; grab the token from the DB
  (`SELECT token_hash FROM email_verification_tokens ORDER BY id DESC LIMIT 1` — it's hashed, so
  in practice pull the raw token from the app log) and open `/verify-email?token=…` →
  `POST /auth/verify-email` → now you can log in.

---

## 4 · Flow B — Notifications (any logged-in role)

The header bell is on every dashboard.

**B1 · Badge + feed.** On load the app fires `GET /api/v1/notifications/unread-count` (badge
number) and, when you open the bell, `GET /api/v1/notifications?page=0&size=20`.
- Verify: the dropdown lists rows newest-first; the badge equals the unread count.

**B2 · Mark one read.** Click a notification → `PUT /api/v1/notifications/{id}/read` → its dot
clears and the badge drops by one.

**B3 · Mark all read.** "Mark all read" → `PUT /api/v1/notifications/read-all` → badge goes to 0.

> There's no in-app way to *create* a notification — the backend writes them on domain events
> (enrolment, review, payment…). To see rows, trigger one of those via another flow, or insert a
> `notifications` row with `channel = 'IN_APP'` directly.

---

## 5 · Flow C — Admin (`admin@moriah.test`, after A2)

**C1 · Users list.** `/admin/users` → `GET /api/v1/admin/users?…` (paged). Optional role/status
filters map to query params.
- Verify: the seeded 10 users appear. Pick `student3@`.

**C2 · Change status.** Row menu → set **Suspended** → `PUT /admin/users/{uuid}/status`
`{status:"SUSPENDED"}`.
- Cross-check: in another tab logged in as `student3@`, the next request returns 401 (token
  version bumped) → they're bounced to `/login`.

**C3 · Change roles.** Row → **Edit roles** → add `DEVELOPER` → `PUT /admin/users/{uuid}/roles`
`{roles:["STUDENT","DEVELOPER"]}`.

**C4 · Edit user record.** Row → **Edit** name/phone → `PUT /admin/users/{uuid}` (B1.17 —
profile fields only, no token bump).

**C5 · Plans.** `/admin/plans` → `GET /api/v1/plans`. Edit a price → `PUT /admin/plans/{id}`.
Create → `POST /admin/plans` (needs a unique `code`). Delete → `DELETE /admin/plans/{id}`
(deactivates). The plan cache is evicted on every write — the list reflects it immediately.

**C6 · Transactions + refund.** `/admin/transactions` → `GET /admin/payments` +
`GET /admin/payments/summary` (the stat cards). Open a **CAPTURED** payment → **Refund** →
`POST /admin/payments/{gatewayOrderId}/refund` → status flips to `REFUNDED`; refunding again →
`409 PAYMENT_NOT_REFUNDABLE`. (Seed a captured payment via `testing-flow.md` Flow 5 first.)

**C7 · Coupons.** In the plans/transactions UI, create a coupon → `POST /admin/coupons`
(`code` must match `^[A-Z0-9][A-Z0-9_-]*$`; dup → `409 COUPON_CODE_TAKEN`). Deactivate →
`DELETE /admin/coupons/{code}`.

**C8 · Audit + export.** `/admin/audit-logs` → `GET /admin/audit` (read-only). `/admin/reports` →
**Export users** → `POST /admin/exports/users` → `data.downloadUrl` (presigned XLSX).

---

## 6 · Flow D — Developer content (`dev@moriah.test`)

**D1 · Resources.** `/developer/resources` → `GET /api/v1/resources` (paged; category/search
filters). **Add resource** → `POST /api/v1/resources` (`category` ∈
ARTICLE/VIDEO/BOOK/TOOL/TEMPLATE/COURSE/OTHER). Edit → `PUT /{id}`. Delete → `DELETE /{id}`
(deactivates — row vanishes from the default list).
- Cross-check: log in as `student1@`, open `/student/resources` → the same resource is listed
  (student browse hits the same endpoint).

**D2 · Video lessons + quiz.** `/developer/video-lessons`.
- **Create lesson** → `POST /api/v1/lessons` (`videoUrl` a YouTube link, `moduleName` free text).
- Open the lesson → **Add quiz question** → `POST /api/v1/lessons/{id}/quiz/questions`
  (2–6 options, `correctIndex` < options length). Repeat.
- Verify: `GET /api/v1/lessons/{id}/quiz` (fired when the quiz panel opens) returns the
  questions **without** the answer key.
- **Publish** toggle → `PUT /api/v1/lessons/{id}` with `published:true`.

**D3 · Assessment bank.** `/developer/assessment-bank`.
- **New bank** → `POST /api/v1/assessments/banks`.
- **Add question** → `POST /api/v1/assessments/banks/{id}/questions`. MCQ/MULTI_SELECT need ≥2
  options; a CODE question is stored with its scaffold JSON-encoded in `explanation` (the FE
  decodes it on read). Answer keys are never returned by `GET …/questions`.
- Rename/deactivate → `PUT` / `DELETE /api/v1/assessments/banks/{id}`.

**D4 · Client requirement review.** `/developer/client-requirements` →
`GET /api/v1/dev/requirement-documents` (needs a doc created via `testing-flow.md` Flow 15 as
`ba@`). Open one → `GET /dev/requirement-documents/{id}` (full `content`). **Mark reviewed** →
`POST /dev/requirement-documents/{id}/acknowledge` (idempotent; stamps `dev_reviewed_at`, does
**not** change the doc status).

---

## 7 · Flow E — Lead campaigns (`sales@moriah.test`)

`/leads/campaigns`.

**E1 · List.** `GET /api/v1/leads/campaigns` on load.

**E2 · Create.** **New Campaign** → name + channel (EMAIL/SOCIAL/EVENT/REFERRAL/PAID_ADS/WEBINAR)
+ optional start/end dates, budget, target leads → `POST /api/v1/leads/campaigns`.
- Verify: the row appears with status **PLANNED**. `startDate` defaults to today if left blank.

**E3 · Edit / status.** Row → **Edit** → change status to **ACTIVE**, save → `PUT
/api/v1/leads/campaigns/{id}` (full body incl. `status`).

**E4 · Cancel.** Row → **Cancel** → `DELETE /api/v1/leads/campaigns/{id}` → status → **CANCELLED**
(row stays, never deleted).

> The row-click **"Send"** modal (bulk WhatsApp/email) is a **client-side helper only** — it
> reads the still-mock `getLeads()` and opens `wa.me` / `mailto` tabs. The message template is
> stored in `localStorage` (`msh_campaign_templates`), not on the server.

---

## 8 · Flow F — BA meetings (`ba@moriah.test`)

`/ba/meetings`.

**F1 · List.** `GET /api/v1/ba/meetings` on load (CANCELLED rows are filtered out client-side).

**F2 · Schedule.** **Schedule Client Ceremony** → title + date + time + agenda → `POST
/api/v1/ba/meetings`. The FE combines the date + `"hh:mm AM"` into the backend `scheduledAt`
Instant; `meetLink` → `location`; a 60-min default duration is sent.
- Verify: the meeting card shows with the right date/time. `type` / `client` / `attendees` are
  UI-only — kept in `localStorage` (`msh_ba_meeting_meta`), not on the server.

**F3 · Log minutes.** **Log MOM** → text → save → `PUT /api/v1/ba/meetings/{id}` with
`minutes` set (and `status` unchanged).

**F4 · Delete.** Trash icon → `DELETE /api/v1/ba/meetings/{id}` → status → CANCELLED → the card
disappears from the list.

---

## 9 · Flow G — Client talent pool (`client@moriah.test`)

`/client/talent-pool`.

**G1 · Candidate list.** `GET /api/v1/talent-pool` on load → cards for users whose profile is
open-to-work. (Seed: as `student1@` set the profile's open-to-work flag / skills via
`PUT /users/me/profile`.) Search + skill filter re-fire the endpoint with query params.
- Note: the **Performance score** on each card is approximated from `yearsExperience` — the DTO
  has no real score field yet.

**G2 · Request recruitment.** On a candidate → the migrated path fires
`POST /api/v1/recruitment-requests` `{candidateUuid, roleTitle, engagementType}` → lands
**PENDING**.
- Cross-check as `hr@`: `GET /api/v1/recruitment-requests` shows it; `PUT
  /recruitment-requests/{id}/status` `{status:"APPROVED"}` decides it. (No HR screen for this yet
  — use the API.)

> Everything past "request" on this page — shortlist, schedule interview, offer letter, client
> signature, "Placed" — is the **mock placement pipeline** (`utils/placementPipeline.js`,
> `localStorage`). It has no backend.

---

## 10 · Flow H — HR onboarding (`hr@moriah.test`, after 2FA)

First you need an employee. `/hr/exit` and `/hr/onboarding` both load the roster from
`GET /api/v1/hr/employees`. The `dev` seed has 2 employees; to add one use `testing-flow.md`
Flow 9 Step 1 (`POST /api/v1/hr/employees`) — there is no "create employee" screen wired yet.

`/hr/onboarding`.

**H1 · List.** `GET /api/v1/hr/onboardings` on load.

**H2 · Start.** **Start Onboarding** → pick an employee from the dropdown (label
`EMP-xxxx — Name`), start date, optional buddy UUID → `POST /api/v1/hr/onboardings` → status
**NOT_STARTED**.

**H3 · Manage.** Row → **Manage** → tick items on the joining checklist (KYC / education / NDA /
BG check / welcome kit / workstation), set status to **IN_PROGRESS** or **COMPLETED**, add notes
→ `PUT /api/v1/hr/onboardings/{id}`.
- Verify: the "Checklist" column shows `n / 6`; **COMPLETED** stamps `completedAt` (visible via
  the API).

---

## 11 · Flow I — HR exit & disciplinary (`hr@moriah.test`, after 2FA)

`/hr/exit` — three tabs.

### Exit Clearances tab

**I1 · List.** `GET /api/v1/hr/exits` on load.

**I2 · Record exit.** **Record Exit Workflow** → pick employee, exit type
(RESIGNATION/TERMINATION/RETIREMENT/CONTRACT_END), last working day, notice period, reason →
`POST /api/v1/hr/exits` → status **INITIATED**.

**I3 · Clearance checklist.** Row → **Checklist** → tick items (IT asset return / accounts
no-dues / knowledge transfer / exit interview / final settlement), set status to **IN_PROGRESS**,
add exit-interview notes → `PUT /api/v1/hr/exits/{id}`. (Sending `COMPLETED` here is rejected by
the API — the FE downgrades it to IN_PROGRESS; use **Finalise**.)

**I4 · Finalise.** Row → **Finalise** → confirm → `POST /api/v1/hr/exits/{id}/complete`.
- Verify: status → **COMPLETED**; the toast says the employee is now EXITED (or **TERMINATED**
  when exit type = TERMINATION).
- Cross-check: `GET /api/v1/hr/employees` → that employee's `status` flipped and `dateOfExit` is
  set. The experience/relieving letter (`testing-flow.md` Flow 9 Step 8) is now allowed for them.

### Disciplinary Actions tab

**I5 · Record.** **Record Disciplinary Action** → employee + action type
(VERBAL_WARNING…TERMINATION_RECOMMENDATION) + severity (LOW/MEDIUM/HIGH) + incident date +
description → `POST /api/v1/hr/disciplinary` → status **OPEN**.

**I6 · Update.** Row → **Update** → set status (ACKNOWLEDGED / RESOLVED / ESCALATED), action
taken, resolution notes → `PUT /api/v1/hr/disciplinary/{id}`.
- Verify: ACKNOWLEDGED / RESOLVED each stamp their own timestamp (visible via the API).

### PIP tab

Still reads the local `msh_pip_records` key written by the (mock) trainer PIP engine — **not**
the backend `/api/v1/pip`. Ignore it for integrated testing.

---

## 12 · Flow J — Student learning: video lessons + quiz (`student1@moriah.test`)

Prereq: as `dev@` create + publish a lesson with ≥2 quiz questions (Flow D2).

`/student/learning`.

**J1 · Lesson list.** `GET /api/v1/lessons?size=100` on load → cards for published lessons; the
progress bar counts lessons whose quiz you've passed.

**J2 · Open a lesson.** Click a card → `GET /api/v1/lessons/{id}` + `GET /api/v1/lessons/{id}/quiz`
in parallel. After ~4s the player fires `POST /api/v1/lessons/{id}/progress`
`{watchedSeconds, completed:false}` → the "Watched" badge appears.
- Verify: the quiz panel shows the question count; the questions carry **no** answer key.

**J3 · Take the quiz.** **Take Quiz** → answer each question → **Submit** →
`POST /api/v1/lessons/{id}/quiz/submit` `{answers:[optionIndex,…]}` (positional, in quiz order).
- Verify: the results screen shows the server's `score/total` and pass/fail. On ≥60% the lesson's
  progress flips to COMPLETED (re-open the list — the card shows a pass badge).
- Cross-check: `GET /api/v1/lessons/{id}` → `progress.status` is `COMPLETED`, `completedAt` set.

---

## 13 · Flow K — Student certificates / PIP / subscription (`student1@moriah.test`)

**K1 · Certificates.** `/student/certificates` → `GET /api/v1/certificates/me`. Empty until a PM
issues one (`testing-flow.md` Flow 6). After issue: the card shows the certificate number,
verification code and a download link; a revoked cert shows status **Revoked**.

**K2 · PIP status.** `/student/pip-status` → `GET /api/v1/pip/me`. `404` → the page shows the
"no active plan" state. If the nightly job (or a manual `pip_records` insert) raised one, the page
shows the trigger reason, status and dates.

**K3 · Subscription — plan list.** `/student/subscription` → `GET /api/v1/plans` renders the real
plan cards (price, features from the backend flags) and `GET /api/v1/subscriptions/me` marks your
current plan. **Checkout is still mock** — clicking "Upgrade" completes locally, it does not hit a
payment gateway (the real flow is `POST /subscriptions/checkout`, `testing-flow.md` Flow 5).

---

## 14 · Flow L — Trainer batches (`pm@moriah.test`)

`/trainer/batches`.

**L1 · List.** `GET /api/v1/batches?size=100` on load → every batch (a PM/ADMIN token sees all).
The seed batch `FS-2026-01` appears. "Health" shows **No activity yet** (no backend metric).
"Students" = the backend `enrolledCount`.

**L2 · Create.** **New Batch** → name + track (FE name → `trackCode`, e.g. Full-Stack Development
→ `FULL_STACK`) + start/end dates (start must be today or later) + capacity → `POST
/api/v1/batches` → you become the batch PM.
- Verify: the row appears with status **Onboarding** (backend `PLANNED`) → **Active** once the
  backend flips it.

> Student enrolment, project assignment, and everything on `/trainer/sprints` /
> `/trainer/sprint-planning` / `/trainer/code-review` / `/trainer/analytics` on this persona is
> **still the mock** — see the matrix.

---

## 15 · Regression checklist (fast pass after any FE/BE change)

1. `admin@` 2FA login → `/admin/dashboard` renders, metrics cards populated.
2. Bell badge shows a number; opening it lists notifications.
3. `/admin/users` lists 10 users; suspend + un-suspend `student3@` round-trips.
4. `dev@` → create a resource, see it as `student1@` in `/student/resources`.
5. `dev@` → create a lesson + 2 quiz questions; `GET /lessons/{id}/quiz` has no answer key.
6. `sales@` → create + cancel a campaign.
7. `ba@` → schedule + delete a meeting.
8. `client@` → talent list loads; a recruitment request shows up for `hr@` via the API.
9. `hr@` → start an onboarding, tick the checklist, mark COMPLETED.
10. `hr@` → record an exit, finalise it, confirm the employee is EXITED via the API.
11. `student1@` → `/student/learning` lists lessons, quiz submit returns a server score.
12. `student1@` → `/student/certificates` + `/student/pip-status` load without error (empty is fine).
13. `pm@` → `/trainer/batches` lists `FS-2026-01`; creating a batch adds a row.
14. Hard-refresh on any dashboard → no bounce to `/login`.
