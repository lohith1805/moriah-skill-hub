import clsx from "clsx";

export default function ProgressBar({ value, max = 100, tone = "primary", showLabel = true, size = "md" }) {
  const pct = Math.min(100, Math.round((value / max) * 100));
  const TONES = {
    primary: "bg-primary-600",
    gold: "bg-gold-500",
    success: "bg-success-500",
    warning: "bg-warning-500",
    error: "bg-error-500",
  };
  return (
    <div>
      <div className={clsx("w-full overflow-hidden rounded-full bg-cream-200", size === "sm" ? "h-1.5" : "h-2.5")}>
        <div className={clsx("h-full rounded-full transition-all duration-500", TONES[tone])} style={{ width: `${pct}%` }} />
      </div>
      {showLabel && <p className="mt-1 text-xs text-ink-500">{pct}%</p>}
    </div>
  );
}
