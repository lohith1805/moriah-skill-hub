import clsx from "clsx";

const TONES = {
  primary: "bg-primary-600",
  gold: "bg-gold-500",
  success: "bg-success-500",
  warning: "bg-warning-500",
  error: "bg-error-500",
};

export default function ProgressBar({ value, tone = "primary", label, showValue = true, size = "md" }) {
  const clamped = Math.min(100, Math.max(0, value));
  return (
    <div>
      {(label || showValue) && (
        <div className="flex items-center justify-between mb-1.5">
          {label && <span className="text-xs font-medium text-ink-600">{label}</span>}
          {showValue && <span className="text-xs font-semibold text-ink-900">{clamped}%</span>}
        </div>
      )}
      <div className={clsx("w-full rounded-full bg-cream-200 overflow-hidden", size === "sm" ? "h-1.5" : "h-2.5")}>
        <div className={clsx("h-full rounded-full transition-all duration-500", TONES[tone])} style={{ width: `${clamped}%` }} />
      </div>
    </div>
  );
}
