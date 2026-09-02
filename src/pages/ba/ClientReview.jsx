import { useEffect, useState } from "react";
import {
  MonitorCheck, Video, Calendar, Star, MessageSquare,
  TrendingUp, GitPullRequest, Layers, CheckCircle2, ArrowRight
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getClientProjects } from "../../services/clientService";

export default function BaClientReview() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getClientProjects().then((p) => {
      setProjects(p);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="flex justify-center py-20"><LoadingSpinner label="Loading client project reviews…" /></div>;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Project & Sprint Review"
        subtitle="Inspect batch sprint progress, sprint burn-down trajectories, and live project demos (MSH-FR-BA-03)"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Client Review" }]}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {projects.map((p) => (
          <Card key={p.id} className="text-left flex flex-col justify-between">
            <div>
              <div className="flex items-start justify-between gap-2 border-b border-border pb-3 mb-3">
                <div>
                  <h3 className="font-semibold text-ink-900 text-sm">{p.title}</h3>
                  <p className="text-xs text-ink-500">{p.client} · Assigned to: <strong className="text-primary-800">{p.batch}</strong></p>
                </div>
                <Badge tone="gold" dot>{p.milestone}</Badge>
              </div>

              <div className="space-y-3">
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-ink-600 font-medium">Sprint Completion Velocity</span>
                    <span className="font-bold text-ink-900">{p.progress}%</span>
                  </div>
                  <ProgressBar value={p.progress} tone="primary" />
                </div>

                <div className="p-3 bg-cream-50/70 rounded-lg border border-border grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-ink-500">Next Live Demo:</span>
                    <p className="font-semibold text-ink-900">{p.demoDate || "September 03, 2026"}</p>
                  </div>
                  <div>
                    <span className="text-ink-500">Client Sign-off:</span>
                    <p className="font-semibold text-success-700">Sprint 1 Approved</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between mt-4 pt-3 border-t border-border">
              <span className="text-xs text-ink-400 font-mono">ID: {p.id}</span>
              <Button size="sm" icon={Video} onClick={() => window.open("https://meet.google.com/zen-demo-call", "_blank")}>
                Join Live Demo
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
