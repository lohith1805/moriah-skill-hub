import { mockRequest } from "./apiClient";
import { BATCHES, SPRINTS, TASKS, PIP_RECORDS } from "./mockData";
import { runPipAutoCheckForAll } from "./pipEngine";

// TASKS/SPRINTS imported above are only a SNAPSHOT taken when this module
// first loaded. Several writers (createTask, reviewSubmission, createSprint,
// the Student portal's task actions, etc.) persist their changes straight to
// localStorage without mutating that in-memory snapshot — so anything that
// read TASKS/SPRINTS directly (instead of re-reading localStorage) kept
// showing stale/empty data after a task was completed or a sprint was added,
// even though Attendance and other localStorage-backed views updated fine.
// getBatches/getAnalytics used to do exactly that, which is why Performance
// Analytics could sit on "No completed sprint tasks yet" / "No assessments
// submitted yet" forever even after a trainer approved a submission. These
// two helpers are now the single source of truth: always re-read from
// localStorage, falling back to the initial snapshot only if nothing is
// stored yet (fresh install) or the stored value is corrupt.
function getStoredTasks() {
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return TASKS;
}

function getStoredSprints() {
  try {
    const raw = localStorage.getItem("msh_sprints");
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return SPRINTS;
}

// Shared by getAnalytics + getPipCases: every registered student (minus the
// seeded demo account so a fresh install doesn't show a phantom row) plus
// anyone who already has tasks/attempts under their name, mapped to their
// batch — this is the roster the auto-PIP rule engine checks on every load.
function getStudentRosterWithBatch(attempts) {
  let roster = {};
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const users = raw ? JSON.parse(raw) : [];
    users
      .filter((u) => u.role === "student" && u.email !== "ananya.student@moriah.io")
      .forEach((u) => { roster[u.name] = u.batch || ""; });
  } catch (e) {
    roster = {};
  }
  getStoredTasks().forEach((t) => { if (t.assignee && !(t.assignee in roster)) roster[t.assignee] = ""; });
  (attempts || []).forEach((a) => { if (a.studentName && !(a.studentName in roster)) roster[a.studentName] = ""; });
  return roster;
}

function getStoredAttempts() {
  try {
    const raw = localStorage.getItem("msh_assessment_attempts");
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

// Batch "health" is derived, never stored — it's recomputed on every load
// from that batch's REAL signals (its students' task completion + quiz
// average), then nudged down per active PIP case in the batch.
//
// Returns null (NOT 0) when there is no signal yet — e.g. a batch that
// already has enrolled students but no sprints/tasks/quiz attempts created
// for it yet. That's "no activity to measure", not "0% healthy" — treating
// it as 0 would paint a batch that just hasn't started work as red/failing,
// which is exactly the bug: a batch with a real student was showing 0
// purely because no sprint/task/quiz data existed for it yet, not because
// anyone actually performed badly. Callers (Batches table, PM Dashboard)
// render null as "No activity yet" instead of a 0% bar.
function computeBatchHealth(tasksForBatch, attemptsForBatch, activePipCount) {
  const signals = [];
  if (tasksForBatch.length) {
    const completion = (tasksForBatch.filter((t) => t.status === "Completed").length / tasksForBatch.length) * 100;
    signals.push(completion);
  }
  if (attemptsForBatch.length) {
    const quizAvg = attemptsForBatch.reduce((sum, a) => sum + a.score, 0) / attemptsForBatch.length;
    signals.push(quizAvg);
  }
  if (!signals.length) return null;
  const base = signals.reduce((a, b) => a + b, 0) / signals.length;
  return Math.max(0, Math.round(base) - activePipCount * 10);
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

export async function getBatches() {
  const attempts = getStoredAttempts();
  let registeredUsers = [];
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    registeredUsers = raw ? JSON.parse(raw) : [];
  } catch (e) {
    registeredUsers = [];
  }

  const allSprints = getStoredSprints();
  const allTasks = getStoredTasks();
  const withHealth = BATCHES.map((b) => {
    const sprintIds = new Set(allSprints.filter((s) => s.batchId === b.id).map((s) => s.id));
    const tasksForBatch = allTasks.filter((t) => sprintIds.has(t.sprintId));
    const batchStudentNames = registeredUsers
      .filter((u) => u.role === "student" && u.batch === b.name)
      .map((u) => u.name);
    const attemptsForBatch = attempts.filter((a) => batchStudentNames.includes(a.studentName));
    const activePip = PIP_RECORDS.filter(
      (p) => batchStudentNames.includes(p.student) && p.status !== "Resolved"
    ).length;
    return { ...b, students: batchStudentNames.length, health: computeBatchHealth(tasksForBatch, attemptsForBatch, activePip) };
  });

  return mockRequest(withHealth);
}

export async function createBatch(payload) {
  const batch = { id: `b${Date.now()}`, students: 0, health: 0, status: "Onboarding", ...payload };
  BATCHES.unshift(batch);
  localStorage.setItem("msh_batches", JSON.stringify(BATCHES));
  return mockRequest(batch, { delay: 700 });
}

export async function getSprints(batchId) {
  const allSprints = getStoredSprints();
  const data = batchId ? allSprints.filter((s) => s.batchId === batchId) : allSprints;
  return mockRequest(data);
}

// Real students registered for a given batch, so Sprint Planning's "assign
// to" dropdown offers actual people instead of free-typed names that could
// typo-drift from what the student's own account is called.
export async function getStudentsForBatch(batchId) {
  const batch = BATCHES.find((b) => b.id === batchId);
  if (!batch) return mockRequest([]);
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const users = raw ? JSON.parse(raw) : [];
    const students = users
      .filter((u) => u.role === "student" && u.batch === batch.name)
      .map((u) => u.name);
    return mockRequest(students);
  } catch (e) {
    return mockRequest([]);
  }
}

// A sprint is just a time-boxed goal until real backlog items exist inside
// it — this is how a trainer actually turns "Sprint 6: goal" into work a
// specific student sees on their own Sprint Board (see studentService's
// getMyTasks, which reads this same TASKS array by assignee name).
export async function createTask({ sprintId, title, points, dueDate, assignee, epic, userStory, acceptanceCriteria, type }) {
  const task = {
    id: `t${Date.now()}`,
    sprintId,
    title,
    points: Number(points) || 0,
    due: dueDate,
    assignee,
    status: "Backlog",
    githubPr: null,
    epic: epic || "General",
    userStory: userStory || "",
    acceptanceCriteria: acceptanceCriteria || "",
    type: type || "Task",
    completedCriteria: []
  };

  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }

  allTasks.unshift(task);
  localStorage.setItem("msh_sprint_tasks", JSON.stringify(allTasks));
  return mockRequest(task, { delay: 400 });
}

export async function getStaffableClientProjects() {
  try {
    const raw = localStorage.getItem("msh_client_projects");
    const projects = raw ? JSON.parse(raw) : [];
    return mockRequest(projects.filter((p) => p.batch === "Unassigned"));
  } catch (e) {
    return mockRequest([]);
  }
}

// If `clientProjectId` is passed, this sprint is the delivery kickoff for an
// approved client requirement: link the sprint to it and update the client's
// project card (batch assigned, live milestone, demo date) so the Client
// portal reflects real progress instead of a static placeholder.
export async function createSprint(payload) {
  const { clientProjectId, ...sprintFields } = payload;
  const sprint = { id: `sp${Date.now()}`, status: "Active", ...sprintFields };
  
  let allSprints = SPRINTS;
  try {
    const raw = localStorage.getItem("msh_sprints");
    if (raw) allSprints = JSON.parse(raw);
  } catch (e) {}

  allSprints.unshift(sprint);
  localStorage.setItem("msh_sprints", JSON.stringify(allSprints));

  if (clientProjectId) {
    try {
      const raw = localStorage.getItem("msh_client_projects");
      const projects = raw ? JSON.parse(raw) : [];
      const batch = BATCHES.find((b) => b.id === sprintFields.batchId);
      const updated = projects.map((p) =>
        p.id === clientProjectId
          ? { ...p, batch: batch?.name || p.batch, milestone: `Sprint ${sprintFields.number} — ${sprintFields.goal}`, progress: 10, demoDate: sprintFields.endDate }
          : p
      );
      localStorage.setItem("msh_client_projects", JSON.stringify(updated));
    } catch (err) {
      console.warn("[trainerService] Could not link sprint to client project:", err.message);
    }
  }

  return mockRequest(sprint, { delay: 700 });
}

export async function getSprintTasks(sprintId) {
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }
  const data = sprintId ? allTasks.filter((t) => t.sprintId === sprintId) : allTasks;
  return mockRequest(data);
}

export async function reviewSubmission(taskId, { score, decision, comment, inlineComments = [] }) {
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }

  const idx = allTasks.findIndex((t) => t.id === taskId);
  if (idx > -1) {
    allTasks[idx] = {
      ...allTasks[idx],
      status: decision === "Approved" ? "Completed" : "In Progress",
      reviewScore: score,
      reviewComment: comment,
      reviewDecision: decision,
      reviewedAt: new Date().toISOString(),
      inlineComments,
    };
    localStorage.setItem("msh_sprint_tasks", JSON.stringify(allTasks));
  }
  await mockRequest(null, { delay: 700 });
  return allTasks[idx];
}

// Analytics is fully derived from real records — real Sprints/Tasks for
// velocity, real per-student Assessment attempts (written by the Developer's
// Assessment engine, see developerService/studentService) for the quiz
// trend, and the real registered-student roster for the performance table.
// Nothing here is hardcoded demo data: every chart starts empty and fills
// in as batches run sprints, students submit tasks, and students take
// assessments — same philosophy as the rest of this app.
export async function getAnalytics(batchId) {
  const allSprints = getStoredSprints();
  const allTasks = getStoredTasks();
  const sprintsForBatch = batchId ? allSprints.filter((s) => s.batchId === batchId) : allSprints;
  const relevantSprintIds = new Set(sprintsForBatch.map((s) => s.id));

  const velocity = sprintsForBatch
    .slice()
    .sort((a, b) => new Date(a.startDate || 0) - new Date(b.startDate || 0))
    .map((s) => {
      const sprintTasks = allTasks.filter((t) => t.sprintId === s.id);
      const points = sprintTasks
        .filter((t) => t.status === "Completed")
        .reduce((sum, t) => sum + (Number(t.points) || 0), 0);
      return { sprint: `Sprint ${s.number}`, points };
    });

  const attempts = getStoredAttempts();

  // Group attempts into calendar weeks for the trend line.
  const weekBuckets = new Map();
  attempts.forEach((a) => {
    const d = new Date(a.submittedAt);
    if (Number.isNaN(d.getTime())) return;
    const weekKey = Math.floor(d.getTime() / (7 * 24 * 60 * 60 * 1000));
    const label = d.toLocaleDateString("en-IN", { day: "2-digit", month: "short" });
    if (!weekBuckets.has(weekKey)) weekBuckets.set(weekKey, { key: weekKey, label, total: 0, count: 0 });
    const bucket = weekBuckets.get(weekKey);
    bucket.total += a.score;
    bucket.count += 1;
  });
  const quizTrend = Array.from(weekBuckets.values())
    .sort((a, b) => a.key - b.key)
    .map((b) => ({ week: b.label, avg: Math.round(b.total / b.count) }));

  const roster = getStudentRosterWithBatch(attempts);
  // Auto-PIP rule engine: re-checks every student's real tasks/quiz attempts
  // against the thresholds each time Analytics is loaded, so a crossed
  // threshold gets flagged even before anyone opens PIP Management.
  runPipAutoCheckForAll({ studentBatchMap: roster, tasks: allTasks, quizAttempts: attempts });

  // roster maps student name -> batch NAME (see getStudentRosterWithBatch),
  // while batchId is the batch's id — resolve the selected batch's name so
  // the table only lists students actually enrolled in it, instead of every
  // registered student regardless of which batch is selected.
  const activeBatchName = batchId ? BATCHES.find((b) => b.id === batchId)?.name : null;

  const studentRows = Object.keys(roster)
    .filter((name) => !batchId || roster[name] === activeBatchName)
    .map((name) => {
    const myTasks = allTasks.filter((t) => t.assignee === name && (!batchId || relevantSprintIds.has(t.sprintId)));
    const completion = myTasks.length ? Math.round((myTasks.filter((t) => t.status === "Completed").length / myTasks.length) * 100) : 0;
    const myAttempts = attempts.filter((a) => a.studentName === name);
    const quiz = myAttempts.length ? Math.round(myAttempts.reduce((sum, a) => sum + a.score, 0) / myAttempts.length) : 0;
    const onPip = PIP_RECORDS.some((p) => p.student === name && p.status !== "Resolved");
    return { 
      name, 
      completion, 
      quiz, 
      onPip,
      hasTasks: myTasks.length > 0,
      hasQuizzes: myAttempts.length > 0
    };
  });

  return mockRequest({ velocity, quizTrend, studentRows });
}

export async function getPipCases() {
  const attempts = getStoredAttempts();
  const roster = getStudentRosterWithBatch(attempts);
  
  // Load tasks from localStorage dynamically for auto check
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {}

  runPipAutoCheckForAll({ studentBatchMap: roster, tasks: allTasks, quizAttempts: attempts });

  let allRecords = PIP_RECORDS;
  try {
    const raw = localStorage.getItem("msh_pip_records");
    if (raw) allRecords = JSON.parse(raw);
  } catch (e) {}

  return mockRequest(allRecords);
}

export async function triggerManualPip(payload) {
  let allRecords = PIP_RECORDS;
  try {
    const raw = localStorage.getItem("msh_pip_records");
    if (raw) allRecords = JSON.parse(raw);
  } catch (e) {}

  const record = { id: `p${Date.now()}`, status: "In Recovery", daysRemaining: 15, triggeredOn: new Date().toISOString().slice(0, 10), ...payload };
  allRecords.unshift(record);
  localStorage.setItem("msh_pip_records", JSON.stringify(allRecords));
  return mockRequest(record, { delay: 700 });
}

export async function removePipCase(id) {
  let allRecords = PIP_RECORDS;
  try {
    const raw = localStorage.getItem("msh_pip_records");
    if (raw) allRecords = JSON.parse(raw);
  } catch (e) {}

  const idx = allRecords.findIndex((p) => p.id === id);
  if (idx > -1) allRecords.splice(idx, 1);
  localStorage.setItem("msh_pip_records", JSON.stringify(allRecords));
  await mockRequest(null, { delay: 500 });
  return { id };
}

export async function updatePipCaseStatus(id, newStatus, repeatBatchName = "") {
  let allRecords = PIP_RECORDS;
  try {
    const raw = localStorage.getItem("msh_pip_records");
    if (raw) allRecords = JSON.parse(raw);
  } catch (e) {}

  const idx = allRecords.findIndex((p) => p.id === id);
  if (idx > -1) {
    const studentName = allRecords[idx].student;
    allRecords[idx] = { 
      ...allRecords[idx], 
      status: newStatus,
      resolvedOn: new Date().toISOString().slice(0, 10)
    };
    localStorage.setItem("msh_pip_records", JSON.stringify(allRecords));

    // Handle student user context updates (termination / repeat module assignment)
    try {
      const rawUsers = localStorage.getItem("mORIAH_REGISTERED_USERS");
      if (rawUsers) {
        const users = JSON.parse(rawUsers);
        const uIdx = users.findIndex(u => u.name === studentName);
        if (uIdx > -1) {
          if (newStatus === "Terminated") {
            users[uIdx].role = "terminated"; 
            users[uIdx].batch = "Terminated";
          } else if (newStatus === "Repeat Foundation" && repeatBatchName) {
            users[uIdx].batch = repeatBatchName; 
          }
          localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(users));
          
          // If active user is the student, update their context too!
          const activeUser = JSON.parse(localStorage.getItem("msh_user"));
          if (activeUser && activeUser.name === studentName) {
            const updatedActive = { 
              ...activeUser, 
              role: newStatus === "Terminated" ? "terminated" : activeUser.role,
              batch: newStatus === "Repeat Foundation" && repeatBatchName ? repeatBatchName : activeUser.batch 
            };
            localStorage.setItem("msh_user", JSON.stringify(updatedActive));
          }
        }
      }
    } catch (e) {
      console.warn("Failed to propagate PIP updates to student details:", e);
    }
  }
  return mockRequest(allRecords[idx]);
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