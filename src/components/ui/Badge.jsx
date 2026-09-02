import clsx from "clsx";

const TONES = {
  neutral: "bg-cream-200 text-ink-700",
  primary: "bg-primary-50 text-primary-700",
  gold: "bg-gold-100 text-gold-800",
  success: "bg-success-50 text-success-600",
  warning: "bg-warning-50 text-warning-600",
  error: "bg-error-50 text-error-600",
  info: "bg-info-50 text-info-600",
};

export default function Badge({ children, tone = "neutral", className, dot = false }) {
  return (
    <span className={clsx("inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium", TONES[tone], className)}>
      {dot && <span className={clsx("h-1.5 w-1.5 rounded-full", tone === "neutral" ? "bg-ink-400" : "bg-current")} />}
      {children}
    </span>
  );
}
