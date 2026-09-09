# Moriah Skill Hub — end-to-end flows

The platform is one system with several personas (Student, Trainer/PM, Developer, Business
Analyst, Lead Generator, HR Manager, Client, Admin). No single diagram is readable, so this
is broken into the flows that actually move a person or an artifact through the system. Each
section is independent; the "System map" ties them together.

Diagrams are Mermaid — they render on GitHub and in most Markdown viewers.

---

## 1. System map

```mermaid
flowchart TB
    subgraph acq["Acquisition"]
        LEAD[Lead / CRM pipeline]
        PAY[Checkout + payment webhook]
        SUB[Subscription + entitlements]
    end
    subgraph train["Training"]
        BATCH[Batch allocation]
        SPRINT[Sprints + tasks]
        SUBM[GitHub submissions + code review]
        ATT[Standups + attendance]
        REV[Weekly reviews]
        QUIZ[Assessments / quizzes]
        METRICS[(student_metrics<br/>nightly)]
        PIP[PIP engine]
        GRAD[Graduation + certificate]
    end
    subgraph biz["Client business"]
        CREQ[Client requirement]
        BA[BA authors BRD / SRS / FRS]
        DEVREV[Developer review]
        POOL[Talent pool]
        PLACE[Placement pipeline]
    end
    subgraph staff["People ops"]
        ONBOARD[Staff onboarding + provisioning]
        HRATT[Attendance + leave]
        PAYROLL[Payroll + payslips]
        EXIT[Exit management]
    end
    ADMIN[Admin: users, roles, plans, refunds, reports, audit]

    LEAD --> PAY --> SUB --> BATCH --> SPRINT
    SPRINT --> SUBM --> REV
    SPRINT --> ATT
    SPRINT --> QUIZ
    ATT --> METRICS
    SUBM --> METRICS
    REV --> METRICS
    QUIZ --> METRICS
    METRICS --> PIP
    PIP --> GRAD
    SPRINT --> GRAD
    GRAD --> POOL
    CREQ --> BA --> DEVREV --> SPRINT
    POOL --> PLACE
    ONBOARD --> HRATT --> PAYROLL
    HRATT --> EXIT
    ADMIN -.governs.-> acq
    ADMIN -.governs.-> train
    ADMIN -.governs.-> biz
    ADMIN -.governs.-> staff
```

---

## 2. Lead → enrolment (Lead Generator / CRM)

`crm` module. `LeadStatus` is a forward pipeline; `LOST` is a terminal exit from any stage.

```mermaid
stateDiagram-v2
    [*] --> NEW: inbound form / manual add
    NEW --> CONTACTED: rep reaches out
    CONTACTED --> DEMO_SCHEDULED: demo booked
    DEMO_SCHEDULED --> COUNSELLING_DONE: demo + counselling held
    COUNSELLING_DONE --> PAYMENT_PENDING: lead ready to pay
    PAYMENT_PENDING --> ENROLLED: payment captured (webhook)
    NEW --> LOST
    CONTACTED --> LOST
    DEMO_SCHEDULED --> LOST
    COUNSELLING_DONE --> LOST
    PAYMENT_PENDING --> LOST
    ENROLLED --> [*]
    LOST --> [*]
```

Targets & leaderboard track each rep's conversions. `ENROLLED` means a real subscription now
exists for that person (flow 3).

---

## 3. Sign-up → subscription → batch allocation

```mermaid
flowchart TD
    R[Register email+password<br/>or Google / GitHub OAuth2] --> V[Verify email link]
    V --> TFA{2FA?}
    TFA -->|ADMIN / HR: mandatory| SETUP[TOTP setup on first login]
    TFA -->|others: optional| LOGIN
    SETUP --> LOGIN[Login -> JWT access + refresh]
    LOGIN --> PREVIEW[GET /checkout/preview<br/>plan + coupon]
    PREVIEW --> CHECKOUT[POST /checkout<br/>Razorpay or Stripe order]
    CHECKOUT --> GATEWAY[[Payment gateway]]
    GATEWAY --> WH[Webhook: /payments/razorpay or /stripe<br/>HMAC-verified]
    WH --> ENT[Activate subscription<br/>set entitlement flags]
    ENT --> INV[Generate invoice PDF + email]
    ENT --> TIER{Tier PROJECT_BASED or higher?}
    TIER -->|yes| ALLOC[BatchAllocationService.allocate]
    TIER -->|no STARTER / PROFESSIONAL| DONE[No batch - self-paced content only]
    ALLOC --> MATCH{Matching batch<br/>with free capacity?}
    MATCH -->|yes| ENROL[batch_students row = ACTIVE<br/>notify student BATCH_ALLOCATED]
    MATCH -->|no| QUEUE[pending_batch_allocations<br/>notify PM]
    QUEUE -->|PM creates / frees a batch| ENROL
    ENROL --> TRAIN[Enter training lifecycle - flow 4]

    REFUND[Admin refund] -.-> DEALLOC[deallocate: release seat,<br/>revoke entitlements]
```

Webhooks are also reconciled by a nightly `WebhookReconciliationJob` in case a gateway
callback is missed.

---

## 4. Student training lifecycle

```mermaid
flowchart TD
    START[ACTIVE in a batch] --> SP[PM plans sprints<br/>PLANNED -> ACTIVE -> COMPLETED]
    SP --> TASKS[Tasks per sprint]

    subgraph taskflow["Per task"]
        BL[BACKLOG] -->|student pulls / PM assigns| AS[ASSIGNED]
        AS -->|student starts| IP[IN_PROGRESS]
        IP -->|opens PR| IR[IN_REVIEW]
        IR -->|mentor approves| CP[COMPLETED]
        IR -->|mentor rejects| RJ[REJECTED]
        IR -->|needs rework| IP
    end

    TASKS --> taskflow
    IP --> PR[Submit GitHub PR<br/>from Submissions]
    PR --> VERIFY{PR URL valid +<br/>PR author == users.github_username?}
    VERIFY -->|yes| SUBOK[submission SUBMITTED<br/>task -> IN_REVIEW]
    VERIFY -->|author mismatch| SUBFAIL[PR_AUTHOR_MISMATCH]
    VERIFY -->|GitHub API down| RETRY[saved unverified<br/>SubmissionVerificationRetryJob]
    RETRY --> VERIFY
    SUBOK --> CR[Mentor code review<br/>APPROVED / CHANGES_REQUESTED]

    START --> STAND[Daily standups<br/>attendance marked / auto-ABSENT]
    START --> WR[Weekly review per student<br/>SATISFACTORY / NEEDS_IMPROVEMENT / UNSATISFACTORY]
    START --> ASSESS[Assessments + lesson quizzes]

    STAND --> M[(student_metrics<br/>rebuilt nightly)]
    CR --> M
    WR --> M
    ASSESS --> M
    M --> ANALYTICS[Performance analytics<br/>student + PM dashboards]
    M --> PIPIN[Feeds PIP engine - flow 5]
```

---

## 5. PIP (Performance Improvement Plan)

`pip` module. Nightly `PipEvaluationJob` runs four passes over `PipEvaluationService`.

```mermaid
flowchart TD
    NIGHT[Nightly, after metrics refresh] --> EVAL[evaluate: run 6 rule evaluators<br/>ATTENDANCE_LOW, PROJECT_DELAY, ASSIGNMENT_MISSED,<br/>QUIZ_FAILURE, REVIEW_FAILED, TASK_ABANDONED]
    EVAL -->|threshold crossed, no open PIP| FIRE[Open PIP: TRIGGERED, 15-day window,<br/>1 seeded milestone from ruleCode,<br/>student -> ON_PIP, notify student + PM + HR]
    PM[PM raises PIP by hand<br/>POST /pip] --> FIRE

    FIRE --> PLAN[Recovery plan]
    PLAN --> SEED[Seeded milestone - tied to the trigger metric]
    PLAN --> EXTRA[PM adds bespoke milestones / tasks]

    NIGHT --> RECON[reconcileSeededMilestones]
    RECON -->|trigger metric recovered| AUTOCOMP[Seeded milestone -> COMPLETED by system]
    RECON -->|metric still failing but marked done| REVERT[Seeded milestone -> PENDING<br/>notify PM PIP_MILESTONE_AUTO_REVERTED]

    NIGHT --> AUTORES[autoResolveElapsed - window ended]
    AUTORES --> GATE{All milestones done<br/>AND task completion OK<br/>AND no UNSATISFACTORY review since start<br/>AND trigger rule no longer trips?}
    GATE -->|yes| AUTOCLEAR[Status CLEARED by system<br/>student -> ACTIVE, notify student + PM]
    GATE -->|no| ESCALATE[Stays open<br/>one PIP_WINDOW_ELAPSED nudge to PM]

    NIGHT --> NUDGE[nudgeEarlyRecoveries - within window]
    NUDGE -->|every criterion already met| READY[One PIP_READY_TO_CLEAR nudge to PM]

    PLAN --> REVIEW[PM review outcome]
    READY --> REVIEW
    ESCALATE --> REVIEW
    REVIEW -->|criteria met| CLEARED[CLEARED -> ACTIVE]
    REVIEW -->|no improvement| TERMINATED[TERMINATED -> seat released]
    REVIEW -->|move batches| REASSIGNED[REASSIGNED -> seat released]
```

Clearance hard gates (manual review and auto-clear alike): task-completion percent >= target
AND no `UNSATISFACTORY` weekly review since the PIP started. Attendance / overdue tasks are
advisory for a human decision but the trigger metric must have recovered for the *automatic*
clear.

---

## 6. Graduation → certificate

```mermaid
flowchart TD
    PM[PM opens Graduation Approval] --> CHK{Student ACTIVE?<br/>no open PIP?<br/>no unfinished tasks assigned to them?}
    CHK -->|no| BLOCK[Rejected with the failing reason]
    CHK -->|yes| GRAD[batch_students -> GRADUATED<br/>graduated_at / graduated_by, seat released]
    GRAD --> ISSUE{Every sprint in the batch COMPLETED?<br/>no unfinished tasks?<br/>not already issued?}
    ISSUE -->|no| LATER[Graduated, certificate pending -<br/>Issue certificate button on the same screen]
    LATER --> ISSUE
    ISSUE -->|yes| CERT[Certificate row + PDF<br/>unique number + QR verification code]
    CERT --> PORT[Shows on student Certificates page + portfolio]
    CERT --> POOL[Eligible for the talent pool - flow 8]
    CERT -.admin can.-> REVOKE[Revoke - with reason]
```

---

## 7. Client requirement → BA authoring → developer

`client` module. Multi-party sign-off before a spec is "approved".

```mermaid
flowchart TD
    C[Client submits a project requirement<br/>ClientProjectStatus SUBMITTED] --> ASSIGN[Dev team assigns a BA + developer]
    ASSIGN --> MEET[Pre-project discussions / BA meetings<br/>auto-completed by BaMeetingAutoCompleteJob when past]
    MEET --> AUTHOR[BA authors BRD, then SRS, then FRS<br/>DRAFT -> IN_REVIEW, version per doc type]
    AUTHOR --> SLOTS[Approval slots created:<br/>one each for CLIENT / BUSINESS_ANALYST / DEVELOPER as required]
    SLOTS --> SIGN{All required slots signed off?}
    SIGN -->|a party rejects| REJECTED[Status REJECTED + reason<br/>BA revises -> new version]
    REJECTED --> AUTHOR
    SIGN -->|yes| APPROVED[Status APPROVED]
    APPROVED --> DEVREAD[Developer Client Requirements page<br/>read + mark reviewed]
    APPROVED --> BUILD[Feeds sprint planning - a training batch<br/>builds against the finalized spec]
    BUILD --> DEMO[Client sprint demo reviews<br/>+ project review by BA]
```

---

## 8. Placement pipeline (Talent pool → hired)

`placement` module. `PlacementStage` — driven by different actors at each step; `PLACED` and
`REJECTED` are terminal.

```mermaid
stateDiagram-v2
    [*] --> SHORTLISTED: client shortlists from talent pool
    SHORTLISTED --> TECHNICAL_SCHEDULED: client schedules technical round
    TECHNICAL_SCHEDULED --> TECHNICAL_COMPLETED: round held
    TECHNICAL_COMPLETED --> TECHNICAL_APPROVED: client passes candidate
    TECHNICAL_APPROVED --> HR_SCHEDULED: HR schedules HR round
    HR_SCHEDULED --> HR_COMPLETED: round held
    HR_COMPLETED --> HR_APPROVED: client passes candidate
    HR_APPROVED --> DOCUMENT_VERIFICATION: candidate uploads docs, HR verifies
    DOCUMENT_VERIFICATION --> OFFER_CREATED: HR prepares the offer letter
    OFFER_CREATED --> CLIENT_SIGNED: client e-signs
    CLIENT_SIGNED --> STUDENT_SIGNED: candidate accepts
    STUDENT_SIGNED --> PLACED: HR finalises
    SHORTLISTED --> REJECTED
    TECHNICAL_COMPLETED --> REJECTED
    HR_COMPLETED --> REJECTED
    DOCUMENT_VERIFICATION --> REJECTED
    PLACED --> [*]
    REJECTED --> [*]
```

Each stage change notifies the relevant party (candidate / client / HR) with copy phrased for
that reader. HR's "Interview Outcome Record" keeps the technical + HR round history per
candidate.

---

## 9. HR / staff lifecycle

```mermaid
flowchart TD
    INVITE[Admin/HR invites a staff member] --> ACCEPT[Accept invite -> account]
    ACCEPT --> ONB[Onboarding documents<br/>submit + HR approve/reject]
    ONB --> PROV[Employee provisioning status<br/>-> fully provisioned]
    PROV --> WORK[Active employee]
    WORK --> ATT[Attendance + leave<br/>PAID: sick/casual/earned - UNPAID: LOP]
    ATT --> PAYRUN[Monthly payroll run<br/>gross = base * present+paidLeave / workingDays]
    PAYRUN --> SLIP[Payslip PDF -> My Payslips]
    WORK --> EXIT[Exit management<br/>resignation / termination]
    EXIT --> LETTERS[Experience / relieving letter<br/>needs GRADUATED or clean exit]
```

---

## 10. Nightly scheduled-job chain

Cron times are Asia/Kolkata. Each job records a `job_runs` row and holds a ShedLock.

```mermaid
flowchart LR
    A[AttendanceFinalisationJob<br/>~01:30<br/>close open standups, auto-ABSENT] --> B[MetricsRefreshJob<br/>~01:45<br/>rebuild student_metrics for the cohort]
    B --> C[PipEvaluationJob<br/>~02:00]
    C --> C1[evaluate - open new PIPs]
    C --> C2[reconcileSeededMilestones]
    C --> C3[autoResolveElapsed]
    C --> C4[nudgeEarlyRecoveries]
    D[SubscriptionExpiryJob<br/>~03:00<br/>lapse ended subscriptions] 
    E[QuizAttemptExpiryJob<br/>abandon stale attempts]
    F[InvoiceGenerationJob<br/>render PDF + email async]

    subgraph continuous["Continuous / short interval"]
        G[NotificationWorker - drains the outbox]
        H[NotificationReaperJob - every minute, purges old]
        I[ExpiredTokenReaperJob - verify / reset tokens]
        J[WebhookReconciliationJob - catch missed gateway callbacks]
        K[SubmissionVerificationRetryJob - re-verify PRs saved during a GitHub outage]
        L[BaMeetingAutoCompleteJob - close past client meetings]
    end
```

Dev-only on-demand triggers for the chain: `POST /api/v1/dev/jobs/{job}/run` — see
[testing-scheduled-jobs.md](testing-scheduled-jobs.md).

---

## 11. Auth & session (cross-cutting)

```mermaid
flowchart TD
    L1[POST /auth/login<br/>email + password] --> L2{2FA enabled?}
    L2 -->|no| TOK[Access JWT + refresh token]
    L2 -->|yes| CHAL[challengeToken returned]
    CHAL --> L3[POST /auth/2fa/verify<br/>challengeToken + TOTP code] --> TOK
    OA[OAuth2: Google / GitHub<br/>/auth/oauth2/callback/*] --> TOK
    TOK --> USE[Bearer token on every request<br/>role + permission checks]
    USE --> RF[POST /auth/refresh<br/>rotate before expiry]
    RF --> TOK
```
