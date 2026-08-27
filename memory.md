# Memory — Moriah Skill Hub Backend

Last updated: 2026-08-27 (Features 09–19 complete, reviewed, `mvn verify` green. Session ran autonomously per standing instruction and stopped after feature 19 as directed.)

## What was built

**Features 01–08.** ✅ Complete, committed. (See git log / `context/progress-tracker.md` decision log.)

**Features 09–12.** ✅ Complete, committed, pushed. (See prior memory entry / progress-tracker.md — not repeated here.)

**Feature 13 — Standups and Attendance.** ✅ Complete, `/architect`'d, `/review`'d.

**Feature 14 — Assessment Engine.** ✅ Complete, `/review`'d.

**Feature 15 — Developer Content Portal.** ✅ Complete, `/review`'d.

**Feature 16 — Student Metrics (V10).** ✅ Complete, `/review`'d.

**Feature 17 — PIP Rule Engine (V11).** ✅ Complete, `/architect`'d, `/review`'d.

**Feature 18 — CRM Module (V12).** ✅ Complete, built directly (no `/architect`), `/review`'d. New `crm/` package: `Lead`/`LeadActivity`/`SalesTarget`, dedupe-hash + WhatsApp inbound/outbound, `v_lead_funnel` view.

**Feature 19 — HR Module (V13).** ✅ Complete, built directly, `/review`'d. New `hr/` package: `Employee`/`HrDocument`/`LeaveRequest`/`PayrollRecord` entities, 5 services (Employee, HrDocument, Leave, Payroll, HrLetter) each with a controller. New shared infra: `common/security/CurrentUserUuid` (mirrors `@CurrentUser`, resolves uuid instead of userId), two new `OwnershipGuard` namespaces (`payslips/`, `hr-letters/` — both "owner OR HR_MANAGER/ADMIN"). New cross-package read: `BatchService.hasGraduated(userId)`.

**`mvn verify`: 323 unit tests + 209 integration tests, 0 failures, genuinely green** as of the end of this session.

## Decisions made

- **Standing instruction governing all of features 13–19 this session**: decide autonomously per `context/build-plan.md`/`context/AGENTS.md` without asking the user design questions; run `/review` after each feature; **stop after feature 19** — do not start feature 20 without new explicit instruction. This instruction is now fulfilled; a fresh session needs new direction to proceed past this point (or the user may simply say "continue with feature 20").
- **No git repository exists in this working directory.** `/review`'s normal `git diff`-based Phase 0 can't run — the established workaround (used for features 18 and 19) is 5 parallel Agent-tool review calls, each briefed with the explicit new/modified file list, together covering the usual 8 review angles. Keep using this approach for future `/review` invocations unless a git repo gets initialized.
- **`users.id` must never be exposed in any response DTO** — always the resource's own `uuid`. A resource's own PK (`Employee.id`, `Lead.id`, `Project.id`, etc.) is fine to expose directly; only `users.id` specifically is forbidden. This has been a recurring `/review` finding across CRM and HR — check new DTOs against it proactively.
- **Mandatory 2FA login flow for `ADMIN`/`HR_MANAGER` roles in integration tests** — established in `PipFlowIT`, must be replicated (`completeMandatoryTwoFactorSetupAndLogin` + `currentTotpCode` helpers) in every new `*FlowIT` class whenever it logs in as one of these roles, or every subsequent authenticated call 401s. Easy to forget — bit twice this session (once implicitly needing the fix in `HrFlowIT`).
- **RFC 5321 email local-part length (64 octets)** — a test fixture full name that's too long breaks registration with a 400 that cascades into confusing 401s downstream. Check fixture name lengths in any new `*FlowIT` class.
- **PDF-rendering boilerplate is now duplicated 3× (`InvoiceService`, `PayrollService`, `HrLetterService`)** — flagged via `spawn_task` (chip: "Extract shared PDF-rendering helper", task_id `task_168cd7c1`) rather than fixed inline, since it would touch the already-shipped Invoice feature. Worth picking up if the user wants that chip actioned.

## Problems solved

- **`HrFlowIT` 12/17 failures, all 401s** — root cause was the missing mandatory-2FA-login branch for `HR_MANAGER` (see Decisions above). Fixed by copying `PipFlowIT`'s helpers.
- **Two genuine authorization bugs found by feature 19's `/review`** (most severe findings of the session): an `HR_MANAGER` could approve their own leave request, and could verify their own uploaded KYC document, because the HR-override branch in each service never checked caller-isn't-requester. Fixed with a new `ErrorCode.SELF_DECISION_NOT_ALLOWED` (403), checked in both `LeaveService.decide` and `HrDocumentService.verify`.
- **TOCTOU race in "check exists, then save" services** (`PayrollService.generate`, `EmployeeService.create`) had no `DataIntegrityViolationException` handler — a genuine concurrent race would 500 instead of returning the documented 409. Fixed with a new codebase-wide `GlobalExceptionHandler` handler (not HR-specific — backstops every service using this pattern).
- **Various HR-specific correctness bugs from `/review`**: BigDecimal scale mismatch in payroll gross-amount calc (violated literal "DECIMAL(12,2)" spec line), unbounded negative net pay, N+1 query in payroll generation (fixed via 2 new batch repository methods), payslip S3 key format deviating from architecture.md's `yyyy-MM` template, a dead `@NotBlank` validation (no `@Validated` on the controller — Bean Validation on `@RequestParam` silently does nothing without it, worth remembering for any future `@RequestParam` validation).

## Current state

Features 01–19 all complete. `context/progress-tracker.md` is fully up to date through feature 19, including its full `/review` decision-log entry (15 findings: 13 fixed, 2 no-change-needed with documented reasoning). Migration Ledger shows V13 applied. Nothing is committed to git in this session (no git repo exists in this working directory at all — confirm with the user whether one should be initialized before Feature 20, since `/review`'s normal diff-based workflow and any future `git commit` request depend on it).

One background task chip is pending (not yet actioned): `task_168cd7c1` — extract shared PDF-rendering helper across Invoice/Payroll/HrLetter services.

## Next session starts with

**Feature 20 — Certificate Engine, Graduation and Public Verification (V14).** Build-plan.md flags this one `/architect` first — do not skip that step (it's the first `/architect` flag since feature 17). Check whether the user wants the same autonomous-build-then-review workflow continued, or wants to weigh in on this feature's design given the `/architect` flag specifically calls for a planning conversation before code.

Before starting, worth asking the user: (1) should a git repository be initialized in this working directory now, given `/review`'s workaround has been needed twice already and will keep being needed every feature until one exists; (2) does the standing "build features 13–19, stop after 19" instruction extend to 20+, or does the user want to redirect.

## Open questions

- No git repository exists in this project directory at all. Every `/review` this session has worked around it with parallel-agent file-list reviews instead of `git diff`. Worth resolving before too many more features stack up with no version history.
- `project-overview.md` still says `/api/v1/webhooks/*` includes `github` — `build-plan.md`'s real spec never builds one (noted in a prior memory entry, still open, not blocking).
- The HR module's staff-track letter-eligibility path (`Employee.status == EXITED`) is currently unreachable — no endpoint in feature 19's spec transitions an employee's status. This is expected to resolve itself if/when a future feature adds staff offboarding; not a bug, just worth knowing if someone asks why an EXITED-status experience letter test needs to hand-construct the entity rather than going through a real endpoint.
