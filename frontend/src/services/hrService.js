import { apiClient } from "./apiClient";

const DEFAULT_EMPLOYEES = [];

// NOTE: the old graduate -> Talent Pool publish (publishGraduateToTalentPool /
// trySyncGraduateToTalentPool / hasUploadedResume) lived here and wrote a
// localStorage "msh_talent_pool" gated on a fake "Graduating Student" exit
// clearance record. Nothing read that store — the client Talent Pool is the
// real GET /api/v1/talent-pool (graduates with a complete profile), and
// students have no exit-clearance concept. All of it deleted.

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
    provisioningStatus: e.provisioningStatus || "CONFIRMED", // PENDING_HR until HR fills + approves
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

// GET /api/v1/hr/employees/pending — records auto-created on invite-accept that HR
// still has to fill in and approve.
export async function getPendingEmployeeRecords() {
  const res = await apiClient.get("/hr/employees/pending", { size: 100 });
  return asRows(res).map(toFeEmployee);
}

// PUT /api/v1/hr/employees/{id} — fill in / correct an employee record. Pass
// confirm: true to also approve it (PENDING_HR -> CONFIRMED). Send exactly one of
// baseSalary / hourlyRate.
export async function updateEmployee(id, input) {
  const salaried = input.compensationMode !== "HOURLY";
  const body = {
    department: (input.department || "").trim(),
    designation: (input.designation || "").trim(),
    employmentType: input.employmentType || "FULL_TIME",
    dateOfJoining: input.dateOfJoining,
    baseSalary: salaried ? Number(input.baseSalary) || 0 : null,
    hourlyRate: salaried ? null : Number(input.hourlyRate) || 0,
    reportingManagerId:
      input.reportingManagerId === "" || input.reportingManagerId == null
        ? null
        : Number(input.reportingManagerId),
    confirm: !!input.confirm,
  };
  return toFeEmployee(await apiClient.put(`/hr/employees/${id}`, body));
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
    userUuid: o.employeeUserUuid || "",
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

// GET /api/v1/hr/onboardings/my-status — the caller's own provisioning state,
// for the dashboard access gate. { staff, provisioningStatus, accessGated }.
// A non-employee account (student/client/admin) returns { staff:false, accessGated:false }.
export async function getMyOnboardingStatus() {
  try {
    const res = await apiClient.get("/hr/onboardings/my-status");
    return {
      staff: !!res?.staff,
      provisioningStatus: res?.provisioningStatus || null,
      accessGated: !!res?.accessGated,
    };
  } catch {
    // On any error, fail open — never lock someone out of their dashboard over a flaky call.
    return { staff: false, provisioningStatus: null, accessGated: false };
  }
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

// --- Leave requests (WIRED) ------------------------------------------
// GET /api/v1/hr/leaves, POST /api/v1/hr/leaves, PUT /{id}/decision.
// An employee sees only their own; HR_MANAGER/ADMIN see all. Submitting
// requires the caller to have an employees row (else 404 EMPLOYEE_NOT_FOUND).
// Staff-attendance clock-in (below) has no backend and stays mock.

export const LEAVE_TYPES = [
  { value: "SICK", label: "Sick Leave" },
  { value: "CASUAL", label: "Casual Leave" },
  { value: "EARNED", label: "Earned Leave" },
  { value: "UNPAID", label: "Unpaid Leave" },
];
const LEAVE_TYPE_LABEL = Object.fromEntries(LEAVE_TYPES.map((t) => [t.value, t.label]));
const LEAVE_STATUS_TO_FE = { PENDING: "Pending", APPROVED: "Approved", REJECTED: "Rejected" };
const FE_LEAVE_DECISION = { Approved: "APPROVED", Rejected: "REJECTED" };

function toFeLeave(l) {
  return {
    id: l.id,
    userUuid: l.userUuid,
    employee: l.userFullName || "",
    type: LEAVE_TYPE_LABEL[l.leaveType] || l.leaveType,
    leaveType: l.leaveType,
    from: l.fromDate || "",
    to: l.toDate || "",
    days: l.days != null ? Number(l.days) : null,
    reason: l.reason || "",
    status: LEAVE_STATUS_TO_FE[l.status] || l.status,
    approvedByUuid: l.approvedByUuid || null,
    decidedAt: l.decidedAt || null,
    createdAt: l.createdAt || null,
  };
}

export async function getLeaveRequests({ status, userUuid } = {}) {
  const params = { size: 100 };
  if (status) params.status = status; // FE passes a backend enum e.g. "PENDING"
  if (userUuid) params.userUuid = userUuid;
  const res = await apiClient.get("/hr/leaves", params);
  return asRows(res).map(toFeLeave);
}

// The caller's own requests. HR/ADMIN would otherwise see everyone, so pass
// their uuid to scope it; a non-HR caller is auto-scoped server-side anyway.
export async function getMyLeaveRequests(myUuid) {
  return getLeaveRequests(myUuid ? { userUuid: myUuid } : {});
}

// POST /api/v1/hr/leaves — `type` is a backend enum value (SICK/CASUAL/…).
export async function submitLeaveRequest({ type, leaveType, from, to, reason }) {
  const body = {
    leaveType: leaveType || type || "CASUAL",
    fromDate: from,
    toDate: to,
    reason: reason || null,
  };
  return toFeLeave(await apiClient.post("/hr/leaves", body));
}

// PUT /api/v1/hr/leaves/{id}/decision — feDecision is "Approved" | "Rejected".
export async function actionLeaveRequest(id, feDecision) {
  return toFeLeave(
    await apiClient.put(`/hr/leaves/${id}/decision`, {
      decision: FE_LEAVE_DECISION[feDecision] || feDecision,
    })
  );
}

// --- HR / KYC documents (WIRED) --------------------------------------
// GET /api/v1/hr/documents, POST (multipart), PUT /{id}/verify.
// An employee sees own; HR_MANAGER/ADMIN see all. Rows carry a presigned
// downloadUrl (15-min TTL). The placement-pipeline tabs on hr/Documents.jsx
// are unrelated and stay on utils/placementPipeline.js.

const HR_DOC_STATUS_TO_FE = { PENDING: "Pending", VERIFIED: "Verified", REJECTED: "Rejected" };
const FE_HR_DOC_DECISION = { Verified: "VERIFIED", Rejected: "REJECTED" };

function toFeHrDocument(d) {
  return {
    id: d.id,
    userUuid: d.userUuid,
    employee: d.userFullName || "",
    documentType: d.documentType,
    status: HR_DOC_STATUS_TO_FE[d.verificationStatus] || d.verificationStatus,
    verifiedByUuid: d.verifiedByUuid || null,
    verifiedAt: d.verifiedAt || null,
    rejectionReason: d.rejectionReason || "",
    downloadUrl: d.downloadUrl || null,
    createdAt: d.createdAt || null,
  };
}

export async function getHrDocuments({ status, userUuid, documentType } = {}) {
  const params = { size: 100 };
  if (status) params.status = status; // backend enum e.g. "PENDING"
  if (userUuid) params.userUuid = userUuid;
  if (documentType) params.documentType = documentType;
  const res = await apiClient.get("/hr/documents", params);
  return asRows(res).map(toFeHrDocument);
}

// POST /api/v1/hr/documents — multipart (field `file` PDF + `documentType`).
export async function uploadHrDocument(file, documentType, onBehalfOfUserUuid) {
  const fields = { documentType: documentType || "OTHER" };
  if (onBehalfOfUserUuid) fields.onBehalfOfUserUuid = onBehalfOfUserUuid; // HR uploading for a joiner (e.g. background check)
  const res = await apiClient.requestMultipart("/hr/documents", {
    method: "POST",
    fields,
    files: { file },
  });
  return toFeHrDocument(res);
}

// The staff onboarding document set. First three are employee uploads; the
// background check is uploaded by HR (onBehalfOfUserUuid). Codes match the
// backend's HrDocumentService.ONBOARDING_DOC_TYPES.
export const ONBOARDING_DOC_SECTIONS = [
  { code: "ID_PROOF", label: "ID Proof", hint: "Aadhaar, Passport or Voter ID — single PDF", who: "EMPLOYEE" },
  { code: "EDUCATION", label: "Education Certificate", hint: "Your highest degree certificate — PDF", who: "EMPLOYEE" },
  { code: "NDA", label: "Signed NDA", hint: "The NDA from your offer, signed — PDF", who: "EMPLOYEE" },
  { code: "BACKGROUND_CHECK", label: "Background Check", hint: "HR uploads the verification report", who: "HR" },
];

// PUT /api/v1/hr/documents/{id}/verify — feDecision "Verified" | "Rejected".
export async function verifyHrDocument(id, feDecision, rejectionReason = "") {
  const res = await apiClient.put(`/hr/documents/${id}/verify`, {
    decision: FE_HR_DOC_DECISION[feDecision] || feDecision,
    rejectionReason: feDecision === "Rejected" ? rejectionReason || "Not acceptable" : null,
  });
  return toFeHrDocument(res);
}

// --- Staff attendance (WIRED, V36) ---------------------------------------
// GET /api/v1/hr/attendance (list), POST /checkin, POST /checkout,
// PUT /mark (HR status override), GET /summary?month=YYYY-MM.
// An employee sees only their own rows; HR_MANAGER/ADMIN see all.

const STAFF_ATT_STATUS_TO_FE = {
  PRESENT: "Present", LATE: "Late", ABSENT: "Absent", HALF_DAY: "Half Day", ON_LEAVE: "On Leave",
};
const FE_STAFF_ATT_STATUS = {
  Present: "PRESENT", Late: "LATE", Absent: "ABSENT", "Half Day": "HALF_DAY", "On Leave": "ON_LEAVE",
};
const fmtTime = (iso) =>
  iso ? new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "--";
const nn = (t) => (t === "--" ? null : t);

// The staff-attendance row is keyed on the *server's* calendar date (workDate,
// Asia/Kolkata). `new Date().toISOString()` is UTC and rolls to the next day from
// ~18:30 IST onwards, so a check-in logged in the evening would never be found by
// a UTC-dated lookup. Use the browser's local date instead — it matches IST here.
const localToday = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

// Returns the "Live Biometric / Web Check-ins" row shape. Role/department are
// enriched client-side from the employee directory (the attendance row itself
// doesn't carry them).
export async function getClockinLogs({ from, to, userUuid } = {}) {
  const params = { size: 200 };
  if (from) params.from = from;
  if (to) params.to = to;
  if (userUuid) params.userUuid = userUuid;

  const [attRes, employees] = await Promise.all([
    apiClient.get("/hr/attendance", params),
    getEmployees().catch(() => []),
  ]);
  const roleByUuid = Object.fromEntries(employees.map((e) => [e.userUuid, e.designation || e.department || "Staff"]));

  return asRows(attRes).map((a) => ({
    id: a.id,
    userUuid: a.userUuid,
    name: a.userFullName || "",
    role: roleByUuid[a.userUuid] || "Staff",
    date: a.workDate,
    workDate: a.workDate,
    checkIn: fmtTime(a.checkedInAt),
    checkOut: fmtTime(a.checkedOutAt),
    deviceId: a.device || "—",
    status: STAFF_ATT_STATUS_TO_FE[a.status] || a.status,
    markedByPm: !!a.markedByUuid,
  }));
}

// POST /api/v1/hr/attendance/checkin — { userUuid?, device }. Omitting userUuid
// (or passing your own) is a self check-in; HR passes another staff member's uuid
// to log a terminal check-in on their behalf.
// Returns the full row (checkIn/checkOut/status) so a caller can update its UI
// straight from the write response instead of re-fetching — the check-in is
// idempotent server-side, so a repeat click is harmless but shouldn't be needed.
export async function logCheckin({ userUuid, device } = {}) {
  const res = await apiClient.post("/hr/attendance/checkin", {
    userUuid: userUuid || null,
    device: device || "WEB-AUTH-PORTAL",
  });
  return {
    id: res.id,
    userUuid: res.userUuid,
    status: STAFF_ATT_STATUS_TO_FE[res.status] || res.status,
    checkIn: nn(fmtTime(res.checkedInAt)),
    checkOut: nn(fmtTime(res.checkedOutAt)),
    workDate: res.workDate || null,
  };
}

// POST /api/v1/hr/attendance/checkout — own, or (HR) ?userUuid=. Same row shape as logCheckin.
export async function clockOut(userUuid) {
  const res = await apiClient.post(
    `/hr/attendance/checkout${userUuid ? `?userUuid=${encodeURIComponent(userUuid)}` : ""}`
  );
  return {
    id: res.id,
    userUuid: res.userUuid,
    status: STAFF_ATT_STATUS_TO_FE[res.status] || res.status,
    checkIn: nn(fmtTime(res.checkedInAt)),
    checkOut: nn(fmtTime(res.checkedOutAt)),
    workDate: res.workDate || null,
  };
}

// The caller's own attendance row for today (for the dashboard clock-in widget).
// null = they haven't checked in yet. A non-HR caller is auto-scoped server-side.
export async function getMyStaffAttendanceToday(myUuid) {
  const today = localToday();
  const res = await apiClient.get("/hr/attendance", { from: today, to: today, size: 50 });
  const rows = asRows(res);
  // A non-HR caller is auto-scoped server-side, so a single returned row is
  // theirs even if the uuid string didn't line up; HR gets everyone's rows and
  // must match on uuid.
  const mine = (myUuid && rows.find((r) => r.userUuid === myUuid)) || (rows.length === 1 ? rows[0] : null);
  if (!mine) return null;
  return {
    checkIn: nn(fmtTime(mine.checkedInAt)),
    checkOut: nn(fmtTime(mine.checkedOutAt)),
    status: STAFF_ATT_STATUS_TO_FE[mine.status] || mine.status,
  };
}

// PUT /api/v1/hr/attendance/mark — HR override. feStatus is a UI label.
export async function markStaffAttendance({ userUuid, workDate, feStatus, notes }) {
  const res = await apiClient.put("/hr/attendance/mark", {
    userUuid,
    workDate,
    status: FE_STAFF_ATT_STATUS[feStatus] || feStatus,
    notes: notes || null,
  });
  return { id: res.id, status: STAFF_ATT_STATUS_TO_FE[res.status] || res.status };
}

// GET /api/v1/hr/attendance/summary?month=YYYY-MM — per-employee monthly roll-up.
export async function getStaffAttendanceSummary(month) {
  const res = await apiClient.get("/hr/attendance/summary", month ? { month } : undefined);
  return (Array.isArray(res) ? res : []).map((r) => ({
    userUuid: r.userUuid,
    name: r.userFullName,
    department: r.department || "",
    presentDays: r.presentDays ?? 0,
    lateDays: r.lateDays ?? 0,
    absentDays: r.absentDays ?? 0,
    halfDays: r.halfDays ?? 0,
    onLeaveDays: r.onLeaveDays ?? 0,
    attendancePct: r.attendancePct ?? null,
    // Sum of check-in -> check-out across days that have both stamps. The Payroll
    // "Generate" modal pre-fills an hourly employee's session hours from this.
    workedHours: r.workedHours != null ? Number(r.workedHours) : 0,
  }));
}

// --- Payroll (WIRED, B1.10) --------------------------------------------
// GET /api/v1/hr/payroll?month=YYYY-MM-01, POST /api/v1/hr/payroll/generate.
// The backend computes gross/deductions/net server-side; there is no per-row
// edit or delete (regenerate the month instead). Leave / attendance have NO
// list endpoint on the backend yet and stay on the localStorage mock.

const firstOfMonth = (ymOrDate) => {
  const d = ymOrDate ? new Date(ymOrDate) : new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
};

function toFePayroll(p) {
  return {
    id: p.id,
    employeeId: p.employeeId,
    employeeCode: p.employeeCode || "",
    name: p.employeeFullName || "",
    periodMonth: p.periodMonth || null,
    workingDays: p.workingDays ?? null,
    presentDays: p.presentDays ?? null,
    unpaidLeaveDays: p.unpaidLeaveDays != null ? Number(p.unpaidLeaveDays) : 0,
    sessionHours: p.sessionHours != null ? Number(p.sessionHours) : null,
    gross: p.grossAmount != null ? Number(p.grossAmount) : 0,
    deductions: p.deductions != null ? Number(p.deductions) : 0,
    net: p.netAmount != null ? Number(p.netAmount) : 0,
    payslipUrl: p.payslipDownloadUrl || null,
    status: p.status, // DRAFT | FINALISED | PAID
  };
}

export async function getPayroll(month) {
  const res = await apiClient.get("/hr/payroll", { month: firstOfMonth(month) });
  return asRows(res).map(toFePayroll);
}

// lines: [{ employeeId, presentDays, sessionHours?, deductions? }]
export async function generatePayroll({ month, workingDays, lines }) {
  const body = {
    periodMonth: firstOfMonth(month),
    workingDays: Number(workingDays) || 22,
    lines: (lines || []).map((l) => ({
      employeeId: Number(l.employeeId),
      presentDays: Number(l.presentDays) || 0,
      sessionHours: l.sessionHours === "" || l.sessionHours == null ? null : Number(l.sessionHours),
      deductions: l.deductions === "" || l.deductions == null ? null : Number(l.deductions),
    })),
  };
  const res = await apiClient.post("/hr/payroll/generate", body);
  return (Array.isArray(res) ? res : asRows(res)).map(toFePayroll);
}