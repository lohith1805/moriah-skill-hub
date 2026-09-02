// This app has no way to deliver real email invites without a backend, so
// there are no seeded accounts here. The very first login to any given role
// creates that account on the spot (see authService.js login()), and a
// default Admin account is bootstrapped automatically the first time the
// app runs (see authService.js readRegisteredUsers()).
export const USERS = [];

const DEFAULT_BATCHES = [];

let loadedBatches = [...DEFAULT_BATCHES];
const savedBatches = localStorage.getItem("msh_batches");
if (savedBatches) {
  try {
    loadedBatches = JSON.parse(savedBatches);
  } catch (e) {
    loadedBatches = [...DEFAULT_BATCHES];
  }
} else {
  localStorage.setItem("msh_batches", JSON.stringify(DEFAULT_BATCHES));
}

export const BATCHES = loadedBatches;

const DEFAULT_SPRINTS = [];

let loadedSprints = [...DEFAULT_SPRINTS];
const savedSprints = localStorage.getItem("msh_sprints");
if (savedSprints) {
  try {
    loadedSprints = JSON.parse(savedSprints);
  } catch (e) {
    loadedSprints = [...DEFAULT_SPRINTS];
  }
} else {
  localStorage.setItem("msh_sprints", JSON.stringify(DEFAULT_SPRINTS));
}

export const SPRINTS = loadedSprints;

const DEFAULT_TASKS = [];

let loadedTasks = [...DEFAULT_TASKS];
const savedTasks = localStorage.getItem("msh_sprint_tasks");
if (savedTasks) {
  try {
    loadedTasks = JSON.parse(savedTasks);
  } catch (e) {
    loadedTasks = [...DEFAULT_TASKS];
  }
} else {
  localStorage.setItem("msh_sprint_tasks", JSON.stringify(DEFAULT_TASKS));
}

export const TASKS = loadedTasks;

const DEFAULT_PIP_RECORDS = [];

let loadedPipRecords = [...DEFAULT_PIP_RECORDS];
const savedPipRecords = localStorage.getItem("msh_pip_records");
if (savedPipRecords) {
  try {
    loadedPipRecords = JSON.parse(savedPipRecords);
  } catch (e) {
    loadedPipRecords = [...DEFAULT_PIP_RECORDS];
  }
} else {
  localStorage.setItem("msh_pip_records", JSON.stringify(DEFAULT_PIP_RECORDS));
}

export const PIP_RECORDS = loadedPipRecords;

export const PROJECTS = [];

export const LEADS = [];

const DEFAULT_EMPLOYEES = [];

let loadedEmployees = [...DEFAULT_EMPLOYEES];
const savedEmployees = localStorage.getItem("msh_employees");
if (savedEmployees) {
  try {
    loadedEmployees = JSON.parse(savedEmployees);
  } catch (e) {
    loadedEmployees = [...DEFAULT_EMPLOYEES];
  }
} else {
  localStorage.setItem("msh_employees", JSON.stringify(DEFAULT_EMPLOYEES));
}

export const EMPLOYEES = loadedEmployees;

export const LEAVE_REQUESTS = [];

const DEFAULT_REQUIREMENT_DOCS = [];

let loadedRequirementDocs = [...DEFAULT_REQUIREMENT_DOCS];
const savedRequirementDocs = localStorage.getItem("msh_requirement_docs");
if (savedRequirementDocs) {
  try {
    loadedRequirementDocs = JSON.parse(savedRequirementDocs);
  } catch (e) {
    loadedRequirementDocs = [...DEFAULT_REQUIREMENT_DOCS];
  }
} else {
  localStorage.setItem("msh_requirement_docs", JSON.stringify(DEFAULT_REQUIREMENT_DOCS));
}

export const REQUIREMENT_DOCS = loadedRequirementDocs;

const DEFAULT_TRANSACTIONS = [];

let loadedTransactions = [...DEFAULT_TRANSACTIONS];
const savedTransactions = localStorage.getItem("msh_transactions");
if (savedTransactions) {
  try {
    loadedTransactions = JSON.parse(savedTransactions);
  } catch (e) {
    loadedTransactions = [...DEFAULT_TRANSACTIONS];
  }
} else {
  localStorage.setItem("msh_transactions", JSON.stringify(DEFAULT_TRANSACTIONS));
}

export const TRANSACTIONS = loadedTransactions;

export const AUDIT_LOGS = [];

const DEFAULT_CLIENT_PROJECTS = [];

let loadedClientProjects = [...DEFAULT_CLIENT_PROJECTS];
const savedClientProjects = localStorage.getItem("msh_client_projects");
if (savedClientProjects) {
  try {
    loadedClientProjects = JSON.parse(savedClientProjects);
  } catch (e) {
    loadedClientProjects = [...DEFAULT_CLIENT_PROJECTS];
  }
} else {
  localStorage.setItem("msh_client_projects", JSON.stringify(DEFAULT_CLIENT_PROJECTS));
}

export const CLIENT_PROJECTS = loadedClientProjects;

export const TALENT_POOL = [];

const DEFAULT_CERTIFICATES = [];

let loadedCertificates = [...DEFAULT_CERTIFICATES];
const savedCertificates = localStorage.getItem("msh_certificates");
if (savedCertificates) {
  try {
    loadedCertificates = JSON.parse(savedCertificates);
  } catch (e) {
    loadedCertificates = [...DEFAULT_CERTIFICATES];
  }
} else {
  localStorage.setItem("msh_certificates", JSON.stringify(DEFAULT_CERTIFICATES));
}

export const CERTIFICATES = loadedCertificates;

export const NOTIFICATIONS = [];

const DEFAULT_ASSESSMENTS = [];

let loadedAssessments = [...DEFAULT_ASSESSMENTS];
const savedAssessments = localStorage.getItem("msh_assessments");
if (savedAssessments) {
  try {
    loadedAssessments = JSON.parse(savedAssessments);
  } catch (e) {
    loadedAssessments = [...DEFAULT_ASSESSMENTS];
  }
} else {
  localStorage.setItem("msh_assessments", JSON.stringify(DEFAULT_ASSESSMENTS));
}

export let ASSESSMENTS = loadedAssessments;