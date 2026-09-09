# Memory — Moriah Skill Hub (PIP hardening, graduation guards, multi-PM scoping, docs)

Last updated: 2026-09-09

Repo lives at `C:\Users\ADMIN\Desktop\moriah-skill-hub` (the shell cwd
`C:\Users\ADMIN\Desktop\Moraih Backend` is NOT the repo). Public GitHub:
`lohith1805/moriah-skill-hub`. All work below is committed and pushed to `main`
(HEAD = `0f14fc8`).

## What was built (this session, in order)

1. **PIP: "trust the metric, not the checkbox"** — commits `9482d72`, `b270793` (docs)
   - `PipEvaluationService.reconcileSeededMilestones()` — nightly, the one auto-seeded
     recovery milestone (matched by `ruleCode.milestoneTitle()`) is auto-completed when the
     trigger metric recovered, and **reverted to PENDING** (+ PM notified
     `PIP_MILESTONE_AUTO_REVERTED`) when it was marked done but the metric is still failing.
     PM-added milestones are never touched.
   - `autoResolveElapsed()` now also requires `!triggerRuleStillTrips(record, metrics)` — a
     premature "Verify" at 0% attendance can no longer auto-clear. `REVIEW_FAILED` checks
     `hasUnsatisfactoryReviewSince(startDate)`, not its cumulative evaluator.
   - `nudgeEarlyRecoveries()` — a still-open, within-window PIP that meets every clearance
     criterion nudges the PM once (`PIP_READY_TO_CLEAR`, guarded by new column
     `pip_records.early_clear_nudged_at`, migration **V55**).
   - NULL task-completion (no metrics row / no sprint tasks) is now **non-blocking** in all
     three gates: `PipService.buildProgress`, `PipService.requireClearanceCriteria`,
     `PipEvaluationService.autoResolveElapsed`. Concrete % below target still blocks.
   - New helper `taskCompletionOk(metrics)` + `triggerRuleStillTrips(record, metrics)` in
     `PipEvaluationService`; injects `TaskStatus.UNFINISHED` / uses `pipRuleRepository` +
     `evaluators` list.
   - Dev: `POST /api/v1/dev/pip/{id}/elapse-window` (new `DevPipController`) backdates a PIP
     window; `pip-evaluation` dev job runs all 4 passes.
   - FE: `PipProgressPanel.jsx` task-gate copy "No sprint tasks in this window — not blocking";
     `notificationService.js` renderers for `PIP_MILESTONE_AUTO_REVERTED`, `PIP_READY_TO_CLEAR`.
   - Docs: `docs/testing-scheduled-jobs.md` (dev job runner, PIP demo recipes, test-data reset).

2. **Graduation / certificate blocked by unfinished tasks** — commits `b99e438`, `f6a08a6`
   - `TaskStatus.UNFINISHED = {BACKLOG, ASSIGNED, IN_PROGRESS, IN_REVIEW}` (static Set on the
     enum). `TaskRepository.countUnfinishedForStudentInBatch(studentId, batchId, statuses)`.
   - Guard added to `BatchService.graduate()` (injects `TaskRepository` directly, cycle-avoidance
     like PipService) and `CertificateService.issue()` (injects `TaskRepository`).
   - FE `trainer/Graduation.jsx`: "Issue certificate" button for GRADUATED students without a
     cert (calls existing `POST /api/v1/certificates/issue`); ConfirmDialog copy updated.

3. **Client project requirements table** — commits `3c54618`, `78caa71`
   - Removed the "Status" column from `client/Projects.jsx` "Your Submissions" table (+ unused
     `Badge` import).
   - Added **Client** + **Project** columns to `developer/ClientRequirements.jsx`.
     `RequirementDocumentResponse` / `RequirementDocumentDetailResponse` gained
     `clientName` + `projectTitle` (from `ClientProject.client.companyName` / `.title`).
     `RequirementDocumentRepository`'s two `@EntityGraph`s extended to fetch
     `clientProject.client`. `developerService.js` `toFeRequirementDoc` maps the two fields.

4. **`.env.example` made copy-and-run** — commit `f2d0f8a`
   - Shipped the real dev-only infra values (DB/MySQL-root/MinIO passwords, valid
     JWT_SECRET / TOTP_ENCRYPTION_KEY) that match `docker-compose.yml` and
     `docker/mysql-init/01-users.sql`. Third-party secrets stay `change_me`.
   - `backend/README.md` got a "Troubleshooting startup" table: the `${DB_HOST}` unparsed
     placeholder → no `.env`; SQLState `28000` "Access denied for user 'moriah_migrate'" →
     `.env` password mismatch or stale MySQL volume → `docker compose down -v && up -d`.

5. **Diagrams / docs** — commits `2c06775`, `30c25f1`
   - `docs/project-flows.md` — 11 Mermaid flow diagrams (system map, lead→enrolment,
     signup→subscription→batch alloc, training lifecycle, PIP engine, graduation, client
     intake, placement pipeline, HR lifecycle, nightly jobs, auth). All parse-verified.
   - `docs/system-blueprint.html` — self-contained one-page zoomable diagram (Mermaid + fonts
     from CDN, no build). Also published as an Artifact "Moriah System Blueprint"
     (claude.ai/code/artifact/b81ddd40-cc90-4a51-ba0c-18a112a1e4da) — private; user turns on
     sharing via the artifact Share menu.

6. **Multi-PM data scoping fix** (final task) — commit `0f14fc8`
   - `GET /api/v1/batches`: TRAINER_PM now gets `batchRepository.findByPmId(caller)` (new
     query); STUDENT keeps enrolled-only; ADMIN keeps `findAll`. Role check via
     `SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name())` inside
     `BatchService.list`.
   - `GET /api/v1/pip`: `PipController.list` now passes `@CurrentUser`; `PipService.list` scopes
     to the caller's owned batches for TRAINER_PM (new `pmId` param on
     `PipRecordRepository.search` → `AND (:pmId IS NULL OR p.batch.pm.id = :pmId)`);
     HR_MANAGER / ADMIN pass `pmId = null` (see all).
   - Tests added: `BatchServiceTest.list_admin_usesTheFullList`,
     `list_trainerPm_scopedToBatchesTheyOwn`; `PipServiceTest.list_trainerPm_scopesSearch...`,
     `list_hrManager_seesEveryBatch` (added a local `authenticateAs` helper + `@AfterEach`).

## Decisions made

- PIP clearance HARD gates stay: task-completion % ≥ target AND no UNSATISFACTORY weekly
  review since PIP start. Attendance/overdue are advisory for a *human* decision, but for the
  *automatic* clear the trigger rule must also no longer trip.
- Auto-clear stays strictly post-window (user's earlier choice); early recovery only *nudges*
  the PM, never auto-clears mid-window.
- Graduation "unfinished" excludes REJECTED (terminal) and COMPLETED. Unassigned BACKLOG tasks
  block nobody (count is scoped to `assignedTo = student`).
- Multi-PM scoping is enforced **backend-side**, not FE — the FE `scope:"mine"` `.filter()`
  couldn't work (data crossed the wire; Dashboard + PIP screens didn't apply it).
- The Batches page "All batches" tab now shows 0 for a non-owning PM. That's the intended
  trade-off; an org-wide read view would be a separate deliberate feature.

## Problems solved

- Mermaid in a plain HTML file → "KaTeX doesn't work in quirks mode" + `translate(undefined,
  NaN)`: needs `<!doctype html>`. Also `<pre class="mermaid">` with `display:none` breaks
  htmlLabel measurement — render with `opacity:0` or via `mermaid.render()` into a container.
  A 33-node flowchart with cross-subgraph edges throws harmless "Could not find a suitable
  point" console warnings but still renders.
- `cp .env.example .env` never worked before — `change_me` DB passwords vs the fixed literals
  the compose stack creates. Fixed in `.env.example` (see item 4).
- Frontend `node_modules` lost `vite` after a machine restart mid-session → `npm install`
  again in `frontend/`.
- Browser `form_input` + ref-click can silently fail to submit React forms. Reliable: press
  Return in the password field, or inject the session into localStorage
  (`msh_access_token`, `msh_refresh_token`, `msh_token_expiry`, `msh_user` with
  `role:"trainer"`, `roles:["TRAINER_PM"]`).
- `docker exec ... mysql -uroot` uses password `root_dev_only` (from `.env`), not `root`.

## Current state

- All 6 workstreams committed + pushed to `main` (HEAD `0f14fc8`).
- Full **unit** test suite green (`mvn -o test`). Multi-PM scoping verified live: 2nd PM
  "Map" (user 117, owns nothing) sees My Batches 0 / Total Students 0 / Active PIP 0 on the
  trainer dashboard; "All batches (0)" on Batches; "No PIP cases" on PIP Management. Priya
  (owns all 5) and ADMIN still see everything.
- Backend + Vite servers were up at end of session (backend on :8080, FE on :5173, Docker
  stack running).
- **Integration tests (`*IT`, Testcontainers) were NOT run this session** — Docker was down
  for part of it. Worth running `mvn -o verify` once with Docker up.

## Next session starts with

If continuing the scoping work: lock down the remaining PM-facing by-`batchId` reads that
still have no ownership check — `GET /api/v1/sprints?batchId=`, `/api/v1/analytics/*`,
`/api/v1/standups`, `/api/v1/assignment-windows`, `/api/v1/dev/requirement-documents`. Pattern:
add `@CurrentUser` + a `BatchService` visibility helper (admin OK / PM must own / student must
be enrolled / else 404). User was offered this and hasn't said yes yet.

Otherwise: run `mvn -o verify` (Docker up) to confirm the `*IT` suite (esp. `PipFlowIT`,
`BatchFlowIT`) still passes with the scoping + graduation-guard + PIP changes.

## Open questions

- Should PMs have an org-wide read-only batch view (separate endpoint), or is "own batches
  only" final?
- The requirement-documents list (`/ba/documents`, `/dev/requirement-documents`) returns all
  docs regardless of the caller's assigned project — same class of leak as batches/PIP, not
  yet fixed. (`ClientProjectService.list` IS already scoped by `assignedBaId` — that's the
  model.)

## Env / setup facts

- `mvn` = Apache Maven 4.0.0-rc-5 at `C:\Program Files\Apache\Maven\apache-maven-4.0.0-rc-5\bin\mvn`
  (no wrapper). Unit tests: `MAVEN_OPTS="-Xmx1200m" mvn -o test`. Backend:
  `nohup env MAVEN_OPTS="-Xmx1g" mvn -o spring-boot:run` from `backend/`. Startup 35–180s.
- Frontend: `npm run dev` in `frontend/` (Vite 8, :5173). Prod build `npx vite build`
  (`rm -f dist.zip` first).
- Docker Desktop must be running for MySQL/Redis/MinIO. Full reset:
  `docker compose down -v && docker compose up -d` (from repo root), then start the app —
  Flyway replays V1..V55 + `R__dev_seed_data.sql` (dev-profile repeatable seed).
- Dev DB (throwaway, now also in `.env.example`): `moriah_app` / `app_dev_only`,
  `moriah_migrate` / `migrate_dev_only`, root `root_dev_only`. MinIO `skillhub_minio` /
  `minio_dev_only`.
- Seed users all password `Password123!`: `admin@ pm@ dev@ sales@ hr@ ba@ client@ student1..3@
  moriah.test`. `pm@` = Priya PM (user 2, owns all 5 batches), no 2FA. `admin@` / `hr@` need
  2FA — TOTP helper script at the session scratchpad (`totp.py <secret>`); the throwaway dev
  2FA secrets are local-only, not in the repo.
- 2nd PM for scoping tests: user 117 "Map" (`jadib73639@bowlfuel.com`) — its password was
  reset to `Password123!` in the dev DB this session (dev-data mutation, gone on
  `docker compose down -v`).
- Constraints: PUBLIC repo — never commit secrets, stage only the specific touched files
  (never `git add -A`). Never `mvn compile`/`test` while a backend JVM is live. Commit footer
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR footer
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## Dev-data mutations lingering (all reset by `docker compose down -v`)

Sprint 1 of batch FS-2026-01 backdated to 2026-09-01→09-14 and set COMPLETED; 3 completion
certificates issued (Sam / Tara / Uday, users 8/9/10, all GRADUATED); Tara's
`student_metrics.attendance_percent` forced to 100; task 2 "Implement POST /todos" deleted;
several PIPs cleared/reassigned/nudged from earlier PIP testing; user 117 password reset.
