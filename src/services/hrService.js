import { mockRequest, apiClient } from "./apiClient";

const DEFAULT_EMPLOYEES = [];

const DEFAULT_LEAVE_REQUESTS = [];

const DEFAULT_CHECKINS = [];

// Rough skill sets per graduation track — used only to give a recruiting
// client something concrete to scan on a Talent Pool card. Matches the same
// track names Trainer > Graduation already issues certificates under.
const SKILLS_BY_TRACK = {
  "Full-Stack Development": ["React", "Node.js", "REST APIs", "SQL"],
  "Data Analytics": ["Python", "SQL", "Data Visualization", "Statistics"],
  "Product Design": ["Figma", "UX Research", "Prototyping", "Design Systems"],
  "Backend Engineering": ["Node.js", "System Design", "Databases", "APIs"],
};

// Same completion/quiz-average blend Trainer > Performance Analytics uses,
// so a graduate's Talent Pool "Performance score" isn't a made-up number —
// it's the same signal the trainer was already looking at when they cleared
// them. Falls back to a flat 85 only when there's genuinely no task/quiz
// history to compute from (rather than showing a misleading 0).
function computeGraduatePerformanceScore(studentName) {
  try {
    const rawTasks = localStorage.getItem("msh_sprint_tasks");
    const tasks = rawTasks ? JSON.parse(rawTasks) : [];
    const myTasks = tasks.filter((t) => t.assignee === studentName);
    const completion = myTasks.length
      ? (myTasks.filter((t) => t.status === "Completed").length / myTasks.length) * 100
      : null;

    const rawAttempts = localStorage.getItem("msh_assessment_attempts");
    const attempts = rawAttempts ? JSON.parse(rawAttempts) : [];
    const myAttempts = attempts.filter((a) => a.studentName === studentName);
    const quizAvg = myAttempts.length
      ? myAttempts.reduce((sum, a) => sum + a.score, 0) / myAttempts.length
      : null;

    const signals = [completion, quizAvg].filter((v) => v !== null);
    if (!signals.length) return 85;
    return Math.round(signals.reduce((a, b) => a + b, 0) / signals.length);
  } catch (e) {
    return 85;
  }
}

// The real "publish to recruiting" step. A trainer clearing graduation only
// unlocks the certificate and hands the student to HR — it does NOT mean
// they're actually done and available; HR still has to run IT/accounts/exit
// clearance. So a graduate only shows up for Corporate Clients to recruit
// once HR finalizes that exit clearance (see ExitManagement's
// finalizeClearance, which calls this). Writes to "msh_talent_pool", which
// clientService.getTalentPool reads. Idempotent — safe to call repeatedly
// (e.g. as a one-time backfill for exits finalized before this existed).
export function publishGraduateToTalentPool({ id, name, track }) {
  if (!name) return;
  try {
    const raw = localStorage.getItem("msh_talent_pool");
    const pool = raw ? JSON.parse(raw) : [];
    if (pool.some((p) => p.name.toLowerCase() === name.toLowerCase())) return;

    pool.unshift({
      id: id || `tp_${Date.now()}`,
      name,
      track: track || "Full-Stack Development",
      availability: "Immediately",
      skills: SKILLS_BY_TRACK[track] || SKILLS_BY_TRACK["Full-Stack Development"],
      score: computeGraduatePerformanceScore(name),
    });
    localStorage.setItem("msh_talent_pool", JSON.stringify(pool));
  } catch (err) {
    console.warn("[hrService] Could not publish graduate to talent pool:", err.message);
  }
}

// Looks up a student's resume directly off their registered-user record —
// the same place studentService.saveResumeFile writes it — so HR's exit
// workflow (and the client-visibility gate downstream of it) can tell
// whether a graduate is actually resume-ready without needing its own
// separate resume store.
function findStudentResume(studentName) {
  if (!studentName) return null;
  try {
    const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const list = raw ? JSON.parse(raw) : [];
    const match = list.find((u) => u.name?.toLowerCase() === studentName.toLowerCase());
    return match?.profileDetails?.resume || null;
  } catch (e) {
    return null;
  }
}

export function hasUploadedResume(studentName) {
  const resume = findStudentResume(studentName);
  return !!(resume && resume.fileData);
}

// The real gate on Client visibility: a graduating student is only
// published to the Talent Pool once BOTH (a) HR has finalized their exit
// clearance AND (b) they've uploaded a resume. Safe to call from anywhere —
// exit finalization, the exit-list backfill on load, or right after the
// student uploads their resume — since publishGraduateToTalentPool itself
// no-ops if the candidate is already in the pool. Returns whether the
// candidate is now (or already was) visible to Clients.
export function trySyncGraduateToTalentPool(studentName) {
  if (!studentName) return false;
  try {
    const raw = localStorage.getItem("msh_hr_exits");
    const exits = raw ? JSON.parse(raw) : [];
    const exit = exits.find(
      (x) =>
        x.name?.toLowerCase() === studentName.toLowerCase() &&
        x.type === "Graduating Student" &&
        x.clearance === "Complete"
    );
    if (!exit) return false;
    if (!hasUploadedResume(studentName)) return false;
    publishGraduateToTalentPool({ id: exit.id, name: exit.name, track: exit.department });
    return true;
  } catch (err) {
    console.warn("[hrService] Could not sync graduate to talent pool:", err.message);
    return false;
  }
}

export function readEmployees() {
  try {
    const raw = localStorage.getItem("msh_employees");
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  localStorage.setItem("msh_employees", JSON.stringify(DEFAULT_EMPLOYEES));
  return DEFAULT_EMPLOYEES;
}

export function saveEmployees(emps) {
  localStorage.setItem("msh_employees", JSON.stringify(emps));
}

// ---------------------------------------------------------------------------
// Employees / exits / onboarding / disciplinary — WIRED to the backend (B1.10):
//   GET  /api/v1/hr/employees            (HR_MANAGER / ADMIN)
//   /api/v1/hr/exits         GET, POST, PUT /{id}, POST /{id}/complete
//   /api/v1/hr/onboardings   GET, POST, GET /{id}, PUT /{id}
//   /api/v1/hr/disciplinary  GET, POST, GET /{id}, PUT /{id}
// Leave / attendance / payroll below stay on the localStorage mock — their
// backend shapes (`/hr/leaves`, `/hr/payroll`) differ and need their own pass.
// `readEmployees()` / `saveEmployees()` stay mock: getPayroll() still uses them.
// ---------------------------------------------------------------------------

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "INTERN", "CONTRACT"];
export const EXIT_TYPES = ["RESIGNATION", "TERMINATION", "RETIREMENT", "CONTRACT_END"];
export const EXIT_STATUSES = ["INITIATED", "IN_PROGRESS", "COMPLETED"];
export const ONBOARDING_STATUSES = ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
export const DISCIPLINARY_ACTION_TYPES = [
  "VERBAL_WARNING",
  "WRITTEN_WARNING",
  "PIP",
  "SUSPENSION",
  "TERMINATION_RECOMMENDATION",
  "OTHER",
];
export const DISCIPLINARY_SEVERITIES = ["LOW", "MEDIUM", "HIGH"];
export const DISCIPLINARY_STATUSES = ["OPEN", "ACKNOWLEDGED", "RESOLVED", "ESCALATED"];

export const DEFAULT_EXIT_CHECKLIST = [
  "IT asset return",
  "Accounts no-dues",
  "Knowledge transfer",
  "Exit interview",
  "Final settlement",
];
export const DEFAULT_ONBOARDING_CHECKLIST = [
  "KYC / ID proof verified",
  "Education documents verified",
  "NDA signed",
  "Background check",
  "Welcome kit dispatched",
  "Workstation / access provisioned",
];

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);
const asChecklist = (list, fallbackLabels = []) => {
  if (Array.isArray(list) && list.length) {
    return list.map((c) => ({ label: c.label, done: !!c.done }));
  }
  return fallbackLabels.map((label) => ({ label, done: false }));
};

function toFeEmployee(e) {
  return {
    id: e.id,
    userUuid: e.userUuid,
    name: e.fullName,
    employeeCode: e.employeeCode,
    department: e.department || "",
    designation: e.designation || "",
    employmentType: e.employmentType,
    dateOfJoining: e.dateOfJoining || null,
    dateOfExit: e.dateOfExit || null,
    baseSalary: e.baseSalary != null ? Number(e.baseSalary) : null,
    hourlyRate: e.hourlyRate != null ? Number(e.hourlyRate) : null,
    reportingManagerId: e.reportingManagerId ?? null,
    status: e.status,
  };
}

export async function getEmployees({ status, department, search } = {}) {
  const params = {};
  if (status) params.status = status;
  if (department) params.department = department;
  if (search) params.search = search;
  const res = await apiClient.get("/hr/employees", Object.keys(params).length ? params : undefined);
  return asRows(res).map(toFeEmployee);
}

// -- Exits ------------------------------------------------------------------

function toFeExit(x) {
  return {
    id: x.id,
    employeeId: x.employeeId,
    employeeCode: x.employeeCode || "",
    name: x.employeeName || "",
    exitType: x.exitType,
    lastWorkingDay: x.lastWorkingDay || null,
    reason: x.reason || "",
    noticePeriodDays: x.noticePeriodDays ?? null,
    status: x.status,
    clearanceChecklist: asChecklist(x.clearanceChecklist),
    exitInterviewNotes: x.exitInterviewNotes || "",
    completedAt: x.completedAt || null,
    createdAt: x.createdAt || null,
  };
}

export async function getExits({ status, employeeId } = {}) {
  const params = {};
  if (status) params.status = status;
  if (employeeId) params.employeeId = employeeId;
  const res = await apiClient.get("/hr/exits", Object.keys(params).length ? params : undefined);
  return asRows(res).map(toFeExit);
}

export async function createExit(input) {
  const body = {
    employeeId: Number(input.employeeId),
    exitType: input.exitType || "RESIGNATION",
    lastWorkingDay: input.lastWorkingDay,
    reason: input.reason || null,
    noticePeriodDays:
      input.noticePeriodDays === "" || input.noticePeriodDays == null
        ? null
        : Number(input.noticePeriodDays),
  };
  return toFeExit(await apiClient.post("/hr/exits", body));
}

// PUT accepts INITIATED / IN_PROGRESS only — COMPLETED goes through completeExit.
export async function updateExit(id, input) {
  const body = {
    exitType: input.exitType,
    lastWorkingDay: input.lastWorkingDay,
    reason: input.reason || null,
    noticePeriodDays:
      input.noticePeriodDays === "" || input.noticePeriodDays == null
        ? null
        : Number(input.noticePeriodDays),
    status: input.status === "COMPLETED" ? "IN_PROGRESS" : input.status || "IN_PROGRESS",
    clearanceChecklist: (input.clearanceChecklist || []).map((c) => ({
      label: c.label,
      done: !!c.done,
    })),
    exitInterviewNotes: input.exitInterviewNotes || null,
  };
  return toFeExit(await apiClient.put(`/hr/exits/${id}`, body));
}

export async function completeExit(id) {
  return toFeExit(await apiClient.post(`/hr/exits/${id}/complete`, {}));
}

// -- Onboarding -----------------------------------------------------------

function toFeOnboarding(o) {
  return {
    id: o.id,
    employeeId: o.employeeId,
    employeeCode: o.employeeCode || "",
    name: o.employeeName || "",
    buddyId: o.buddyId ?? null,
    startDate: o.startDate || null,
    status: o.status,
    checklist: asChecklist(o.checklist),
    notes: o.notes || "",
    completedAt: o.completedAt || null,
    createdAt: o.createdAt || null,
  };
}

export async function getOnboardings({ status, employeeId } = {}) {
  const params = {};
  if (status) params.status = status;
  if (employeeId) params.employeeId = employeeId;
  const res = await apiClient.get("/hr/onboardings", Object.keys(params).length ? params : undefined);
  return asRows(res).map(toFeOnboarding);
}

export async function createOnboarding(input) {
  const body = {
    employeeId: Number(input.employeeId),
    startDate: input.startDate,
    buddyUuid: input.buddyUuid || null,
  };
  return toFeOnboarding(await apiClient.post("/hr/onboardings", body));
}

export async function updateOnboarding(id, input) {
  const body = {
    startDate: input.startDate,
    buddyUuid: input.buddyUuid || null,
    status: input.status || "IN_PROGRESS",
    checklist: (input.checklist || []).map((c) => ({ label: c.label, done: !!c.done })),
    notes: input.notes || null,
  };
  return toFeOnboarding(await apiClient.put(`/hr/onboardings/${id}`, body));
}

// -- Disciplinary -------------------------------------------------------

function toFeDisciplinary(d) {
  return {
    id: d.id,
    employeeId: d.employeeId,
    employeeCode: d.employeeCode || "",
    name: d.employeeName || "",
    actionType: d.actionType,
    severity: d.severity,
    incidentDate: d.incidentDate || null,
    description: d.description || "",
    actionTaken: d.actionTaken || "",
    status: d.status,
    acknowledgedAt: d.acknowledgedAt || null,
    resolvedAt: d.resolvedAt || null,
    resolutionNotes: d.resolutionNotes || "",
    createdAt: d.createdAt || null,
  };
}

export async function getDisciplinaryActions({ status, severity, employeeId } = {}) {
  const params = {};
  if (status) params.status = status;
  if (severity) params.severity = severity;
  if (employeeId) params.employeeId = employeeId;
  const res = await apiClient.get(
    "/hr/disciplinary",
    Object.keys(params).length ? params : undefined
  );
  return asRows(res).map(toFeDisciplinary);
}

export async function createDisciplinaryAction(input) {
  const body = {
    employeeId: Number(input.employeeId),
    actionType: input.actionType || "WRITTEN_WARNING",
    severity: input.severity || "MEDIUM",
    incidentDate: input.incidentDate,
    description: input.description,
  };
  return toFeDisciplinary(await apiClient.post("/hr/disciplinary", body));
}

export async function updateDisciplinaryAction(id, input) {
  const body = {
    actionType: input.actionType,
    severity: input.severity,
    incidentDate: input.incidentDate,
    description: input.description,
    actionTaken: input.actionTaken || null,
    status: input.status || "OPEN",
    resolutionNotes: input.resolutionNotes || null,
  };
  return toFeDisciplinary(await apiClient.put(`/hr/disciplinary/${id}`, body));
}

export async function getLeaveRequests() {
  try {
    const raw = localStorage.getItem("msh_leave_requests");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_leave_requests", JSON.stringify(DEFAULT_LEAVE_REQUESTS));
  return mockRequest(DEFAULT_LEAVE_REQUESTS);
}

// Lets any staff role (Trainer, Developer, BA, Lead Generator, HR itself)
// self-submit a leave request from their own dashboard. It lands in the
// exact same "msh_leave_requests" list HR's Attendance & Leave screen
// reads/approves from, so no separate approval path is needed.
export async function submitLeaveRequest({ employee, role, type, from, to, reason }) {
  const leaves = await (async () => {
    try {
      const raw = localStorage.getItem("msh_leave_requests");
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return DEFAULT_LEAVE_REQUESTS;
  })();

  const entry = {
    id: `leave_${Date.now()}`,
    employee,
    role: role || "Staff",
    type: type || "Casual Leave",
    from,
    to,
    reason: reason || "",
    status: "Pending",
    appliedAt: new Date().toISOString(),
  };

  const updated = [entry, ...leaves];
  localStorage.setItem("msh_leave_requests", JSON.stringify(updated));
  return mockRequest(entry);
}

// Returns only the leave requests a given staff member has personally
// submitted — used to render "My Leave Requests" on their own dashboard.
export async function getMyLeaveRequests(employeeName) {
  const leaves = await getLeaveRequests();
  return leaves.filter((l) => l.employee?.toLowerCase() === (employeeName || "").toLowerCase());
}

export async function actionLeaveRequest(id, decision) {
  const leaves = await (async () => {
    try {
      const raw = localStorage.getItem("msh_leave_requests");
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return DEFAULT_LEAVE_REQUESTS;
  })();

  const idx = leaves.findIndex((l) => l.id === id);
  if (idx > -1) {
    leaves[idx] = { ...leaves[idx], status: decision };
    localStorage.setItem("msh_leave_requests", JSON.stringify(leaves));
  }
  return mockRequest(leaves[idx]);
}

export async function getClockinLogs() {
  try {
    const raw = localStorage.getItem("msh_attendance_logs");
    if (raw) return mockRequest(JSON.parse(raw));
  } catch (e) {}
  localStorage.setItem("msh_attendance_logs", JSON.stringify(DEFAULT_CHECKINS));
  return mockRequest(DEFAULT_CHECKINS);
}

export async function logCheckin(payload) {
  const logs = await (async () => {
    try {
      const raw = localStorage.getItem("msh_attendance_logs");
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return DEFAULT_CHECKINS;
  })();

  const entry = {
    id: `c_${Date.now()}`,
    name: payload.name,
    role: payload.role || "Staff",
    date: new Date().toISOString().slice(0, 10),
    checkIn: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    checkOut: "--",
    hours: 0,
    deviceId: payload.deviceId || "WEB-AUTH-PORTAL",
    status: payload.status || "Present"
  };

  const updated = [entry, ...logs];
  localStorage.setItem("msh_attendance_logs", JSON.stringify(updated));
  return mockRequest(entry);
}

export async function getPayroll() {
  const roster = readEmployees();
  let rows = [];
  try {
    const raw = localStorage.getItem("msh_hr_payroll");
    if (raw) rows = JSON.parse(raw);
  } catch (e) {
    rows = [];
  }

  // Compute realistic salary breakdown:
  // Trainer: Hourly Rate * Hours + Session Bonus
  // Full-time: Base + HRA (40% of Base) - PF (12% of Base) - Tax
  const existingIds = new Set(rows.map((r) => r.employeeId));
  const autoGenerated = roster
    .filter((e) => !existingIds.has(e.id))
    .map((e) => {
      const isTrainer = e.type === "Trainer" || e.role.includes("Trainer");
      let base = e.baseSalary || 50000;
      let hra = 0;
      let sessionEarnings = 0;
      let pf = 0;
      let tax = 2000;
      let net = 0;

      if (isTrainer) {
        const rate = e.hourlyRate || 1200;
        const hours = e.completedHours || 40;
        sessionEarnings = rate * hours;
        base = sessionEarnings;
        hra = 0;
        pf = Math.round(sessionEarnings * 0.05);
        net = sessionEarnings - pf;
      } else {
        hra = Math.round(base * 0.4);
        pf = Math.round(base * 0.12);
        net = base + hra - pf - tax;
      }

      return {
        id: `pr_${e.id}`,
        employeeId: e.id,
        name: e.name,
        role: e.role,
        department: e.department || "Operations",
        type: isTrainer ? "Hourly Trainer" : "Monthly Salaried",
        hourlyRate: e.hourlyRate || 1200,
        completedHours: e.completedHours || 40,
        baseSalary: base,
        hra,
        deductions: pf + tax,
        net,
        status: "Pending",
        payPeriod: "August 2026"
      };
    });

  const merged = [...rows, ...autoGenerated];
  localStorage.setItem("msh_hr_payroll", JSON.stringify(merged));
  return mockRequest(merged);
}