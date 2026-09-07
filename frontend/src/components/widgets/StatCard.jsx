import { ArrowUpRight, ArrowDownRight } from "lucide-react";
import clsx from "clsx";
import Card from "../ui/Card";

export default function StatCard({ label, value, icon: Icon, trend, trendLabel, tone = "primary" }) {
  const TONE_BG = {
    primary: "bg-primary-50 text-primary-700",
    gold: "bg-gold-100 text-gold-800",
    success: "bg-success-50 text-success-600",
    warning: "bg-warning-50 text-warning-600",
    error: "bg-error-50 text-error-600",
  };
  const positive = trend >= 0;

  return (
    <Card className="flex items-start justify-between">
      <div>
        <p className="text-sm text-ink-500">{label}</p>
        <p className="text-2xl font-semibold text-ink-900 font-display mt-1">{value}</p>
        {trend !== undefined && (
          <p className={clsx("flex items-center gap-1 text-xs mt-2 font-medium", positive ? "text-success-600" : "text-error-500")}>
            {positive ? <ArrowUpRight size={13} /> : <ArrowDownRight size={13} />}
            {Math.abs(trend)}% {trendLabel}
          </p>
        )}
      </div>
      {Icon && (
        <div className={clsx("flex h-10 w-10 items-center justify-center rounded-lg shrink-0", TONE_BG[tone])}>
          <Icon size={19} />
        </div>
      )}
    </Card>
  );
}
