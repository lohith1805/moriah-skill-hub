# Memory — Moriah Skill Hub (backend + frontend integration)

Last updated: **2026-09-03**. This file is the authoritative handoff — earlier prose was
consolidated here.

---

## Layout

| Path | What | Git |
|---|---|---|
| `C:\Users\ADMIN\Desktop\Moraih Backend\Moraih Backend` | Spring Boot 3.5 / Java 21 / MySQL 8 / Redis 7 backend, modular monolith | own repo, **no remote**, branch `main` |
| `C:\Users\ADMIN\Desktop\Moraih Backend\moriah-skill-hub-updated` | React 19 + Vite 8 + Redux Toolkit + react-router 7 frontend | **own repo** (`git init`'d this session), no remote, branch `main` |
| `C:\Users\ADMIN\Desktop\Moraih Backend\frontend-backend-gap-report.md` | the integration plan (Part A frontend / Part B backend) | untracked, workspace parent |

Backend HEAD: `6e481c8` (`22caf12` question-bank finish · `60741b3` per-lesson quiz · `1761ae9`
batches widened to STUDENT · `6e481c8` docs). Frontend HEAD: developerService migration commit
(after `c80e0e0`).
Backend working tree: only `README.md` modified — **NOT mine**: someone pasted Razorpay TEST api
keys + seeded-user rows into it. Left untouched; should be moved out of the tracked file. Never
`git add -A` in the backend repo without checking — scope the add.
**Migrations now V20–V32, next = V33.** Unit tests: **522, 0 failures** (last full `mvn clean
verify` green, 1 proven-environmental IT flake).

---

## BACKEND — Part B of the gap report is 100% COMPLETE

All committed to `main`, each its own commit, all with unit tests, conventions followed
(ApiResponse envelope, pagination, soft-delete, AuditLogService, no outbound calls in
`@Transactional`, `common/` imports no feature package).

| Gap | Endpoints | Migration | Commit |
|---|---|---|---|
| B1.4 Video Lessons | 8 under `/api/v1/lessons` (CRUD + `/modules`, `/me/progress`, `POST /{id}/progress`); `watchedSeconds` never rewinds, `completedAt` once | V22 `video_lessons`+`lesson_progress` | `541c072` |
| B1.5 Notifications feed | `GET/PUT /api/v1/notifications` + `/unread-count` + `/read-all`; IN_APP channel only; `NotificationFeedService` in `common/notification/` | V20 `notifications.read_at` | `466ff3f` |
| B1.6 Resource Library | `GET/POST/PUT/DELETE /api/v1/resources`; curators TRAINER_PM/DEVELOPER/BA/ADMIN; edit=creator-or-ADMIN | V21 `learning_resources` (link-only) | `f7c5250` |
| B1.7 Lead-Gen Campaigns | `GET/POST/PUT/DELETE /api/v1/leads/campaigns`; LEAD_GEN/ADMIN; DELETE→CANCELLED | V24 `lead_campaigns` | `812318d` |
| B1.8 Student Interviews | `POST/GET /api/v1/interviews` + `GET /me` (STUDENT) + `PUT/DELETE /{id}` | V26 `student_interviews` | `ac13b26` |
| B1.9 Talent Pool + recruitment | `GET /api/v1/talent-pool`, `POST/GET /api/v1/recruitment-requests`, `PUT /{id}/status`; resume access NOT wired (needs OwnershipGuard branch) | V27 `recruitment_requests` (+ `UserProfileRepository.searchTalentPool`) | `18ff04b` |
| B1.10 HR Exit / Onboarding / Disciplinary | `/api/v1/hr/exits` (POST/GET/PUT/`POST {id}/complete` → flips `employees.status` EXITED/TERMINATED + `date_of_exit`); `/api/v1/hr/onboardings` (POST/GET/GET{id}/PUT); `/api/v1/hr/disciplinary` (POST/GET/GET{id}/PUT); HR_MANAGER/ADMIN | V28/V29/V30 | `64a5257`,`2193010`,`37f6902` |
| B1.11 Transactions + refunds | `GET /api/v1/admin/payments` + `/summary` + `/{gatewayOrderId}` + `POST /{id}/refund`; added `RazorpayService.refund`/`StripeService.refund`; `AdminPaymentService.refund` is NOT `@Transactional`, sets REFUNDED optimistically (webhook no-ops if already REFUNDED) | — | `fb2aa5f` |
| B1.12 Admin coupons | `GET/POST/PUT/DELETE /api/v1/admin/coupons`; `payment/CouponAdminService` (separate from checkout `CouponService`); DELETE=deactivate | — | `765a7ec` |
| B1.13 Plan create/delete | `POST/DELETE /api/v1/admin/plans` via `EntitlementService` (evicts `plans`/`planCodesById` caches) | — | `8e1afff` |
| B1.14 BA Meetings | `GET/POST/PUT/DELETE /api/v1/ba/meetings`; BA/ADMIN; DELETE→CANCELLED | V25 `ba_meetings` | `9079ecd` |
| B1.15 Question bank + bug-challenge CRUD | banks: `POST/GET /api/v1/assessments/banks` + `POST/GET /{id}/questions` (`correctAnswer` never serialized); challenges: `GET /projects/{id}/challenges` + `GET/PUT/DELETE /api/v1/challenges/{id}` | V31 `question_banks`+`question_bank_items` | `56e2b3b`, `1f7ec8a` |
| B1.16 Dev requirements review + BA doc list | `GET /api/v1/ba/documents` (new), `GET /api/v1/dev/requirement-documents` + `/{id}` + `POST /{id}/acknowledge`; `RequirementDocumentDetailResponse` adds `content` | V23 `requirement_documents.dev_reviewed_at`/`_by` | `c25465f` |
| B1.17 Admin user detail/edit | `GET/PUT /api/v1/admin/users/{userUuid}` (`AdminUserDetailResponse`; profile fields only, no token_version bump) | — | `8e1afff` |
| + `GET /api/v1/hr/employees` list + `/{id}` | feature 19 had only POST | — | `c293d0b` |

New `ErrorCode`s this session: `COUPON_CODE_TAKEN`, `PLAN_CODE_TAKEN`, `PAYMENT_NOT_REFUNDABLE`,
`BUG_CHALLENGE_NOT_FOUND`, `LEAD_CAMPAIGN_NOT_FOUND`, `INTERVIEW_NOT_FOUND`,
`RECRUITMENT_REQUEST_NOT_FOUND`, `EMPLOYEE_EXIT_NOT_FOUND`, `EMPLOYEE_ONBOARDING_NOT_FOUND`,
`DISCIPLINARY_ACTION_NOT_FOUND`, `QUESTION_BANK_NOT_FOUND`.
New modules: `learning/`, `resource/`, `interview/`, `talent/`. **Migrations V20–V31. Next = V32.**

### Backend verify status — GREEN (cleared)
Full `mvn clean verify` over V20–V31: **507 unit + 267 integration**, 1 flaky IT
(`PipFlowIT.updateRule_asPm_returns403` — got 401 not 403 after 19.5s: JWT auth timed out under a
thrashing DB pool). **Re-ran `PipFlowIT` alone → 14/14 PASS.** Environmental, not a regression
(V26–V31 touch nothing in `pip/`). `mvn verify` NEEDS Docker (Testcontainers). Unit tests
(`mvn test` / `surefire:test`, ~15s) do not.

### Backend — Part B still open
- **B1.18** installment/EMI plans at checkout — deferred, "if the product needs it" (user: "discuss later").
- **B1.4 per-lesson quiz — DONE** (`60741b3`, V32 `lesson_quiz_questions`/`lesson_quiz_attempts`).
  `learning/LessonQuizService`: `GET /lessons/{id}/quiz` (any auth), `POST /{id}/quiz/questions` +
  `DELETE /{id}/quiz/questions/{qid}` (curators), `POST /{id}/quiz/submit` (any auth). ≥60% →
  upserts `LessonProgress` COMPLETED. `correctIndex` never serialized. 7 unit tests.
- Optional: scope `GET /api/v1/batches` list for TRAINER_PM to owned batches only (today a PM
  sees all). STUDENT already enrolled-scoped.

### Docs (2026-09-03, commit `6e481c8`)
- `docs/testing-flow.md`: every step now tagged `· auth: «token as ROLE»`; added "which token
  unlocks which flow" table; new **Flows 12–22** for the post-launch endpoints (notifications,
  resources, lessons+quiz, requirement docs, interviews, question banks, lead campaigns, BA
  meetings, talent/recruitment, HR onboarding/disciplinary/exit, admin payments/coupons/plans);
  Appendix C carry-over table extended; Flow 2 Step 3 documents the batches widening.
- `docs/API-Documentation.md` regenerated (`python scripts/gen-api-doc.py`) — the `@PreAuthorize`
  scan corrected stale Auth lines: `PUT /admin/plans/{id}` → ADMIN, `POST /ba/documents` → BA/ADMIN,
  `GET /batches` + `/{id}` → +STUDENT.
- **`docs/openapi.json` is stale** — 95 paths / 107 ops, predates Flows 12–22 (~35 routes). Only a
  running-app re-export (`GET /v3/api-docs`, then `gen-api-doc.py`) refreshes it. Noted in-doc.

---

## FRONTEND — Part A STARTED (`moriah-skill-hub-updated`, its own repo)

Commits: `a86db27` mock-prototype baseline · `b0e4b21` **auth spike** · `c80e0e0` notification +
admin services. `npm install` done (`node_modules` gitignored). **Every commit verified with
`npm run build` (passes, 2795 modules).** Node v24. Dev: `npm run dev` (vite, port 5173).

### DONE
- **`src/services/apiClient.js`** — `USE_MOCKS=false`; real fetch vs `VITE_API_BASE_URL || /api/v1`;
  unwraps `{success,data,error}` → returns `data`, throws `ApiError{status,code}`; `tokenStore`
  (access + rotating refresh + expiry in localStorage keys `msh_access_token`/`msh_refresh_token`/
  `msh_token_expiry`); transparent 401→`POST /auth/refresh`→retry-once, single-flight; proactive
  refresh ~30s before expiry; `requestMultipart()`.
- **`src/services/authService.js`** — vs `/api/v1/auth/**` + `/users/me` + `/admin/**`. FE `user`
  = `/users/me` profile fields **+ JWT `roles` claim** (decoded FE-side; `/users/me` has no
  roles/status). D3 primary-role collapse via `primaryFeRole()`. Exports (same names the app
  imports, now real): `login` (→ `{user}` or `{twoFactorRequired,twoFactorSetupRequired,
  challengeToken}`), `completeTwoFactor`/`verifyTwoFactor`, `beginTwoFactorSetup`,
  `refreshSession`, `logout` (revokes refresh), `getMe`, `registerStudent` (→ needs email verify,
  D2), `verifyEmail`, `registerClient`, `requestPasswordReset`, `resetPassword`, `acceptInvite`
  (2FA-aware), `getPendingClients`/`setClientApproval` → `/admin/client-requests`,
  `inviteStaffMember`/`resendStaffInvite` → `/admin/users`, `oauthAuthorizeUrl`,
  `persistSession`/`clearSession`/`getPersistedUser`.
- **`src/utils/constants.js`** — added `BACKEND_ROLE_TO_FE`/`FE_ROLE_TO_BACKEND`, `ROLE_PRIORITY`,
  `primaryFeRole()`, `BACKEND_STATUS_TO_FE`, `PLAN_CODE_TO_FE`/`FE_PLAN_TO_BACKEND`, extended
  `ACCOUNT_STATUS`.
- **`src/context/AuthContext.jsx`** — removed the 1s localStorage poll; hydrates from `/users/me`
  on load; `hydrating` flag; `completeTwoFactor`, `refreshUser`. **`src/routes/ProtectedRoute.jsx`**
  waits out `hydrating` (spinner) so a hard refresh doesn't bounce to /login.
- **`src/pages/auth/Login.jsx`** — email+password only; real 2FA step (challenge code +
  mandatory-setup showing secret/provisioningUri); OAuth buttons → `oauthAuthorizeUrl(provider)`
  full-page redirect. **`AcceptInvite.jsx`** — 2FA-aware result. New pages + routes:
  `/reset-password` (`ResetPassword.jsx`), `/verify-email` (`VerifyEmail.jsx`),
  `/auth/oauth/callback` (`OAuthCallback.jsx`, parses `?accessToken&refreshToken&expiresIn` or `?error`).
- **`vite.config.js`** — `/api` → `http://localhost:8080` dev proxy. `.env` / `.env.example`
  (`VITE_API_BASE_URL`, blank = use proxy).
- **`src/services/notificationService.js`** — → `/api/v1/notifications` feed; maps backend
  `{templateCode,payload,createdAt}` → UI `{title,body,time,read}`; humanizes templateCode.
- **`src/services/adminService.js`** — `getExecutiveMetrics` → `/admin/metrics/overview`
  (derives mrr/arr from `recentRevenue`, crmConversion from `leadFunnel`; `pipRatio` stubbed 0);
  `getAllUsers`/`getUser`/`updateUserStatus`/`updateUserRoles`/`updateUserRecord` (B1.17 PUT)/
  `deleteUserRecord` (→ status TERMINATED, no hard delete) → `/admin/users**`;
  `getPlans` → `/plans`, `createPlan`/`updatePlan`/`deletePlan` → `/admin/plans` (B1.13);
  `getTransactions`/`getTransactionSummary`/`refundTransaction` → `/admin/payments` (B1.11);
  `getAuditLogs` → `/admin/audit`; `exportReport` → `/admin/exports/{report}`;
  `getCoupons`/`createCoupon`/`updateCoupon`/`deleteCoupon` → `/admin/coupons` (B1.12).
  Backend enum codes translated; `PageResponse.content` unwrapped.

### FRONTEND — Part A integration status (branch `master`, HEAD `fd63107`)

`npm run build` green after every commit (2796 modules, Node v24). **Nothing browser-tested yet.**
Commit trail: auth spike `b0e4b21` → notif+admin `c80e0e0` → developer `534db75` → lead campaigns
`d67633f` → BA meetings + talent `71de48d` → HR exits/onboarding/disciplinary `238ef3d` →
onboarding route `4662f79` → student reads `c60f083` → trainer batches+review-queue `9b41df3` →
trainer sprint/task `dd853c7` → student sprint board `1df39e3` → trainer CodeReview `d1cc66d` →
HR payroll `1bf6c25` → trainer PIP `cb313f3` → D2 register `e1f69a5` → OAuth fragment (FE `e1c4d19`
+ BE `bb09501`) → shared Profile/Settings `6f6d797` → student resume+profile `fd63107`.

**WIRED to the backend:**
- **auth** — login/2FA/refresh/logout, register (student+client), verify-email, reset-password,
  OAuth callback (reads `location.hash`), `updateProfile` (PUT /users/me/profile), voluntary 2FA
  enable/verify/disable, `getMe`. `toFeUser` = /users/me + JWT `roles` claim + `twoFactorEnabled`.
- **D2 register flow** — `Register.jsx` student path: create account → "verify email then sign in
  & pick a plan" screen (no inline plan/payment). `student/Dashboard.jsx` guards on
  GET /subscriptions/me → redirects to `/student/subscription` if none.
- **OAuth** — backend success/failure handlers now `sendRedirect` to
  `moriah.security.oauth2.frontend-redirect-uri` with the result in the URL **fragment**
  (`#accessToken=…` / `#twoFactorRequired=true` / `#error=…`) — keeps tokens off the wire/logs.
- **notificationService, adminService, developerService** (resources B1.6, lessons+quiz B1.4,
  assessment banks B1.15, dev requirement-docs B1.16).
- **crmService** — lead campaigns CRUD → `/leads/campaigns` (B1.7); `Campaigns.jsx` rebuilt.
- **baService** — meetings → `/ba/meetings` (B1.14); `type`/`client`/`attendees` local sidecar.
- **clientService** — talent-pool browse + `POST /recruitment-requests` (B1.9); `score` faked from
  yearsExperience.
- **hrService** — employees list, exits/onboarding/disciplinary CRUD + `/{id}/complete` (B1.10),
  payroll (`getPayroll(month)` + `generatePayroll`). `hr/ExitManagement.jsx`, `hr/Onboarding.jsx`
  (also newly routed at `/hr/onboarding`), `hr/Payroll.jsx` all rebuilt around employee pickers +
  generic checklist editors.
- **studentService** — lessons+quiz (server-graded, positional answer array), certificates
  (`/certificates/me`), PIP (`/pip/me`), plans (`/plans`), `getMySubscription`, `getMyInterviews`
  (B1.8), sprint board (`getMySprints`/`getMyTasks` fan-out over enrolled batches; `updateTaskStatus`
  = `POST /tasks/{id}/pull` only), `submitGithubPR` → `POST /submissions`, `saveResumeFile` →
  `POST /users/me/resume` multipart, profile save → `PUT /users/me/profile`.
- **trainerService** — batches list/create, review queue + `reviewSubmission` → resolve latest
  submission then `POST /reviews`, sprints (`getSprints` fan-out, `createSprint`, `activateSprint`),
  tasks (`getSprintTasks` fan-out, `createTask` — epic/user-story/AC folded into `description`;
  tasks start BACKLOG unassigned), `assignTask`/`updateTask`, PIP list + `updatePipCaseStatus` →
  `POST /pip/{id}/review`. `trainer/Sprints.jsx`, `SprintPlanning.jsx`, `CodeReview.jsx`,
  `PIPManagement.jsx` updated (assignee dropdown removed — see blocker below).
- **shared/Profile.jsx** + **shared/Settings.jsx** — profile save, reset-link email, real 2FA.

**BLOCKED on missing backend endpoints (Part B round 2 — cannot finish these on the FE alone):**
- **No PM/HR-visible "people list with uuids"**: no `GET /batches/{id}/students` roster. This
  blocks: PM assign-task-to-named-student, `trainer/Graduation.jsx` (needs batchId+userUuid — has
  `graduateStudent`/`issueCertificate` helpers ready), `trainer/Standups.jsx`, `trainer/Batches.jsx`
  student enrolment, `hr/Documents.jsx` letters (`IssueLetterRequest.userUuid`).
- **No aggregate analytics endpoint** → `trainer/Analytics.jsx` stays mock (`getAnalytics`).
- **No PIP create endpoint** (nightly job only) → `triggerManualPip`/`removePipCase` throw a
  clear message; only list + `/{id}/review` are wired.
- **No HR leave-list / HR staff-attendance endpoint** → `hr/AttendanceLeave.jsx` + `ApplyLeaveWidget`
  stay mock (LeaveController is POST + PUT decision only).
- **No HR-document list endpoint** → `hr/Documents.jsx` KYC list can't load.
- **No subscription checkout endpoint** on SubscriptionController → `subscribeToPlan` stays mock
  (real flow is `POST /subscriptions/checkout` in the payment module).
- **No backend for the client placement pipeline** (shortlist → interview rounds → offer → sign →
  placed) → `client/TalentPool.jsx`, `student/Interviews.jsx`, most of `hr/Documents.jsx` stay on
  `utils/placementPipeline.js`.
- **Lead pipeline**: `/leads` has no per-lead detail / activity-list GET / DELETE →
  `leadgen/Pipeline.jsx` + `leadgen/Targets.jsx` stay mock.
- **BA docs are text-only** (no file upload) + **no resource-plan endpoint** → `ba/Documents.jsx`,
  `ba/ResourcePlanning.jsx`, `ba/ClientReview.jsx` stay mock.
- **Developer projects / bug challenges** — file-upload + in-browser test runner, unmigrated.
- **`student/Assessments.jsx`** — separate quiz engine (`/api/v1/assessments`), not yet wired.

**Cleanup deferred until the blocked features land**: delete `mockData.js` / `pipEngine.js` /
`placementPipeline.js`; remove unused `clientService` `TALENT_POOL` import + `trainerService`
`computeBatchHealth`; drop dead Register plan/payment handlers.

**Recommended Part B round 2 (unblocks most of the above), smallest first:**
1. `GET /api/v1/batches/{id}/students` → `[{userUuid, fullName, status}]` (PM/ADMIN). Unblocks
   task assignment, graduation, standups, batch enrolment.
2. `GET /api/v1/hr/leaves` (+ filters) and `GET /api/v1/hr/documents` list endpoints.
3. `GET /api/v1/trainer/analytics?batchId=` (velocity + quiz trend + per-student rows).
4. `POST /api/v1/subscriptions/checkout` surfaced on SubscriptionController (or document that the
   FE must call the payment module directly).
5. Client placement-pipeline module (biggest) — or decide it stays FE-only.

### Docs
- `docs/testing-flow.md` — API-only E2E (Postman), Flows 1–22, per-step auth tags.
- `docs/integrated-testing-flow.md` — UI-driven E2E companion: wired-vs-mock matrix per screen +
  browser walkthroughs. **Needs a refresh** to add the screens wired after Flow L (student sprint
  board, submissions, resume/profile, trainer sprints/tasks/code-review/PIP, HR payroll, shared
  Settings, D2 register, OAuth fragment flow).
- `docs/API-Documentation.md` regenerated 2026-09-03; `docs/openapi.json` still predates Flows
  12–22 (needs a running-app re-export).


## Contract cheatsheet (from gap report Part C)
- Roles: `student↔STUDENT`, `trainer↔TRAINER_PM`, `developer↔DEVELOPER`, `lead_generator↔LEAD_GEN`,
  `hr↔HR_MANAGER`, `business_analyst↔BUSINESS_ANALYST`, `admin↔ADMIN`, `client↔CLIENT`.
- Plans: `starter↔STARTER`, `professional↔PROFESSIONAL`, `project_based↔PROJECT_BASED`,
  `internship↔INTERNSHIP`, `corporate↔CORPORATE_PROGRAM`. Prices 3999/7999/14999/19999/29999.
- Auth DTOs: `LoginRequest{email,password}` → `LoginResponse{twoFactorRequired,
  twoFactorSetupRequired,challengeToken,tokens{accessToken,refreshToken,expiresInSeconds}}`.
  `/auth/refresh{refreshToken}` → `TokenPairResponse` (bare, not nested). `/auth/2fa/verify
  {challengeToken,totpCode}` → `{tokens}`. `/auth/2fa/enable {challengeToken}` →
  `{secret,provisioningUri}`.

---

## Standing rules / gotchas (held all session)
- **`@Transactional` never wraps an outbound call** (S3, HTTP, gateway). Side effects via `afterCommit`.
- **Honesty**: never state a passing test count not from a real local run.
- Machine is HEAVILY load-sensitive — a full `mvn verify` is 60–90 min right now; run it ALONE,
  filter its log with `grep --line-buffered` (never unbounded `tee` — a huge log killed a run),
  and close other JVMs first.
- **Do not `taskkill //F //IM java.exe`** — it also kills the user's VS Code redhat.java language
  server. Kill maven JVMs by PID or `CommandLine -like '*Adoptium*'`.
- `docker/mysql-init/01-users.sql` — if ITs fail suite-wide with `GRANT command denied to
  'moriah_migrate'`, this file is missing again.
- Dev seed is `db/testdata/R__dev_seed_data.sql` (Flyway repeatable, `dev` profile only). 10
  accounts, password `Password123!`.
- Commit message bodies: **no backticks** in `git commit -m` / heredocs (shell runs them —
  dropped two words from a commit earlier). Use plain words or `git commit -F -` with a
  single-quoted heredoc `<<'EOF'`.

---

## Next session starts with
**FE integration against the current backend is as complete as it can be** — everything left is
BLOCKED on missing endpoints (see the "BLOCKED" list in the FRONTEND section). Two paths:
- **Build Part B round 2** (the 5 endpoints listed under "Recommended Part B round 2"), starting
  with `GET /api/v1/batches/{id}/students` — that one alone unblocks task assignment, graduation,
  standups and batch enrolment.
- OR decide the placement pipeline + a few others stay FE-only, then do the mock-layer cleanup.
Also outstanding, low-risk: wire `student/Assessments.jsx` to `/api/v1/assessments` (the quiz
engine that predates this session).
Frontend HEAD `fd63107`. Backend code HEAD `bb09501` (OAuth fragment redirect). `README.md` still
has the user's uncommitted Razorpay-test-key edit — never `git add -A`.
