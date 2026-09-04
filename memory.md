# Memory — Moriah Skill Hub (backend + frontend integration)

Last updated: **2026-09-04** (part-3 session — MCQ-only assessments, bug-challenge submission flow,
P0 batch; see "Part-3 session" at the very end of this file). This file is the authoritative
handoff — earlier prose was consolidated here.

---

## Layout

| Path | What | Git |
|---|---|---|
| `C:\Users\ADMIN\Desktop\Moraih Backend\Moraih Backend` | Spring Boot 3.5 / Java 21 / MySQL 8 / Redis 7 backend, modular monolith | own repo, **no remote**, branch `main` |
| `C:\Users\ADMIN\Desktop\Moraih Backend\moriah-skill-hub-updated` | React 19 + Vite 8 + Redux Toolkit + react-router 7 frontend | **own repo** (`git init`'d this session), no remote, branch `main` |
| `C:\Users\ADMIN\Desktop\Moraih Backend\frontend-backend-gap-report.md` | the integration plan (Part A frontend / Part B backend) | untracked, workspace parent |

HEADs (authoritative copy near end of file, plus the dated session log below it):
Backend `26c96d7` (branch `main`). Frontend `084dbcc` (branch `master`).
**part-2 2026-09-04 session made a LARGE number of changes in BOTH working trees and committed
NOTHING** — everything is uncommitted in the working tree. Do NOT `git add -A`; the backend
`README.md` still carries the user's Razorpay-test-key edit (not ours). See the final section for
the full file list. The user runs the backend on 8080 themselves and MUST restart it to load any
of it.
**Migrations now V20–V37 (V35 standup meeting_link, V36 staff_attendance, V37 lesson/resource
track — added part-2 2026-09-04, Flyway auto-applies on restart). Next = V38.**
Unit tests: full `mvn verify` NOT re-run this session (Docker/slow); targeted `surefire:test`
slices green every pass — QuizServiceTest 17, QuestionBankServiceTest 10, LessonServiceTest 13,
ResourceServiceTest 7, ProjectServiceTest 30, BatchServiceTest 18, StaffAttendanceServiceTest 6,
EntitlementServiceTest 6, StandupServiceTest, CouponServiceTest, ClientProjectServiceTest 8,
ClientRegistrationServiceTest 2, ClientServiceTest 3, AuthServiceTest, LessonQuizServiceTest 7.
Frontend `npm run build` green every pass (~3002 modules).

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
- **User must restart their backend** (they run it themselves on 8080) to load `26c96d7` — the
  checkout guards + `InvoiceService.findWithUserById` email fix aren't live until then. Confirm no
  bean cycle on start (new deps added to CheckoutService/AdminPaymentService/DevJobController).
- Then re-send Man1's confirmation email: `POST /api/v1/dev/jobs/invoice-generation/run?paymentId=6`.
- Regenerate the 4 API docs for `26c96d7` (see doc-regen procedure): new `invoice-generation` job
  arg on `POST /dev/jobs/{job}/run`, `AdminPaymentResponse` +`planCode`/`planName`.
- Still open (not blocking): Google OAuth `redirect_uri_mismatch` — user must set the Console
  redirect URI to exactly `http://localhost:8080/api/v1/auth/oauth2/callback/{google,github}`.
- Layers 2–4 of the double-charge plan (auto-refund duplicate, reconciliation sweep, FE button
  disable) not started.

### Older "next session" note (pre-2026-09-04, kept for context)
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
ensure Docker MySQL is up on 3306 (`docker compose up -d`; `MySQL97` is Stopped/Manual so 3306 is
free), `mvn -o spring-boot:run`, `redis-cli FLUSHALL` if `/plans` 500s (stale cache), then
`curl :8080/v3/api-docs` + both gen scripts. Pretty-print the export to 4-space/LF before committing.

**`mockData.js` / `pipEngine.js` still cannot be deleted** — 6 feature areas have no backend
(BA docs + resource plans, client project briefs, developer projects + bug challenges, HR
biometric attendance, trainer cross-batch student mgmt, student `getPerformanceSummary` +
auto-PIP). `placementPipeline.js` stays too (display-helper + backend adapter).

**Two bugs fixed after the seed/spec work (verified against a live instance):**
1. **Admin dashboard "Loading executive metrics…" forever** — `MetricsService` + admin exports
   query `replicaJdbcTemplate` *only*; locally there's no real replica, so `/admin/metrics/*`
   500'd ("table doesn't exist" on the empty `mysql-replica` container). Fix `5c80beb`:
   `application-dev.yml` now sets `moriah.datasource.replica.url` to fall back to the primary
   (`${DB_REPLICA_HOST:${DB_HOST}}:${DB_REPLICA_PORT:${DB_PORT}}`). No `.env` change needed.
2. **Settings shows "2FA: Disabled" for every role even when it's on** (and Enable → `409
   TWO_FACTOR_ALREADY_ENABLED`) — `GET /users/me` never returned the flag. Fix: `UserProfileResponse`
   + `ProfileService.toResponse` now carry `twoFactorEnabled` (backend `32e81ac`, openapi patched);
   `Settings.jsx` also self-heals on the 409 (FE `b7abb1e`). FE `toFeUser` already read the field.

**Auth-entry UX pass (2026-09-03, verified: FE build + BE compile + `*Auth*/*OAuth*` unit tests all green):**
1. **Google OAuth showed a raw `UNAUTHENTICATED` JSON page instead of ever logging in.** Root
   cause: `application.yml` never set each registration's `redirect-uri`, so it defaulted to
   Spring's `{baseUrl}/login/oauth2/code/{id}` — but `SecurityConfig` moved the login filter to
   `/api/v1/auth/oauth2/callback/*`, so the provider's callback hit an unmapped path →
   `anyRequest().authenticated()` → `authenticationEntryPoint` JSON. Fix (BE `496e67b`):
   pinned `redirect-uri: "{baseUrl}/api/v1/auth/oauth2/callback/{google|github}"`; added
   `OAuth2DefaultCallbackFallbackController` (`GET /login/oauth2/code/**`, in `PUBLIC_PATHS`) that
   302s to `frontendRedirectUri()#error=oauth_callback_misrouted` so a provider still pointed at
   the old default URI degrades to a friendly SPA bounce, not JSON.
2. **`POST /api/v1/auth/resend-verification`** (NEW, public, IP-rate-limited via the shared
   `/api/v1/auth/**` bucket). `AuthService.resendVerificationEmail` mirrors `forgotPassword`:
   non-enumerating, only acts for `PENDING_VERIFICATION`, burns prior unused tokens first
   (`EmailVerificationTokenRepository.markAllUnusedAsUsedForUser`). New DTO `ResendVerificationRequest`.
3. **Frontend (FE `0d25ebb`):** Register page — Google button now actually redirects (was a stub
   toast) + a GitHub button beside it (matches Login). `OAuthCallback.jsx` — any failure bounces
   to `/login` with a readable reason in router state instead of a dead-end card; `Login.jsx`
   surfaces `location.state.authError` as a toast + inline banner then clears it. `VerifyEmail.jsx`
   — "resend verification email" form (own email input) on the failed/missing states; Register
   student-success screen — inline "resend the verification link". `authService.resendVerificationEmail()`.

✅ **`openapi.json` / Postman / `API-Documentation.md` re-exported from the live app** (commit
`f7cacfc`, 2026-09-03): 153 paths / 202 ops; Postman v2.1.0 = 33 folders / 214 requests;
API-Documentation.md = 202 endpoints. Covers `POST /auth/resend-verification` +
`GET /subscriptions/me/invoices`. Diff was purely additive (0 removed lines).

**Student billing / plans page pass (2026-09-03, verified: FE build + BE compile + payment/
invoice/ownership/cache tests green):**
1. **`GET /api/v1/plans` → 500 (recurring)** — a poison Redis value (stale DTO shape / serializer
   mismatch / shared keyspace) made the `@Cacheable` throw `SerializationException` out of the
   proxy. Durable fix (BE `4054e17`): new `CacheErrorHandlingConfig implements CachingConfigurer`
   — a `CacheErrorHandler` that evicts the bad key + treats a failed GET as a miss (method runs,
   repopulates); failed put/evict/clear logged & swallowed. Redis down now = "do the real work",
   never a 500. No more manual `redis-cli FLUSHALL`.
2. **Student "Invoice History" was a localStorage mock** (`msh_transactions` filtered by user
   name) → real payment never showed, Plan column blank. New **`GET /api/v1/subscriptions/me/invoices`**
   (BE `dcc5bd3`, `payment/InvoiceController` mapped under `/subscriptions/*` like `CheckoutController`
   so `subscription/` keeps no dep on `payment/`). `InvoiceService.listForUser(userId, uuid)` =
   caller's CAPTURED/REFUNDED payments left-joined to `invoices`; row carries planCode/planName/
   amount/status + a 10-min pre-signed `pdfUrl` (null while the async `InvoiceGenerationJob` hasn't
   issued it → status `"PROCESSING"`). `OwnershipGuard` gained an `invoices/` branch
   (`canAccessInvoice`: invoice→payment→user, student-owner only) — was deny-by-default before.
   FE `0d25… → e1e5a52`: `studentService.getMyInvoices()`, `Subscription.jsx` billing table now
   backend-driven with per-status badge tones; PDF button opens the pre-signed link or explains
   it's still generating.
   ⚠️ If rows stay `"PROCESSING"` forever, the async invoice job is failing — check `skillhub-minio`
   is up and `job_runs` / logs for `[InvoiceGenerationJob] failed` (S3/MinIO upload is the usual
   culprit). And a Razorpay webhook can't reach `localhost` — needs the Cloudflare tunnel URL
   registered as the webhook endpoint, or no `payments`/`invoices` row is ever created.

✅ **openapi.json / Postman / API-Documentation.md regenerated** (commit `f7cacfc`) — see the note
above; covers both `POST /auth/resend-verification` and `GET /subscriptions/me/invoices`.

**Whole-project verified E2E run (2026-09-03): [`docs/verified-e2e-flow.md`](docs/verified-e2e-flow.md) +
`scripts/e2e-flow.py`.** Booted deps + `mvn spring-boot:run` (dev) + walked 44 steps across
public / register+verify+login / no-sub student / active-sub student / trainer-PM / lead-gen /
admin (real 2FA setup dance in the harness). **44/44 green.** Also re-confirmed the FE↔BE API audit:
144 distinct `apiClient.*` calls, **0 with no matching backend route**.
Found + fixed 2 `GlobalExceptionHandler` gaps where a client mistake surfaced as a 500:
- missing/unbindable `@RequestParam` (e.g. `GET /standups` w/o `batchId`) → now **400 VALIDATION_FAILED**
  (`MissingServletRequestParameterException` + `MethodArgumentTypeMismatchException` handler).
- wrong HTTP verb on an existing path (e.g. `GET /admin/plans`, which is POST/PUT/DELETE-only) →
  now **405** (`HttpRequestMethodNotSupportedException` handler + new `ErrorCode.METHOD_NOT_ALLOWED`).
Harness notes: raises `AUTH_RATE_LIMIT_PER_MIN` (default 10/min IP-scoped trips on the login burst);
uses unique email+phone per run (`users.phone` is UNIQUE); resets `admin@` 2FA to setup-required.

**Payment-page + post-payment UX pass (2026-09-03):**
- **Student now hears about their payment.** `PaymentWebhookService` after a subscription goes
  ACTIVE: IN_APP `SUBSCRIPTION_ACTIVATED` row (reaches the bell, not just a toast) + email.
  `activateSubscription` now returns the `UserSubscription` (or null) not a boolean.
  `BatchAllocationService` also notifies the student IN_APP for both allocation outcomes —
  `BATCH_ALLOCATED` and `BATCH_PLACEMENT_PENDING` (before: only PMs were told). BE `0e2c13c`
  (+ `PaymentWebhookServiceTest`/`BatchAllocationServiceTest` updated).
- **Coupon on the checkout page.** New `POST /api/v1/subscriptions/checkout/preview`
  `{planCode,couponCode?}` → payable amount via the same `CouponService.preview` the real
  checkout uses; invalid coupon = `couponApplied:false` + message, not an error. BE `9e4e8b7`
  (DTOs `CheckoutPreviewRequest`/`Response`). FE `8aa7de0`: coupon field + Apply in the
  gateway-select modal, discounted price inline, code passed through to `/checkout`.
- **`docs/webhooks.md` already documents the manual signed-webhook method** (`openssl dgst
  -sha256 -hmac`, both gateways) — no change needed (I briefly clobbered it, restored from git).

**Backend context notes (things the user flagged, mostly frontend-mock, NOT bugs in real code):**
- Developer dashboard "Simulated Client Projects" + Bug-Challenges projects: 100% frontend mock
  (`developerService.getProjects()` → `mockRequest(PROJECTS)`, localStorage `msh_developer_projects`
  / `BUG_CHALLENGES_KEY`). Two different mock sources → the 4-vs-3 mismatch; "disappears on assign"
  is a localStorage-mutation quirk. Real backend: `GET /api/v1/projects` (PUBLISHED only for
  non-admins), seed has **1** published project (`Todo API`). Dev projects/challenges = one of the
  6 still-unwired areas.
- Toasts vs bell: toasts are `ToastContext` (ephemeral); the bell reads `GET /notifications`,
  which only shows backend `notifications` rows. Before this pass, a STUDENT got IN_APP rows only
  for PIP; now also subscription-activated + batch placement. Task/sprint/review/quiz/certificate/
  interview events still fire a FE toast only — each needs an `enqueueAfterCommit` added at its
  service to land in the bell (not done).

**Batch auto-assignment pass (2026-09-03, BE `70645f1` / FE `88327dc`):** a student who paid for
a batch plan while no matching batch existed sat forever in `pending_batch_allocations` (only PMs
notified). Now: `BatchService.create` publishes `BatchCreatedEvent` → `PendingAllocationDrainer`
(`@Async @TransactionalEventListener AFTER_COMMIT`) re-runs `BatchAllocationService.allocate` for
every unresolved pending row on that track, oldest first, each its own tx. **Event indirection is
mandatory** — a direct `BatchService → BatchAllocationService` call closes a bean cycle via
`UserService → TaskService → SprintService → BatchService` (confirmed: `BeanCurrentlyInCreationException`).
New `GET /api/v1/batches/pending-allocations` (TRAINER_PM/ADMIN) + a card in `trainer/Batches.jsx`
Students tab.

**Dev-job trigger + email/PDF + developerService pass (2026-09-03):**
- **Test scheduled jobs on demand** (BE `a36f9f5`): `POST /api/v1/dev/jobs/{job}/run` — dev
  profile only, ADMIN-gated, calls the job's service method directly. `{job}` ∈
  `attendance-finalisation | metrics-refresh | pip-evaluation | subscription-expiry | quiz-attempt-expiry`.
  Full runbook (incl. SQL to make a student breach a PIP rule): **`docs/testing-scheduled-jobs.md`**.
  The nightly chain is 01:30 attendance → 01:45 metrics → 02:00 PIP.
- **Subscription confirmation email now carries the invoice PDF** (BE `035dc3b`): the email moved
  from `PaymentWebhookService` (webhook time, before the PDF exists) to `InvoiceService.renderAndUpload`
  (after upload). `EmailDispatcher` gained a generic single attachment (`attachmentBase64` /
  `attachmentFilename` / `attachmentContentType` in the payload); `NotificationService.enqueueNow`
  (write+queue in a fresh tx, for a caller with no ambient tx). The instant IN_APP
  `SUBSCRIPTION_ACTIVATED` row still fires from the webhook.
- **#1 developerService → real `/projects`** DONE (FE `4317f07`): `getProjects/createProject/
  updateProject/publishProject/getProjectChallenges` hit `/api/v1/projects*`; `developer/Projects.jsx`
  is backend-driven. Backend `Project` is lean, so the modal's reference-solution/swagger/ER/video/
  README/file-upload fields and the Delete button no longer persist; heading → "Practice Projects".
  **Bug Challenges tab still mock** — backend challenge is a broken-code + test-script *file upload*,
  not inline `testCases`; wiring it needs that page reshaped.
- openapi/postman re-exported: **156 paths / 205 ops** (`/dev/jobs/{job}/run` + earlier
  `/checkout/preview`, `/batches/pending-allocations`).

**#4 "all downloads → PDF" DONE** (FE `6415425`): new `src/utils/pdf.js` (jsPDF 4.2.1 — pulls
`html2canvas` transitively, bundle ~1.9→2.3MB). `downloadPdf(filename,title,blocks)` +
`downloadInvoicePdf(...)` + `openPdfUrl`. Converted: admin Transactions invoice (`.html`→`.pdf`),
student Subscription invoice fallback (HTML window→`.pdf`; real server invoice still opens from
`pdfUrl` first), student Interviews offer letter (`.txt`→`.pdf`), HR Documents (`.txt`→`.pdf`),
student Profile resume-fallback (print window→`.pdf`; a real uploaded resume still downloads
as-is). CSV exports (audit log, admin reports) stay CSV — data, not documents.

**Student dashboard "not assigned to a batch" was a FE display bug, NOT invoice/subscription-
related** (FE `fdeacb9`). The header read `user.track`/`user.batch`, which `GET /users/me`
(`UserProfileResponse`) **never returns** — so it always said "not assigned" regardless of
enrolment. Fixed: `studentService.getMyBatch()` → `GET /api/v1/batches` (student-scoped). The
subscription goes ACTIVE in the SAME webhook tx as the capture (payment `CAPTURED` + `user_subscriptions`
ACTIVE are two rows/tables); the invoice PDF is downstream/async and gates nothing.

**#3 admin invoice list + PDF DONE** (BE `ff95117` / FE `b7aff9e`): `AdminPaymentResponse` gains
`invoiceNumber` + `invoiceStatus` (ISSUED/PENDING/FAILED/PROCESSING/null, joined per page). New
`GET /api/v1/admin/payments/{gatewayOrderId}/invoice` (ADMIN) → `{ url }` (10-min presigned PDF),
404 `INVOICE_NOT_FOUND` until the async job issues it. `OwnershipGuard`'s `invoices/` branch now
grants ADMIN any invoice. FE `admin/Transactions.jsx`: invoice column + Download prefers the
server PDF (falls back to the client jsPDF for rows without one).

**Still-open items the user flagged (NOT yet done):**
- **#7 client "view projects" → 403 "no access to this document"** — `GET /api/v1/projects` excludes
  CLIENT by design (internal training catalogue); there is **no `GET /clients/projects` list**
  endpoint (only `POST /clients/projects` + `GET /clients/projects/{id}/progress`). `clientService`
  project fns are localStorage mocks (no API call) so the failing request is something else on that
  page — NEED the exact failing URL from the user's Network tab to pin it.

**Explained (no code bug):**
- **Invoice appears ~30 min late / payment missing from Admin Audit** — there is NO scheduled
  invoice job; `InvoiceGenerationJob` fires the instant the webhook tx commits. A delay = the
  gateway **redelivering** the webhook after an earlier delivery failed / never reached localhost.
  `AuditQueryService` uses `LEFT JOIN users actor` so null-actor `PAYMENT_CAPTURED` rows DO show —
  they're just never created if the webhook doesn't fire. Root fix for both: make the first webhook
  delivery succeed (tunnel up, MinIO up, `RAZORPAY_WEBHOOK_SECRET` set). `WebhookReconciliationJob`
  (every 10 min) is the backstop.

**HEADs:** Frontend `2f6f637` (branch `master`). Backend `26c96d7` (branch `main`) on top of
`ff95117` / `035dc3b` / `a36f9f5` / `70645f1`. openapi/postman NOT regenerated for `26c96d7`
(new op `POST /dev/jobs/invoice-generation/run?paymentId=` via the existing `/{job}/run` route;
`AdminPaymentResponse` gained `planCode`/`planName`) — regen next pass → will bump ~1 op.
Targeted unit slices green each pass (checkout/adminpayment/invoice/webhook/batch); full surefire
not re-run. `*IT` need Docker — a bean-wiring change once showed up only as a `BatchFlowIT`
context-load failure, so run one IT (or restart the app) after touching bean graphs. `26c96d7`
added `UserSubscriptionRepository` + `SubscriptionPlanRepository` deps to `CheckoutService`,
`SubscriptionPlanRepository` to `AdminPaymentService`, `InvoiceService`+`InvoiceRepository` to
`DevJobController` — restart the app to confirm no cycle.

### Session 2026-09-04 — checkout double-charge guard, invoice-email fix, plan column, webhook debugging
- **403 on `POST /api/v1/auth/login` (5174)** — root cause: Spring CORS rejects `Origin:
  http://localhost:5174` ("Invalid CORS request" → 403) because `application-dev.yml`
  `allowed-origins` only had 3000/5173 and Vite hopped to 5174 (5173 was occupied). Fixed by
  adding 5174 + 5175 to the dev list (committed in `26c96d7`).
- **Google OAuth `redirect_uri_mismatch`** — user kept registering a wrong path in Google Console.
  The ONLY correct value is what Spring sends, verified: `http://localhost:8080/api/v1/auth/oauth2/callback/google`
  (and `.../github`). NOT a frontend URL, no `/login`, no trailing slash. Vite base URL / `.env`
  are irrelevant to this error. Still unresolved on the user's side (they need to fix the Console).
- **Payment stuck at CREATED** — all 6 `payments` rows were `CREATED`; no webhook had ever been
  processed on this DB. Razorpay can't reach localhost. User fumbled the manual `openssl` webhook
  3× (used `order_TEST1`; then renamed the JSON *key* `order_id`→`pay_manual6` leaving
  `id:pay_TEST1` → dedup-ignored). I fired the correct one for payment 6 →
  `CAPTURED` + `user_subscriptions` id 11 ACTIVE (user 55 "Man1", plan 3 PROJECT_BASED, track
  PRODUCT_DESIGN → parked pending, no batch) + invoice `MSH-INV-000006` ISSUED. Payments 4,5 left
  CREATED (dup retries by same user).
- **Confirmation email never sent** — `InvoiceGenerationJob` failed for payment 6 with
  `LazyInitializationException` at `InvoiceService.sendConfirmationEmail` →
  `payment.getUser().getEmail()` (renderAndUpload has no session). Fixed with
  `PaymentRepository.findWithUserById` (`@EntityGraph "user"`). Invoice 6 is already ISSUED so a
  retrigger short-circuits — use the new `POST /api/v1/dev/jobs/invoice-generation/run?paymentId=6`
  (dev, ADMIN) to reset it to PENDING and re-render + re-send. Needs the backend restarted first.
  NOTE: recurring `NotificationWorker.drainBatch` "Redis command timed out" ERROR in logs — looks
  like empty-queue blocking-pop noise (real dispatches DID send: notif rows 12/15 SENT); not fixed.
- **Layer 1 (double-charge prevention)** implemented in `CheckoutService.checkout` — see `26c96d7`
  commit body. Guard A: 409 `SUBSCRIPTION_ALREADY_ACTIVE` if an ACTIVE sub exists. Guard B:
  reuse a <15-min-old open Razorpay `CREATED` payment for same user+plan instead of a new order.
  Layers 2 (auto-refund duplicate in the webhook "already active" branch), 3 (reconciliation
  sweep for CAPTURED-with-no-subscription), 4 (FE button disable + idempotency key) NOT done.
- **Blank Plan column** (admin Transactions + student dashboard) — `AdminPaymentResponse` only had
  `planId`. Added `planCode`/`planName`; FE `toFeTransaction` now sets `plan`; student
  `Dashboard.jsx` shows `subscription.planName` in the header.
- **Student dashboard stuck on "Cohort Allocation Pending" even after batch allocation** — root
  cause: `DashboardLayout.jsx` gated on `user?.batch`, which is ALWAYS undefined (`/users/me`
  carries no batch). Man1 (user 55) WAS enrolled: `batch_students` id 9 → batch 5 `PD-Batch-01`
  PRODUCT_DESIGN status `PLANNED` (a TRAINER_PM created it → `PendingAllocationDrainer` resolved
  `pending_batch_allocations` id 1 at 03:17). Fixed FE `084dbcc`: `DashboardLayout` now calls
  `getMyBatch()` (`GET /api/v1/batches` student-scoped → `findEnrolledByUserId`, returns PLANNED
  or ACTIVE) and gates on that, with a "Loading your dashboard…" state during the fetch.
  `getMyBatch()` already falls back to `rows[0]` when no ACTIVE batch, so a PLANNED batch unblocks.
- Frontend HEAD now `084dbcc` (was `2f6f637`).
- Backend is run by the USER in their own terminal on 8080 — do NOT kill/restart it or start one
  on 8080. Use `SERVER_PORT=8081` if I need my own instance. User must restart to load `26c96d7`.
- All seeded accounts password = `Password123!`. `admin@moriah.test` has `two_factor_enabled=1`
  (not from seed — set later); disable with
  `UPDATE users SET two_factor_enabled=0, two_factor_secret=NULL WHERE email='admin@moriah.test';`
  The TOTP secret column is AES-encrypted at rest — not usable as a plain authenticator secret.

**Local run state right now:** Docker `skillhub-mysql` (**3306:3306** — reverted 2026-09-03 at the
user's request via `docker compose up -d --force-recreate --no-deps mysql`; the compose file was
never edited, it's `"${DB_PORT:-3306}:3306"`, and `.env` has `DB_PORT=3306`; the named volume
`skillhub-mysql-data` survived so all seed data is intact), `skillhub-mysql-replica` (3307:3306),
`-redis`, `-minio` all UP. Native Windows `MySQL97` service is **Stopped / StartType Manual** so
it no longer squats 3306 — leave it Manual. Plain `mvn -o spring-boot:run` (reads `.env`) now
Just Works. **Do not pass `DB_PORT=3316` anymore** and do not re-remap the container.
`application-dev.yml` already falls replica → primary, so no `DB_REPLICA_PORT` override needed.

`README.md` still has the user's uncommitted Razorpay-test-key edit — never `git add -A`; scope
every `git add`. Untracked `*_Integration*.zip` / `bun.lock` in both repos are the user's — leave them.

---

# Session 2026-09-04 (part 2) — dashboard bug-fix sweep, every role portal

Goal: the user walked every dashboard and listed bugs / mock-data / broken flows. Worked through
admin -> student -> trainer -> HR -> developer -> client -> BA. **Nothing committed** — all changes
uncommitted in both working trees. Backend must be **restarted** (user runs it on 8080) to load:
the `CouponService` fix, V35/V36/V37 (Flyway auto), every `@PreAuthorize` change, and the new
endpoints below.

## Admin dashboard + coupons

- **FE `admin/Transactions.jsx`** — removed **Record / Edit / Delete** (fake localStorage CRUD on
  top of the real `/admin/payments` ledger). **Refund** now calls real
  `POST /admin/payments/{gatewayOrderId}/refund` via `adminService.refundTransaction`. Status
  vocabulary fixed to the real enum `CREATED/PENDING/CAPTURED/FAILED/REFUNDED` (badges, filters,
  refund/invoice buttons were all keyed to invented "Success" labels -> never matched live data).
  `utils/invoiceTemplate.js` updated to recognise `REFUNDED`/`CAPTURED`.
- **FE `admin/UserManagement.jsx`** — removed **granular permissions** entirely (`GRANULAR_PERMISSIONS`
  const, `permOpen`/`permUserId`/`editPermValues` state, `openPerm`/`handlePermSave`, the "Assigned
  Permissions" column, the perm modal, `permissions` from `persistUsers`). No backend for it; it
  vanished on refresh. Page title de-permissioned.
- **FE `admin/Dashboard.jsx` + `adminService.js`** — CRM funnel + revenue chart now from real
  `GET /admin/metrics/overview`. REAL BUG fixed: `getExecutiveMetrics` read `recentRevenue` items
  as `.total`/`.amount` but the API field is `totalCaptured`, and `leadFunnel` as `.count`/`.stage`
  vs the real `leadCount`/`status` -> **MRR/ARR were always Rs 0**. Fixed key names; normalised
  `recentRevenue`->[{month,total}] (sorted asc), `leadFunnel`->[{stage,count}]. Dropped invented
  numbers (p95 latency, "3.5-day cycle", fake StatCard trend deltas, "+18% QoQ"); replaced the
  invented "System & Infrastructure SLA" card with a real "Cohort Health" card (avg attendance /
  task completion / quiz score / sprint velocity — all from the overview endpoint).
- **BACKEND `payment/CouponService.java`** — removed `@Transactional(readOnly = true)` from
  `preview()`. **Root cause of the "unexpected error" popup on a wrong coupon**: `preview()` shared
  the read-only txn opened by `CheckoutService.previewCheckout`; an invalid coupon throws
  `BusinessException` -> marked that shared txn rollback-only -> `previewCheckout` catches it and
  returns a clean `couponApplied:false` body, but the commit afterwards throws
  `UnexpectedRollbackException` -> generic 500. `preview()` only reads a couple of non-lazy
  columns, so it needs no txn. Fixes BOTH the wrong-coupon message AND valid coupons "not applying"
  (every code the user tried hit the invalid path -> 500). `redeem()` stays `@Transactional`.
- The user's "mock data in the active open section" was the coupons "42/100" figure (fixed below),
  not a separate screen.

## Admin — Subscription Tiers + Coupons tab (`admin/Plans.jsx`, full rewrite)

Whole page was localStorage mock (`msh_subscription_plans`, `msh_coupons` — hardcoded
`EARLYBIRD25 42/100`, `COLLEGE15 18/50`, ...). Now:
- **Coupons tab -> real** `GET/POST/DELETE /api/v1/admin/coupons`. "Redeemed / Limit" column =
  real `timesRedeemed / maxRedemptions` (infinity when null). New Coupon modal: code / discount
  type PERCENTAGE|FLAT / value / valid-from / valid-until / max redemptions (blank = unlimited).
  Delete = deactivate.
- **Tiers tab -> real**, which needed a NEW backend endpoint:
  **BACKEND NEW `GET /api/v1/admin/plans`** (ADMIN) — every plan, active AND deactivated, each
  carrying the numeric `id` (the `PUT`/`DELETE /admin/plans/{id}` routes need it; the public
  `GET /plans` `PlanResponse` has no id — that is why FE edit/delete never worked). New
  `AdminPlanResponse` DTO, `PlanMapper.toAdminResponse` (MapStruct),
  `SubscriptionPlanRepository.findAllByOrderByTierRankAsc()`,
  `EntitlementService.listAllPlansForAdmin()` (uncached). FE: `adminService.getAdminPlans()` +
  `toFeAdminPlan`; Edit Pricing sends a full-field-replace body built from the loaded row;
  Deactivate = `DELETE`; inactive plans keep a **Reactivate** action; Add Tier modal rebuilt to
  the real `CreatePlanRequest` shape.

## Trainer standup meeting-link flow + student attendance section

**BACKEND V35** `standups.meeting_link VARCHAR(500) NULL`. Threaded through `Standup` entity +
`CreateStandupRequest` + `UpdateStandupRequest` + `StandupResponse` + `StandupService`. 9
positional ctor call sites in `StandupServiceTest` updated. All attendance plumbing already
existed: `GET /standups?batchId=&date=` (STUDENT-visible), `POST /standups/{id}/checkin` (student
self check-in), `POST /standups/{id}/attendance` (PM override), `GET /attendance/me`,
`GET /attendance/batch/{id}`.

- **FE `trainer/Standups.jsx`** rewritten. "Schedule standup" opens a MODAL (datetime-local +
  **meeting link, required, manual paste** + late-cutoff + notes) instead of one-click ->
  present/absent roster. After scheduling: time/cutoff/link + a **Join meet** button + a
  **Cancel** action (`PUT /standups/{id}` -> CANCELLED). Roster **pre-loads existing check-ins**
  from `getStandupAttendance` (= `GET /attendance/batch/{id}` filtered to this standup).
- **FE `trainerService.js`** — `scheduleStandup` gains `meetingLink`; added `cancelStandup`,
  `getStandupAttendance`. `STANDUP_STATUS_TO_FE` fixed (`CONDUCTED`, was stale `FINALISED`).
- **FE NEW `student/Attendance.jsx`** + nav "Standups & Attendance" + route `/student/attendance`.
  Today's standup with Join meet + "I've joined — check in" + attendance history
  (`GET /attendance/me`). `studentService`: `getMyStandups(date)`, `checkInToStandup(id, notes)`,
  `getMyAttendance()`.
- **FE `student/Dashboard.jsx`** — the "Daily Standup Check-in" card was a PURE localStorage mock
  (fake 09:00-10:00 window, `msh_attendance_logs`). Replaced with a real "Today's Standup" card.
  ~180 lines of mock deleted.
- Caveat: meeting link is manual paste (no Meet/Zoom integration).

## HR staff attendance module (NEW — user asked for the backend)

**BACKEND V36** `staff_attendance` — one row per staff user per calendar day
(`uq_staff_attendance_user_date`), `checked_in_at` / `checked_out_at` / `status` / `device` /
`marked_by` / `notes`. Students are NOT tracked here (that is the standup `attendance` table).

New: `StaffAttendance` entity + `StaffAttendanceStatus` enum (PRESENT/LATE/ABSENT/HALF_DAY/
ON_LEAVE), `StaffAttendanceRepository` (JPQL GROUP-BY summary query), `StaffAttendanceService`,
`StaffAttendanceController` (`/api/v1/hr/attendance`), DTOs (`StaffAttendanceResponse`,
`StaffCheckinRequest`, `MarkStaffAttendanceRequest`, `StaffAttendanceSummaryRow`,
`StaffAttendanceSummaryProjection`), `ErrorCode.STAFF_ATTENDANCE_NOT_FOUND`,
`StaffAttendanceServiceTest` (6 green). Modelled on `LeaveService`'s "own data unless you are HR"
scoping.

Endpoints:
- `GET /api/v1/hr/attendance` — `isAuthenticated()`; non-HR auto-scoped to self; HR/ADMIN see all,
  filters `userUuid` / `from` / `to`.
- `POST /api/v1/hr/attendance/checkin` — `isAuthenticated()`. Own, or (HR/ADMIN + `userUuid`)
  another staff member's. Idempotent per day. PRESENT before **10:00 Asia/Kolkata**, else LATE
  (hardcoded `LATE_AFTER = LocalTime.of(10,0)`). Requires the target to have an `employees` row.
- `POST /api/v1/hr/attendance/checkout` — own, or `?userUuid=` for HR.
- `PUT /api/v1/hr/attendance/mark` — HR_MANAGER/ADMIN. Upsert status override, audited
  `STAFF_ATTENDANCE_OVERRIDE`.
- `GET /api/v1/hr/attendance/summary?month=YYYY-MM` — HR_MANAGER/ADMIN. Per-employee monthly
  roll-up (`attendancePct` = (present+late)/(present+late+absent+half)*100; ON_LEAVE excluded).

FE:
- `hrService.js` — `getClockinLogs`/`logCheckin` now real; added `clockOut`, `markStaffAttendance`,
  `getStaffAttendanceSummary`, `getMyStaffAttendanceToday`. Removed dead `DEFAULT_CHECKINS` +
  `mockRequest` import.
- `hr/AttendanceLeave.jsx` — "Live Biometric / Web Check-ins" tab -> real `GET /hr/attendance`
  (last 14 days, role enriched from the employee directory); "Mark attendance" -> real `PUT /mark`.
  "Staff Attendance Ledger" tab -> real `GET /summary` for the current month. Check-in modal
  targets an employee by `userUuid`.
- **`components/widgets/AttendanceCheckinWidget.jsx`** (the shared "Today's Attendance" card on
  ALL 5 staff dashboards — trainer/developer/BA/HR/leadgen) — was localStorage mock; wired to
  `POST /hr/attendance/checkin` (self) + `/checkout` + reads today from `GET /hr/attendance`. Shows
  a quiet "no employee record — ask HR" note on 404. **This IS the staff self check-in — one
  component covers all portals.**

## Developer dashboard — assessments, track scoping, bug challenges

### Assessment pipeline -> trainer-owned (user redesigned it mid-session)

First pass opened it to DEVELOPER; then the user decided **developers author question banks only;
the TRAINER decides when to publish a bank to a batch and sees the results**. Final state:

- **BACKEND `QuestionBankController`** — `CURATOR_ROLES = hasAnyRole('DEVELOPER','TRAINER_PM','ADMIN')`
  on all 7 mappings (was TRAINER_PM/ADMIN only -> **403 on every dev call — that is why "can't
  create an assessment bank"**). DEVELOPER keeps this.
- **BACKEND NEW `POST /api/v1/assessments/from-bank`** — `hasAnyRole('TRAINER_PM','ADMIN')`.
  `CreateAssessmentFromBankRequest` {bankId, batchId, projectId?, title?, durationMinutes,
  passPercentage?, maxAttempts?}. `QuizService.createFromBank` snapshots the bank's
  `QuestionBankItem`s (INCLUDING `correctAnswer` JSON) field-by-field into fresh `QuizQuestion`s on
  a new batch-scoped `Quiz`. ADMIN may target any batch; a TRAINER_PM is held to an owned batch
  (`batchService.requireOwnerOrAdmin`). `ErrorCode.QUESTION_BANK_EMPTY`.
- **BACKEND NEW `DELETE /api/v1/assessments/{id}`** — `hasAnyRole('TRAINER_PM','ADMIN')`.
  `QuizService.deactivate` (`active=false`; never row-deletes).
- **BACKEND `GET /api/v1/assessments`** — `batchId` now optional; TRAINER_PM/ADMIN may omit it to
  list every assessment. `QuizService.list(callerUserId, batchId, pageable)` (student without
  batchId -> 400).
- **BACKEND NEW `GET /api/v1/assessments/results`** — `hasAnyRole('TRAINER_PM','ADMIN')`. One row
  per student attempt, filters `assessmentId` / `batchId` / `track` / `onlyFinished`.
  `AssessmentResultRow` DTO, `QuizAttemptRepository.searchResults` (fetch-joins quiz/batch/user,
  filters by `quiz.batch.trackCode`), `QuizService.results`.
- **BACKEND `POST /api/v1/assessments`** reverted to TRAINER_PM/ADMIN; `GET /api/v1/batches` list
  reverted to TRAINER_PM/ADMIN/STUDENT (the brief DEVELOPER grant is gone).
- **FE** — DELETED `developer/Assessments.jsx` + `developer/AssessmentResults.jsx`. **NEW
  `trainer/Assessments.jsx`** — tabbed: "Question Banks" (browse dev banks -> "Publish to batch"
  you own, with duration + pass mark; unpublish live) + "Results" (attempt table with **Batch +
  Track + Assessment filters** and pass-rate / avg / awaiting-manual-grading stat cards).
  `trainerService.js` gained `getQuestionBanks`, `getPublishedAssessments`,
  `publishAssessmentFromBank`, `unpublishAssessment`, `getAssessmentResults`. Nav: developer
  "Assessments"/"Assessment Results" removed + `/developer/assessments` route removed; trainer
  "Assessments" added at `/trainer/assessments`. Dead dev publish/results fns removed from
  `developerService.js` (bank CRUD stays).
- **How results reach people**: student -> `student/Assessments.jsx` fans out
  `GET /assessments?batchId=` over enrolled batches (a trainer-published from-bank assessment shows
  up automatically, server-graded on submit). Trainer -> the new Results tab. Admin -> nightly
  `student_metrics.quiz_average_percent` -> "Avg Quiz Score" + the PIP `QUIZ_FAILURE` rule.
  **Bug challenges have NO server-side grading at all** (self-report only).

### Track / cohort scoping — video lessons + resources (V37)

**BACKEND V37** `track VARCHAR(30) NULL` on `video_lessons` AND `learning_resources` (free string
matching `batches.track_code` — FULL_STACK / DATA_ANALYTICS / PRODUCT_DESIGN / BACKEND_ENGINEERING;
NULL = all tracks). Threaded through both entities, all Create/Update/Response DTOs,
LessonService/ResourceService, both repo `search` queries
(`AND (:track IS NULL OR l.track IS NULL OR l.track = :track)`), both `GET` controllers gained an
optional `?track=`. **UX filter, not an access boundary.** `LessonServiceTest` / `ResourceServiceTest`
ctor + `search` mock call sites updated.

FE: `developer/VideoLessons.jsx` + `developer/Resources.jsx` — Track `<Select>` on the create forms
+ a track badge in tables. `student/Learning.jsx` + `student/Resources.jsx` resolve
`getMyBatch().trackCode` and pass `?track=`. `studentService.getVideoLessons(track)`,
`developerService.getResourceLibrary(track)`, plus lesson/resource create+update send `track`.
New `TRACKS` / `TRACK_LABELS` in `utils/constants.js`.

### Bug challenges -> real project-scoped challenges

**BACKEND** — added `STUDENT` + `TRAINER_PM` to `GET /api/v1/projects/{id}/challenges` and
`GET /api/v1/challenges/{challengeId}` (were DEVELOPER/ADMIN only). Create/edit/delete stay
DEVELOPER/ADMIN. `ChallengeResponse.brokenCodeUrl`/`testScriptUrl` are presigned GET URLs.

FE:
- **`developer/BugChallenges.jsx` fully rewritten** — pick a REAL project -> list/add/edit/delete
  its challenges via `POST /projects/{id}/challenges` (multipart: title, expectedBehaviour,
  difficulty, `brokenCode` file (required), `testScript` file (optional)), `PUT`/`DELETE
  /challenges/{id}`. GONE: `msh_bug_challenges` localStorage, `functionName`/`starterCode`/
  `testCases`, the in-browser runner.
- `developerService.js` — replaced the 4 localStorage bug-challenge fns with `getBugChallenges`
  (real aggregate), `createChallenge`, `updateChallenge`, `deleteChallenge`.
- **`student/Projects.jsx` rewritten** — real published projects (`GET /projects`) + their
  challenges; download broken-code/test-script links; **"Mark as fixed"** toggle. In-browser editor
  gone. `studentService.getMyProjects` -> `GET /projects` (published); `getMyBugChallenges` fans
  out over projects; `getBugChallengeDetails` -> `GET /challenges/{id}`. `attemptBugChallenge` = a
  local-only "solved" set at `localStorage['msh_bug_solved_<uuid>']` — **THE ONE REMAINING LOCAL
  BIT** (no backend attempt/grading endpoint for challenges). Unifies Projects & Bug Challenges
  (both read `/api/v1/projects`).

## Client + BA dashboards

- **Client registration "something unexpected happened"** — `users.phone` is `UNIQUE` (V1) but both
  self-register flows only pre-checked email. A duplicate phone -> raw
  `DataIntegrityViolationException` -> generic 409 "The request could not be completed." Fix:
  `UserRepository.existsByPhone` + `ErrorCode.PHONE_ALREADY_REGISTERED` + a pre-check in
  **`ClientRegistrationService.register` AND `AuthService.register`** (students had the same latent
  bug).
- **Client project submissions invisible / BA can't see them** — the whole client-project flow was
  localStorage mock AND there was **no `GET /api/v1/clients/projects` list endpoint** (only `POST`
  + `GET /{id}/progress`). Fix: **BACKEND NEW `GET /api/v1/clients/projects`** —
  `hasAnyRole('CLIENT','BUSINESS_ANALYST','ADMIN')`; a CLIENT sees only their own company's,
  BA/ADMIN see all; `?status` filter. `ClientController.listProjects`, `ClientProjectService.list`,
  `ClientProjectRepository.search`. `ClientProjectResponse` gained `clientName`.
- **FE `clientService.js` rewritten** onto the real endpoints: `getClientProjects` /
  `getMyRequirements` (alias) / `submitProjectRequirement` (-> `POST /clients/projects`, **text
  only**: title + scopeDescription + budgetRange — the backend record has no file field) /
  `getClientProjectProgress`. Removed `updateMyRequirement`/`deleteMyRequirement` (no backend — a
  submitted client project has no edit/delete route), file upload, `mockData` imports.
- **FE `client/Projects.jsx` rewritten** — submit a scope, list submissions with status ("Awaiting
  batch" until staff allocate one), Progress modal with the real sprint burndown.
  `client/Dashboard.jsx` + `client/Demos.jsx` wired + `.catch()`-guarded.
- **BA dashboard stuck on "Loading data..."** — `ba/Documents.jsx` `load()` did
  `Promise.all([getDocuments(), getDocReviews()])` with **no `.catch()`**, and `getDocReviews()`
  hits `GET /api/v1/dev/requirement-documents` which is **DEVELOPER/ADMIN-only** -> a BA gets 403
  -> the promise never resolves -> infinite spinner. Removed the `getDocReviews()` call + added
  `.catch().finally()`. Same missing-catch pattern fixed in `ba/Dashboard.jsx`,
  `ba/ResourcePlanning.jsx`.
- **FE `ba/ClientReview.jsx` rewritten** as the **client-submission inbox** — `GET /clients/projects`
  (BA sees all): project / client company / scope brief / budget / status / date + a drill-in with
  the full brief and (once a batch is allocated) the sprint burndown.

## Still mock / NOT done — next pass

- `ba/Documents.jsx` "Requirements Authoring" — the real `POST /api/v1/ba/documents` API is
  plain-text `content` only (no file attachments); the page is built entirely around file uploads
  (`msh_ba_documents` localStorage). `PUT /ba/documents/{id}/approve` also exists and is unused.
- `ba/ResourcePlanning.jsx` — no resource-plan endpoint exists (`msh_ba_resource_plans`).
- **Bug-challenge student grading** — no backend. "solved" is `localStorage['msh_bug_solved_<uuid>']`.
- `trainerService` project<->batch assignment (`setProjectBatches` / `getAssignableProjects` reads
  `msh_developer_projects`), cross-batch student management, `pipEngine` auto-PIP — all still
  localStorage. `mockData.js` / `pipEngine.js` still cannot be deleted.
- `docs/*` NOT regenerated for any of this session's new endpoints (see table below).

## New endpoints this session (for the doc regen + a quick mental map)

| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/api/v1/admin/plans` | ADMIN | plan list WITH numeric id + inactive |
| POST | `/api/v1/assessments/from-bank` | TRAINER_PM, ADMIN | publish a bank as a batch assessment |
| DELETE | `/api/v1/assessments/{id}` | TRAINER_PM, ADMIN | deactivate an assessment |
| GET | `/api/v1/assessments/results` | TRAINER_PM, ADMIN | per-attempt results, batch/track filters |
| GET | `/api/v1/hr/attendance` | authenticated (self-scoped) | staff attendance list |
| POST | `/api/v1/hr/attendance/checkin` | authenticated | staff self / HR-for-other check-in |
| POST | `/api/v1/hr/attendance/checkout` | authenticated | staff check-out |
| PUT | `/api/v1/hr/attendance/mark` | HR_MANAGER, ADMIN | status override (audited) |
| GET | `/api/v1/hr/attendance/summary?month=` | HR_MANAGER, ADMIN | monthly per-employee rollup |
| GET | `/api/v1/clients/projects` | CLIENT (own), BA/ADMIN (all) | client project submissions list |

Role widenings: `QuestionBankController` +DEVELOPER (all 7); `GET /assessments` +optional batchId;
`GET /projects/{id}/challenges` + `GET /challenges/{id}` +STUDENT +TRAINER_PM; `GET /lessons` +
`GET /resources` gained `?track=`; `POST /lessons` / `PUT /lessons/{id}` / resource create+update
gained `track` in the body; `standups` create/update gained `meetingLink`.

Migrations: **V35** `standups.meeting_link`, **V36** `staff_attendance` (new table), **V37**
`video_lessons.track` + `learning_resources.track`. Next = **V38**.

## Standing gotchas from THIS session

- Spring gotcha (the coupon bug): catching a `BusinessException` thrown by a nested `@Transactional`
  proxy method that JOINS your transaction still fails your commit with `UnexpectedRollbackException`.
  Fix = make the nested method non-transactional (if read-only), or `REQUIRES_NEW`, or `noRollbackFor`.
- **Every BA page's `useEffect` load was `Promise.all([...]).then()` with NO `.catch()`** — one
  403/500 from any call -> the page hangs on its spinner forever. `client/*` had the same shape.
  Always `.catch(() => [])` per call + `.finally(() => setLoading(false))`.
- Adding a component to a Java `record` breaks positional `new X(...)` in tests — grep
  `new <Record>(` across `src/test` and fix call sites (did this for `StandupResponse`,
  `CreateVideoLessonRequest`, `UpdateVideoLessonRequest`, `CreateResourceRequest`,
  `UpdateResourceRequest`, `ClientProjectResponse`).
- MapStruct `unmappedTargetPolicy = ERROR` — a new response-record component with no matching
  entity getter fails the build.
- `mvn -o -q surefire:test` hides the "Tests run:" line; exit 0 == BUILD SUCCESS == all matched
  tests passed. Drop `-q` when you need the count.

---

# Part-3 session (2026-09-04) — assessment/bug-challenge redesign + P0 batch

Follows the part-2 sweep. Big user batch (~16 items) + two decisions: **drop the live code runner
entirely** (assessments are MCQ-only now) and **build a real bug-challenge submission flow**
(student pastes rewritten code; dev/trainer review it). Backend + FE both **build clean**
(`mvn -o -q compile/test-compile` exit 0; `npm run build` OK). **NOT committed yet, backend NOT
restarted** — same as always, user runs it on 8080.

## Backend changes (uncommitted)

- **Migration V38** `challenge_submissions` (`src/main/resources/db/migration/V38__challenge_submissions.sql`) —
  challenge_id, student_id, solution_code MEDIUMTEXT, notes, status (`SUBMITTED|ACCEPTED|NEEDS_WORK`),
  reviewer_id, reviewer_feedback, score 0-100, submitted_at, reviewed_at. Next migration = **V39**.
- `project/entity/ChallengeSubmission.java` + `ChallengeSubmissionStatus.java` (NEW).
- `project/repository/ChallengeSubmissionRepository.java` (NEW) — `findMine(studentId, pageable)`,
  `findForChallenge(challengeId, pageable)` (both JOIN FETCH challenge→project).
- `project/ChallengeSubmissionService.java` (NEW) — `submit` / `mySubmissions` / `submissionsForChallenge` / `review`.
  Kept OUT of the already-huge `ProjectService`.
- `project/ChallengeSubmissionController.java` (NEW):
  | Method | Path | Roles |
  |---|---|---|
  | POST | `/api/v1/challenges/{challengeId}/submissions` | STUDENT |
  | GET | `/api/v1/challenges/submissions/me` | STUDENT |
  | GET | `/api/v1/challenges/{challengeId}/submissions` | DEVELOPER, TRAINER_PM, ADMIN |
  | PUT | `/api/v1/challenges/submissions/{id}/review` | DEVELOPER, TRAINER_PM, ADMIN |
- `project/dto/`: `SubmitChallengeRequest`, `ReviewChallengeSubmissionRequest`, `ChallengeSubmissionResponse` (NEW).
- `ErrorCode` +`CHALLENGE_SUBMISSION_NOT_FOUND`.
- **Assessment "completed" state**: `assessment/dto/MyAssessmentAttemptRow.java` (NEW);
  `QuizAttemptRepository.findMineByUser(userId, pageable)` (JOIN FETCH quiz + LEFT JOIN batch);
  `QuizService.myAttempts(callerUserId, pageable)`; `AssessmentController` NEW
  `GET /api/v1/assessments/attempts/me` (STUDENT) — returns newest-first attempts so the student
  list can show Completed/Passed/Failed instead of always "Start Assessment".
- `ClientProjectService.resolveOrCreateClient` self-heal (from part-2, still uncommitted) — carried forward.
- **Seed** (`db/testdata/R__dev_seed_data.sql`): added a richer PUBLISHED project
  `shopsprint-storefront-api` ("ShopSprint — E-Commerce Storefront API") with a multi-line brief +
  **2 bug_challenges** (cart-total coupon bug, checkout oversell-under-concurrency) so the new
  submission flow has real data on a `dev` boot. Seed question banks were ALREADY MCQ/MULTI_SELECT
  only — no CODE items to strip.

## Frontend changes (uncommitted)

- **MCQ-only** `pages/developer/AssessmentBank.jsx` — removed all live-code-runner UI (codeForm,
  addCodeQuestion, parseJsonField, the "Live Code Runner" type option, the code-form JSX branch,
  `Code2` icon). Create-bank modal is title-only now (type hardcoded `"MCQ"`).
  `services/developerService.js` `addQuestionToBank` is MCQ-only. `getAssessmentBanks()` sends
  `?active=true` (deleted banks stay gone). `questionFileParser.js` accepts `.xlsx/.xls` via SheetJS
  (`xlsx` in package.json). Manual add-question + delete-bank now wrapped in try/catch + notify;
  `addMcqQuestion` passes the correct-option **index** (was passing option text → backend 400).
- **Assessment completed state** `pages/student/Assessments.jsx` — `load()` now
  `Promise.all([getAssessments(), getMyAssessmentAttempts()])`; each card shows In progress / Awaiting
  grading / Passed / Not passed + "Assessment Completed — scored X%" and only offers Retake when
  failed with attempts left. `studentService.getMyAssessmentAttempts()` → map keyed by assessmentId.
- **Remove student self check-in** — `pages/student/Attendance.jsx` + `pages/student/Dashboard.jsx`
  standup card: Join-meet + read-only history only, no check-in button. `studentService.checkInToStandup`
  removed. Trainer already marks attendance on `trainer/Standups.jsx` (unchanged).
- **Trainer Resources track filter** `pages/trainer/Resources.jsx` — `<Select>` of `TRACKS` +
  client-side filter (blank track row = "All tracks", always shows).
- **Admin Reports format picker** `pages/admin/Reports.jsx` — dropped the silently-failing
  `exportReport(format)` backend call; Export now opens a radio modal (.csv / .pdf / .txt) and
  generates+downloads client-side (`utils/pdf.js` `downloadPdf` for PDF). Preview modal's Download
  routes through the same picker.
- **Code review full-screen + GitHub** — `components/ui/Modal.jsx` gained `size="full"`
  (`max-w-[96vw] h-[92vh]`, flex column, body flex-1). `pages/trainer/CodeReview.jsx` uses it,
  plus an "Open in GitHub" footer button + a prominent "Open this pull request on GitHub" banner
  (icon `GitPullRequest` — lucide has no `Github` export in this version).
- **Bug-challenge submission** — `pages/student/Projects.jsx` rewritten: per-challenge "Submit fix"
  modal (code textarea + notes), shows Submitted/Accepted/Needs work badge + reviewer feedback,
  resubmit allowed. `pages/developer/BugChallenges.jsx`: "Submissions" button per challenge →
  full-screen modal listing every submission with the code, a feedback box, a /100 score, and
  Accept / Needs work buttons. `studentService`: `getMyBugChallengeSubmissions` / `submitBugChallenge`
  (dropped the localStorage `attemptBugChallenge`). `developerService`: `getChallengeSubmissions` /
  `reviewChallengeSubmission`.
- **Client shows as Student in admin** — `services/adminService.js` `toFeUserRow` now uses
  `primaryFeRole(u.roles)` (priority collapse) instead of "first recognised role else student".
  NOTE: the client-registration → student-dashboard report could NOT be reproduced in code —
  `Register.jsx` → `registerClientAccount` → `/auth/register/client` → PENDING_APPROVAL is correct
  and can't even log in. Needs the user's fresh re-test after a backend restart.

## New endpoints (V38 batch)

| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/api/v1/assessments/attempts/me` | STUDENT | caller's attempts, newest-first → list state |
| POST | `/api/v1/challenges/{id}/submissions` | STUDENT | submit a rewritten fix |
| GET | `/api/v1/challenges/submissions/me` | STUDENT | caller's own submissions |
| GET | `/api/v1/challenges/{id}/submissions` | DEVELOPER, TRAINER_PM, ADMIN | review view |
| PUT | `/api/v1/challenges/submissions/{id}/review` | DEVELOPER, TRAINER_PM, ADMIN | verdict + feedback + score |

## V39 — resource library project filter (added same session, after the batch above)

Follow-up ask: "resource library with track filter but we can also add project as filter also."
- **Migration V39** `learning_resource_project.sql` — `learning_resources.project_id BIGINT UNSIGNED
  NULL` + FK to `projects(id)` + index. NULL = shown everywhere (same semantics as `track`). Next = **V40**.
- `LearningResource` entity / `CreateResourceRequest` / `UpdateResourceRequest` / `LearningResourceResponse`
  all gained `projectId` (plain `Long`, no `@ManyToOne` — same "id only, no navigation" reasoning as `createdBy`).
- `LearningResourceRepository.search(...)` + `ResourceService.list(...)` + `ResourceController GET
  /api/v1/resources` gained optional `projectId` param — filters "this project OR untied" like `track` does.
- `ResourceServiceTest` updated for the new record component + repo arity (added one `any()` to the
  `search(...)` matchers, one `null` to the `Create/UpdateResourceRequest` constructors, one arg to `list(...)`).
- FE: `developerService.getResourceLibrary(track, projectId)` + `create/updateResource` carry `projectId`
  + `toFeResource` exposes it. `developer/Resources.jsx` — Track + Project filter dropdowns + a Project
  column + a Project selector in the Add form. `trainer/Resources.jsx` — Project filter next to the Track
  filter. `student/Resources.jsx` — Project filter (shown only when the student has projects). All three
  use `getProjects()` (`GET /projects` allows TRAINER_PM + STUDENT already).
- Seed: the ShopSprint project now also seeds 2 project-scoped `learning_resources` (OpenAPI contract,
  Postman collection) so the filter has something to show.

## Verification at commit time

- Backend: `mvn -o -q test` — full suite, **exit 0** (all pass).
- Frontend: `npm run build` — **green**.
- Committed: backend `main`, frontend `master` (frontend repo is on `master`, not `main`).

## Still NOT done / open

- **Client-registration → Student dashboard** report (message 17). Traced end to end:
  `registerClientAccount` → `POST /auth/register/client` → `PENDING_APPROVAL` (no email-verify step) →
  admin approve (`ClientApprovalService.approve` flips status only, CLIENT role was set at registration) →
  login → `AuthService.issueTokenPair` reads `userRoleRepository.findRoleCodesByUserId` fresh → JWT
  `roles` claim = `["CLIENT"]` → FE `primaryFeRole` → `/client/dashboard`. **The code path is correct
  and the bug could not be reproduced.** Defensive fix applied anyway: `adminService.toFeUserRow` now
  uses `primaryFeRole(u.roles)` (priority collapse) instead of "first recognised role else student", so
  a client never *displays* as Student in User Management. Needs the user's fresh re-test after a
  backend restart. Likely causes if it recurs: OAuth "Continue with Google" on the login page
  (auto-creates an ACTIVE STUDENT), or registering on the Student tab by mistake, or a stale pre-restart observation.
