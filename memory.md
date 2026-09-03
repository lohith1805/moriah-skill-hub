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
- **B1.18** installment/EMI plans at checkout — **DROPPED** (user, 2026-09-03). Was an admin-only
  localStorage mock (`msh_installment_plans`) with no consumer; removed from `admin/Plans.jsx` +
  the admin Dashboard caption in frontend `867a6c0`. No backend was ever built. Do not revive
  without a fresh product decision.
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

### FRONTEND — Part A integration status (branch `master`, HEAD `c33ddb7`)

`npm run build` green after every commit (2796 modules, Node v24). **Nothing browser-tested yet.**
Commit trail (…`fd63107` student resume+profile) → **BE `04368c0` `GET /batches/{id}/students`
(the batch roster — PM/ADMIN, must own the batch)** → `8cb23f7` PM task assignment (roster
dropdown + `assignTask`) → `0413525` Graduation (roster + `graduateStudent`+`issueCertificate`) →
`9c423da` Standups (`getStandups`/`scheduleStandup`/`overrideAttendance`) → `ade10f2` Batches
"view roster" popup → `a5a4582` Analytics derived from real sprints/tasks/roster/PIP.

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
- **trainerService** — batches list/create; review queue + `reviewSubmission` → resolve latest
  submission then `POST /reviews`; sprints (`getSprints` fan-out, `createSprint`, `activateSprint`);
  tasks (`getSprintTasks` fan-out, `createTask` — epic/user-story/AC folded into `description`) +
  `assignTask`/`updateTask`; **`getStudentsForBatch` → `GET /batches/{id}/students`**; standups
  (`getStandups`/`scheduleStandup`/`overrideAttendance`); PIP list + `updatePipCaseStatus` →
  `POST /pip/{id}/review`; `getAnalytics(batchId)` now DERIVED from real sprints+tasks+roster+PIP
  (velocity + per-student task completion; quiz score stays 0 — no PM endpoint for it).
  Pages: `trainer/Sprints.jsx`, `SprintPlanning.jsx` (optional assignee dropdown from the roster),
  `CodeReview.jsx`, `PIPManagement.jsx`, `Graduation.jsx` (batch picker + roster, graduate + issue
  cert), `Standups.jsx` (batch picker, schedule, mark attendance), `Batches.jsx` (roster popup).
- **shared/Profile.jsx** + **shared/Settings.jsx** — profile save, reset-link email, real 2FA.

**Still MOCK in trainerService** (no backend): `getStaffableClientProjects`, `setProjectBatches`
(project↔batch assignment), `getAllStudents`/`updateStudentBatch`/`createStudent` (no PM endpoint
to list/create students or move them between batches), `triggerManualPip`/`removePipCase` (throw
a clear message), `ensureGraduationExitHandoff`/`approveGraduation` (superseded by
`graduateStudent`), the dead `reviewSubmissionMock`/`computeBatchHealth`/`getStored*` helpers.

**DONE — `GET /api/v1/batches/{id}/students`** (BE `04368c0`): PM/ADMIN, must own the batch;
returns `[{userUuid, fullName, email, status, joinedAt, graduatedAt, finalScore}]`. This unblocked
and now-wired: PM task assignment, `trainer/Graduation.jsx`, `trainer/Standups.jsx`,
`trainer/Batches.jsx` roster popup, `trainer/Analytics.jsx` (derived client-side).

**STILL BLOCKED (need a new backend endpoint / a product decision):**
- **`student/Assessments.jsx`** — the quiz engine (`GET /assessments?batchId=`, `POST
  /assessments/{id}/attempts`, `GET/POST /assessments/attempts/{id}[/submit]`) EXISTS and is
  unblocked, but the FE page is a 415-line **in-browser code runner** (`q.testFn(code)` grades
  client-side). Wiring it = a rewrite that drops the runner for server-side grading + a plain code
  textarea (CODE questions land `PENDING_MANUAL_GRADING`). Product call: keep the runner as a
  practice aid, or go fully server-graded? Not started.
- ~~`GET /api/v1/hr/leaves`~~ **DONE** (BE `7758f89`): isAuthenticated; employee sees own,
  HR/ADMIN see all (status/userUuid filters). `LeaveRequestResponse` gained `userFullName` +
  `createdAt`. `hrService` leave fns wired; `hr/AttendanceLeave.jsx` Leave-Approvals tab + the
  `ApplyLeaveWidget` are LIVE (FE `fe7fb5d`). The page's Attendance-ledger + biometric-checkin
  tabs stay mock — still no HR staff-attendance endpoint.
- ~~`GET /api/v1/hr/documents`~~ **DONE** (BE `a689457`): isAuthenticated; own docs / HR-all with
  status/userUuid/documentType filters; presigned `downloadUrl` per row. `hr/Documents.jsx` gained
  an **Employee KYC Docs** tab (list + verify/reject) — FE `7927204`. Upload has no FE caller (it's
  a self-upload by `@CurrentUser`); the 5 placement tabs are separate (see below).
- ~~`POST /subscriptions/checkout`~~ **ALREADY EXISTED** (`payment/CheckoutController`, path
  `/api/v1/subscriptions/checkout`) — just wired. `studentService.subscribeToPlan` now calls it;
  `student/Subscription.jsx` opens the real Razorpay widget / Stripe redirect and polls
  `/subscriptions/me` for webhook activation (FE `f8619e5`). Without real test-mode keys the
  backend 502s — surfaced.
- ~~client placement pipeline~~ **BUILT** — new `placement/` module (BE `60e58d3`): `Placement`
  entity + `PlacementStage` (13, ordered), V33 migration, `GET/GET{id}/PUT /api/v1/placements`,
  auto-created when `TalentService.decide` APPROVES a request, per-target-stage role gates
  (client=technical+CLIENT_SIGNED, HR=HR-rounds+docs+offer+PLACED, student=STUDENT_SIGNED),
  forward-only, `details` = merged JSON bag. 7 unit tests, Flow 23. FE `4435d11`:
  `placementService.js` + `placementPipeline.js` re-based to the 13 stages + a
  loadRecruitments/saveRecruitments adapter that diffs & PUTs; `client/TalentPool.jsx`
  ("Shortlist" → `requestRecruitment`), `student/Interviews.jsx`, `hr/Documents.jsx` all read/write
  `/placements`. `loadDocs/saveDocs` (rendered offer-letter artifacts) stay local.
- **Lead pipeline**: `/leads` has no per-lead detail / activity-list GET / DELETE →
  `leadgen/Pipeline.jsx` + `leadgen/Targets.jsx` stay mock.
- **BA docs text-only** (no file upload) + **no resource-plan endpoint** → `ba/Documents.jsx`,
  `ba/ResourcePlanning.jsx`, `ba/ClientReview.jsx` stay mock.
- **Developer projects / bug challenges** — file-upload + in-browser test runner, unmigrated.
- No PM endpoint to **create students or move them between batches** → `trainer/Batches.jsx`
  add-student / change-batch tabs + `setProjectBatches` (project↔batch) stay mock.

**Cleanup deferred until the blocked features land**: delete `mockData.js` / `pipEngine.js` /
`placementPipeline.js`; remove unused `clientService` `TALENT_POOL` import + `trainerService`
dead helpers (`computeBatchHealth`, `reviewSubmissionMock`, `getStored*`, `runPipAutoCheckForAll`);
drop the dead Register plan/payment handlers.

**Recommended Part B round 2, smallest first:**
1. ~~`GET /api/v1/batches/{id}/students`~~ **DONE** (`04368c0`).
2. ~~`GET /api/v1/hr/leaves`~~ **DONE** (`7758f89`).
3. ~~`GET /api/v1/hr/documents`~~ **DONE** (`a689457`).
4. ~~`POST /subscriptions/checkout`~~ — already existed, now wired (`f8619e5`).
5. ~~client placement-pipeline module~~ **DONE** (BE `60e58d3`, FE `4435d11`).
6. ~~`student/Assessments.jsx`~~ **DONE** (FE `b4d82e9`): server-graded quiz engine —
   `getAssessments` / `startAssessmentAttempt` / `getAssessmentAttempt` /
   `submitAssessmentAttempt` vs `/api/v1/assessments*`; the in-browser CODE runner is gone
   (CODE → textarea → PENDING_MANUAL_GRADING).
7. **Mock layer — pruned, NOT deletable** (FE `c33ddb7`). Removed the dead trainerService
   snapshot helpers + `mockData`/`pipEngine` imports there; trimmed student/client imports.
   `mockData.js` + `pipEngine.js` stay — still-mock features with **no backend at all**:
   BA documents (file upload) + resource plans, client project briefs
   (`clientService.getClientProjects`/`submitProjectRequirement`/…), the lead pipeline
   (`crmService` — `/leads` has no detail/activity-list/delete), developer projects + bug
   challenges (file upload + in-browser runner), HR staff biometric attendance
   (`hrService.getClockinLogs`/`logCheckin`), trainer student management
   (`getAllStudents`/`updateStudentBatch`/`createStudent`), student `getPerformanceSummary` +
   the `pipEngine` auto-PIP rule engine. Each needs its own Part-B module before the layer goes.
   Minor leftover: `auth/Register.jsx` still has unreachable step-2/3 plan+payment code
   (dead since the D2 flow) — safe to strip in a follow-up.

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
Frontend integration is essentially complete. Backend this session added
`GET /batches/{id}/students`, `GET /hr/leaves`, `GET /hr/documents`, `POST` was already there for
`/subscriptions/checkout`, and a full `placement/` module (`GET /placements`, `GET`/`PUT
/placements/{id}`, migration `V33`, `PlacementStage` 13-value enum). All wired FE-side.
`student/Assessments.jsx` is server-graded; Register's dead step-2/3 code is stripped
(commit `038d67f`, FE).

All four docs now cover this round's endpoints (commit `fc5a84b`): `openapi.json` (additive
patch — keep its 4-space / LF / no-unicode-escape formatting if you patch it again),
`postman/*` (regenerated — has a Placements folder), `API-Documentation.md` (regenerated),
`testing-flow.md` (Flow 11 OAuth fragment-redirect + Flow 23 placement), `integrated-testing-flow.md`
(Flows M–R, matrix re-scored). `scripts/gen-api-doc.py` was fixed — its `@PreAuthorize`→route
association was off-by-one for this codebase's mapping-then-preauth style and dropped bare
`@GetMapping`; now correct for all 192 handlers.

**Lead pipeline is now WIRED** (backend `3208192`, FE `d16349f`). V34 added `deal_value` /
`next_follow_up_at` / `archived_at` to `leads`; new endpoints `GET`/`PUT`/`DELETE /api/v1/leads/{id}`,
`GET /leads/{id}/activities`, `GET /leads/targets/leaderboard`; `UpdateLeadStatusRequest` also
takes `convertedUserEmail`. `crmService.js` lead layer + `leadgen/Pipeline.jsx` no longer use
`msh_crm_leads`. Also wired earlier this session: a **client-side audit trail** (`utils/auditLog.js`,
FE `88daf4f`) and **2FA QR codes** (`qrcode.react`, FE `fd0c681`).

**Public landing page wired** — new `site/` package: `GET /api/v1/public/stats` (aggregate
COUNTs, `JdbcTemplate`) + `POST /api/v1/leads/inbound` (unauthenticated contact-form lead,
`assignedAgent=null`, dedupe-upsert). Both in `SecurityConfig.PUBLIC_PATHS`. FE `siteService.js`
+ `Home.jsx` — hero stats, pricing (real price + curated feature copy), contact form now hit
the backend. Testimonials / FAQ / feature blurbs stay static (marketing copy, not DB data).

**`R__dev_seed_data.sql` greatly expanded** — 6 more students, 3 more batches
(`FS-2026-02`/`DA-2026-01` active, `BE-2026-01` planned), sprints+tasks, 3 question banks (9
items), 5 learning resources, 3 notifications, 1 graduate + certificate. Validated by a live
`spring-boot:run` (Flyway applied V34 + the repeatable seed clean).

**`openapi.json` / Postman / `API-Documentation.md` RE-EXPORTED from a running app** (2026-09):
150 paths / ~200 ops — the first complete spec (all prior exports predated flows 12-24).
`gen-postman.py` → 36 folders / 212 requests; `gen-api-doc.py` → 199 endpoints. To refresh:
free port 3306 (native `MySQL97` squats it — `Stop-Service MySQL97` in an admin shell), or bring
Docker MySQL up on another port (`DB_PORT=3316 docker compose up -d`), `mvn -o spring-boot:run`
with matching `DB_PORT` (+ `DB_REPLICA_PORT` same value), `redis-cli FLUSHALL` if `/plans` 500s
(stale cache), then `curl :8080/v3/api-docs` + both gen scripts. Pretty-print the export to
4-space/LF before committing.

**`mockData.js` / `pipEngine.js` still cannot be deleted** — 6 feature areas have no backend
(BA docs + resource plans, client project briefs, developer projects + bug challenges, HR
biometric attendance, trainer cross-batch student mgmt, student `getPerformanceSummary` +
auto-PIP). `placementPipeline.js` stays too (display-helper + backend adapter).

Frontend HEAD `fd86bc2` (landing page wired). Backend HEAD = the "crm: public site + inbound
lead" commit + its docs-regen follow-up (this session's last two backend commits).
`README.md` still has the user's uncommitted Razorpay-test-key edit — never `git add -A`; scope
every `git add`. Untracked `*_Integration*.zip` / `bun.lock` in both repos are the user's — leave them.
