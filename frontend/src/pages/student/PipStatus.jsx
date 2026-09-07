import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Clock } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getMyPipStatus } from "../../services/studentService";
import { PIP_TRIGGERS } from "../../utils/constants";

export default function StudentPipStatus() {
  const [pip, setPip] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyPipStatus()
      .then((p) => setPip(p))
      .catch(() => setPip(null))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <PageHeader title="PIP & Performance" subtitle="Automated Performance Improvement Plan tracking" breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "PIP Status" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Checking your status…" /></div>
      ) : pip ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <Card className="lg:col-span-2">
            <CardHeader title="Active Recovery Plan" subtitle={`Triggered on ${pip.triggeredOn}`} action={<Badge tone="warning" dot>{pip.status}</Badge>} />
            <div className="flex items-center gap-2 rounded-lg bg-warning-50 px-4 py-3 mb-4">
              <AlertTriangle size={17} className="text-warning-600 shrink-0" />
              <p className="text-sm text-warning-600"><strong>{pip.reason}</strong> triggered this recovery track.</p>
            </div>
            <ProgressBar value={pip.daysRemaining > 0 ? ((15 - pip.daysRemaining) / 15) * 100 : 100} tone="warning" label={`${Math.max(pip.daysRemaining, 0)} of 15 days remaining`} />

            <div className="mt-6">
              <p className="text-sm font-semibold text-ink-800 mb-3">Recovery milestones</p>
              <div className="flex flex-col gap-3">
                {[
                  { label: "Formal PIP notice dispatched", done: true },
                  { label: "Daily mentor check-ins scheduled", done: true },
                  { label: "≥ 85% task completion target", done: false },
                  { label: "Formal exit review with PM", done: false },
                ].map((m) => (
                  <div key={m.label} className="flex items-center gap-2.5 text-sm">
                    <CheckCircle2 size={16} className={m.done ? "text-success-600" : "text-ink-300"} />
                    <span className={m.done ? "text-ink-700" : "text-ink-400"}>{m.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </Card>

          <Card>
            <CardHeader title="PIP Trigger Reference" subtitle="Platform-wide thresholds" />
            <div className="flex flex-col gap-3">
              {PIP_TRIGGERS.map((t) => (
                <div key={t.reason} className="flex items-start justify-between gap-2 text-sm">
                  <div>
                    <p className="text-ink-800 font-medium">{t.reason}</p>
                    <p className="text-xs text-ink-400">{t.threshold}</p>
                  </div>
                  <Badge tone={t.severity === "Critical" ? "error" : t.severity === "High" ? "warning" : "neutral"}>{t.severity}</Badge>
                </div>
              ))}
            </div>
          </Card>
        </div>
      ) : (
        <EmptyState icon={CheckCircle2} title="You're in good standing" description="No active PIP flags on your account." />
      )}
    </div>
  );
}
