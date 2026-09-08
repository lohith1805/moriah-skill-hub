// PIP (Performance Improvement Plan) — feature 17.
//   GET    /api/v1/pip?batchId=&status=            browse records (PM/HR/ADMIN)
//   GET    /api/v1/pip/me                          the caller's own record (STUDENT)
//   GET    /api/v1/pip/{id}/progress               recovery-progress panel (PM/HR/ADMIN)
//   GET    /api/v1/pip/me/progress                 the same panel, own record (STUDENT)
//   POST   /api/v1/pip                             raise a PIP by hand (PM/ADMIN)
//   POST   /api/v1/pip/{id}/milestones             add a recovery task (PM/ADMIN)
//   DELETE /api/v1/pip/{id}/milestones/{mid}       remove a pending recovery task
//   POST   /api/v1/pip/{id}/milestones/{mid}/complete
//   POST   /api/v1/pip/{id}/review                 CLEARED | TERMINATED | REASSIGNED
//   GET/POST /api/v1/reviews/weekly                weekly qualitative ratings
import { apiClient } from "./apiClient";

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

// --- vocabulary ------------------------------------------------------------

export const PIP_STATUS_LABEL = {
  TRIGGERED: "Triggered",
  IN_PROGRESS: "In recovery",
  CLEARED: "Cleared",
  TERMINATED: "Terminated",
  REASSIGNED: "Reassigned",
};

export const RULE_LABEL = {
  ATTENDANCE_LOW: "Low attendance",
  PROJECT_DELAY: "Overdue project tasks",
  ASSIGNMENT_MISSED: "Missed assignments",
  QUIZ_FAILURE: "Quiz scores below the pass mark",
  REVIEW_FAILED: "Unsatisfactory weekly review",
  TASK_ABANDONED: "No recent activity",
};

export const SEVERITY_TONE = { LOW: "neutral", MEDIUM: "warning", HIGH: "warning", CRITICAL: "error" };

const OPEN_STATUSES = new Set(["TRIGGERED", "IN_PROGRESS"]);
export const isOpen = (backendStatus) => OPEN_STATUSES.has(backendStatus);

/** Monday of the week containing `d` (a Date or YYYY-MM-DD), as YYYY-MM-DD. */
export function mondayOf(d = new Date()) {
  const date = typeof d === "string" ? new Date(`${d}T00:00:00`) : new Date(d);
  const day = (date.getDay() + 6) % 7; // 0 = Monday
  date.setDate(date.getDate() - day);
  return date.toISOString().slice(0, 10);
}

// --- records -------------------------------------------------------------

function toFeCase(p) {
  return {
    id: p.id,
    studentUuid: p.studentUuid,
    student: p.studentFullName || "",
    batchId: p.batchId ?? null,
    batch: p.batchName || "",
    ruleCode: p.ruleCode,
    reason: p.triggerReason || RULE_LABEL[p.ruleCode] || (p.ruleCode || "").replace(/_/g, " "),
    severity: p.severity,
    status: p.status,
    open: isOpen(p.status),
    triggeredAt: p.triggeredAt || null,
    startDate: p.startDate || null,
    endDate: p.endDate || null,
    blocksTaskPull: !!p.blocksTaskPull,
    reviewNotes: p.reviewNotes || "",
    outcomeAt: p.outcomeAt || null,
    milestones: (p.milestones || []).map(toFeMilestone),
  };
}

function toFeMilestone(m) {
  return {
    id: m.id,
    title: m.title,
    description: m.description || "",
    dueDate: m.dueDate || null,
    status: m.status,
    done: m.status === "COMPLETED",
    missed: m.status === "MISSED",
    completedAt: m.completedAt || null,
  };
}

export async function getPipCases({ batchId, status } = {}) {
  const params = { size: 100 };
  if (batchId) params.batchId = batchId;
  if (status) params.status = status;
  return asRows(await apiClient.get("/pip", params)).map(toFeCase);
}

export async function getPipProgress(pipId) {
  return toFeProgress(await apiClient.get(`/pip/${pipId}/progress`));
}

export async function getMyPipProgress() {
  try {
    return toFeProgress(await apiClient.get("/pip/me/progress"));
  } catch (e) {
    if (e?.status === 404) return null;
    throw e;
  }
}

function toFeProgress(p) {
  return {
    pipRecordId: p.pipRecordId,
    student: p.studentFullName || "",
    ruleCode: p.ruleCode,
    reason: RULE_LABEL[p.ruleCode] || (p.ruleCode || "").replace(/_/g, " "),
    status: p.status,
    startDate: p.startDate,
    endDate: p.endDate,
    windowTotalDays: p.windowTotalDays,
    daysElapsed: p.daysElapsed,
    daysRemaining: p.daysRemaining,
    windowElapsed: !!p.windowElapsed,
    milestonesTotal: p.milestonesTotal,
    milestonesCompleted: p.milestonesCompleted,
    milestones: (p.milestones || []).map(toFeMilestone),
    gates: [
      {
        key: "tasks",
        label: "Task completion",
        current: p.taskCompletionPercent == null ? null : Number(p.taskCompletionPercent),
        target: p.taskCompletionTarget,
        unit: "%",
        met: !!p.taskCompletionMet,
        hard: true,
      },
      {
        key: "review",
        label: "Weekly review",
        current: p.weeklyReviewOk ? "No unsatisfactory review" : "Unsatisfactory review on file",
        target: null,
        met: !!p.weeklyReviewOk,
        hard: true,
      },
      {
        key: "attendance",
        label: "Attendance",
        current: p.attendancePercent == null ? null : Number(p.attendancePercent),
        target: p.attendanceTarget,
        unit: "%",
        met: !!p.attendanceMet,
        hard: false,
      },
      {
        key: "overdue",
        label: "Overdue tasks",
        current: p.tasksOverdue == null ? null : p.tasksOverdue,
        target: 0,
        met: !!p.overdueCleared,
        hard: false,
      },
    ],
    clearanceCriteriaMet: !!p.clearanceCriteriaMet,
    outstandingTasks: (p.outstandingTasks || []).map((t) => ({
      id: t.id,
      title: t.title,
      status: t.status,
      dueAt: t.dueAt || null,
      overdue: !!t.overdue,
    })),
  };
}

// --- milestones -------------------------------------------------------------

export async function addPipMilestone(pipId, { title, description, dueDate }) {
  return toFeMilestone(await apiClient.post(`/pip/${pipId}/milestones`, {
    title: (title || "").trim(),
    description: description ? description.trim() : null,
    dueDate,
  }));
}

export async function deletePipMilestone(pipId, milestoneId) {
  return apiClient.del(`/pip/${pipId}/milestones/${milestoneId}`);
}

export async function completePipMilestone(pipId, milestoneId) {
  return toFeMilestone(await apiClient.post(`/pip/${pipId}/milestones/${milestoneId}/complete`, {}));
}

// --- review / raise -------------------------------------------------------

const OUTCOME = { cleared: "CLEARED", terminated: "TERMINATED", reassigned: "REASSIGNED" };

export async function reviewPip(pipId, outcomeKey, reviewNotes) {
  const outcome = OUTCOME[outcomeKey] || outcomeKey;
  return toFeCase(await apiClient.post(`/pip/${pipId}/review`, {
    outcome,
    reviewNotes: (reviewNotes || `Recovery review — outcome ${outcome}.`).trim(),
  }));
}

export async function createManualPip({ studentUuid, batchId, ruleCode, reason, severity }) {
  return toFeCase(await apiClient.post("/pip", {
    studentUuid,
    batchId: Number(batchId),
    ruleCode,
    reason: (reason || "").trim(),
    severity,
  }));
}

// --- weekly reviews -----------------------------------------------------

export async function getWeeklyReviews(batchId, weekStart) {
  const rows = asRows(await apiClient.get("/reviews/weekly", { batchId, weekStart }));
  return rows.map((r) => ({
    id: r.id,
    studentUuid: r.studentUuid,
    student: r.studentName || "",
    sprintId: r.sprintId,
    weekStart: r.weekStart,
    rating: r.rating,
    notes: r.notes || "",
    reviewedAt: r.reviewedAt || null,
  }));
}

export async function saveWeeklyReview({ studentUuid, batchId, sprintId, weekStart, rating, notes }) {
  return apiClient.post("/reviews/weekly", {
    userUuid: studentUuid,
    batchId: Number(batchId),
    sprintId: Number(sprintId),
    weekStart,
    rating,
    notes: notes ? notes.trim() : null,
  });
}
