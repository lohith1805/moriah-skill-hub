import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import PipProgressPanel from "../../components/pip/PipProgressPanel";
import { getMyPipProgress, RULE_LABEL } from "../../services/pipService";
import { PIP_TRIGGERS } from "../../utils/constants";

export default function StudentPipStatus() {
  const [progress, setProgress] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyPipProgress()
      .then(setProgress)
      .catch(() => setProgress(null))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <PageHeader
        title="PIP & Performance"
        subtitle="Your Performance Improvement Plan — what to do to clear it (metrics refresh nightly)"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "PIP Status" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Checking your status…" /></div>
      ) : progress ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <Card className="lg:col-span-2">
            <CardHeader
              title="Your recovery plan"
              subtitle={RULE_LABEL[progress.ruleCode] || progress.reason}
              action={<Badge tone="warning" dot>{progress.status === "TRIGGERED" ? "Triggered" : "In recovery"}</Badge>}
            />
            <div className="flex items-start gap-2 rounded-lg bg-warning-50 px-4 py-3 mb-4">
              <AlertTriangle size={17} className="text-warning-600 shrink-0 mt-0.5" />
              <p className="text-sm text-warning-700">
                <strong>{RULE_LABEL[progress.ruleCode] || progress.reason}</strong> put you on this plan. Work through the
                recovery tasks your PM sets below and hit the clearance criteria — do that and you're cleared,
                automatically once the 15-day window ends even if your PM hasn't reviewed it yet.
              </p>
            </div>
            <PipProgressPanel progress={progress} editable={false} />
          </Card>

          <Card>
            <CardHeader title="How a PIP is triggered" subtitle="Platform-wide thresholds" />
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
        <EmptyState icon={CheckCircle2} title="You're in good standing" description="No active PIP on your account." />
      )}
    </div>
  );
}
