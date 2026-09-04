import { mockRequest, apiClient } from "./apiClient";
import { logAudit, AUDIT_CATEGORIES } from "../utils/auditLog";

// ---------------------------------------------------------------------------
// WIRED to the backend (this session): batch list + create (/api/v1/batches),
// review queue (/api/v1/reviews/queue).
// STILL MOCK: sprints/tasks planning (needs a coordinated batch↔sprint↔task
// rewrite with name→uuid remapping and no epic/userStory fields on the API),
// analytics + auto-PIP engine, graduation cert issue + HR handoff, trainer
// student management. Track those in memory.md for the next pass.
// ---------------------------------------------------------------------------

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);
const humanize = (s) => (s || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());

// FE track display names <-> backend trackCode strings.
const TRACK_FE_TO_CODE = {
  "Full-Stack Development": "FULL_STACK",
  "Data Analytics": "DATA_ANALYTICS",
  "Product Design": "PRODUCT_DESIGN",
  "Backend Engineering": "BACKEND_ENGINEERING",
};
const TRACK_CODE_TO_FE = Object.fromEntries(
  Object.entries(TRACK_FE_TO_CODE).map(([fe, code]) => [code, fe])
);

function toFeBatch(b) {
  return {
    id: b.id,
    name: b.name,
    track: TRACK_CODE_TO_FE[b.trackCode] || b.trackCode || "Full-Stack Development",
    trackCode: b.trackCode,
    pmName: b.pmFullName || "",
    planTierMinCode: b.planTierMinCode || null,
    startDate: b.startDate || null,
    endDate: b.endDate || null,
    capacity: b.capacity ?? null,
    students: b.enrolledCount ?? 0,
    // health is a FE-derived metric with no backend field; the Batches table
    // renders null as "No activity yet".
    health: null,
    status: b.status === "ACTIVE" ? "Active" : b.status === "PLANNED" ? "Onboarding" : humanize(b.status),
    backendStatus: b.status,
  };
}

const DEV_PROJECTS_KEY = "msh_developer_projects";

function readLocalProjects() {
  try {
    const raw = localStorage.getItem(DEV_PROJECTS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

// Reads the exact same localStorage key Developer > Projects writes to.
// Only Published projects are assignable — a Draft isn't finished being
// authored yet, so it shouldn't be handed to a batch.
export async function getAssignableProjects() {
  return mockRequest(readLocalProjects().filter((p) => p.status === "Published"));
}

// Sets the full list of batch IDs a project is assigned to. This is what
// makes a Published project (and any Bug Challenges linked to it) show up
// on that batch's Student > Projects & Challenges page — see
// studentService.getMyProjects / getMyBugChallenges.
export async function setProjectBatches(projectId, batchIds) {
  const all = readLocalProjects();
  const idx = all.findIndex((p) => p.id === projectId);
  if (idx > -1) {
    all[idx] = { ...all[idx], assignedBatches: batchIds };
    localStorage.setItem(DEV_PROJECTS_KEY, JSON.stringify(all));
  }
  return mockRequest(idx > -1 ? all[idx] : null);
}

// GET /api/v1/batches — TRAINER_PM/ADMIN see every batch (a STUDENT token would
// see only enrolled). `health` is null (no backend field); the table copes.
export async function getBatches() {
  const res = await apiClient.get("/batches", { size: 100 });
  return asRows(res).map(toFeBatch);
}

// GET /api/v1/batches/pending-allocations — students who paid for a batch plan
// but have no matching batch yet. They're auto-placed the moment a batch for
// their track is created; until then they show here as "assignment pending".
export async function getPendingAllocations() {
  const res = await apiClient.get("/batches/pending-allocations");
  return asRows(res).map((p) => ({
    uuid: p.studentUuid,
    name: p.studentName,
    email: p.studentEmail,
    track: TRACK_CODE_TO_FE[p.trackCode] || p.trackCode,
    trackCode: p.trackCode,
    plan: p.planCode || "—",
    reason: p.reason || "",
    requestedAt: p.requestedAt ? String(p.requestedAt).slice(0, 10) : "",
  }));
}

// POST /api/v1/batches — the caller becomes the batch PM.
export async function createBatch(payload) {
  const body = {
    name: (payload.name || "").trim(),
    trackCode: TRACK_FE_TO_CODE[payload.track] || payload.track || "FULL_STACK",
    planTierMinCode: payload.planTierMinCode || null,
    startDate: payload.startDate,
    endDate: payload.endDate,
    capacity:
      payload.capacity === "" || payload.capacity == null ? 20 : Number(payload.capacity),
  };
  return toFeBatch(await apiClient.post("/batches", body));
}

// --- Sprints ------------------------------------------------------------

const SPRINT_STATUS_TO_FE = { PLANNED: "Planned", ACTIVE: "Active", COMPLETED: "Completed" };

function toFeSprint(s) {
  return {
    id: s.id,
    batchId: s.batchId,
    number: s.sprintNumber,
    goal: s.goal,
    startDate: s.startDate || null,
    endDate: s.endDate || null,
    status: SPRINT_STATUS_TO_FE[s.status] || s.status,
    backendStatus: s.status,
    plannedPoints: s.plannedPoints ?? null,
    completedPoints: s.completedPoints ?? null,
  };
}

// GET /api/v1/sprints?batchId= — the backend requires batchId, so a no-arg
// call fans out over every batch the caller can see.
export async function getSprints(batchId) {
  if (batchId) {
    const res = await apiClient.get("/sprints", { batchId, size: 100 });
    return asRows(res).map(toFeSprint);
  }
  const batches = await getBatches();
  const perBatch = await Promise.all(
    batches.map((b) =>
      apiClient.get("/sprints", { batchId: b.id, size: 100 }).then(asRows).catch(() => [])
    )
  );
  return perBatch
    .flat()
    .map(toFeSprint)
    .sort((a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0));
}

// POST /api/v1/sprints — sprintNumber 1-52, goal required, 7-14 day window.
export async function createSprint(payload) {
  const body = {
    batchId: Number(payload.batchId),
    sprintNumber: Number(payload.number) || 1,
    goal: (payload.goal || "").trim(),
    startDate: payload.startDate,
    endDate: payload.endDate,
    plannedPoints:
      payload.plannedPoints === "" || payload.plannedPoints == null
        ? null
        : Number(payload.plannedPoints),
  };
  return toFeSprint(await apiClient.post("/sprints", body));
}

// POST /api/v1/sprints/{id}/activate — needs the previous sprint COMPLETED.
export async function activateSprint(id) {
  return toFeSprint(await apiClient.post(`/sprints/${id}/activate`, {}));
}

// --- Tasks --------------------------------------------------------------

const TASK_STATUS_TO_FE = {
  BACKLOG: "Backlog",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In Progress",
  IN_REVIEW: "Review",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
};
const TASK_TYPE_TO_FE = { STORY: "User Story", BUGFIX: "Bug", ASSIGNMENT: "Task", DAILY: "Daily" };
const FE_TYPE_TO_TASK = { "User Story": "STORY", Bug: "BUGFIX", Task: "ASSIGNMENT", Daily: "DAILY" };

export function toFeTask(t) {
  return {
    id: t.id,
    sprintId: t.sprintId,
    projectId: t.projectId ?? null,
    title: t.title,
    description: t.description || "",
    type: TASK_TYPE_TO_FE[t.taskType] || "Task",
    epic: "General", // no epic field on the API — folded into description on create
    userStory: "",
    acceptanceCriteria: "",
    assignee: t.assignedToName || "Unassigned",
    assigneeUuid: t.assignedToUuid || null,
    points: t.storyPoints ?? 0,
    due: t.dueAt ? t.dueAt.slice(0, 10) : "",
    dueAt: t.dueAt || null,
    status: TASK_STATUS_TO_FE[t.status] || t.status,
    backendStatus: t.status,
    githubPr: null,
    videoUrl: null,
    completedCriteria: [],
  };
}

// GET /api/v1/tasks?sprintId= — required param; no-arg fans out over every
// sprint the caller can see.
export async function getSprintTasks(sprintId) {
  if (sprintId) {
    const res = await apiClient.get("/tasks", { sprintId, size: 100 });
    return asRows(res).map(toFeTask);
  }
  const sprints = await getSprints();
  const perSprint = await Promise.all(
    sprints.map((s) =>
      apiClient.get("/tasks", { sprintId: s.id, size: 100 }).then(asRows).catch(() => [])
    )
  );
  return perSprint.flat().map(toFeTask);
}

// POST /api/v1/tasks — starts BACKLOG (unassigned); students self-pull it.
// epic / user story / acceptance criteria have no API field, so they are
// stitched into `description`.
export async function createTask({ sprintId, title, type, points, dueDate, description, userStory, acceptanceCriteria, epic }) {
  const descParts = [
    description,
    epic ? `Epic: ${epic}` : "",
    userStory ? `User story: ${userStory}` : "",
    acceptanceCriteria ? `Acceptance criteria:\n${acceptanceCriteria}` : "",
  ].filter(Boolean);
  const body = {
    sprintId: Number(sprintId),
    projectId: null,
    title: (title || "").trim(),
    description: descParts.join("\n\n") || null,
    taskType: FE_TYPE_TO_TASK[type] || "ASSIGNMENT",
    storyPoints: points === "" || points == null ? null : Number(points),
    dueAt: dueDate ? new Date(`${dueDate}T18:00:00`).toISOString() : null,
  };
  return toFeTask(await apiClient.post("/tasks", body));
}

// PM assigns a BACKLOG task to a specific student by uuid.
export async function assignTask(taskId, userUuid) {
  return toFeTask(await apiClient.post(`/tasks/${taskId}/assign`, { userUuid }));
}

// PM drives a task's status forward (PUT /tasks/{id} — full body).
export async function updateTask(taskId, current, nextStatus) {
  const body = {
    title: current.title,
    description: current.description || null,
    taskType: FE_TYPE_TO_TASK[current.type] || "ASSIGNMENT",
    storyPoints: current.points ?? null,
    dueAt: current.dueAt || null,
    status: nextStatus,
  };
  return toFeTask(await apiClient.put(`/tasks/${taskId}`, body));
}

// GET /api/v1/batches/{id}/students — the batch roster (PM/ADMIN, must own the
// batch). Used to populate "assign to" dropdowns and the graduation list.
export async function getStudentsForBatch(batchId) {
  if (!batchId) return [];
  const res = await apiClient.get(`/batches/${batchId}/students`);
  return (Array.isArray(res) ? res : asRows(res)).map((s) => ({
    userUuid: s.userUuid,
    name: s.fullName,
    email: s.email || "",
    status: s.status,
    joinedAt: s.joinedAt || null,
    graduatedAt: s.graduatedAt || null,
    finalScore: s.finalScore != null ? Number(s.finalScore) : null,
  }));
}

// --- Standups + attendance -------------------------------------------

const STANDUP_STATUS_TO_FE = { SCHEDULED: "Scheduled", CONDUCTED: "Conducted", CANCELLED: "Cancelled" };

// GET /api/v1/standups?batchId=&date=YYYY-MM-DD
export async function getStandups(batchId, date) {
  const params = { batchId };
  if (date) params.date = date;
  const res = await apiClient.get("/standups", params);
  return asRows(res).map((s) => ({
    id: s.id,
    batchId: s.batchId,
    sprintId: s.sprintId ?? null,
    scheduledAt: s.scheduledAt,
    lateCutoffMinutes: s.lateCutoffMinutes ?? null,
    notes: s.notes || "",
    meetingLink: s.meetingLink || null,
    finalisedAt: s.finalisedAt || null,
    status: STANDUP_STATUS_TO_FE[s.status] || s.status,
  }));
}

// POST /api/v1/standups
export async function scheduleStandup({ batchId, sprintId, scheduledAt, lateCutoffMinutes = 15, notes, meetingLink }) {
  const res = await apiClient.post("/standups", {
    batchId: Number(batchId),
    sprintId: sprintId ? Number(sprintId) : null,
    scheduledAt: scheduledAt || new Date().toISOString(),
    lateCutoffMinutes: Number(lateCutoffMinutes),
    notes: notes || null,
    meetingLink: meetingLink || null,
  });
  return { id: res.id, ...res };
}

// PUT /api/v1/standups/{id} — used here only to CANCEL a scheduled standup.
export async function cancelStandup(standupId) {
  return apiClient.put(`/standups/${standupId}`, { status: "CANCELLED" });
}

// GET /api/v1/attendance/batch/{batchId} → the rows for one standup, keyed by
// student, so the roster can show who has already checked in / been marked.
export async function getStandupAttendance(batchId, standupId) {
  const res = await apiClient.get(`/attendance/batch/${batchId}`, { size: 200 });
  const FE = { PRESENT: "Present", LATE: "Late", ABSENT: "Absent", EXCUSED: "Excused" };
  return asRows(res)
    .filter((a) => String(a.standupId) === String(standupId))
    .map((a) => ({
      userUuid: a.userUuid,
      status: FE[a.status] || a.status,
      autoMarked: !!a.autoMarked,
      markedByPm: !!a.markedByUuid,
      checkedInAt: a.checkedInAt || null,
    }));
}

const FE_ATTENDANCE_TO_STATUS = { Present: "PRESENT", Late: "LATE", Absent: "ABSENT", Excused: "EXCUSED" };

// POST /api/v1/standups/{id}/attendance — PM override of one student's status.
export async function overrideAttendance(standupId, userUuid, feStatus, blockerNotes = "") {
  return apiClient.post(`/standups/${standupId}/attendance`, {
    userUuid,
    status: FE_ATTENDANCE_TO_STATUS[feStatus] || feStatus,
    blockerNotes: blockerNotes || null,
  });
}

// GET /api/v1/reviews/queue — IN_REVIEW tasks awaiting the caller's review,
// each enriched (best-effort) with its latest submission's PR / video links.
export async function getReviewQueue() {
  const res = await apiClient.get("/reviews/queue", { size: 50 });
  const rows = asRows(res);
  return Promise.all(
    rows.map(async (t) => {
      const sub = await latestSubmissionForTask(t.id).catch(() => null);
      return {
        id: t.id,
        title: t.title,
        description: t.description || "",
        sprintId: t.sprintId,
        assignee: t.assignedToName || "",
        assigneeUuid: t.assignedToUuid || null,
        points: t.storyPoints ?? 0,
        status: "Review",
        dueAt: t.dueAt || null,
        submissionId: sub?.id ?? null,
        githubPr: sub?.prUrl || null,
        videoUrl: sub?.videoUrl || null,
      };
    })
  );
}

async function latestSubmissionForTask(taskId) {
  const res = await apiClient.get("/submissions", { taskId, size: 50 });
  const subs = asRows(res);
  if (!subs.length) return null;
  return subs.reduce((a, b) => ((b.attemptNumber ?? 0) >= (a.attemptNumber ?? 0) ? b : a));
}

const FE_DECISION_TO_VERDICT = { Approved: "APPROVED", Rejected: "CHANGES_REQUESTED" };

export async function getStaffableClientProjects() {
  try {
    const raw = localStorage.getItem("msh_client_projects");
    const projects = raw ? JSON.parse(raw) : [];
    return mockRequest(projects.filter((p) => p.batch === "Unassigned"));
  } catch (e) {
    return mockRequest([]);
  }
}

// POST /api/v1/reviews — resolve the task's latest submission, then review it.
// APPROVED closes the task (COMPLETED); CHANGES_REQUESTED sends it back.
export async function reviewSubmission(taskId, { submissionId, score, decision, comment, inlineComments = [] }) {
  let subId = submissionId;
  if (!subId) {
    const sub = await latestSubmissionForTask(taskId);
    if (!sub) throw new Error("This task has no submission to review yet.");
    subId = sub.id;
  }
  const body = {
    submissionId: subId,
    score: Math.min(10, Math.max(1, Number(score) || 1)),
    verdict: FE_DECISION_TO_VERDICT[decision] || "CHANGES_REQUESTED",
    comments: comment || "",
    inlineComments: (inlineComments || [])
      .filter((c) => c && c.comment)
      .map((c) => ({ filePath: c.file || c.filePath || "unknown", line: c.line || 1, comment: c.comment })),
  };
  return apiClient.post("/reviews", body);
}

// Analytics is fully derived from real records — real Sprints/Tasks for
// velocity, real per-student Assessment attempts (written by the Developer's
// Assessment engine, see developerService/studentService) for the quiz
// trend, and the real registered-student roster for the performance table.
// Nothing here is hardcoded demo data: every chart starts empty and fills
// in as batches run sprints, students submit tasks, and students take
// assessments — same philosophy as the rest of this app.
// Derived client-side from real sprints + tasks + roster + PIP for one batch —
// there is no aggregate analytics endpoint. `quiz` per student stays 0: a PM
// has no endpoint for another user's assessment scores. Pass a batchId.
export async function getAnalytics(batchId) {
  if (!batchId) return { velocity: [], quizTrend: [], studentRows: [] };

  const [sprints, roster, pipCases] = await Promise.all([
    getSprints(batchId),
    getStudentsForBatch(batchId).catch(() => []),
    getPipCases({ batchId }).catch(() => []),
  ]);
  const sprintsAsc = sprints.slice().sort((a, b) => new Date(a.startDate || 0) - new Date(b.startDate || 0));

  const tasksBySprint = await Promise.all(
    sprintsAsc.map((s) => getSprintTasks(s.id).then((t) => [s.id, t]).catch(() => [s.id, []]))
  );
  const taskMap = new Map(tasksBySprint);
  const allTasks = [...taskMap.values()].flat();

  const velocity = sprintsAsc.map((s) => ({
    sprint: `Sprint ${s.number}`,
    points: (taskMap.get(s.id) || [])
      .filter((t) => t.status === "Completed")
      .reduce((sum, t) => sum + (Number(t.points) || 0), 0),
  }));

  const onPipUuids = new Set(
    pipCases.filter((p) => p.backendStatus === "TRIGGERED" || p.backendStatus === "IN_PROGRESS").map((p) => p.studentUuid)
  );

  const studentRows = roster
    .filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP")
    .map((s) => {
      const mine = allTasks.filter((t) => t.assigneeUuid === s.userUuid);
      const completion = mine.length
        ? Math.round((mine.filter((t) => t.status === "Completed").length / mine.length) * 100)
        : 0;
      return {
        name: s.name,
        completion,
        quiz: 0,
        onPip: onPipUuids.has(s.userUuid) || s.status === "ON_PIP",
        hasTasks: mine.length > 0,
        hasQuizzes: false,
      };
    });

  return { velocity, quizTrend: [], studentRows };
}

// --- PIP (WIRED, read + day-15 review) --------------------------------
// GET /api/v1/pip, POST /api/v1/pip/{id}/review. PIP records are RAISED by
// the nightly rule-engine job, not by an API — so there is no "create" or
// "delete" here.

const PIP_STATUS_TO_FE = {
  TRIGGERED: "In Progress",
  IN_PROGRESS: "In Progress",
  CLEARED: "Resolved",
  TERMINATED: "Terminated",
  REASSIGNED: "Repeat Foundation",
};
const FE_PIP_ACTION_TO_OUTCOME = {
  Resolved: "CLEARED",
  Terminated: "TERMINATED",
  "Repeat Foundation": "REASSIGNED",
};

function toFePipCase(p) {
  return {
    id: p.id,
    student: p.studentFullName || "",
    studentUuid: p.studentUuid || null,
    batch: p.batchName || "",
    batchId: p.batchId ?? null,
    reason: p.triggerReason || (p.ruleCode || "").replace(/_/g, " "),
    ruleCode: p.ruleCode,
    severity: humanize(p.severity),
    triggeredOn: p.triggeredAt ? p.triggeredAt.slice(0, 10) : "",
    startDate: p.startDate || null,
    endDate: p.endDate || null,
    status: PIP_STATUS_TO_FE[p.status] || p.status,
    backendStatus: p.status,
    note: p.reviewNotes || "",
    blocksTaskPull: !!p.blocksTaskPull,
    milestones: p.milestones || [],
  };
}

export async function getPipCases({ batchId, status } = {}) {
  const params = { size: 100 };
  if (batchId) params.batchId = batchId;
  if (status) params.status = status;
  const res = await apiClient.get("/pip", params);
  return asRows(res).map(toFePipCase);
}

// PIP is raised by the nightly job — there is no manual-create endpoint.
export async function triggerManualPip() {
  throw new Error("PIP cases are raised automatically by the nightly rule engine — there is no manual trigger.");
}

// No delete endpoint — a case is closed by the day-15 review instead.
export async function removePipCase() {
  throw new Error("PIP cases can't be deleted — resolve one with the day-15 recovery review.");
}

// POST /api/v1/pip/{id}/review — CLEARED is server-gated on task completion
// >= 85% and no unsatisfactory reviews; a failing student can't be cleared.
export async function updatePipCaseStatus(id, newStatus, _repeatBatchName = "", reviewNotes = "") {
  const outcome = FE_PIP_ACTION_TO_OUTCOME[newStatus];
  if (!outcome) throw new Error(`Unsupported PIP outcome: ${newStatus}`);
  const res = await apiClient.post(`/pip/${id}/review`, {
    outcome,
    reviewNotes: reviewNotes || `Day-15 review — outcome ${outcome}.`,
  });
  logAudit({
    category: AUDIT_CATEGORIES.PIP_STATUS_CHANGE,
    severity: outcome === "TERMINATED" ? "Critical" : outcome === "REASSIGNED" ? "Warning" : "Info",
    action: `PIP day-15 review — outcome ${outcome}`,
    target: `pip#${id}`,
  });
  return res;
}

export async function completePipMilestone(pipId, milestoneId) {
  return apiClient.post(`/pip/${pipId}/milestones/${milestoneId}/complete`, {});
}

// --- Graduation (backend-ready helpers) -------------------------------
// POST /api/v1/batches/{batchId}/students/{userUuid}/graduate, then
// POST /api/v1/certificates/issue. NOTE: trainer/Graduation.jsx still builds
// its candidate list from the localStorage roster because there is NO
// PM-visible "students in a batch" endpoint yet (same Part B gap that blocks
// task assignment). Once that lands, switch the page to these two calls.
export async function graduateStudent(batchId, userUuid) {
  const res = await apiClient.post(`/batches/${batchId}/students/${userUuid}/graduate`, {});
  logAudit({
    category: AUDIT_CATEGORIES.GRADE_CHANGE,
    action: `Student graduated from batch ${batchId}`,
    target: userUuid,
  });
  return res;
}

export async function issueCertificate(batchId, userUuid, certificateType = "COMPLETION") {
  const res = await apiClient.post("/certificates/issue", { batchId, userUuid, certificateType });
  logAudit({
    category: AUDIT_CATEGORIES.DOCUMENT_GEN,
    action: `Certificate issued (${certificateType})`,
    target: userUuid,
  });
  return res;
}

// Approving graduation now actually issues a certificate — writes into the
// same "msh_certificates" list the Student portal's Certificates page and
// the public /verify/:code checker both read from.
// Writes (or backfills) the HR-facing exit-clearance record for a graduated
// student. Exported and idempotent — safe to call both at the moment a
// trainer clicks Approve, AND as a backfill for students who were already
// cleared/certificated before this handoff existed (their certificate is
// already on record, but nothing was ever written for HR to see, so a
// one-time approval done under the old code silently never reaches Exit
// Management). Skips if a "Graduating Student" exit record for this name
// already exists, so calling it repeatedly is harmless.
export function ensureGraduationExitHandoff({ studentId, studentName, track, certificateId }) {
  if (!studentName) return;
  try {
    const raw = localStorage.getItem("msh_hr_exits");
    const exits = raw ? JSON.parse(raw) : [];
    const alreadyHandedOff = exits.some(
      (x) => x.type === "Graduating Student" && x.name?.toLowerCase() === studentName.toLowerCase()
    );
    if (!alreadyHandedOff) {
      exits.unshift({
        id: `x_grad_${studentId || Date.now()}`,
        name: studentName,
        type: "Graduating Student",
        department: track || "Full-Stack Development",
        reason: "Course completion — graduation clearance",
        exitDate: new Date().toISOString().slice(0, 10),
        itClearance: "Pending",
        accountsClearance: "Pending",
        exitInterview: "Scheduled",
        clearance: "Pending",
        sourceCertificateId: certificateId || null,
      });
      localStorage.setItem("msh_hr_exits", JSON.stringify(exits));
    }
  } catch (err) {
    console.warn("[trainerService] Could not hand off graduation exit record to HR:", err.message);
  }
}

export async function approveGraduation(studentId, studentName, track, studentEmail) {
  const verifyCode = `MSH-CERT-${Math.floor(10000 + Math.random() * 89999)}`;
  const certificate = {
    id: `c${Date.now()}`,
    studentName: studentName || "Student",
    studentEmail: studentEmail || null,
    title: `${track || "Full-Stack Development"} — Graduation Track`,
    track: track || "Full-Stack Development",
    issuedOn: new Date().toISOString().slice(0, 10),
    verifyCode,
    status: "Issued",
    hash: `sha256:${Array.from({ length: 64 }, () => Math.floor(Math.random() * 16).toString(16)).join("")}`,
  };

  try {
    const raw = localStorage.getItem("msh_certificates");
    const certs = raw ? JSON.parse(raw) : [];
    certs.unshift(certificate);
    localStorage.setItem("msh_certificates", JSON.stringify(certs));
  } catch (err) {
    console.warn("[trainerService] Could not persist certificate:", err.message);
  }

  // Hand off to HR — see ensureGraduationExitHandoff above.
  ensureGraduationExitHandoff({ studentId, studentName, track, certificateId: certificate.id });

  await mockRequest(null, { delay: 800 });
  return { studentId, status: "Cleared for Graduation", certificate };
}

// Student Management for Trainers
export async function getAllStudents() {
  let registeredUsers = [];
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    registeredUsers = raw ? JSON.parse(raw) : [];
  } catch (e) {
    registeredUsers = [];
  }
  const students = registeredUsers.filter((u) => u.role === "student");
  return mockRequest(students);
}

export async function updateStudentBatch(studentId, batchName) {
  let registeredUsers = [];
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    registeredUsers = raw ? JSON.parse(raw) : [];
  } catch (e) {
    registeredUsers = [];
  }

  const idx = registeredUsers.findIndex((u) => u.id === studentId);
  if (idx > -1) {
    registeredUsers[idx] = { ...registeredUsers[idx], batch: batchName || null };
    localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(registeredUsers));
    return mockRequest(registeredUsers[idx]);
  }
  throw new Error("Student not found");
}

export async function createStudent(payload) {
  let registeredUsers = [];
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    registeredUsers = raw ? JSON.parse(raw) : [];
  } catch (e) {
    registeredUsers = [];
  }

  const existing = registeredUsers.find((u) => u.email && u.email.toLowerCase() === payload.email.toLowerCase());
  if (existing) {
    throw new Error("A user with this email already exists");
  }

  const newStudent = {
    id: `u${Date.now()}`,
    role: "student",
    accountStatus: "Active",
    avatarColor: "#1E4A78",
    ...payload,
  };

  registeredUsers.push(newStudent);
  localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(registeredUsers));
  return mockRequest(newStudent, { delay: 500 });
}
// --- Assessments: publish dev-authored question banks + read results --------
// The DEVELOPER authors reusable question banks; the TRAINER decides when to
// publish one as a live assessment to a batch they own, and reviews results.
// GET /assessments/banks · POST /assessments/from-bank · GET /assessments
// · DELETE /assessments/{id} · GET /assessments/results

export async function getQuestionBanks() {
  const res = await apiClient.get("/assessments/banks", { size: 100 });
  return asRows(res).map((b) => ({
    id: b.id,
    title: b.name,
    topic: b.topic,
    description: b.description || "",
    active: b.active !== false,
    questionCount: b.questionCount ?? 0,
  }));
}

function toFePublishedAssessment(a) {
  return {
    id: a.id,
    title: a.title,
    batchId: a.batchId ?? null,
    duration: `${a.durationMinutes} min`,
    durationMinutes: a.durationMinutes,
    passingScore: a.passPercentage,
    maxAttempts: a.maxAttempts,
    active: a.active !== false,
  };
}

export async function getPublishedAssessments() {
  const res = await apiClient.get("/assessments", { size: 100 });
  return asRows(res).map(toFePublishedAssessment);
}

// POST /assessments/from-bank — snapshots the bank's questions (+ answer keys)
// into a live assessment for one batch. The backend holds a TRAINER_PM to a
// batch they own.
export async function publishAssessmentFromBank({ bankId, batchId, title, durationMinutes, passingScore }) {
  if (!bankId) throw new Error("Pick a question bank.");
  if (!batchId) throw new Error("Pick the batch this assessment is for.");
  const res = await apiClient.post("/assessments/from-bank", {
    bankId: Number(bankId),
    batchId: Number(batchId),
    title: title || undefined,
    durationMinutes: Number(durationMinutes) || 20,
    passPercentage: passingScore ? Number(passingScore) : undefined,
  });
  return toFePublishedAssessment(res);
}

export async function unpublishAssessment(id) {
  await apiClient.del(`/assessments/${id}`);
  return { id };
}

// GET /assessments/results — one row per student attempt, filterable by
// assessment / batch / cohort track.
export async function getAssessmentResults({ assessmentId, batchId, track } = {}) {
  const params = { size: 300 };
  if (assessmentId) params.assessmentId = assessmentId;
  if (batchId) params.batchId = batchId;
  if (track) params.track = track;
  const res = await apiClient.get("/assessments/results", params);
  return asRows(res).map((r) => ({
    attemptId: r.attemptId,
    assessmentId: r.assessmentId,
    assessmentTitle: r.assessmentTitle,
    batchId: r.batchId,
    batchName: r.batchName || "—",
    track: r.track || "",
    studentName: r.studentName,
    studentUuid: r.studentUuid,
    attemptNumber: r.attemptNumber,
    status: r.status,
    percentage: r.percentage != null ? Number(r.percentage) : null,
    passed: r.passed,
    passMark: r.passMark,
    submittedAt: r.submittedAt,
  }));
}
