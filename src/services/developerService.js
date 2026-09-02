import { mockRequest } from "./apiClient";
import { PROJECTS } from "./mockData";

const BANK_KEY = "msh_assessment_bank";
const PUBLISHED_KEY = "msh_dev_assessments";

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

export async function getProjects() {
  return mockRequest(PROJECTS);
}

// ---------------------------------------------------------------------------
// Client Requirement Documents (read-only mirror of BA's Requirements
// Authoring queue) — lets a Developer see BRD/SRS/FRS files the Business
// Analyst has uploaded/verified before they start building a project, and
// track which ones they've personally reviewed. The documents themselves
// still live under "msh_ba_documents" (owned by baService); this only adds
// a developer-side "reviewed" flag per doc so BA can see it was checked.
// ---------------------------------------------------------------------------
const DEV_DOC_REVIEWS_KEY = "msh_dev_doc_reviews";

export async function getDocReviews() {
  try {
    const raw = localStorage.getItem(DEV_DOC_REVIEWS_KEY);
    return mockRequest(raw ? JSON.parse(raw) : {});
  } catch (e) {
    return mockRequest({});
  }
}

export async function markDocReviewed(docId, reviewerName = "Developer") {
  let reviews = {};
  try {
    const raw = localStorage.getItem(DEV_DOC_REVIEWS_KEY);
    reviews = raw ? JSON.parse(raw) : {};
  } catch (e) {}
  reviews[docId] = { reviewed: true, reviewedBy: reviewerName, reviewedAt: new Date().toISOString().slice(0, 10) };
  localStorage.setItem(DEV_DOC_REVIEWS_KEY, JSON.stringify(reviews));
  return mockRequest(reviews[docId]);
}

export async function createProject(payload) {
  const project = { id: `pr${Date.now()}`, status: "Draft", version: "v1.0", assignedBatches: [], ...payload };
  PROJECTS.unshift(project);
  return mockRequest(project, { delay: 700 });
}

export async function publishProject(projectId) {
  const idx = PROJECTS.findIndex((p) => p.id === projectId);
  if (idx > -1) PROJECTS[idx] = { ...PROJECTS[idx], status: "Published" };
  return mockRequest(PROJECTS[idx]);
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

const RESOURCES_KEY = "msh_shared_resources";

export async function getResourceLibrary() {
  return mockRequest(readJSON(RESOURCES_KEY, [
    { id: "r_1", title: "Git Cheat Sheet for Teams", type: "Cheat Sheet", link: "https://education.github.com/git-cheat-sheet-education.pdf", updatedAt: "2026-08-25" },
    { id: "r_2", title: "Standard REST API Response SDK", type: "Starter Template", link: "https://github.com/moriah-hub/rest-api-starter", updatedAt: "2026-08-28" }
  ]));
}

export async function createResource(payload) {
  const items = readJSON(RESOURCES_KEY, [
    { id: "r_1", title: "Git Cheat Sheet for Teams", type: "Cheat Sheet", link: "https://education.github.com/git-cheat-sheet-education.pdf", updatedAt: "2026-08-25" },
    { id: "r_2", title: "Standard REST API Response SDK", type: "Starter Template", link: "https://github.com/moriah-hub/rest-api-starter", updatedAt: "2026-08-28" }
  ]);
  const newItem = {
    id: `r_${Date.now()}`,
    updatedAt: new Date().toISOString().split('T')[0],
    ...payload
  };
  items.unshift(newItem);
  writeJSON(RESOURCES_KEY, items);
  return mockRequest(newItem, { delay: 400 });
}

// ---------------------------------------------------------------------------
// Assessment engine — everything a Student ever takes on their Assessments
// page is authored here first. A "bank" is a reusable set of real questions
// (actual MCQ options + correct answer, or actual code specs + test cases).
// Publishing a bank turns it into a live Assessment students can see and
// take; the questions are snapshotted at publish time so editing a bank
// later doesn't silently change an assessment students already started.
// ---------------------------------------------------------------------------

export async function getAssessmentBanks() {
  return mockRequest(readJSON(BANK_KEY));
}

export async function createAssessmentBank({ title, type }) {
  const banks = readJSON(BANK_KEY);
  const bank = { id: `bank_${Date.now()}`, title, type, questions: [], createdAt: new Date().toISOString() };
  banks.unshift(bank);
  writeJSON(BANK_KEY, banks);
  return mockRequest(bank, { delay: 400 });
}

export async function deleteAssessmentBank(bankId) {
  const banks = readJSON(BANK_KEY).filter((b) => b.id !== bankId);
  writeJSON(BANK_KEY, banks);
  return mockRequest(null);
}

// question = { text, options, correctAnswer } for MCQ
//          | { text, starterCode, functionName, testCases: [{ name, args, expected }] } for Code
export async function addQuestionToBank(bankId, question) {
  const banks = readJSON(BANK_KEY);
  const idx = banks.findIndex((b) => b.id === bankId);
  if (idx === -1) throw new Error("Question bank not found.");
  const q = { id: `q_${Date.now()}`, ...question };
  banks[idx] = { ...banks[idx], questions: [...banks[idx].questions, q] };
  writeJSON(BANK_KEY, banks);
  return mockRequest(banks[idx], { delay: 300 });
}

export async function removeQuestionFromBank(bankId, questionId) {
  const banks = readJSON(BANK_KEY);
  const idx = banks.findIndex((b) => b.id === bankId);
  if (idx === -1) throw new Error("Question bank not found.");
  banks[idx] = { ...banks[idx], questions: banks[idx].questions.filter((q) => q.id !== questionId) };
  writeJSON(BANK_KEY, banks);
  return mockRequest(banks[idx]);
}

// Adds many MCQ questions to a bank at once — used by the "Bulk Upload"
// flow in Assessment Bank, where questions arrive already parsed from a
// CSV or Word/text file (see utils/questionFileParser.js). Each question
// must already be in the { text, options, correctAnswer } shape used
// elsewhere in this file.
export async function bulkAddQuestionsToBank(bankId, questions) {
  const banks = readJSON(BANK_KEY);
  const idx = banks.findIndex((b) => b.id === bankId);
  if (idx === -1) throw new Error("Question bank not found.");
  const withIds = questions.map((q, i) => ({ id: `q_${Date.now()}_${i}`, type: "MCQ", ...q }));
  banks[idx] = { ...banks[idx], questions: [...banks[idx].questions, ...withIds] };
  writeJSON(BANK_KEY, banks);
  return mockRequest(banks[idx], { delay: 400 });
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

const VIDEO_LESSONS_KEY = "msh_dev_video_lessons";

export async function getDevVideoLessons() {
  return mockRequest(readJSON(VIDEO_LESSONS_KEY));
}

export async function createVideoLesson({ title, module, description, videoId, videoUrl, videoType = "youtube", duration }) {
  const lessons = readJSON(VIDEO_LESSONS_KEY);
  const lesson = {
    id: `vl_${Date.now()}`,
    title,
    module,
    description,
    videoType,
    // Only one of these is ever populated, based on videoType — Student >
    // Learning picks which player to render off of videoType (see
    // pages/student/Learning.jsx).
    videoId: videoType === "youtube" ? videoId : "",
    videoUrl: videoType === "upload" ? videoUrl : "",
    duration,
    passingScore: 60,
    quiz: [],
    status: "Draft",
    createdAt: new Date().toISOString(),
  };
  lessons.unshift(lesson);
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(lesson, { delay: 400 });
}

export async function deleteVideoLesson(id) {
  const lessons = readJSON(VIDEO_LESSONS_KEY).filter((l) => l.id !== id);
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(null);
}

// question = { question, options: [4], correctAnswer }
export async function addQuestionToLesson(lessonId, question) {
  const lessons = readJSON(VIDEO_LESSONS_KEY);
  const idx = lessons.findIndex((l) => l.id === lessonId);
  if (idx === -1) throw new Error("Video lesson not found.");
  const q = { id: `q_${Date.now()}`, ...question };
  lessons[idx] = { ...lessons[idx], quiz: [...lessons[idx].quiz, q] };
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(lessons[idx], { delay: 300 });
}

export async function removeQuestionFromLesson(lessonId, questionId) {
  const lessons = readJSON(VIDEO_LESSONS_KEY);
  const idx = lessons.findIndex((l) => l.id === lessonId);
  if (idx === -1) throw new Error("Video lesson not found.");
  lessons[idx] = { ...lessons[idx], quiz: lessons[idx].quiz.filter((q) => q.id !== questionId) };
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(lessons[idx]);
}

export async function publishVideoLesson(id) {
  const lessons = readJSON(VIDEO_LESSONS_KEY);
  const idx = lessons.findIndex((l) => l.id === id);
  if (idx === -1) throw new Error("Video lesson not found.");
  if (!lessons[idx].quiz.length) throw new Error("Add at least one quiz question before publishing.");
  lessons[idx] = { ...lessons[idx], status: "Published", publishedAt: new Date().toISOString() };
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(lessons[idx], { delay: 400 });
}

export async function unpublishVideoLesson(id) {
  const lessons = readJSON(VIDEO_LESSONS_KEY);
  const idx = lessons.findIndex((l) => l.id === id);
  if (idx > -1) lessons[idx] = { ...lessons[idx], status: "Draft" };
  writeJSON(VIDEO_LESSONS_KEY, lessons);
  return mockRequest(lessons[idx]);
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