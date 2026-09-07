import { PIP_RECORDS } from "./mockData";

const MS_PER_HOUR = 60 * 60 * 1000;

function persistPipRecords() {
  localStorage.setItem("msh_pip_records", JSON.stringify(PIP_RECORDS));
}

function hasOpenCase(name) {
  return PIP_RECORDS.some((p) => p.student === name && p.status !== "Resolved" && p.status !== "Graduated");
}

function createAutoRecord(name, batch, reason, severity, note, remediationGoal) {
  const record = {
    id: `p_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    student: name,
    batch: batch || "",
    reason,
    severity,
    note,
    remediationGoal: remediationGoal || "Maintain attendance >= 85%, clear overdue backlog PRs, and achieve >= 70% in mandatory re-test.",
    status: "In Recovery",
    daysRemaining: 15,
    triggeredOn: new Date().toISOString().slice(0, 10),
    source: "auto",
    mentorAssigned: "",
    weeklyReviewNotes: []
  };
  PIP_RECORDS.unshift(record);
  persistPipRecords();
  return record;
}

/**
 * Deterministic Automated PIP Rule Engine (MSH-FR-PIP-01 to 06)
 * Evaluates a student against all 6 FRS triggers:
 * 1. Attendance < 75% (Attendance Default)
 * 2. Project Milestone > 48 Hours Past Due (Critical Project Delay)
 * 3. 2+ Consecutive Missing Assignments (Assignment Delay)
 * 4. Quiz / Assessment Average < 60% (Quiz Failure)
 * 5. Standup / Review Failed (Weekly Review Failure)
 * 6. 3 Consecutive Inactive Days (Task Inactivity)
 */
export function evaluateStudentAutoPip({ name, batch, attendance, tasks, quizAttempts, inactiveDays, reviewScore }) {
  if (!name || hasOpenCase(name)) return null;

  const now = Date.now();
  const myTasks = (tasks || []).filter((t) => t.assignee === name && t.due);

  // Trigger 1: Batch Attendance < 75% Cumulative
  if (attendance !== undefined && attendance < 75) {
    return createAutoRecord(
      name,
      batch,
      "Attendance Default",
      "High",
      `Automated Trigger: Student cumulative attendance is ${attendance}%, which is below the mandatory 75% threshold. Instant PIP Warning issued and SMS/WhatsApp alert dispatched.`,
      "Must achieve >= 85% attendance over next 15 days by checking in daily before 09:30 AM."
    );
  }

  // Trigger 2: Project Milestone Delivery > 48 Hours Past Due (Critical)
  const criticalOverdue = myTasks.find(
    (t) => t.status !== "Completed" && now - new Date(t.due).getTime() > 48 * MS_PER_HOUR
  );
  if (criticalOverdue) {
    return createAutoRecord(
      name,
      batch,
      "Project Delay",
      "Critical",
      `Automated Trigger: Milestone deliverable "${criticalOverdue.title}" is more than 48 hours overdue (Due: ${criticalOverdue.due}). Task pickup temporarily blocked.`,
      "Complete and submit GitHub PR for overdue milestone and obtain PM approval."
    );
  }

  // Trigger 3: Weekly Assignment Submissions Missing 2+ Consecutive
  const overdueAssignments = myTasks.filter((t) => t.status !== "Completed" && new Date(t.due).getTime() < now);
  if (overdueAssignments.length >= 2) {
    return createAutoRecord(
      name,
      batch,
      "Assignment Delay",
      "Medium",
      `Automated Trigger: Missing ${overdueAssignments.length} consecutive assignment submissions. Student entered into 15-day PIP recovery cycle.`,
      "Submit all backlogged pull requests with passing test suites."
    );
  }

  // Trigger 4: Quiz / Assessment Score < 60% Average
  const myAttempts = (quizAttempts || []).filter((a) => a.studentName === name);
  if (myAttempts.length) {
    const avg = myAttempts.reduce((sum, a) => sum + a.score, 0) / myAttempts.length;
    if (avg < 60) {
      return createAutoRecord(
        name,
        batch,
        "Quiz Failure",
        "Medium",
        `Automated Trigger: Cumulative assessment average is ${Math.round(avg)}%, below the 60% passing baseline. Mandatory re-test scheduled.`,
        "Complete remedial video modules and pass re-assessment with >= 70%."
      );
    }
  }

  // Trigger 5: Weekly Review / Standup Score Failed (< 5 / 10)
  if (reviewScore !== undefined && reviewScore < 5) {
    return createAutoRecord(
      name,
      batch,
      "Weekly Review Failure",
      "High",
      `Automated Trigger: Unsatisfactory PM sprint review grade (${reviewScore}/10). Mentor 1:1 intervention call scheduled.`,
      "Attend weekly 1:1 mentor coaching and resolve code architecture concerns."
    );
  }

  // Trigger 6: Daily Task Activity 3 Days Consecutive Inactive
  if (inactiveDays !== undefined && inactiveDays >= 3) {
    return createAutoRecord(
      name,
      batch,
      "Daily Task Abandonment",
      "High",
      `Automated Trigger: 3 consecutive inactive days without code commits or standup check-ins. Escalated to HR and PM.`,
      "Resume daily check-ins and submit daily code commits."
    );
  }

  return null;
}

export function runPipAutoCheckForAll({ studentBatchMap, tasks, quizAttempts }) {
  const created = [];
  Object.entries(studentBatchMap || {}).forEach(([name, batch]) => {
    const rec = evaluateStudentAutoPip({ name, batch, tasks, quizAttempts });
    if (rec) created.push(rec);
  });
  return created;
}