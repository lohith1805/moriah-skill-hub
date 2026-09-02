import { mockRequest } from "./apiClient";
import { TASKS, PIP_RECORDS, CERTIFICATES, BATCHES, SPRINTS } from "./mockData";
import { SUBSCRIPTION_PLANS } from "../utils/constants";
import { getPersistedUser } from "./authService";
import { evaluateStudentAutoPip } from "./pipEngine";
import { trySyncGraduateToTalentPool } from "./hrService";

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

export async function getMyTasks() {
  const user = getPersistedUser();
  if (!user) return mockRequest([]);
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }
  
  const name = user.name || "Student";
  const myTasks = allTasks.filter((t) => t.assignee === name);
  return mockRequest(myTasks);
}

// A student's account only stores their batch's NAME (see authService's
// registerStudent), while Trainer's Sprint Planning links a sprint to a
// batch by ID — so look the batch up by name first to resolve its ID, then
// pull every sprint scheduled for it. This is how "trainer creates a sprint
// for a batch" becomes visible on the Student Sprint Board.
export async function getMySprints() {
  const user = getPersistedUser();
  if (!user) return mockRequest([]);

  const batch = BATCHES.find((b) => b.name === user.batch);
  if (!batch) return mockRequest([]);

  const mySprints = SPRINTS.filter((s) => s.batchId === batch.id).sort(
    (a, b) => new Date(b.startDate || 0) - new Date(a.startDate || 0)
  );
  return mockRequest(mySprints);
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

export async function updateTaskStatus(taskId, status) {
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }

  const idx = allTasks.findIndex((t) => t.id === taskId);
  if (idx > -1) {
    allTasks[idx] = { ...allTasks[idx], status };
    localStorage.setItem("msh_sprint_tasks", JSON.stringify(allTasks));
  }
  return mockRequest(allTasks[idx]);
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

export async function getMyPipStatus() {
  const user = getPersistedUser();
  if (!user) return mockRequest(null);
  runAutoPipCheckForCurrentUser(user);

  // Read fresh from localStorage — a trainer raising a PIP (manually, or via
  // the rule engine) writes into "msh_pip_records" at runtime, after this
  // module's PIP_RECORDS snapshot may already be loaded, so we can't rely on
  // the stale in-memory array here (same reasoning as getCertificates below).
  let allPipRecords = PIP_RECORDS;
  try {
    const raw = localStorage.getItem("msh_pip_records");
    if (raw) allPipRecords = JSON.parse(raw);
  } catch (e) {
    allPipRecords = PIP_RECORDS;
  }

  const name = user.name || "Student";
  const mine = allPipRecords.filter((p) => p.student === name);
  if (!mine.length) return mockRequest(null);

  // Prefer an active (non-resolved) case; fall back to the most recent one.
  const active = mine.find((p) => p.status !== "Resolved");
  return mockRequest(active || mine[0]);
}

export async function getPlans() {
  return mockRequest(SUBSCRIPTION_PLANS);
}

export async function subscribeToPlan(planCode, paymentMethod) {
  await mockRequest(null, { delay: 900 });
  return { invoiceId: `INV-${Date.now()}`, status: "success", planCode, paymentMethod };
}

export async function getCertificates() {
  const user = getPersistedUser();
  if (!user) return mockRequest([]);

  // Read fresh from localStorage — Graduation approvals write new
  // certificates at runtime, after this module's CERTIFICATES snapshot was
  // already loaded, so we can't rely on the stale in-memory array here.
  let allCertificates = CERTIFICATES;
  try {
    const raw = localStorage.getItem("msh_certificates");
    if (raw) allCertificates = JSON.parse(raw);
  } catch (e) {
    allCertificates = CERTIFICATES;
  }

  const name = (user.name || "").toLowerCase();
  const email = (user.email || "").toLowerCase();

  // A student only sees certificates actually issued to them via Trainer's
  // Graduation Approval.
  const myCertificates = allCertificates.filter((c) => {
    return (c.studentEmail && c.studentEmail.toLowerCase() === email) || c.studentName?.toLowerCase() === name;
  });

  return mockRequest(myCertificates);
}

export async function submitGithubPR(taskId, prUrl, videoUrl = "") {
  let allTasks = TASKS;
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    if (raw) allTasks = JSON.parse(raw);
  } catch (e) {
    allTasks = TASKS;
  }

  const idx = allTasks.findIndex((t) => t.id === taskId);
  if (idx > -1) {
    allTasks[idx] = { ...allTasks[idx], githubPr: prUrl, videoUrl, status: "Review" };
    localStorage.setItem("msh_sprint_tasks", JSON.stringify(allTasks));
  }
  return mockRequest(allTasks[idx]);
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

function readPublishedVideoLessons() {
  try {
    const raw = localStorage.getItem("msh_dev_video_lessons");
    const list = raw ? JSON.parse(raw) : [];
    return list.filter((l) => l.status === "Published");
  } catch (e) {
    return [];
  }
}

function readLessonProgress() {
  try {
    const raw = localStorage.getItem("msh_lesson_progress");
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

function writeLessonProgress(list) {
  localStorage.setItem("msh_lesson_progress", JSON.stringify(list));
}

export async function getVideoLessons() {
  const user = getPersistedUser();
  const email = (user?.email || "").toLowerCase();
  const progress = readLessonProgress().filter((p) => p.studentEmail === email);

  const list = readPublishedVideoLessons().map((lesson) => {
    const p = progress.find((pr) => pr.lessonId === lesson.id);
    return {
      id: lesson.id,
      module: lesson.module,
      title: lesson.title,
      description: lesson.description,
      duration: lesson.duration,
      questionCount: lesson.quiz.length,
      watched: p?.watched || false,
      quizScore: p?.quizScore ?? null,
      quizPassed: p?.quizPassed ?? null,
    };
  });

  return mockRequest(list);
}

export async function getVideoLessonDetail(id) {
  const lesson = readPublishedVideoLessons().find((l) => l.id === id);
  if (!lesson) return mockRequest(null);
  return mockRequest(lesson);
}

export async function markLessonWatched(id) {
  const user = getPersistedUser();
  const email = (user?.email || "").toLowerCase();
  const progress = readLessonProgress();
  const idx = progress.findIndex((p) => p.lessonId === id && p.studentEmail === email);

  if (idx > -1) {
    progress[idx] = { ...progress[idx], watched: true };
  } else {
    progress.push({ lessonId: id, studentEmail: email, watched: true, quizScore: null, quizPassed: null });
  }
  writeLessonProgress(progress);
  return mockRequest({ ok: true });
}

export async function submitLessonQuiz(id, score) {
  const user = getPersistedUser();
  const email = (user?.email || "").toLowerCase();
  const lesson = readPublishedVideoLessons().find((l) => l.id === id);
  const passed = score >= (lesson?.passingScore ?? 60);

  const progress = readLessonProgress();
  const idx = progress.findIndex((p) => p.lessonId === id && p.studentEmail === email);
  const entry = { lessonId: id, studentEmail: email, watched: true, quizScore: score, quizPassed: passed, submittedAt: new Date().toISOString() };

  if (idx > -1) {
    progress[idx] = { ...progress[idx], ...entry };
  } else {
    progress.push(entry);
  }
  writeLessonProgress(progress);
  return mockRequest(entry);
}