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
};

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