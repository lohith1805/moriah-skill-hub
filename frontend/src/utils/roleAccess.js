import { ROLES } from "./constants";

// Maps each role to its dashboard home route and allowed route prefix.
export const ROLE_HOME = {
  [ROLES.STUDENT]: "/student/dashboard",
  [ROLES.TRAINER]: "/trainer/dashboard",
  [ROLES.DEVELOPER]: "/developer/dashboard",
  [ROLES.LEAD_GENERATOR]: "/leads/dashboard",
  [ROLES.HR]: "/hr/dashboard",
  [ROLES.BUSINESS_ANALYST]: "/ba/dashboard",
  [ROLES.ADMIN]: "/admin/dashboard",
  [ROLES.CLIENT]: "/client/dashboard",
};

export const ROLE_PREFIX = {
  [ROLES.STUDENT]: "/student",
  [ROLES.TRAINER]: "/trainer",
  [ROLES.DEVELOPER]: "/developer",
  [ROLES.LEAD_GENERATOR]: "/leads",
  [ROLES.HR]: "/hr",
  [ROLES.BUSINESS_ANALYST]: "/ba",
  [ROLES.ADMIN]: "/admin",
  [ROLES.CLIENT]: "/client",
};
