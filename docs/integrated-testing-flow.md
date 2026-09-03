# Moriah Skill Hub — Integrated (UI + API) Testing Flow

Companion to `testing-flow.md`. That doc drives the **backend API directly** (Postman/curl).
**This doc drives the React app in a browser** and tells you, per screen: which page, what to
click, which backend endpoint it calls, what you should see, and how to cross-check the result
against the API or DB.

- Frontend repo: `moriah-skill-hub-updated` (React 19 + Vite). Backend: this repo.
- Last updated **2026-09-03**. Covers the screens whose services are wired to the backend (see
  the matrix in §2) — everything still on the localStorage mock is called out so you don't file
  bugs against it.
- **This round added:** HR payroll / leave / KYC-docs, the whole trainer sprint→review→graduation
  set, student tasks / submissions / assessments / real checkout, the placement pipeline
  (client → HR → student), and profile / settings / 2FA. New flows **M–R** below.

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
- **OAuth2:** `…/auth/oauth2/authorize/{google|github}` in the browser → the backend 302s back to
  `http://localhost:5173/auth/oauth/callback#accessToken=…&refreshToken=…&expiresIn=…` (result in
  the URL **fragment**). `OAuthCallback.jsx` parses it, stores the tokens, and scrubs the URL.
  `#twoFactorRequired=true&challengeToken=…` or `#error=<CODE>` are the other two outcomes.

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
| Shared | `/{role}/profile` | ✅ wired | `PUT /api/v1/users/me/profile` (photo stays local) |
| Shared | `/{role}/settings` (2FA enable / verify / disable, password-reset email) | ✅ wired | `/api/v1/auth/2fa/**`, `/auth/password/forgot` (notif prefs stay local) |
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
| Client | `/client/talent-pool` — candidate list, "recruit", **and** the placement pipeline | ✅ wired | `/api/v1/talent-pool`, `/recruitment-requests`, `/placements**` |
| HR | `/hr/onboarding` | ✅ wired | `/api/v1/hr/onboardings**`, `/hr/employees` |
| HR | `/hr/exit` (Exit + Disciplinary tabs) | ✅ wired | `/api/v1/hr/exits**`, `/hr/disciplinary**`, `/hr/employees` |
| HR | `/hr/payroll` | ✅ wired | `GET /api/v1/hr/payroll`, `POST /hr/payroll/generate` |
| HR | `/hr/attendance` — **Leave tab only** | ✅ wired | `/api/v1/hr/leaves**` (attendance + check-in tabs stay demo) |
| HR | `/hr/documents` — **Employee KYC Docs tab** + placement tabs | ✅ wired | `/api/v1/hr/documents**`, `/placements**` |
| Trainer | `/trainer/batches` (list + create + roster popup) | ✅ wired | `/api/v1/batches**`, `/batches/{id}/students` |
| Trainer | `/trainer/sprints` (sprint + task planning, assign) | ✅ wired | `/api/v1/sprints**`, `/tasks**`, `/batches/{id}/students` |
| Trainer | `/trainer/standups` | ✅ wired | `/api/v1/standups**`, `/batches/{id}/students` |
| Trainer | `/trainer/code-review` | ✅ wired | `/api/v1/reviews/queue`, `/submissions`, `POST /reviews` |
| Trainer | `/trainer/pip` | ✅ wired | `GET /api/v1/pip`, `POST /pip/{id}/review`, `/pip/{id}/milestones/{mid}/complete` |
| Trainer | `/trainer/graduation` | ✅ wired | `/batches/{id}/students`, `.../graduate`, `POST /certificates/issue` |
| Trainer | `/trainer/analytics` | ✅ derived | no endpoint — computed FE-side from `/sprints` + `/tasks` + roster + `/pip` |
| Student | `/student/learning` (video lessons + quiz) | ✅ wired | `/api/v1/lessons**` (+ `/quiz`, `/progress`, `/quiz/submit`) |
| Student | `/student/tasks` | ✅ wired | `GET /api/v1/tasks`, `POST /tasks/{id}/pull` (pull only) |
| Student | `/student/submissions` | ✅ wired | `POST /api/v1/submissions`, `GET /submissions?taskId=` |
| Student | `/student/assessments` | ✅ wired | `/api/v1/assessments`, `/assessments/{id}/attempts`, `/attempts/{id}`, `/attempts/{id}/submit` |
| Student | `/student/interviews` (placement pipeline view) | ✅ wired | `GET /api/v1/placements`, `PUT /placements/{id}` |
| Student | `/student/certificates` | ✅ wired | `GET /api/v1/certificates/me` |
| Student | `/student/pip` | ✅ wired | `GET /api/v1/pip/me` |
| Student | `/student/subscription` — plan list **and checkout** | ✅ wired | `GET /plans`, `GET /subscriptions/me`, `POST /subscriptions/checkout` (real gateway) |
| Student | `/student/dashboard` (no-subscription guard) | ✅ wired | `GET /api/v1/subscriptions/me` → redirect to `/student/subscription` if null |
| — | **everything below is still localStorage mock** | ❌ | — |
| Lead-gen | `/leads/pipeline`, `/leads/targets`, campaign "Send" helper | ❌ mock | `/leads` has no per-lead detail / activity list / delete yet |
| BA | `/ba/documents`, `/ba/resource-planning`, `/ba/client-review` | ❌ mock | doc API is text-only (no file upload); no resource-plan endpoint |
| Client | `/client/projects`, `/client/demos` | ❌ mock | no project-brief / demo endpoint |
| HR | `/hr/attendance` **Attendance + Check-in tabs**, `/hr/documents` **letter tabs**, Exit page's **PIP tab** | ❌ mock | no biometric-attendance endpoint; letters are a separate `/hr/letters` flow |
| Developer | `/developer/projects`, `/developer/assessments` (publish), bug challenges | ❌ mock | project publish + in-browser bug runner unmigrated |
| Student | `/student/projects`, `/student/dashboard` perf widgets, auto-PIP engine | ❌ mock | no "my projects" / performance-summary endpoint |
| Trainer | project assignment / staffing, "all students" management | ❌ mock | no cross-batch student-management endpoint |

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

**A5 · Register (student).** `/register` → fill the one-step form (name, track, email, phone,
password, accept terms) → **Create account**. Plan + payment are **not** here any more — they
happen after first login on `/student/subscription`.
- Network: `POST /api/v1/auth/register` → `data.needsEmailVerification:true`. **No auto-login.**
- The page shows a "Verify your email" screen. The account is `PENDING_VERIFICATION`; pull the
  raw token from the app log and open `/verify-email?token=…` → `POST /auth/verify-email` → now
  you can log in.

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
filters map to query params. Verify: the seeded 10 users appear. Pick `student3@`.

**C2 · Change status.** Row menu → set **Suspended** → `PUT /admin/users/{uuid}/status`
`{status:"SUSPENDED"}`. Cross-check: in another tab logged in as `student3@`, the next request
returns 401 (token version bumped) → they're bounced to `/login`.

**C3 · Change roles.** Row → **Edit roles** → add `DEVELOPER` → `PUT /admin/users/{uuid}/roles`
`{roles:["STUDENT","DEVELOPER"]}`.

**C4 · Edit user record.** Row → **Edit** name/phone → `PUT /admin/users/{uuid}` (profile fields
only, no token bump).

**C5 · Plans.** `/admin/plans` → `GET /api/v1/plans`. Edit a price → `PUT /admin/plans/{id}`.
Create → `POST /admin/plans` (needs a unique `code`). Delete → `DELETE /admin/plans/{id}`
(deactivates). The plan cache is evicted on every write.

**C6 · Transactions + refund.** `/admin/transactions` → `GET /admin/payments` +
`GET /admin/payments/summary` (the stat cards). Open a **CAPTURED** payment → **Refund** →
`POST /admin/payments/{gatewayOrderId}/refund` → status flips to `REFUNDED`; refunding again →
`409 PAYMENT_NOT_REFUNDABLE`. (Seed a captured payment via Flow P or `testing-flow.md` Flow 5.)

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
(deactivates). Cross-check: log in as `student1@`, open `/student/resources` → same row.

**D2 · Video lessons + quiz.** `/developer/video-lessons`.
- **Create lesson** → `POST /api/v1/lessons` (`videoUrl` a YouTube link, `moduleName` free text).
- Open the lesson → **Add quiz question** → `POST /api/v1/lessons/{id}/quiz/questions`
  (2–6 options, `correctIndex` < options length). Repeat.
- Verify: `GET /api/v1/lessons/{id}/quiz` returns the questions **without** the answer key.
- **Publish** toggle → `PUT /api/v1/lessons/{id}` with `published:true`.

**D3 · Assessment bank.** `/developer/assessment-bank`.
- **New bank** → `POST /api/v1/assessments/banks`.
- **Add question** → `POST /api/v1/assessments/banks/{id}/questions`. MCQ/MULTI_SELECT need ≥2
  options; a CODE question stores its scaffold JSON-encoded in `explanation`. Answer keys are
  never returned by `GET …/questions`.
- Rename/deactivate → `PUT` / `DELETE /api/v1/assessments/banks/{id}`.

**D4 · Client requirement review.** `/developer/client-requirements` →
`GET /api/v1/dev/requirement-documents` (needs a doc created via `testing-flow.md` Flow 15 as
`ba@`). Open one → `GET /dev/requirement-documents/{id}`. **Mark reviewed** →
`POST /dev/requirement-documents/{id}/acknowledge` (idempotent; stamps `dev_reviewed_at`).

---

## 7 · Flow E — Lead campaigns (`sales@moriah.test`)

`/leads/campaigns`.

**E1 · List.** `GET /api/v1/leads/campaigns` on load.
**E2 · Create.** **New Campaign** → name + channel (EMAIL/SOCIAL/EVENT/REFERRAL/PAID_ADS/WEBINAR)
+ optional dates, budget, target leads → `POST /api/v1/leads/campaigns` → status **PLANNED**.
**E3 · Edit / status.** Row → **Edit** → status **ACTIVE** → `PUT /api/v1/leads/campaigns/{id}`.
**E4 · Cancel.** Row → **Cancel** → `DELETE /api/v1/leads/campaigns/{id}` → status **CANCELLED**.

> The row-click **"Send"** modal is a client-side helper only — it reads the still-mock
> `getLeads()` and opens `wa.me` / `mailto` tabs; templates live in `localStorage`.

---

## 8 · Flow F — BA meetings (`ba@moriah.test`)

`/ba/meetings`.

**F1 · List.** `GET /api/v1/ba/meetings` on load (CANCELLED rows filtered out client-side).
**F2 · Schedule.** **Schedule Client Ceremony** → title + date + time + agenda → `POST
/api/v1/ba/meetings`. `type` / `client` / `attendees` are UI-only (`localStorage`
`msh_ba_meeting_meta`).
**F3 · Log minutes.** **Log MOM** → text → save → `PUT /api/v1/ba/meetings/{id}` (`minutes` set).
**F4 · Delete.** Trash icon → `DELETE /api/v1/ba/meetings/{id}` → status CANCELLED → card gone.

---

## 9 · Flow G — Client talent pool (`client@moriah.test`)

`/client/talent-pool`.

**G1 · Candidate list.** `GET /api/v1/talent-pool` on load → cards for users HR has published.
Search + skill filter re-fire with query params. The **Performance score** is approximated from
`yearsExperience` (no real score field yet).

**G2 · Request recruitment.** On a candidate → **Shortlist / Recruit** fires
`POST /api/v1/recruitment-requests` `{candidateUuid, roleTitle, engagementType}` → lands
**PENDING**.
- Cross-check as `hr@`: `GET /api/v1/recruitment-requests` shows it; `PUT
  /recruitment-requests/{id}/status` `{status:"APPROVED"}` decides it → **a `placements` row is
  created automatically** (stage `SHORTLISTED`). No HR screen for the decision yet — use the API,
  or Flow Q.

**G3 · Drive the pipeline (client-owned stages).** After approval the page's pipeline section
loads `GET /api/v1/placements` and shows the candidate at `SHORTLISTED`. The client advances the
**technical** stages and the final client signature:
- **Schedule technical** → `PUT /api/v1/placements/{id}` `{stage:"TECHNICAL_SCHEDULED",
  details:{roundType,date,time,meetingLink,notes}}`.
- **Complete / approve** → two more PUTs: `TECHNICAL_COMPLETED` (add
  `details.technicalFeedback`, `details.technicalRating`) then `TECHNICAL_APPROVED`.
- Later, after HR (Flow Q) reaches `OFFER_CREATED`: **Sign offer** → `PUT` `{stage:"CLIENT_SIGNED",
  details:{clientSignedAt}}`.
- Verify: a client PUT targeting an HR-owned stage → `403 INSUFFICIENT_ROLE`; a backwards move →
  `409`. `details` is merged (a key set to `null` is removed); `{stage:"<current>", details:{…}}`
  updates fields without advancing.

---

## 10 · Flow H — HR onboarding (`hr@moriah.test`, after 2FA)

`/hr/exit` and `/hr/onboarding` both load the roster from `GET /api/v1/hr/employees`. The `dev`
seed has 2 employees; to add one use `testing-flow.md` Flow 9 Step 1 (`POST /api/v1/hr/employees`)
— there is no "create employee" screen wired yet.

`/hr/onboarding`.

**H1 · List.** `GET /api/v1/hr/onboardings` on load.
**H2 · Start.** **Start Onboarding** → employee (`EMP-xxxx — Name`), start date, optional buddy
UUID → `POST /api/v1/hr/onboardings` → status **NOT_STARTED**.
**H3 · Manage.** Row → **Manage** → tick joining-checklist items, set **IN_PROGRESS** /
**COMPLETED**, add notes → `PUT /api/v1/hr/onboardings/{id}`. Verify: "Checklist" shows `n / 6`;
**COMPLETED** stamps `completedAt`.

---

## 11 · Flow I — HR exit & disciplinary (`hr@moriah.test`, after 2FA)

`/hr/exit` — three tabs.

### Exit Clearances tab

**I1 · List.** `GET /api/v1/hr/exits` on load.
**I2 · Record exit.** **Record Exit Workflow** → employee, exit type
(RESIGNATION/TERMINATION/RETIREMENT/CONTRACT_END), last working day, notice period, reason →
`POST /api/v1/hr/exits` → status **INITIATED**.
**I3 · Clearance checklist.** Row → **Checklist** → tick items, set **IN_PROGRESS**, add notes →
`PUT /api/v1/hr/exits/{id}`. (Sending `COMPLETED` here is rejected — use **Finalise**.)
**I4 · Finalise.** Row → **Finalise** → `POST /api/v1/hr/exits/{id}/complete`. Verify: status
**COMPLETED**; `GET /api/v1/hr/employees` shows that employee EXITED (or TERMINATED) with
`dateOfExit` set.

### Disciplinary Actions tab

**I5 · Record.** **Record Disciplinary Action** → employee + action type + severity
(LOW/MEDIUM/HIGH) + incident date + description → `POST /api/v1/hr/disciplinary` → status **OPEN**.
**I6 · Update.** Row → **Update** → status (ACKNOWLEDGED / RESOLVED / ESCALATED), action taken,
resolution notes → `PUT /api/v1/hr/disciplinary/{id}`.

### PIP tab

Still reads the local `msh_pip_records` key — **not** backend `/api/v1/pip`. Ignore it here.

---

## 12 · Flow J — Student learning: video lessons + quiz (`student1@moriah.test`)

Prereq: as `dev@` create + publish a lesson with ≥2 quiz questions (Flow D2).

`/student/learning`.

**J1 · Lesson list.** `GET /api/v1/lessons?size=100` on load → cards for published lessons; the
progress bar counts lessons whose quiz you've passed.
**J2 · Open a lesson.** `GET /api/v1/lessons/{id}` + `GET /api/v1/lessons/{id}/quiz` in parallel.
After ~4s the player fires `POST /api/v1/lessons/{id}/progress` `{watchedSeconds, completed:false}`.
**J3 · Take the quiz.** **Take Quiz** → answer → **Submit** → `POST
/api/v1/lessons/{id}/quiz/submit` `{answers:[optionIndex,…]}` (positional, in quiz order). Verify:
server `score/total` + pass/fail; ≥60% flips the lesson to COMPLETED. Cross-check: `GET
/api/v1/lessons/{id}` → `progress.status:"COMPLETED"`.

---

## 13 · Flow K — Student certificates / PIP (`student1@moriah.test`)

**K1 · Certificates.** `/student/certificates` → `GET /api/v1/certificates/me`. Empty until a PM
issues one (Flow N7 / `testing-flow.md` Flow 6). After issue: certificate number, verification
code, download link; a revoked cert shows **Revoked**.

**K2 · PIP status.** `/student/pip` → `GET /api/v1/pip/me`. `404` → "no active plan" state. If a
PIP was raised (Flow N6), the page shows the trigger reason, status, dates and milestones.

---

## 14 · Flow L — Trainer batches (`pm@moriah.test`)

`/trainer/batches`.

**L1 · List.** `GET /api/v1/batches?size=100` on load → every batch (PM/ADMIN token sees all).
Seed batch `FS-2026-01` appears. "Students" = the backend `enrolledCount`.
**L2 · Create.** **New Batch** → name + track (FE name → `trackCode`, e.g. Full-Stack Development
→ `FULL_STACK`) + start/end dates (start ≥ today) + capacity → `POST /api/v1/batches` → you become
the batch PM. Verify: row appears as **Onboarding** (backend `PLANNED`).
**L3 · Roster popup.** Row → **View students** → `GET /api/v1/batches/{id}/students` → table of
`{fullName, email, status, joinedAt, finalScore}`. A PM who does not own the batch → `403
NOT_BATCH_OWNER`.

---

## 15 · Flow M — HR payroll, leave & KYC docs (`hr@moriah.test`, after 2FA)

### M1 · Payroll — `/hr/payroll`

- **List.** Month picker → `GET /api/v1/hr/payroll?month=YYYY-MM-01` → payslip rows for that period.
- **Generate.** **Run Payroll** → working days + per-employee `{employeeId, presentDays,
  sessionHours, deductions}` lines → `POST /api/v1/hr/payroll/generate`. Unique per
  `(employee, periodMonth)` → re-running the same month → `409`.
- Verify: each row shows gross / deductions / net and a payslip-PDF key; re-list the month to see
  it persisted.

### M2 · Leave — `/hr/attendance`, **Leave tab**

- **List.** Opening the Leave tab → `GET /api/v1/hr/leaves` (HR sees all rows;
  `?status=` / `?userUuid=` filters map to the tab's controls). Each row:
  `{userFullName, leaveType, fromDate, toDate, days, status, createdAt}`.
- **Apply.** `POST /api/v1/hr/leaves` is `isAuthenticated()` — any logged-in user can file one
  for themselves. **Request Leave** → type + from/to + reason → **PENDING**. Overlapping approved
  leave → `409`. (`hr@` filing their own request is the simplest single-account path.)
- **Decide.** Back as `hr@`, row → **Approve** / **Reject** → `PUT /api/v1/hr/leaves/{id}/decision`
  `{decision:"APPROVED"}` (REJECT needs a reason). Verify: status + `decidedAt` + `approvedByUuid`.
- The **Attendance** and **Check-in** tabs on this page are still demo data (`msh_*` keys) — no
  biometric-attendance endpoint.

### M3 · Employee KYC documents — `/hr/documents`, **Employee KYC Docs tab**

- **List.** `GET /api/v1/hr/documents` → rows `{userFullName, documentType, verificationStatus,
  downloadUrl (presigned, 15-min TTL), createdAt}`. Status / type filters map to
  `?status=` / `?documentType=`.
- **Upload.** **Upload Document** → pick a PDF + document type → `POST /api/v1/hr/documents`
  (`multipart/form-data`, field `file` + `documentType`) → **PENDING**. `downloadUrl` is `null` on
  this response — re-list to get a link.
- **Verify / reject.** Row → **Verify** or **Reject** (reason modal) →
  `PUT /api/v1/hr/documents/{id}/verify` `{decision:"VERIFIED"|"REJECTED", rejectionReason}`. You
  can't verify your own document.
- The other tabs on this page (offer / experience / relieving **letters**) are still mock — those
  are the separate `/hr/letters` flow.

---

## 16 · Flow N — Trainer sprint → review → graduation (`pm@moriah.test`)

Uses the seed batch `FS-2026-01` (you may need to be its PM — create your own batch in Flow L and
add `student2` to it via `testing-flow.md` Flow 3 Step 2 if the seed batch isn't yours).

### N1 · Sprints & tasks — `/trainer/sprints`

- **Load.** Batch picker → `GET /api/v1/sprints?batchId=` (falls back to a fan-out over your
  batches) and `GET /api/v1/tasks?sprintId=` for the selected sprint.
- **Create sprint** → `POST /api/v1/sprints` `{batchId, sprintNumber, goal, startDate, endDate,
  plannedPoints}`. **Activate** → `POST /api/v1/sprints/{id}/activate` (one active per batch).
- **Create task** → `POST /api/v1/tasks` `{sprintId, title, type, points, dueDate, …}`.
- **Assign** → the optional "Assign to" dropdown is the batch roster
  (`GET /api/v1/batches/{id}/students`); picking a name → `POST /api/v1/tasks/{id}/assign`
  `{userUuid}` → task status **ASSIGNED**.
- **Edit** → `PUT /api/v1/tasks/{id}` (title / points / status within the allowed transitions).

### N2 · Standups — `/trainer/standups`

- Batch picker + date → `GET /api/v1/standups?batchId=&date=`.
- **Schedule** → `POST /api/v1/standups` `{batchId, sprintId, scheduledAt, lateCutoffMinutes,
  notes}`.
- **Mark attendance** → per-student control → `POST /api/v1/standups/{id}/attendance`
  `{userUuid, status, blockerNotes}` (the roster comes from `GET /api/v1/batches/{id}/students`).

### N3 · Code review — `/trainer/code-review`

- **Queue.** `GET /api/v1/reviews/queue?size=50` on load → submissions awaiting review.
- Open one → `GET /api/v1/submissions?taskId=` resolves the latest submission id.
- **Submit review** → `POST /api/v1/reviews` `{submissionId, score, decision
  (APPROVED|CHANGES_REQUESTED), comment, inlineComments[]}`. Verify: the item leaves the queue;
  an APPROVED review moves the task to COMPLETED.

### N4 · PIP management — `/trainer/pip`

- **List.** `GET /api/v1/pip?batchId=&status=`.
- **Review a case** → `POST /api/v1/pip/{id}/review` `{decision, reviewNotes}`.
- **Complete a milestone** → `POST /api/v1/pip/{pipId}/milestones/{milestoneId}/complete`.
- **"Trigger manual PIP"** and **"Remove case"** buttons **throw a "not supported" error** on
  purpose — there is no create/delete PIP endpoint; PIPs are raised by the nightly job only.

### N5 · Graduation — `/trainer/graduation`

- Batch picker → `GET /api/v1/batches/{id}/students` → roster with status.
- **Graduate** an ACTIVE student → `POST /api/v1/batches/{id}/students/{userUuid}/graduate` →
  status **GRADUATED**.
- **Issue certificate** → `POST /api/v1/certificates/issue` `{batchId, userUuid, certificateType}`
  → cross-check as that student in Flow K1.

### N6 · Analytics — `/trainer/analytics`

No dedicated endpoint. The page fires `GET /api/v1/sprints`, `/tasks`, `/batches/{id}/students`
and `/pip` for the selected batch and derives velocity / completion / at-risk counts **in the
browser**. Verify the numbers by hand against those four responses.

---

## 17 · Flow O — Student delivery: tasks, submissions, assessments (`student1@moriah.test`)

### O1 · Tasks — `/student/tasks`

- **Load.** The page finds your batches (`GET /api/v1/batches`), then their sprints
  (`GET /api/v1/sprints?batchId=`), then fans out `GET /api/v1/tasks?sprintId=` — the board shows
  every task across your active sprints.
- **Pull a task** → on a BACKLOG task → `POST /api/v1/tasks/{id}/pull` → it moves to ASSIGNED
  (assigned to you). This is the **only** status transition wired from this screen — IN_PROGRESS /
  IN_REVIEW / COMPLETED are driven by submissions + PM review, and the board reconciles to the
  server's response.

### O2 · Submissions — `/student/submissions`

- **Submit work** → pick a task + **GitHub PR URL** (required) + optional demo video + notes →
  `POST /api/v1/submissions` → the task moves to IN_REVIEW.
- **History** → `GET /api/v1/submissions?taskId=` per task → prior attempts + any review scores.

### O3 · Assessments — `/student/assessments`

- **List.** `GET /api/v1/assessments?batchId=` (fan-out over your batches) → published quizzes
  with duration, pass %, attempts allowed.
- **Start** → `POST /api/v1/assessments/{id}/attempts` → questions **without** the answer key; a
  countdown starts from `durationMinutes`.
- Navigate single-/multi-select (buttons) and CODE (textarea, marked *manually graded*).
- **Submit** (or auto-submit on timeout) → `POST /api/v1/assessments/attempts/{id}/submit`
  `{answers:[{questionId, selectedOptionIndices, codeAnswer}]}` → server returns
  `percentage` / `passed` / per-question marks.
- If the quiz has any CODE question the attempt lands **`PENDING_MANUAL_GRADING`** — the result
  screen says so and the percentage covers the auto-graded portion only.
- **Re-open a graded attempt** → `GET /api/v1/assessments/attempts/{id}`.

---

## 18 · Flow P — Student subscription & real checkout (`student3@moriah.test`)

`student3@` is on STARTER, so entitlement-gated screens 403 until they upgrade.

**P1 · Dashboard guard.** Log in as `student3@` → `/student/dashboard` fires
`GET /api/v1/subscriptions/me`; if it's `null` you are redirected straight to
`/student/subscription`.

**P2 · Plan list.** `/student/subscription` → `GET /api/v1/plans` renders the real plan cards;
`GET /api/v1/subscriptions/me` marks the current plan. A **track** picker (`TRACK_CODES`) sets
`trackCode` for the checkout body.

**P3 · Checkout (needs real Razorpay/Stripe TEST keys in the backend `.env`).**
- Pick a plan + gateway → **Upgrade** → `POST /api/v1/subscriptions/checkout`
  `{planCode, gateway, trackCode, couponCode?}` → `data` carries either
  `{razorpayOrderId, razorpayKeyId, amount}` (Razorpay) or `{stripeCheckoutUrl}` (Stripe).
- Razorpay → the real Checkout.js widget opens; Stripe → the browser redirects to the hosted page.
  Complete the payment in test mode.
- The gateway calls its webhook (`/webhooks/{razorpay|stripe}`) → the backend activates the
  subscription. The page **polls `GET /api/v1/subscriptions/me`** (`waitForActivation()`) until
  `status:"ACTIVE"`, then unlocks the dashboard.
- Without real keys the `checkout` call fails at the gateway step — expected; drive the webhook by
  hand per `testing-flow.md` Flow 5 to finish activation.

---

## 19 · Flow Q — Placement pipeline, end to end (client → HR → student)

One `placements` row, three personas. Prereq: Flow G2 — a recruitment request **APPROVED**, so a
placement exists at `SHORTLISTED`. Every step is `PUT /api/v1/placements/{id}` with a target
`stage` + merged `details`; `GET /api/v1/placements` is what each screen lists.

| # | Persona / screen | Action | `stage` → | key `details` |
|---|---|---|---|---|
| Q1 | client · `/client/talent-pool` | schedule technical | `TECHNICAL_SCHEDULED` | `roundType, date, time, meetingLink` |
| Q2 | client | complete + rate | `TECHNICAL_COMPLETED` → `TECHNICAL_APPROVED` | `technicalFeedback, technicalRating` |
| Q3 | HR · `/hr/documents` (placement tab) | HR round | `HR_SCHEDULED` → `HR_COMPLETED` → `HR_APPROVED` | `hrRoundDate, hrRoundLink` |
| Q4 | HR | verify docs | `DOCUMENT_VERIFICATION` | `docsVerifiedAt`, `placementConfirmedAt` flags |
| Q5 | HR | create offer | `OFFER_CREATED` | `offerText, offerCtc` |
| Q6 | client · `/client/talent-pool` | sign offer | `CLIENT_SIGNED` | `clientSignedAt` |
| Q7 | student · `/student/interviews` | countersign | `STUDENT_SIGNED` | `studentSignedAt` |
| Q8 | HR · `/hr/documents` | finalise | `PLACED` | — (terminal) |

- **Ownership is enforced server-side by *target* stage.** A client PUT to an HR stage, or an HR
  PUT to `STUDENT_SIGNED`, → `403 INSUFFICIENT_ROLE`. A student can only set `STUDENT_SIGNED`.
- **Forward-only.** Any move to a lower stage → `409`. From a terminal stage (`PLACED` /
  `REJECTED`) any further PUT → `409`.
- **`REJECTED`** is reachable from any non-terminal stage by the client or HR (`details.rejectedAt`,
  `details.rejectionReason`).
- Each screen shows the same row from its own angle: the client sees the full ladder,
  `/student/interviews` shows the candidate their pipeline + the countersign button,
  `/hr/documents` placement tabs collapse "Docs Verified" / "Placement Confirmed" into the
  `DOCUMENT_VERIFICATION` detail flags.

---

## 20 · Flow R — Profile & settings (any role)

### R1 · Profile — `/{role}/profile` (and `/student/profile`)

- **Load.** `GET /api/v1/users/me` hydrates the form.
- **Save** editable fields → `PUT /api/v1/users/me/profile` — `bio, location, currentTitle,
  githubUsername, experienceLevel, yearsExperience, skills, education, workExperience`. **Name /
  email / phone are read-only here** (admin-only via Flow C4).
- The profile **photo** is local-only (`localStorage` `msh_profile_photo`) — no upload endpoint.
- Student-only extras (`education`, `linkedin`) that the backend has no field for are kept in
  `msh_student_profile_extra`.

### R2 · Settings — `/{role}/settings`

- **Notification preferences** → local only (`msh_notif_prefs`), no endpoint.
- **Password** → **"Email me a reset link"** → `POST /api/v1/auth/password/forgot` (always
  200, even for an unknown address). There is no change-password-in-place form.
- **Two-factor:**
  - **Enable** → `POST /api/v1/auth/2fa/enable` → returns a secret + `otpauth://` URI (QR). Enter
    a current TOTP code → `POST /api/v1/auth/2fa/verify` → 2FA is on; `GET /users/me` now reports
    `twoFactorEnabled:true`.
  - **Disable** → enter a current code → `POST /api/v1/auth/2fa/disable`.
  - `admin@` / `hr@` cannot disable (mandatory) — the API returns a 4xx and the UI shows the error.

---

## 21 · Regression checklist (fast pass after any FE/BE change)

1. `admin@` 2FA login → `/admin/dashboard` renders, metrics cards populated.
2. Bell badge shows a number; opening it lists notifications.
3. `/admin/users` lists 10 users; suspend + un-suspend `student3@` round-trips.
4. `dev@` → create a resource, see it as `student1@` in `/student/resources`.
5. `dev@` → create a lesson + 2 quiz questions; `GET /lessons/{id}/quiz` has no answer key.
6. `sales@` → create + cancel a campaign.
7. `ba@` → schedule + delete a meeting.
8. `client@` → talent list loads; recruitment request → APPROVE via API → a `placements` row appears.
9. `hr@` → start an onboarding, tick the checklist, mark COMPLETED.
10. `hr@` → record an exit, finalise it, confirm the employee is EXITED via the API.
11. `hr@` → `/hr/payroll` generates a month; re-running it 409s.
12. `hr@` → `/hr/attendance` Leave tab lists requests; apply → PENDING → approve → decidedAt set.
13. `hr@` → `/hr/documents` KYC tab: upload a PDF → PENDING → verify → VERIFIED, `downloadUrl` on re-list.
14. `pm@` → `/trainer/sprints`: create sprint, activate, create + assign a task from the roster dropdown.
15. `pm@` → `/trainer/code-review`: queue loads; submitting a review clears the item.
16. `pm@` → `/trainer/graduation`: roster loads; graduate a student, issue a certificate.
17. `student1@` → `/student/tasks`: pull a BACKLOG task → ASSIGNED.
18. `student1@` → `/student/submissions`: submit a PR URL → task IN_REVIEW.
19. `student1@` → `/student/assessments`: start → answer → submit → server percentage; CODE quiz → PENDING_MANUAL_GRADING.
20. `student3@` → `/student/dashboard` redirects to `/student/subscription`; plan list + `subscriptions/me` load.
21. Placement Flow Q: client → HR → student PUTs advance one row `SHORTLISTED → … → PLACED`; a cross-role PUT 403s.
22. Any role → `/{role}/profile`: edit bio, save → `PUT /users/me/profile`; name/email stay read-only.
23. Any role → `/{role}/settings`: enable 2FA (secret → verify), then disable (non-mandatory roles).
24. `student1@` → `/student/learning` lists lessons, quiz submit returns a server score.
25. `student1@` → `/student/certificates` + `/student/pip` load without error (empty is fine).
26. `pm@` → `/trainer/batches` lists `FS-2026-01`; creating a batch adds a row; roster popup loads.
27. Hard-refresh on any dashboard → no bounce to `/login`.
28. OAuth2: `…/authorize/google` → lands back on `/auth/oauth/callback`, tokens stored, URL scrubbed.
