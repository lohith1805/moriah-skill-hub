import { forwardRef } from "react";
import clsx from "clsx";

// Shared wrapper for label + control + helper/error text, used by Input, Select, Textarea.
export function FieldShell({ label, htmlFor, error, hint, required, children, className }) {
  return (
    <div className={clsx("flex flex-col gap-1.5", className)}>
      {label && (
        <label htmlFor={htmlFor} className="text-sm font-medium text-ink-700">
          {label} {required && <span className="text-error-500">*</span>}
        </label>
      )}
      {children}
      {error ? (
        <p className="text-xs text-error-500">{error}</p>
      ) : hint ? (
        <p className="text-xs text-ink-400">{hint}</p>
      ) : null}
    </div>
  );
}

const baseControl =
  "w-full rounded-lg border bg-white px-3.5 h-10 text-sm text-ink-900 placeholder:text-ink-400 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-400/40 focus:border-primary-400 disabled:bg-cream-100 disabled:text-ink-400";

export const Input = forwardRef(({ label, error, hint, required, id, icon: Icon, rightElement, className, ...props }, ref) => {
  const fieldId = id || props.name;

  const dateProps = {};
  if (props.type === "date" && !props.hasOwnProperty("min")) {
    const localToday = new Date();
    const year = localToday.getFullYear();
    const month = String(localToday.getMonth() + 1).padStart(2, '0');
    const day = String(localToday.getDate()).padStart(2, '0');
    dateProps.min = `${year}-${month}-${day}`;
  }

  return (
    <FieldShell label={label} htmlFor={fieldId} error={error} hint={hint} required={required}>
      <div className="relative flex items-center w-full">
        {Icon && (
          <div className="absolute left-3.5 text-ink-400 pointer-events-none flex items-center justify-center">
            <Icon size={18} />
          </div>
        )}
        <input
          ref={ref}
          id={fieldId}
          className={clsx(
            baseControl,
            Icon && "pl-10",
            rightElement && "pr-10",
            error && "border-error-500 focus:ring-error-500/30 focus:border-error-500",
            !error && "border-border",
            className
          )}
          {...dateProps}
          {...props}
        />
        {rightElement && (
          <div className="absolute right-3.5 flex items-center justify-center">
            {rightElement}
          </div>
        )}
      </div>
    </FieldShell>
  );
});
Input.displayName = "Input";

export const Textarea = forwardRef(({ label, error, hint, required, id, rows = 4, className, ...props }, ref) => {
  const fieldId = id || props.name;
  return (
    <FieldShell label={label} htmlFor={fieldId} error={error} hint={hint} required={required}>
      <textarea
        ref={ref}
        id={fieldId}
        rows={rows}
        className={clsx(baseControl, "h-auto py-2.5 resize-y", error && "border-error-500 focus:ring-error-500/30 focus:border-error-500", !error && "border-border", className)}
        {...props}
      />
    </FieldShell>
  );
});
Textarea.displayName = "Textarea";

export const Select = forwardRef(({ label, error, hint, required, id, options = [], placeholder, icon: Icon, className, ...props }, ref) => {
  const fieldId = id || props.name;
  return (
    <FieldShell label={label} htmlFor={fieldId} error={error} hint={hint} required={required}>
      <div className="relative flex items-center w-full">
        {Icon && (
          <div className="absolute left-3.5 text-ink-400 pointer-events-none flex items-center justify-center">
            <Icon size={18} />
          </div>
        )}
        <select
          ref={ref}
          id={fieldId}
          className={clsx(
            baseControl,
            Icon && "pl-10",
            "appearance-none bg-[url('data:image/svg+xml;utf8,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2210%22 height=%226%22><path d=%22M0 0l5 6 5-6z%22 fill=%22%235B6472%22/></svg>')] bg-no-repeat bg-[right_0.9rem_center]",
            error && "border-error-500 focus:ring-error-500/30 focus:border-error-500",
            !error && "border-border",
            className
          )}
          {...props}
        >
          {placeholder && <option value="">{placeholder}</option>}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>
    </FieldShell>
  );
});
Select.displayName = "Select";

export const Checkbox = forwardRef(({ label, id, className, ...props }, ref) => {
  const fieldId = id || props.name;
  return (
    <label htmlFor={fieldId} className={clsx("flex items-center gap-2 text-sm text-ink-700 cursor-pointer select-none", className)}>
      <input ref={ref} id={fieldId} type="checkbox" className="h-4 w-4 rounded border-border-dark text-primary-700 focus:ring-primary-400/40" {...props} />
      {label}
    </label>
  );
});
Checkbox.displayName = "Checkbox";
