import clsx from "clsx";

export default function Card({ children, className, padding = true, ...props }) {
  return (
    <div className={clsx("rounded-xl border border-border bg-white shadow-card", padding && "p-5", className)} {...props}>
      {children}
    </div>
  );
}

export function CardHeader({ title, subtitle, action, className }) {
  return (
    <div className={clsx("flex items-start justify-between gap-3 mb-4", className)}>
      <div>
        <h3 className="text-base font-semibold text-ink-900 font-display">{title}</h3>
        {subtitle && <p className="text-sm text-ink-500 mt-0.5">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
