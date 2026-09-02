// Lightweight, dependency-free form validation helpers used across all forms.

export const required = (value) =>
  value === undefined || value === null || String(value).trim() === "" ? "This field is required" : "";

export const isEmail = (value) => {
  if (!value) return "";
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(value) ? "" : "Enter a valid email address";
};

export const isPhone = (value) => {
  if (!value) return "";
  const re = /^[0-9+\-\s]{7,15}$/;
  return re.test(value) ? "" : "Enter a valid phone number";
};

export const minLength = (len) => (value) =>
  value && value.length < len ? `Must be at least ${len} characters` : "";

export const isUrl = (value) => {
  if (!value) return "";
  try {
    new URL(value);
    return "";
  } catch {
    return "Enter a valid URL";
  }
};

export const passwordStrength = (value) => {
  if (!value) return "";
  if (value.length < 8) return "Password must be at least 8 characters";
  if (!/[A-Z]/.test(value)) return "Include at least one uppercase letter";
  if (!/[0-9]/.test(value)) return "Include at least one number";
  return "";
};

export const matches = (otherValue, message = "Values do not match") => (value) =>
  value !== otherValue ? message : "";

// Runs a { field: [validatorFns] } map against a values object.
// Returns { field: errorMessage } including only fields with errors.
export function validateForm(values, rules) {
  const errors = {};
  Object.entries(rules).forEach(([field, validators]) => {
    for (const validate of validators) {
      const message = validate(values[field]);
      if (message) {
        errors[field] = message;
        break;
      }
    }
  });
  return errors;
}
