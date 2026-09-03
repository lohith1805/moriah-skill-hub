import { mockRequest, apiClient } from "./apiClient";
import { TASKS, PIP_RECORDS, CERTIFICATES, BATCHES, SPRINTS } from "./mockData";
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

// Persists the resume (with real file content) onto the student's own user
// record — the same "msh_user" / registered-users records every other
// profile field lives on, and the same place hrService/clientService look
// it up from for the HR exit → Client Talent Pool handoff. After saving, it
// re-attempts publishing this student to the Talent Pool in case HR already
// finalized their exit clearance and was only waiting on the resume.
export async function saveResumeFile(user, file) {
  if (!user) throw new Error("No logged-in user to save a resume for.");
  const fileData = await fileToDataURL(file);
  const now = new Date().toISOString();
  const existing = user?.profileDetails?.resume;
  const resumeMeta = {
    name: file.name,
    size: file.size,
    fileData,
    uploadedAt: existing?.fileData ? existing.uploadedAt : now,
    updatedAt: now,
  };

  const updatedUser = {
    ...user,
    profileDetails: { ...(user.profileDetails || {}), resume: resumeMeta },
  };
  localStorage.setItem("msh_user", JSON.stringify(updatedUser));
  try {
    const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
    if (rawList) {
      const list = JSON.parse(rawList);
      const idx = list.findIndex((u) => u.id === user.id);
      if (idx > -1) {
        list[idx] = updatedUser;
        localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
      }
    }
  } catch (e) {
    console.warn("[studentService] Could not update registered users list:", e.message);
  }

  // Non-fatal — HR may not have finalized (or even started) this student's
  // exit clearance yet, in which case there's simply nothing to sync yet.
  try {
    trySyncGraduateToTalentPool(user.name);
  } catch (e) {}

  return { updatedUser, resumeMeta };
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

// A Project only appears here once a Trainer has deliberately assigned it to
// this student's batch (Trainer > Batches > Assign Projects) — publishing it
// on the Developer side alone is not enough. Same batch-name-to-id lookup
// pattern as getMySprints above.
export async function getMyProjects() {
  const user = getPersistedUser();
  if (!user) return mockRequest([]);

  const batch = BATCHES.find((b) => b.name === user.batch);
  if (!batch) return mockRequest([]);

  const projects = readLocalJSON("msh_developer_projects");
  const mine = projects.filter(
    (p) => p.status === "Published" && (p.assignedBatches || []).includes(batch.id)
  );
  return mockRequest(mine);
}

// Bug Challenges are linked to a Project by title, not by batch (see
// Developer > Bug Challenges), so a challenge becomes visible the moment its
// parent Project is assigned to this student's batch — no separate
// assignment step needed for challenges themselves.
export async function getMyBugChallenges() {
  const myProjects = await getMyProjects();
  const titles = new Set(myProjects.map((p) => p.title));
  const challenges = readLocalJSON("msh_bug_challenges");
  return mockRequest(challenges.filter((c) => titles.has(c.project)));
}

// Reconstructs real, callable testFn closures for a single Bug Challenge's
// test cases from the developer-authored spec (functionName + args +
// expected) — identical mechanism to getAssessmentDetails' Code questions,
// so a student's fix is genuinely executed and checked, not just clicked
// through.
export async function getBugChallengeDetails(challengeId) {
  const challenges = readLocalJSON("msh_bug_challenges");
  const challenge = challenges.find((c) => c.id === challengeId);
  if (!challenge) return null;

  return {
    ...challenge,
    testCases: (challenge.testCases || []).map((tc) => ({
      ...tc,
      inputDesc: tc.inputDesc || `${challenge.functionName}(${(tc.args || []).map((a) => JSON.stringify(a)).join(", ")})`,
      testFn: (codeStr) => {
        try {
          const argNames = (tc.args || []).map((_, i) => `arg${i}`);
          const fn = new Function(...argNames, `${codeStr}\nreturn ${challenge.functionName}(${argNames.join(", ")});`);
          const result = fn(...(tc.args || []));
          return JSON.stringify(result) === JSON.stringify(tc.expected);
        } catch (e) {
          return false;
        }
      },
    })),
  };
}

// Records a student's attempt at a Bug Challenge. Updates the same
// solved/attempts counters Developer > Bug Challenges displays, so that
// progress bar reflects real student activity instead of manually-edited
// numbers.
export async function attemptBugChallenge(challengeId, solved) {
  const challenges = readLocalJSON("msh_bug_challenges");
  const idx = challenges.findIndex((c) => c.id === challengeId);
  if (idx > -1) {
    challenges[idx] = {
      ...challenges[idx],
      attempts: (challenges[idx].attempts || 0) + 1,
      solved: (challenges[idx].solved || 0) + (solved ? 1 : 0),
    };
    localStorage.setItem("msh_bug_challenges", JSON.stringify(challenges));
  }
  return mockRequest(idx > -1 ? challenges[idx] : null);
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

// No checkout endpoint on the subscription controller yet — the real payment
// flow is POST /subscriptions/checkout in the payment module (see
// testing-flow.md Flow 5). Kept mock so the UI still completes.
export async function subscribeToPlan(planCode, paymentMethod) {
  await mockRequest(null, { delay: 900 });
  return { invoiceId: `INV-${Date.now()}`, status: "success", planCode, paymentMethod };
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



// Assessments are authored and published entirely by the Developer role
// (see developerService: createAssessmentBank → addQuestionToBank →
// publishAssessment). This just reads whatever is currently published and
// layers the logged-in student's own attempt history on top, so two
// different demo student accounts never see or overwrite each other's
// scores (msh_assessment_attempts is keyed by student email).
function readPublishedAssessments() {
  try {
    const raw = localStorage.getItem("msh_dev_assessments");
    const list = raw ? JSON.parse(raw) : [];
    return list.filter((a) => a.status === "Published");
  } catch (e) {
    return [];
  }
}

function readAttempts() {
  try {
    const raw = localStorage.getItem("msh_assessment_attempts");
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

export async function getAssessments() {
  const user = getPersistedUser();
  const email = (user?.email || "").toLowerCase();
  const attempts = readAttempts().filter((a) => a.studentEmail?.toLowerCase() === email);

  const list = readPublishedAssessments().map((a) => {
    const attempt = attempts.find((at) => at.assessmentId === a.id);
    return {
      id: a.id,
      title: a.title,
      type: a.type,
      duration: a.duration,
      score: attempt ? attempt.score : null,
      status: attempt ? "Completed" : "Available",
    };
  });

  return mockRequest(list);
}

export async function getAssessmentDetails(id) {
  const assessment = readPublishedAssessments().find((a) => a.id === id);
  if (!assessment) return [];

  // Bypassing mockRequest (no JSON clone) so we can attach real, callable
  // testFn closures reconstructed from the developer-authored spec
  // (functionName + args + expected output per test case).
  return Promise.resolve(
    assessment.questions.map((q) => {
      if (q.type !== "Code") return q;
      return {
        ...q,
        testCases: (q.testCases || []).map((tc) => ({
          ...tc,
          inputDesc: tc.inputDesc || `${q.functionName}(${(tc.args || []).map((a) => JSON.stringify(a)).join(", ")})`,
          testFn: (codeStr) => {
            try {
              const argNames = (tc.args || []).map((_, i) => `arg${i}`);
              const fn = new Function(...argNames, `${codeStr}\nreturn ${q.functionName}(${argNames.join(", ")});`);
              const result = fn(...(tc.args || []));
              return JSON.stringify(result) === JSON.stringify(tc.expected);
            } catch (e) {
              return false;
            }
          },
        })),
      };
    })
  );
}

export async function submitAssessment(id, score) {
  const user = getPersistedUser();
  const assessment = readPublishedAssessments().find((a) => a.id === id);

  const attempt = {
    id: `att_${Date.now()}`,
    assessmentId: id,
    assessmentTitle: assessment?.title || "Assessment",
    studentEmail: user?.email || null,
    studentName: user?.name || "Student",
    score,
    passed: score >= (assessment?.passingScore ?? 60),
    submittedAt: new Date().toISOString(),
  };

  // A retake replaces the previous attempt for this student+assessment
  // rather than piling up duplicates.
  const attempts = readAttempts().filter(
    (a) => !(a.assessmentId === id && a.studentEmail === attempt.studentEmail)
  );
  attempts.unshift(attempt);
  localStorage.setItem("msh_assessment_attempts", JSON.stringify(attempts));

  return mockRequest(attempt);
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

export async function getVideoLessons() {
  const res = await apiClient.get("/lessons", { size: 100 });
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