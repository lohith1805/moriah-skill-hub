import { mockRequest, apiClient } from "./apiClient";

const BANK_KEY = "msh_assessment_bank";
const PUBLISHED_KEY = "msh_dev_assessments";

// ---------------------------------------------------------------------------
// Projects — real backend (feature 15). GET/POST/PUT /api/v1/projects,
// POST /projects/{id}/publish, GET /projects/{id}/challenges.
// Non-admin callers only ever see PUBLISHED; a DEVELOPER additionally authors
// DRAFTs and publishes them. The backend Project is intentionally lean —
// title, description, techStack, difficulty, domain, starterRepoUrl, version,
// status, assets[], challenges[]. There is no delete (edit a DRAFT, or bump a
// PUBLISHED one into a new DRAFT version) and no "assigned batches" on the
// project itself (a project attaches to a task, feature 11).
// ---------------------------------------------------------------------------

const PROJECT_STATUS_TO_FE = { DRAFT: "Draft", PUBLISHED: "Published", ARCHIVED: "Archived" };
const DIFFICULTY_TO_FE = { BEGINNER: "Beginner", INTERMEDIATE: "Intermediate", ADVANCED: "Advanced" };
const DIFFICULTY_TO_API = { Beginner: "BEGINNER", Intermediate: "INTERMEDIATE", Advanced: "ADVANCED" };

function toFeProject(p) {
  return {
    id: p.id,
    title: p.title,
    slug: p.slug,
    description: p.description || "",
    stack: p.techStack || [],
    difficulty: DIFFICULTY_TO_FE[p.difficulty] || p.difficulty || "",
    domain: p.domain || "",
    starterRepo: p.starterRepoUrl || "",
    version: p.version || "v1",
    status: PROJECT_STATUS_TO_FE[p.status] || p.status,
    backendStatus: p.status,
    createdBy: p.createdByFullName || "",
    assets: p.assets || [],
    challenges: (p.challenges || []).map(toFeChallenge),
  };
}

function toFeChallenge(c) {
  return {
    id: c.id,
    title: c.title,
    expectedBehaviour: c.expectedBehaviour || "",
    brokenCodeUrl: c.brokenCodeUrl || "",
    testScriptUrl: c.testScriptUrl || "",
    difficulty: DIFFICULTY_TO_FE[c.difficulty] || c.difficulty || "",
  };
}

export async function getProjects({ status, difficulty, domain } = {}) {
  const res = await apiClient.get("/projects", {
    status: status || undefined,
    difficulty: difficulty ? DIFFICULTY_TO_API[difficulty] || difficulty : undefined,
    domain: domain || undefined,
    size: 100,
  });
  return (res && res.content ? res.content : []).map(toFeProject);
}

// payload: { title, stack (comma string or array), difficulty, description, domain, starterRepo, version }
export async function createProject(payload) {
  const body = {
    title: (payload.title || "").trim(),
    description: payload.description || undefined,
    techStack: Array.isArray(payload.stack)
      ? payload.stack
      : String(payload.stack || "").split(",").map((s) => s.trim()).filter(Boolean),
    difficulty: DIFFICULTY_TO_API[payload.difficulty] || payload.difficulty || undefined,
    domain: payload.domain || undefined,
    starterRepoUrl: payload.starterRepo || undefined,
    version: payload.version || undefined,
  };
  return toFeProject(await apiClient.post("/projects", body));
}

export async function updateProject(id, payload) {
  const body = {
    title: payload.title,
    description: payload.description || undefined,
    techStack: Array.isArray(payload.stack)
      ? payload.stack
      : String(payload.stack || "").split(",").map((s) => s.trim()).filter(Boolean),
    difficulty: DIFFICULTY_TO_API[payload.difficulty] || payload.difficulty || undefined,
    domain: payload.domain || undefined,
    starterRepoUrl: payload.starterRepo || undefined,
    version: payload.version || undefined,
  };
  return toFeProject(await apiClient.put(`/projects/${id}`, body));
}

export async function publishProject(id) {
  return toFeProject(await apiClient.post(`/projects/${id}/publish`));
}

export async function getProjectChallenges(projectId) {
  const res = await apiClient.get(`/projects/${projectId}/challenges`);
  return (Array.isArray(res) ? res : res?.content || []).map(toFeChallenge);
}

function readJSON(key, fallback = []) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch (e) {
    return fallback;
  }
}
function writeJSON(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

// ---------------------------------------------------------------------------
// Client Requirement Documents (read-only mirror of BA's Requirements
// Authoring queue) — lets a Developer see BRD/SRS/FRS files the Business
// Analyst has uploaded/verified before they start building a project, and
// track which ones they've personally reviewed. The documents themselves
// still live under "msh_ba_documents" (owned by baService); this only adds
// a developer-side "reviewed" flag per doc so BA can see it was checked.
// ---------------------------------------------------------------------------
// Backend gap B1.16 — GET /api/v1/dev/requirement-documents + POST /{id}/acknowledge.
// The document list AND the per-developer "reviewed" flag come from the same
// endpoint (RequirementDocumentResponse.devReviewedAt / devReviewedByFullName).

const DOC_STATUS_TO_FE = { DRAFT: "Draft", IN_REVIEW: "Under Review", APPROVED: "Approved" };

function toFeRequirementDoc(d) {
  return {
    id: d.id,
    clientProjectId: d.clientProjectId,
    docType: d.docType,
    title: d.title,
    version: d.version,
    status: DOC_STATUS_TO_FE[d.status] || d.status,
    backendStatus: d.status,
    authoredBy: d.authoredByFullName,
    approvedBy: d.approvedByFullName,
    devReviewed: !!d.devReviewedAt,
    devReviewedBy: d.devReviewedByFullName,
    devReviewedAt: (d.devReviewedAt || "").slice(0, 10),
  };
}

export async function getDevRequirementDocs({ clientProjectId, status } = {}) {
  const res = await apiClient.get("/dev/requirement-documents", {
    clientProjectId,
    status,
    size: 100,
  });
  return (res && res.content ? res.content : []).map(toFeRequirementDoc);
}

export async function getRequirementDocDetail(id) {
  const d = await apiClient.get(`/dev/requirement-documents/${id}`);
  return { ...toFeRequirementDoc(d), content: d.content };
}

// Returns the { [docId]: {reviewed, reviewedBy, reviewedAt} } map the
// ClientRequirements page keys off.
export async function getDocReviews() {
  const docs = await getDevRequirementDocs();
  const map = {};
  docs.forEach((d) => {
    if (d.devReviewed) {
      map[d.id] = { reviewed: true, reviewedBy: d.devReviewedBy, reviewedAt: d.devReviewedAt };
    }
  });
  return map;
}

export async function markDocReviewed(docId) {
  const d = await apiClient.post(`/dev/requirement-documents/${docId}/acknowledge`);
  return { reviewed: true, reviewedBy: d.devReviewedByFullName, reviewedAt: (d.devReviewedAt || "").slice(0, 10) };
}

export async function getBugChallenges() {
  return mockRequest([]);
}

// ---------------------------------------------------------------------------
// Bug Challenges (Developer > Bug Challenges) — a challenge is real,
// auto-graded broken code, not just a label: starterCode is the buggy
// function the student edits, and testCases (functionName + args +
// expected, same shape as Assessment Bank's Code questions) decide whether
// their fix actually works. studentService.getBugChallengeDetails
// reconstructs the same new Function(...) test runner used for Assessments,
// and attemptBugChallenge() is what actually increments solved/attempts —
// they're no longer hand-typed numbers.
// ---------------------------------------------------------------------------

const BUG_CHALLENGES_KEY = "msh_bug_challenges";

export async function getDevBugChallenges() {
  return mockRequest(readJSON(BUG_CHALLENGES_KEY));
}

// payload = { title, project, difficulty, description, functionName, starterCode, testCases }
// testCases = [{ name, args, expected }] — same shape as Assessment Bank's Code questions.
export async function createBugChallenge(payload) {
  const challenges = readJSON(BUG_CHALLENGES_KEY);
  const challenge = {
    id: `bc_${Date.now()}`,
    solved: 0,
    attempts: 0,
    ...payload,
    testCases: (payload.testCases || []).map((tc, i) => ({ id: tc.id || i + 1, ...tc })),
    createdAt: new Date().toISOString(),
  };
  challenges.unshift(challenge);
  writeJSON(BUG_CHALLENGES_KEY, challenges);
  return mockRequest(challenge, { delay: 400 });
}

export async function updateBugChallenge(id, payload) {
  const challenges = readJSON(BUG_CHALLENGES_KEY);
  const idx = challenges.findIndex((c) => c.id === id);
  if (idx === -1) throw new Error("Bug challenge not found.");
  challenges[idx] = {
    ...challenges[idx],
    ...payload,
    testCases: payload.testCases
      ? payload.testCases.map((tc, i) => ({ id: tc.id || i + 1, ...tc }))
      : challenges[idx].testCases,
  };
  writeJSON(BUG_CHALLENGES_KEY, challenges);
  return mockRequest(challenges[idx], { delay: 300 });
}

export async function deleteBugChallenge(id) {
  const challenges = readJSON(BUG_CHALLENGES_KEY).filter((c) => c.id !== id);
  writeJSON(BUG_CHALLENGES_KEY, challenges);
  return mockRequest(null);
}

// ---------------------------------------------------------------------------
// Resource Library (backend gap B1.6) — GET/POST/PUT/DELETE /api/v1/resources.
// One shared library read by developer/, trainer/ and student/ Resources pages.
// Backend row: { id, title, description, category, url, tags[], active,
// createdByUuid, createdAt, updatedAt }. The UI wants { id, title, type, link,
// updatedAt } — the FE's free-text "type" label is stored verbatim as tags[0]
// so it round-trips exactly.
// ---------------------------------------------------------------------------

const RESOURCE_CATEGORY_FOR_TYPE = {
  "cheat sheet": "OTHER",
  "sdk documentation": "ARTICLE",
  "starter template": "TEMPLATE",
  "shared library": "TOOL",
  "boilerplate": "TEMPLATE",
  article: "ARTICLE",
  video: "VIDEO",
  book: "BOOK",
  tool: "TOOL",
  template: "TEMPLATE",
  course: "COURSE",
};

function toFeResource(r) {
  const tag = Array.isArray(r.tags) && r.tags.length ? r.tags[0] : null;
  const type =
    tag ||
    (r.category
      ? r.category.charAt(0) + r.category.slice(1).toLowerCase().replace(/_/g, " ")
      : "Resource");
  return {
    id: r.id,
    title: r.title,
    description: r.description || "",
    type,
    category: r.category,
    link: r.url,
    active: r.active !== false,
    updatedAt: (r.updatedAt || r.createdAt || "").slice(0, 10),
    createdByUuid: r.createdByUuid,
  };
}

export async function getResourceLibrary() {
  const res = await apiClient.get("/resources", { size: 100 });
  return (res && res.content ? res.content : []).map(toFeResource);
}

export async function createResource({ title, type, link, description }) {
  const category = RESOURCE_CATEGORY_FOR_TYPE[String(type || "").toLowerCase()] || "OTHER";
  const created = await apiClient.post("/resources", {
    title,
    description: description || undefined,
    category,
    url: link,
    tags: type ? [type] : undefined,
  });
  return toFeResource(created);
}

export async function updateResource(id, { title, type, link, description, active }) {
  const category = RESOURCE_CATEGORY_FOR_TYPE[String(type || "").toLowerCase()] || "OTHER";
  const updated = await apiClient.put(`/resources/${id}`, {
    title,
    description: description || undefined,
    category,
    url: link,
    tags: type ? [type] : undefined,
    active: active !== false,
  });
  return toFeResource(updated);
}

export async function deleteResource(id) {
  await apiClient.del(`/resources/${id}`);
  return { id };
}

// ---------------------------------------------------------------------------
// Assessment engine — everything a Student ever takes on their Assessments
// page is authored here first. A "bank" is a reusable set of real questions
// (actual MCQ options + correct answer, or actual code specs + test cases).
// Publishing a bank turns it into a live Assessment students can see and
// take; the questions are snapshotted at publish time so editing a bank
// later doesn't silently change an assessment students already started.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Assessment question bank (backend gap B1.15) — /api/v1/assessments/banks.
// Bank metadata + MCQ questions live on the backend. The FE's richer CODE
// question (starterCode + functionName + testCases for an in-browser test
// runner) has no dedicated backend columns, so it is JSON-encoded into the
// question's `explanation` field and decoded back on read — one field,
// fully round-tripped, backend `correctAnswer` keys are never returned.
// ---------------------------------------------------------------------------

function toFeQuestion(q) {
  if (q.questionType === "CODE") {
    let spec = {};
    try {
      spec = q.explanation ? JSON.parse(q.explanation) : {};
    } catch {
      spec = {};
    }
    return {
      id: q.id,
      type: "CODE",
      text: spec.text || q.questionText,
      starterCode: spec.starterCode || "",
      functionName: spec.functionName || "",
      testCases: spec.testCases || [],
    };
  }
  return {
    id: q.id,
    type: "MCQ",
    text: q.questionText,
    options: q.options || [],
    // correctAnswer is intentionally not returned by the backend
    marks: q.marks,
    explanation: q.explanation || "",
  };
}

function toFeBank(b, questions = []) {
  return {
    id: b.id,
    title: b.name,
    type: b.topic,
    description: b.description || "",
    active: b.active !== false,
    questionCount: b.questionCount ?? questions.length,
    questions,
    createdAt: b.createdAt,
  };
}

async function fetchBankQuestions(bankId) {
  const res = await apiClient.get(`/assessments/banks/${bankId}/questions`, { size: 200 });
  return (res && res.content ? res.content : []).map(toFeQuestion);
}

export async function getAssessmentBanks() {
  const res = await apiClient.get("/assessments/banks", { size: 100 });
  const banks = res && res.content ? res.content : [];
  // Few banks in an authoring view — embed each bank's questions.
  return Promise.all(banks.map(async (b) => toFeBank(b, await fetchBankQuestions(b.id))));
}

export async function createAssessmentBank({ title, type, description }) {
  const b = await apiClient.post("/assessments/banks", {
    name: title,
    topic: type || "general",
    description: description || undefined,
  });
  return toFeBank(b, []);
}

export async function updateAssessmentBank(bankId, { title, type, description, active }) {
  const b = await apiClient.put(`/assessments/banks/${bankId}`, {
    name: title,
    topic: type || "general",
    description: description || undefined,
    active: active !== false,
  });
  return toFeBank(b);
}

export async function deleteAssessmentBank(bankId) {
  await apiClient.del(`/assessments/banks/${bankId}`);
  return { id: bankId };
}

// question = { type:"MCQ", text, options, correctAnswer }
//          | { type:"CODE", text, starterCode, functionName, testCases }
export async function addQuestionToBank(bankId, question) {
  if ((question.type || "MCQ") === "CODE") {
    const created = await apiClient.post(`/assessments/banks/${bankId}/questions`, {
      questionText: question.text,
      questionType: "CODE",
      marks: 1,
      explanation: JSON.stringify({
        text: question.text,
        starterCode: question.starterCode || "",
        functionName: question.functionName || "",
        testCases: question.testCases || [],
      }),
    });
    return toFeQuestion(created);
  }
  const created = await apiClient.post(`/assessments/banks/${bankId}/questions`, {
    questionText: question.text,
    questionType: "MCQ",
    options: question.options,
    correctAnswerIndices: [Number(question.correctAnswer)],
    marks: question.marks || 1,
    explanation: question.explanation || undefined,
  });
  return toFeQuestion(created);
}

export async function removeQuestionFromBank(bankId, questionId) {
  await apiClient.del(`/assessments/banks/${bankId}/questions/${questionId}`);
  return { id: questionId };
}

// Bulk MCQ import (CSV / Word / text — parsed by utils/questionFileParser.js).
// Each question is { text, options, correctAnswer }.
export async function bulkAddQuestionsToBank(bankId, questions) {
  const added = [];
  for (const q of questions) {
    // Sequential so a mid-batch failure leaves a coherent partial state.
    added.push(await addQuestionToBank(bankId, { type: "MCQ", ...q }));
  }
  return added;
}

export async function getPublishedAssessments() {
  return mockRequest(readJSON(PUBLISHED_KEY));
}

// batchIds: [] means visible to every batch/student (no roster restriction
// exists in this data model yet, so an empty list is the honest default —
// see studentService.getAssessments for how this is consumed).
export async function publishAssessment({ bankId, title, durationMinutes, passingScore, batchIds }) {
  const banks = readJSON(BANK_KEY);
  const bank = banks.find((b) => b.id === bankId);
  if (!bank) throw new Error("Select a question bank first.");
  if (!bank.questions.length) throw new Error("This question bank has no questions yet — add at least one.");

  const published = readJSON(PUBLISHED_KEY);
  const assessment = {
    id: `asmt_${Date.now()}`,
    bankId,
    title: title || bank.title,
    type: bank.type,
    duration: `${durationMinutes} mins`,
    passingScore: Number(passingScore) || 60,
    batchIds: batchIds || [],
    status: "Published",
    questions: bank.questions, // snapshot
    publishedAt: new Date().toISOString(),
  };
  published.unshift(assessment);
  writeJSON(PUBLISHED_KEY, published);
  return mockRequest(assessment, { delay: 500 });
}

export async function deletePublishedAssessment(id) {
  const published = readJSON(PUBLISHED_KEY).filter((a) => a.id !== id);
  writeJSON(PUBLISHED_KEY, published);
  return mockRequest(null);
}

export async function getAssessmentAttempts() {
  return mockRequest(readJSON("msh_assessment_attempts"));
}

// Lightweight read of Trainer's real batch list, so "Publish Assessment"
// can optionally target specific batches instead of everyone.
export async function getBatchesForAssignment() {
  return mockRequest(readJSON("msh_batches"));
}

// ---------------------------------------------------------------------------
// Video Lesson authoring (MSH-FR-DEV-04) — Developer creates and publishes
// video lessons here; studentService.getVideoLessons() reads whatever is
// published. Same Draft -> Published lifecycle as the Assessment engine
// above, so a lesson isn't visible to students until explicitly published.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Video Lessons (backend gap B1.4) — /api/v1/lessons + /api/v1/lessons/{id}/quiz.
// Backend lesson: { id, title, description, moduleName, videoUrl,
// durationSeconds, published, ... }. The FE splits youtube vs upload by
// videoType, derived here from the URL. Quiz questions come from the
// dedicated /quiz endpoints and are embedded as `quiz` for the authoring UI.
// ---------------------------------------------------------------------------

const YT_HOST = /(?:youtube\.com|youtu\.be)/i;

function secondsToClock(s) {
  if (!s || s < 0) return "";
  const m = Math.floor(s / 60);
  const sec = s % 60;
  return `${m}:${String(sec).padStart(2, "0")}`;
}

function clockToSeconds(v) {
  if (v == null || v === "") return undefined;
  if (typeof v === "number") return Math.round(v > 0 && v < 600 ? v * 60 : v); // bare number -> minutes if small
  const parts = String(v).split(":").map(Number);
  if (parts.some(Number.isNaN)) return undefined;
  return parts.reduce((acc, n) => acc * 60 + n, 0);
}

function toFeLesson(l, quiz = []) {
  const url = l.videoUrl || "";
  const isYt = YT_HOST.test(url);
  return {
    id: l.id,
    title: l.title,
    module: l.moduleName,
    description: l.description || "",
    videoType: isYt ? "youtube" : "upload",
    videoId: isYt ? extractYouTubeId(url) : "",
    videoUrl: isYt ? "" : url,
    duration: secondsToClock(l.durationSeconds),
    durationSeconds: l.durationSeconds,
    passingScore: 60,
    quiz,
    status: l.published ? "Published" : "Draft",
    published: !!l.published,
    progress: l.progress,
    createdAt: l.createdAt,
    updatedAt: l.updatedAt,
  };
}

async function fetchLessonQuiz(lessonId) {
  const list = await apiClient.get(`/lessons/${lessonId}/quiz`);
  return (Array.isArray(list) ? list : []).map((q) => ({
    id: q.id,
    question: q.questionText,
    options: q.options || [],
    sortOrder: q.sortOrder,
    // correctAnswer is not returned by the backend
  }));
}

export async function getDevVideoLessons() {
  const res = await apiClient.get("/lessons", { includeUnpublished: true, size: 100 });
  const lessons = res && res.content ? res.content : [];
  return Promise.all(lessons.map(async (l) => toFeLesson(l, await fetchLessonQuiz(l.id))));
}

export async function createVideoLesson({ title, module, description, videoId, videoUrl, videoType = "youtube", duration }) {
  const finalUrl =
    videoType === "youtube"
      ? `https://www.youtube.com/watch?v=${extractYouTubeId(videoId || videoUrl)}`
      : videoUrl;
  const created = await apiClient.post("/lessons", {
    title,
    description: description || undefined,
    moduleName: module,
    videoUrl: finalUrl,
    durationSeconds: clockToSeconds(duration),
    sortOrder: 0,
    published: false,
  });
  return toFeLesson(created, []);
}

export async function updateVideoLesson(id, { title, module, description, videoId, videoUrl, videoType, duration, published }) {
  const finalUrl =
    videoType === "youtube"
      ? `https://www.youtube.com/watch?v=${extractYouTubeId(videoId || videoUrl)}`
      : videoUrl;
  const updated = await apiClient.put(`/lessons/${id}`, {
    title,
    description: description || undefined,
    moduleName: module,
    videoUrl: finalUrl,
    durationSeconds: clockToSeconds(duration),
    sortOrder: 0,
    published: !!published,
  });
  return toFeLesson(updated);
}

export async function deleteVideoLesson(id) {
  // Backend row-deletes an untouched lesson, else unpublishes it.
  await apiClient.del(`/lessons/${id}`);
  return { id };
}

// question = { question, options: [4], correctAnswer }  (correctAnswer = 0-based index)
export async function addQuestionToLesson(lessonId, question) {
  const created = await apiClient.post(`/lessons/${lessonId}/quiz/questions`, {
    questionText: question.question,
    options: question.options,
    correctIndex: Number(question.correctAnswer),
    explanation: question.explanation || undefined,
  });
  return { id: created.id, question: created.questionText, options: created.options };
}

export async function removeQuestionFromLesson(lessonId, questionId) {
  await apiClient.del(`/lessons/${lessonId}/quiz/questions/${questionId}`);
  return { id: questionId };
}

export async function publishVideoLesson(id) {
  // The FE enforces "at least one quiz question" before calling this; the
  // backend PUT just flips is_published. Re-send the lesson with published=true.
  const lesson = await apiClient.get(`/lessons/${id}`);
  const updated = await apiClient.put(`/lessons/${id}`, {
    title: lesson.title,
    description: lesson.description || undefined,
    moduleName: lesson.moduleName,
    videoUrl: lesson.videoUrl,
    durationSeconds: lesson.durationSeconds ?? undefined,
    sortOrder: lesson.sortOrder ?? 0,
    published: true,
  });
  return toFeLesson(updated);
}

export async function unpublishVideoLesson(id) {
  // DELETE unpublishes a lesson that has learner history (and hard-deletes
  // an untouched one). The FE calls this only for lessons with a real
  // Published lifecycle, so it maps to unpublish.
  await apiClient.del(`/lessons/${id}`);
  return { id };
}

// Accepts a full YouTube URL (watch?v=, youtu.be/, embed/) or a bare 11-char
// video ID, and returns just the ID -- so authors can paste whatever they
// have on hand.
export function extractYouTubeId(input) {
  if (!input) return "";
  const trimmed = input.trim();
  if (/^[a-zA-Z0-9_-]{11}$/.test(trimmed)) return trimmed;
  const patterns = [/[?&]v=([a-zA-Z0-9_-]{11})/, /youtu\.be\/([a-zA-Z0-9_-]{11})/, /embed\/([a-zA-Z0-9_-]{11})/];
  for (const re of patterns) {
    const match = trimmed.match(re);
    if (match) return match[1];
  }
  return trimmed;
}