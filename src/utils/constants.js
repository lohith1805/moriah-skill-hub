// Central constants — single source of truth for roles, plans, statuses.
// Mirrors SRS §2.1 (RBAC Matrix) and §2.2 (Subscription Tiers).

export const ROLES = {
  STUDENT: "student",
  TRAINER: "trainer",
  DEVELOPER: "developer",
  LEAD_GENERATOR: "lead_generator",
  HR: "hr",
  BUSINESS_ANALYST: "business_analyst",
  ADMIN: "admin",
  CLIENT: "client",
};

export const ROLE_LABELS = {
  [ROLES.STUDENT]: "Student",
  [ROLES.TRAINER]: "Trainer / PM",
  [ROLES.DEVELOPER]: "Developer",
  [ROLES.LEAD_GENERATOR]: "Lead Generator",
  [ROLES.HR]: "HR Specialist",
  [ROLES.BUSINESS_ANALYST]: "Business Analyst",
  [ROLES.ADMIN]: "System Admin",
  [ROLES.CLIENT]: "Corporate Client",
};

// Only these two roles may self-register through the public /register page.
// Every other role is staff, created internally by an Admin via an invite
// (see UserManagement "Add Staff"). Keep this list in sync with §1 of the
// backend requirements doc.
export const SELF_REGISTER_ROLES = [ROLES.STUDENT, ROLES.CLIENT];

// Lifecycle states an account can be in.
//   active            – can log in and use their dashboard
//   pending_approval   – Client only: awaiting Admin/BA review
//   rejected           – Client only: review declined
//   invited             – Staff only: invite sent, hasn't completed signup yet
export const ACCOUNT_STATUS = {
  ACTIVE: "active",
  PENDING_APPROVAL: "pending_approval",
  REJECTED: "rejected",
  INVITED: "invited",
  // Backend-only lifecycle states surfaced after the integration (UserStatus enum).
  PENDING_VERIFICATION: "pending_verification",
  SUSPENDED: "suspended",
  TERMINATED: "terminated",
};

// ---------------------------------------------------------------------------
// Backend <-> frontend contract mapping. The Spring Boot backend uses SCREAMING
// role/status/plan codes (RoleCode, UserStatus, subscription_plans.code); this
// app uses lowercase codes throughout. These maps are the single translation
// point — see src/services/authService.js (login -> /users/me) for where a
// backend user is normalised into the shape the app's components expect.
// ---------------------------------------------------------------------------

export const BACKEND_ROLE_TO_FE = {
  STUDENT: ROLES.STUDENT,
  TRAINER_PM: ROLES.TRAINER,
  DEVELOPER: ROLES.DEVELOPER,
  LEAD_GEN: ROLES.LEAD_GENERATOR,
  HR_MANAGER: ROLES.HR,
  BUSINESS_ANALYST: ROLES.BUSINESS_ANALYST,
  ADMIN: ROLES.ADMIN,
  CLIENT: ROLES.CLIENT,
};

export const FE_ROLE_TO_BACKEND = Object.fromEntries(
  Object.entries(BACKEND_ROLE_TO_FE).map(([backend, fe]) => [fe, backend])
);

// Decision D3: the FE collapses a multi-role JWT to one primary role by this
// priority (highest first). Backend codes.
export const ROLE_PRIORITY = [
  "ADMIN",
  "TRAINER_PM",
  "BUSINESS_ANALYST",
  "HR_MANAGER",
  "LEAD_GEN",
  "DEVELOPER",
  "CLIENT",
  "STUDENT",
];

// Given the backend JWT's `roles` array (or a /users/me roles list), return the
// single lowercase FE role code the router/nav/guards use.
export function primaryFeRole(backendRoles) {
  const list = Array.isArray(backendRoles) ? backendRoles : [];
  for (const code of ROLE_PRIORITY) {
    if (list.includes(code)) return BACKEND_ROLE_TO_FE[code];
  }
  // Fall back to the first thing we recognise, else student.
  const first = list.map((r) => BACKEND_ROLE_TO_FE[r]).find(Boolean);
  return first || ROLES.STUDENT;
}

export const BACKEND_STATUS_TO_FE = {
  ACTIVE: ACCOUNT_STATUS.ACTIVE,
  PENDING_VERIFICATION: ACCOUNT_STATUS.PENDING_VERIFICATION,
  SUSPENDED: ACCOUNT_STATUS.SUSPENDED,
  TERMINATED: ACCOUNT_STATUS.TERMINATED,
  INVITED: ACCOUNT_STATUS.INVITED,
  PENDING_APPROVAL: ACCOUNT_STATUS.PENDING_APPROVAL,
  REJECTED: ACCOUNT_STATUS.REJECTED,
};

export const PLAN_CODE_TO_FE = {
  STARTER: "starter",
  PROFESSIONAL: "professional",
  PROJECT_BASED: "project_based",
  INTERNSHIP: "internship",
  CORPORATE_PROGRAM: "corporate",
};

export const FE_PLAN_TO_BACKEND = Object.fromEntries(
  Object.entries(PLAN_CODE_TO_FE).map(([backend, fe]) => [fe, backend])
);

// Self-paced video curriculum (MSH-FR-STU-07 / MSH-FR-STU-08). Each module
export const SUBSCRIPTION_PLANS = [
  {
    code: "starter",
    name: "Starter",
    price: 3999,
    model: "Self-paced",
    features: [
      "Self-paced learning modules",
      "Core curriculum & reading",
      "Community forum support",
      "Auto-graded assessments",
      "Certificate of completion"
    ]
  },
  {
    code: "professional",
    name: "Professional",
    price: 7999,
    model: "Guided Track",
    features: [
      "Structured learning track",
      "Instructor Q&A support",
      "Weekly peer review sessions",
      "Standard capstone projects",
      "Placement guidance & assistance"
    ]
  },
  {
    code: "project_based",
    name: "Project Based",
    price: 14999,
    model: "Agile Simulation",
    features: [
      "Complete Agile simulation",
      "Real-world team projects",
      "Dedicated PM / Mentor",
      "Sprint board & code reviews",
      "Github submission workflow",
      "Direct corporate referrals"
    ]
  },
  {
    code: "internship",
    name: "Internship",
    price: 19999,
    model: "Live Apprenticeship",
    features: [
      "Work on live company projects",
      "Daily standups & scrum",
      "Industry mentor guidance",
      "Internship experience letter",
      "1-on-1 career coaching",
      "High-performer stipends"
    ]
  },
  {
    code: "corporate",
    name: "Corporate Program",
    price: 29999,
    model: "Executive Fast-Track",
    features: [
      "Tailored learning modules",
      "Dedicated learning advisor",
      "Custom capstone simulation",
      "Enterprise grading & reports",
      "Guaranteed interview calls",
      "Lifetime alumni network access"
    ]
  },
];

export const PIP_TRIGGERS = [
  { reason: "Attendance Default", threshold: "< 75% (14-day window)", severity: "High" },
  { reason: "Project Delay", threshold: "> 48 hrs past sprint deadline", severity: "Critical" },
  { reason: "Assignment Delay", threshold: "2+ consecutive missed submissions", severity: "Medium" },
  { reason: "Quiz Failure", threshold: "< 60% cumulative average", severity: "Medium" },
  { reason: "Weekly Review Failure", threshold: "Unsatisfactory PM grade", severity: "High" },
  { reason: "Daily Task Abandonment", threshold: "3 consecutive inactive days", severity: "High" },
];

export const TASK_STATUSES = ["Backlog", "Assigned", "In Progress", "Review", "Completed"];

export const LEAD_STAGES = [
  "New Inquiry",
  "Contacted",
  "Demo Scheduled",
  "Counseling Completed",
  "Payment Pending",
  "Enrolled",
];

export const CURRENCY = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);