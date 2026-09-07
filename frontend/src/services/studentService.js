import { mockRequest, apiClient } from "./apiClient";
import { TASKS, BATCHES } from "./mockData";
import { SUBSCRIPTION_PLANS, PLAN_CODE_TO_FE } from "../utils/constants";
import { getPersistedUser } from "./authService";
import { evaluateStudentAutoPip } from "./pipEngine";
import { trySyncGraduateToTalentPool } from "./hrService";

// ---------------------------------------------------------------------------
// WIRED to the backend (this session): video lessons + quiz (/api/v1/lessons,
// B1.4), certificates (/api/v1/certificates/me), PIP status (/api/v1/pip/me),
// plan catalogue (/api/v1/plans), current subscription (/api/v1/subscriptions/me),
// scheduled interviews (/api/v1/interviews/me, B1.8).
// STILL MOCK: sprint board / tasks / submissions (no "my tasks across sprints"
// endpoint — needs batch->sprint->task fan-out), assessments + bug challenges
// (dev in-browser runner), resume upload, subscribeToPlan (no checkout endpoint
// on the subscription controller yet).
// ---------------------------------------------------------------------------

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

// crude "watched" heuristic for the still-fake video player: any progress row
// whose status is IN_PROGRESS/COMPLETED counts as watched.
function ytId(url) {
  if (!url) return null;
  const m = String(url).match(/(?:v=|youtu\.be\/|embed\/)([A-Za-z0-9_-]{6,})/);
  return m ? m[1] : null;
}

// Converts an uploaded File to a base64 data URL so the resume's actual
// content survives a reload and can be opened by HR/Client later — same
// trick clientService.js and ba/Documents.jsx use for their own uploads.
function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// "Not Uploaded" until a resume with real file content exists; "Uploaded"
// the first time it's attached; "Updated" once the student replaces an
// already-uploaded resume with a newer file.
export function getResumeStatus(resume) {
  if (!resume || !resume.fileData) return "Not Uploaded";
  if (resume.updatedAt && resume.uploadedAt && resume.updatedAt !== resume.uploadedAt) return "Updated";
  return "Uploaded";
}

// POST /api/v1/users/me/resume — multipart, field `file` (PDF, magic-byte
// checked server-side). `user` is accepted for call-site compatibility but the
// backend keys off the caller's token. Returns { resumeMeta } describing the
// upload so the page can update its status chip.
export async function saveResumeFile(user, file) {
  if (!file) throw new Error("No file selected.");
  await apiClient.requestMultipart("/users/me/resume", { method: "POST", files: { file } });
  const now = new Date().toISOString();
  const resumeMeta = { name: file.name, size: file.size, uploadedAt: now, updatedAt: now };
  return { resumeMeta };
}

// GET /api/v1/users/me/resume -> a presigned download URL (or null).
export async function getMyResumeUrl() {
  try {
    const res = await apiClient.get("/users/me/resume");
    return res?.downloadUrl || res?.url || (typeof res === "string" ? res : null);
  } catch (e) {
    if (e?.status === 404) return null;
    throw e;
  }
}

function getStoredQuizAttempts() {
  try {
    const raw = localStorage.getItem("msh_assessment_attempts");
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

// Runs the auto-PIP rule engine for the CURRENTLY LOGGED-IN student against
// their own real tasks/quiz attempts. Called whenever the student's own
// dashboard/PIP page is opened, so a threshold crossed since their last
// visit gets flagged immediately — no trainer needs to look first.
function runAutoPipCheckForCurrentUser(user) {
  if (!user) return;
  const name = user.name || "Student";
  evaluateStudentAutoPip({
    name,
    batch: user.batch || "",
    tasks: TASKS,
    quizAttempts: getStoredQuizAttempts(),
  });
}

const TASK_STATUS_TO_FE = {
  BACKLOG: "Backlog",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In Progress",
  IN_REVIEW: "Review",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};
const TASK_TYPE_TO_FE = { STORY: "User Story", BUGFIX: "Bug", ASSIGNMENT: "Task", DAILY: "Daily" };

function toFeStudentTask(t) {
  return {
    id: t.id,
    sprintId: t.sprintId,
    title: t.title,
    description: t.description || "",
    type: TASK_TYPE_TO_FE[t.taskType] || "Task",
    epic: "General",
    userStory: "",
    acceptanceCriteria: "",
    points: t.storyPoints ?? 0,
    due: t.dueAt ? t.dueAt.slice(0, 10) : "",
    dueAt: t.dueAt || null,
    status: TASK_STATUS_TO_FE[t.status] || t.status,
    backendStatus: t.status,
    assignee: t.assignedToName || "",
    assigneeUuid: t.assignedToUuid || null,
    githubPr: null,
    videoUrl: null,
    completedCriteria: [],
    inlineComments: [],
  };
}

// The caller's enrolled batches (a STUDENT token is scoped to these).
async function myBatchIds() {
  const res = await apiClient.get("/batches", { size: 100 });
  return asRows(res).map((b) => b.id);
}

// GET /api/v1/batches (student-scoped) → the caller's current batch, or null if
// they haven't been placed into one yet. `/users/me` does NOT carry batch
// enrolment, so the dashboard reads it from here.
export async function getMyBatch() {
  const res = await apiClient.get("/batches", { size: 100 });
  const rows = asRows(res);
  const active = rows.find((b) => b.status === "ACTIVE") || rows[0];
  return active
    ? { id: active.id, name: active.name, trackCode: active.trackCode, status: active.status }
    : null;
}

// --- Standups + attendance --------------------------------------------------

const ATT_STATUS_TO_FE = { PRESENT: "Present", LATE: "Late", ABSENT: "Absent", EXCUSED: "Excused" };

// GET /api/v1/standups?batchId=&date= for the caller's own batch (STUDENT is
// allowed to read their batch's schedule). `date` is YYYY-MM-DD. [] if not in a
// batch yet.
export async function getMyStandups(date) {
  const batch = await getMyBatch().catch(() => null);
  if (!batch) return [];
  const params = { batchId: batch.id, size: 20 };
  if (date) params.date = date;
  const res = await apiClient.get("/standups", params);
  return asRows(res)
    .map((s) => ({
      id: s.id,
      batchId: s.batchId,
      scheduledAt: s.scheduledAt,
      meetingLink: s.meetingLink || null,
      notes: s.notes || "",
      lateCutoffMinutes: s.lateCutoffMinutes ?? 15,
      status: s.status,
      finalisedAt: s.finalisedAt || null,
    }))
    .sort((a, b) => new Date(b.scheduledAt) - new Date(a.scheduledAt));
}

// Students no longer self check-in — the trainer records standup attendance at
// the start of the day from the Trainer → Standups roster. Students only join
// the meeting link and view their attendance history (getMyAttendance below).

// GET /api/v1/attendance/me — the caller's attendance history, newest first.
export async function getMyAttendance() {
  const res = await apiClient.get("/attendance/me", { size: 50 });
  return asRows(res).map((a) => ({
    id: a.id,
    standupId: a.standupId,
    scheduledAt: a.standupScheduledAt,
    status: ATT_STATUS_TO_FE[a.status] || a.status,
    checkedInAt: a.checkedInAt || null,
    blockerNotes: a.blockerNotes || "",
    autoMarked: !!a.autoMarked,
    markedByPm: !!a.markedByUuid,
  }));
}

// GET /api/v1/tasks?sprintId= for every sprint in the caller's batches, kept
// to tasks assigned to the caller OR still in the BACKLOG (pullable).
export async function getMyTasks() {
  const myUuid = getPersistedUser()?.uuid;
  const sprints = await getMySprints();
  const perSprint = await Promise.all(
    sprints.map((s) =>
      apiClient.get("/tasks", { sprintId: s.id, size: 100 }).then(asRows).catch(() => [])
    )
  );
  return perSprint
    .flat()
    .filter((t) => t.status === "BACKLOG" || (myUuid && t.assignedToUuid === myUuid))
    .map(toFeStudentTask);
}

// A student's account only stores their batch's NAME (see authService's
// registerStudent), while Trainer's Sprint Planning links a sprint to a
// batch by ID — so look the batch up by name first to resolve its ID, then
// pull every sprint scheduled for it. This is how "trainer creates a sprint
// for a batch" becomes visible on the Student Sprint Board.
const SPRINT_STATUS_TO_FE = { PLANNED: "Planned", ACTIVE: "Active", COMPLETED: "Completed" };

// GET /api/v1/sprints?batchId= for each enrolled batch, newest first.
export async function getMySprints() {
  const batchIds = await myBatchIds();
  const perBatch = await Promise.all(
    batchIds.map((id) =>
      apiClient.get("/sprints", { batchId: id, size: 100 }).then(asRows).catch(() => [])
    )
  );
  return perBatch
    .flat()
    .map((s) => ({
      id: s.id,
      batchId: s.batchId,
      number: s.sprintNumber,
      goal: s.goal,
      startDate: s.startDate || null,
      endDate: s.endDate || null,
      status: SPRINT_STATUS_TO_FE[s.status] || s.status,
    }))
    .sort((a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0));
}

function readLocalJSON(key) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

const DIFFICULTY_TO_FE = { BEGINNER: "Beginner", INTERMEDIATE: "Intermediate", ADVANCED: "Advanced" };

// GET /api/v1/projects — the practice-project catalogue. A STUDENT only ever
// gets PUBLISHED projects back from this endpoint.
export async function getMyProjects() {
  const res = await apiClient.get("/projects", { size: 100 }).catch(() => null);
  return asRows(res)
    .filter((p) => (p.status || "PUBLISHED") === "PUBLISHED")
    .map((p) => ({
      id: p.id,
      title: p.title,
      description: p.description || "",
      stack: p.techStack || [],
      difficulty: DIFFICULTY_TO_FE[p.difficulty] || p.difficulty || "",
      domain: p.domain || "",
      starterRepo: p.starterRepoUrl || "",
      challengeCount: (p.challenges || []).length,
    }));
}

function toFeBugChallenge(c, projectTitle) {
  return {
    id: c.id,
    project: projectTitle,
    title: c.title,
    expectedBehaviour: c.expectedBehaviour || "",
    brokenCodeUrl: c.brokenCodeUrl || null,
    testScriptUrl: c.testScriptUrl || null,
    difficulty: DIFFICULTY_TO_FE[c.difficulty] || c.difficulty || "",
  };
}

const SUB_STATUS_TO_FE = { SUBMITTED: "Submitted", ACCEPTED: "Accepted", NEEDS_WORK: "Needs work" };

function toFeChallengeSubmission(s) {
  return {
    id: s.id,
    challengeId: s.challengeId,
    challengeTitle: s.challengeTitle || "",
    projectTitle: s.projectTitle || "",
    solutionCode: s.solutionCode || "",
    notes: s.notes || "",
    status: s.status,
    statusLabel: SUB_STATUS_TO_FE[s.status] || s.status,
    reviewerFeedback: s.reviewerFeedback || "",
    score: s.score ?? null,
    submittedAt: s.submittedAt || null,
    reviewedAt: s.reviewedAt || null,
  };
}

// GET /api/v1/projects/{id}/challenges for every published project. Each row's
// brokenCode / testScript are fresh presigned download URLs.
export async function getMyBugChallenges() {
  const projects = await getMyProjects();
  const perProject = await Promise.all(
    projects.map((p) =>
      apiClient.get(`/projects/${p.id}/challenges`)
        .then((list) => (Array.isArray(list) ? list : list?.content || []).map((c) => toFeBugChallenge(c, p.title)))
        .catch(() => [])
    )
  );
  return perProject.flat();
}

// GET /api/v1/challenges/{id}.
export async function getBugChallengeDetails(challengeId) {
  const c = await apiClient.get(`/challenges/${challengeId}`).catch(() => null);
  return c ? toFeBugChallenge(c, "") : null;
}

// GET /api/v1/challenges/submissions/me — the caller's own bug-challenge
// submissions, newest first. The Projects page merges these onto each challenge
// so it can show "Submitted / Accepted / Needs work" and the reviewer's notes.
export async function getMyBugChallengeSubmissions() {
  const rows = await apiClient.get("/challenges/submissions/me", { size: 200 }).then(asRows).catch(() => []);
  return rows.map(toFeChallengeSubmission);
}

// POST /api/v1/challenges/{id}/submissions — submit the rewritten fix.
// payload: { solutionCode, notes? }
export async function submitBugChallenge(challengeId, { solutionCode, notes }) {
  const s = await apiClient.post(`/challenges/${challengeId}/submissions`, {
    solutionCode,
    notes: notes || null,
  });
  return toFeChallengeSubmission(s);
}

// A STUDENT can only self-assign a BACKLOG task (POST /tasks/{id}/pull ->
// IN_PROGRESS). Every other board transition is driven by the PM or the
// review flow, so those moves are no-ops here (the board re-syncs on reload).
export async function updateTaskStatus(taskId, status) {
  if (status === "In Progress" || status === "Assigned") {
    try {
      await apiClient.post(`/tasks/${taskId}/pull`, {});
      return { ok: true, pulled: true };
    } catch (e) {
      // 409 = not BACKLOG / already pulled / blocked by an open PROJECT_DELAY PIP
      return { ok: false, error: e?.message || "Could not pull this task." };
    }
  }
  return { ok: false, unsupported: true };
}

export async function getPerformanceSummary() {
  const user = getPersistedUser();
  const baseSummary = {
    attendance: 0,
    taskCompletion: 0,
    quizAverage: 0,
    sprintVelocity: 0,
    pipActive: false,
  };
  if (!user) return mockRequest(baseSummary);
  runAutoPipCheckForCurrentUser(user);

  const name = user.name || "Student";
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {}

  const myTasks = allTasks.filter((t) => t.assignee === name);

  const completed = myTasks.filter((t) => t.status === "Completed").length;
  const total = myTasks.length;
  const taskCompletion = total > 0 ? Math.round((completed / total) * 100) : 0;
  const sprintVelocity = myTasks.filter((t) => t.status === "Completed").reduce((sum, t) => sum + (t.points || 0), 0);

  let attendance = 0;
  try {
    const logsRaw = localStorage.getItem("msh_attendance_logs");
    if (logsRaw) {
      const logs = JSON.parse(logsRaw);
      const studentLogs = logs.filter((l) => l.studentName === name);
      if (studentLogs.length > 0) {
        const presentCount = studentLogs.filter((l) => l.status === "Present" || l.status === "Clocked In").length;
        attendance = Math.round((presentCount / studentLogs.length) * 100);
      }
    }
  } catch (e) {}

  return mockRequest({
    ...baseSummary,
    attendance,
    taskCompletion,
    sprintVelocity,
  });
}

// GET /api/v1/pip/me — the caller's own PIP record (or null if none).
export async function getMyPipStatus() {
  let rec;
  try {
    rec = await apiClient.get("/pip/me");
  } catch (e) {
    if (e?.status === 404) return null;
    throw e;
  }
  if (!rec) return null;
  return {
    id: rec.id,
    reason: rec.triggerReason || (rec.ruleCode || "").replace(/_/g, " "),
    ruleCode: rec.ruleCode,
    severity: rec.severity,
    status: rec.status,
    triggeredOn: rec.triggeredAt ? rec.triggeredAt.slice(0, 10) : "",
    startDate: rec.startDate || null,
    endDate: rec.endDate || null,
    blocksTaskPull: !!rec.blocksTaskPull,
    reviewNotes: rec.reviewNotes || "",
    milestones: (rec.milestones || []).map((m) => ({
      id: m.id,
      label: m.title,
      description: m.description || "",
      dueDate: m.dueDate || null,
      done: m.status === "COMPLETED" || m.status === "VERIFIED",
      status: m.status,
    })),
  };
}

const FE_PLAN_META = Object.fromEntries(SUBSCRIPTION_PLANS.map((p) => [p.code, p]));

function toFePlan(p) {
  const feCode = PLAN_CODE_TO_FE[p.code] || p.code.toLowerCase();
  const meta = FE_PLAN_META[feCode] || {};
  return {
    code: feCode,
    backendCode: p.code,
    name: p.name,
    model: meta.model || "",
    price: p.priceInr != null ? Number(p.priceInr) : meta.price ?? 0,
    durationDays: p.durationDays ?? null,
    tierRank: p.tierRank ?? null,
    features:
      meta.features ||
      [
        p.allowsBatch && "Batch enrolment",
        p.allowsSprints && "Sprint board & reviews",
        p.allowsPip && "Performance Improvement Plans",
        p.mentorSupport && "Mentor support",
        p.allowsInternshipLetter && "Internship letter",
        p.allowsClientProject && "Client projects",
      ].filter(Boolean),
  };
}

export async function getPlans() {
  try {
    const res = await apiClient.get("/plans");
    const rows = asRows(res);
    return rows.length ? rows.map(toFePlan) : SUBSCRIPTION_PLANS;
  } catch {
    return SUBSCRIPTION_PLANS;
  }
}

// GET /api/v1/subscriptions/me -> the caller's active subscription (or null).
export async function getMySubscription() {
  try {
    const s = await apiClient.get("/subscriptions/me");
    if (!s) return null;
    return {
      planCode: PLAN_CODE_TO_FE[s.planCode] || (s.planCode || "").toLowerCase(),
      backendPlanCode: s.planCode,
      planName: s.planName,
      startDate: s.startDate || null,
      endDate: s.endDate || null,
      status: s.status,
      autoRenew: !!s.autoRenew,
    };
  } catch (e) {
    if (e?.status === 404) return null;
    throw e;
  }
}

// POST /api/v1/subscriptions/checkout — creates a gateway order/session. It
// NEVER activates the subscription directly; the (signed) gateway webhook
// does that after the payment is captured. Returns what the FE needs to open
// the Razorpay widget or redirect to Stripe. Without real test-mode keys the
// backend responds 502 PAYMENT_GATEWAY_ERROR.
// `feGateway` is "Razorpay" | "Stripe"; `feBackendCode` is a backend plan code
// (STARTER / PROFESSIONAL / …) — the caller resolves it from the FE plan.
// POST /api/v1/subscriptions/checkout/preview — "what would I pay for this plan
// with this coupon?" Creates no order and reserves no coupon capacity. An invalid
// coupon comes back as { couponApplied: false, couponMessage } rather than an error.
export async function previewCheckout(backendPlanCode, couponCode) {
  const res = await apiClient.post("/subscriptions/checkout/preview", {
    planCode: backendPlanCode,
    couponCode: couponCode || null,
  });
  return {
    originalAmount: Number(res.originalAmount ?? 0),
    payableAmount: Number(res.payableAmount ?? res.originalAmount ?? 0),
    currency: res.currency || "INR",
    couponApplied: !!res.couponApplied,
    couponMessage: res.couponMessage || "",
  };
}

export async function subscribeToPlan(backendPlanCode, feGateway = "Razorpay", { trackCode = "FULL_STACK", couponCode = null } = {}) {
  const res = await apiClient.post("/subscriptions/checkout", {
    planCode: backendPlanCode,
    gateway: (feGateway || "Razorpay").toUpperCase() === "STRIPE" ? "STRIPE" : "RAZORPAY",
    couponCode: couponCode || null,
    trackCode,
  });
  return {
    gateway: res.gateway,
    paymentId: res.paymentId,
    amount: res.amount != null ? Number(res.amount) : null,
    currency: res.currency || "INR",
    razorpayOrderId: res.razorpayOrderId || null,
    razorpayKeyId: res.razorpayKeyId || null,
    stripeCheckoutUrl: res.stripeCheckoutUrl || null,
  };
}

// GET /api/v1/subscriptions/me/invoices — the caller's billing history. Each row
// is a captured/refunded payment; `invoiceStatus` is "PROCESSING" until the async
// invoice job has rendered the PDF, then "ISSUED" with a short-lived `pdfUrl`.
const INVOICE_DISPLAY_STATUS = (r) => {
  if (r.paymentStatus === "REFUNDED") return "REFUNDED";
  if (r.invoiceStatus === "ISSUED") return "CAPTURED";
  if (r.invoiceStatus === "FAILED") return "INVOICE FAILED";
  return "PROCESSING";
};

export async function getMyInvoices() {
  const res = await apiClient.get("/subscriptions/me/invoices");
  return asRows(res).map((r) => ({
    id: r.invoiceNumber || r.reference,
    reference: r.reference,
    plan: r.planName || r.planCode || "—",
    amount: r.amount != null ? Number(r.amount) : 0,
    currency: r.currency || "INR",
    date: r.paidAt ? String(r.paidAt).slice(0, 10) : "—",
    status: INVOICE_DISPLAY_STATUS(r),
    pdfUrl: r.pdfUrl || null,
  }));
}

// GET /api/v1/interviews/me — mock/technical/HR/placement interviews a PM
// scheduled for the caller (B1.8). No dedicated page yet; here for reuse.
export async function getMyInterviews() {
  const res = await apiClient.get("/interviews/me");
  return asRows(res).map((i) => ({
    id: i.id,
    type: i.interviewType,
    scheduledAt: i.scheduledAt,
    durationMinutes: i.durationMinutes ?? null,
    mode: i.mode,
    location: i.location || "",
    interviewerName: i.interviewerName || "",
    meetingLink: i.meetingLink || "",
    status: i.status,
    feedback: i.feedback || "",
    rating: i.rating ?? null,
  }));
}

// GET /api/v1/certificates/me — certificates issued to the caller.
export async function getCertificates() {
  const res = await apiClient.get("/certificates/me");
  return asRows(res).map((c) => ({
    id: c.id,
    title: c.certificateType === "EXCELLENCE" ? "Certificate of Excellence" : "Certificate of Completion",
    certificateNumber: c.certificateNumber,
    batchName: c.batchName || "",
    issuedOn: c.issuedAt ? c.issuedAt.slice(0, 10) : "",
    verifyCode: c.verificationCode,
    downloadUrl: c.downloadUrl || null,
    status: c.revokedAt ? "Revoked" : "Issued",
    revokeReason: c.revokeReason || null,
  }));
}

// POST /api/v1/submissions — the task moves to IN_REVIEW server-side. GitHub
// PR verification is best-effort on the backend (needs a real GITHUB_API_TOKEN).
export async function submitGithubPR(taskId, prUrl, videoUrl = "", notes = "") {
  const s = await apiClient.post("/submissions", {
    taskId: Number(taskId),
    prUrl: prUrl || "",
    videoUrl: videoUrl || "",
    notes: notes || "",
  });
  return {
    id: s.id,
    taskId: s.taskId,
    prUrl: s.prUrl,
    videoUrl: s.videoUrl,
    status: s.status,
    submittedAt: s.submittedAt,
    verifiedAt: s.verifiedAt || null,
  };
}

// GET /api/v1/submissions?taskId= — the caller's submissions for one task.
export async function getSubmissionsForTask(taskId) {
  const res = await apiClient.get("/submissions", { taskId });
  return asRows(res).map((s) => ({
    id: s.id,
    taskId: s.taskId,
    attemptNumber: s.attemptNumber,
    prUrl: s.prUrl,
    videoUrl: s.videoUrl,
    prState: s.prState,
    status: s.status,
    submittedAt: s.submittedAt,
    verifiedAt: s.verifiedAt || null,
  }));
}



// --- Assessments (WIRED, server-graded) -----------------------------
// GET /api/v1/assessments?batchId=, POST /{id}/attempts (start/resume),
// GET /assessments/attempts/{id}, POST /assessments/attempts/{id}/submit.
// The old in-browser CODE runner is gone — CODE answers are submitted as
// text and land PENDING_MANUAL_GRADING; MCQ / MULTI_SELECT auto-grade.

const Q_TYPE_TO_FE = { MCQ: "MCQ", MULTI_SELECT: "MULTI_SELECT", CODE: "CODE" };

function toFeQuestion(q) {
  return {
    id: q.questionId,
    text: q.questionText,
    type: Q_TYPE_TO_FE[q.questionType] || q.questionType,
    options: q.options || [],
    marks: q.marks ?? null,
    // present only on a graded (terminal) attempt:
    givenAnswerIndices: q.givenAnswerIndices || [],
    givenCodeAnswer: q.givenCodeAnswer || "",
    isCorrect: q.isCorrect ?? null,
    marksAwarded: q.marksAwarded != null ? Number(q.marksAwarded) : null,
    explanation: q.explanation || "",
  };
}

function toFeAttempt(a) {
  return {
    attemptId: a.id,
    assessmentId: a.quizId,
    title: a.quizTitle,
    attemptNumber: a.attemptNumber,
    status: a.status, // IN_PROGRESS | SUBMITTED | EXPIRED | PENDING_MANUAL_GRADING
    durationMinutes: a.durationMinutes ?? null,
    startedAt: a.startedAt || null,
    submittedAt: a.submittedAt || null,
    questions: (a.questions || []).map(toFeQuestion),
    autoGradedMarks: a.autoGradedMarks != null ? Number(a.autoGradedMarks) : null,
    autoGradableMarks: a.autoGradableMarks != null ? Number(a.autoGradableMarks) : null,
    percentage: a.percentage != null ? Number(a.percentage) : null,
    passed: a.passed ?? null,
  };
}

export async function getAssessments() {
  const batchIds = await myBatchIds();
  if (!batchIds.length) return [];
  const perBatch = await Promise.all(
    batchIds.map((id) =>
      apiClient.get("/assessments", { batchId: id, size: 100 }).then(asRows).catch(() => [])
    )
  );
  return perBatch
    .flat()
    .filter((a) => a.active)
    .map((a) => ({
      id: a.id,
      title: a.title,
      type: "Assessment",
      duration: a.durationMinutes ? `${a.durationMinutes} min` : "",
      durationMinutes: a.durationMinutes ?? null,
      passPercentage: a.passPercentage ?? 60,
      maxAttempts: a.maxAttempts ?? null,
      score: null,
      status: "Available",
    }));
}

// POST /api/v1/assessments/{id}/attempts — start or resume; returns the
// questions WITHOUT any answer key.
export async function startAssessmentAttempt(assessmentId) {
  return toFeAttempt(await apiClient.post(`/assessments/${assessmentId}/attempts`, {}));
}

// GET /api/v1/assessments/attempts/{id}
export async function getAssessmentAttempt(attemptId) {
  return toFeAttempt(await apiClient.get(`/assessments/attempts/${attemptId}`));
}

// GET /api/v1/assessments/attempts/me — the caller's own attempts across every
// assessment. Used to show each assessment's real state ("Completed — Passed
// 82%", "Failed", "In progress") instead of always offering "Start Assessment".
// Returns a map keyed by assessmentId -> the newest attempt for that assessment
// (the endpoint is already ordered newest-first, so the first one wins).
export async function getMyAssessmentAttempts() {
  const rows = await apiClient.get("/assessments/attempts/me", { size: 200 }).then(asRows).catch(() => []);
  const byAssessment = {};
  for (const r of rows) {
    const key = r.assessmentId;
    if (byAssessment[key]) continue; // newest already taken
    byAssessment[key] = {
      attemptId: r.attemptId,
      assessmentId: r.assessmentId,
      attemptNumber: r.attemptNumber ?? null,
      status: r.status, // IN_PROGRESS | SUBMITTED | EXPIRED | PENDING_MANUAL_GRADING
      percentage: r.percentage != null ? Number(r.percentage) : null,
      passed: r.passed ?? null,
      passMark: r.passMark ?? null,
      submittedAt: r.submittedAt || null,
    };
  }
  return byAssessment;
}

// POST /api/v1/assessments/attempts/{id}/submit
// answers: [{ questionId, selectedOptionIndices?: number[], codeAnswer?: string }]
export async function submitAssessmentAttempt(attemptId, answers) {
  return toFeAttempt(
    await apiClient.post(`/assessments/attempts/${attemptId}/submit`, {
      answers: (answers || []).map((x) => ({
        questionId: Number(x.questionId),
        selectedOptionIndices: x.selectedOptionIndices || [],
        codeAnswer: x.codeAnswer || null,
      })),
    })
  );
}
// --- Video Player & Quiz screen (MSH-FR-STU-07 / MSH-FR-STU-08) ---------
// Video lessons are authored and published entirely by the Developer role
// (see developerService: createVideoLesson -> addQuestionToLesson ->
// publishVideoLesson) -- same pattern as the Assessment engine. This just
// reads whatever is currently published and layers the logged-in student's
// own watch/quiz progress on top, keyed by student email (mirrors
// msh_assessment_attempts) so two demo student accounts never collide.

const PASS_MARK = 60;

function fmtDuration(seconds) {
  if (!seconds) return "";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s ? `${m}m ${s}s` : `${m}m`;
}

// GET /api/v1/lessons — published lessons, each row carrying the caller's
// progress. Watch/quiz-pass state comes from `progress` + a local quiz-score
// cache (the backend attempt only exposes pass/fail via the submit response).
function readQuizScoreCache() {
  try {
    return JSON.parse(localStorage.getItem("msh_lesson_quiz_scores") || "{}");
  } catch {
    return {};
  }
}
function writeQuizScore(id, score, passed) {
  const all = readQuizScoreCache();
  all[id] = { score, passed };
  localStorage.setItem("msh_lesson_quiz_scores", JSON.stringify(all));
}

// `track` (optional) scopes the list to that cohort track + the all-track
// lessons — the caller passes their enrolled batch's trackCode.
export async function getVideoLessons(track) {
  const res = await apiClient.get("/lessons", track ? { size: 100, track } : { size: 100 });
  const scores = readQuizScoreCache();
  return asRows(res)
    .filter((l) => l.published)
    .map((l) => {
      const cached = scores[l.id] || {};
      const completed = l.progress?.status === "COMPLETED";
      return {
        id: l.id,
        module: l.moduleName,
        title: l.title,
        description: l.description || "",
        duration: fmtDuration(l.durationSeconds),
        durationSeconds: l.durationSeconds ?? null,
        videoUrl: l.videoUrl,
        videoId: ytId(l.videoUrl),
        questionCount: null, // filled in on detail load
        watched: !!l.progress && l.progress.status !== "NOT_STARTED",
        quizScore: cached.score ?? (completed ? 100 : null),
        quizPassed: cached.passed ?? (completed ? true : null),
        passingScore: PASS_MARK,
      };
    });
}

export async function getVideoLessonDetail(id) {
  const [lesson, quiz] = await Promise.all([
    apiClient.get(`/lessons/${id}`),
    apiClient.get(`/lessons/${id}/quiz`).catch(() => []),
  ]);
  if (!lesson) return null;
  return {
    id: lesson.id,
    module: lesson.moduleName,
    title: lesson.title,
    description: lesson.description || "",
    duration: fmtDuration(lesson.durationSeconds),
    videoUrl: lesson.videoUrl,
    videoId: ytId(lesson.videoUrl),
    videoType: ytId(lesson.videoUrl) ? "youtube" : "upload",
    passingScore: PASS_MARK,
    // Learning.jsx expects {id, question, options}; the answer key is never
    // returned by the API — grading happens server-side on submit.
    quiz: (Array.isArray(quiz) ? quiz : []).map((q) => ({
      id: q.id,
      question: q.questionText,
      options: q.options || [],
      explanation: q.explanation || "",
    })),
  };
}

// POST /api/v1/lessons/{id}/progress — watchedSeconds never rewinds server-side.
export async function markLessonWatched(id, watchedSeconds = 1) {
  await apiClient.post(`/lessons/${id}/progress`, {
    watchedSeconds: Math.max(1, Math.round(watchedSeconds)),
    completed: false,
  });
  return { ok: true };
}

// POST /api/v1/lessons/{id}/quiz/submit — `answers` is a positional array of
// chosen option indices (one per question, in quiz order). The server grades
// and, on >= 60%, upserts LessonProgress to COMPLETED.
export async function submitLessonQuiz(id, answers) {
  const r = await apiClient.post(`/lessons/${id}/quiz/submit`, { answers });
  const pct = r.total ? Math.round((r.score * 100) / r.total) : 0;
  writeQuizScore(id, pct, !!r.passed);
  return {
    quizScore: pct,
    quizPassed: !!r.passed,
    correct: r.score,
    total: r.total,
    passMarkPercent: r.passMarkPercent ?? PASS_MARK,
  };
}