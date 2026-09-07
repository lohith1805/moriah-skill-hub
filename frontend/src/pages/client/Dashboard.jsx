import { useEffect, useState } from "react";
import { Briefcase, Users, MonitorCheck } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getClientProjects, getTalentPool } from "../../services/clientService";
import { useAuth } from "../../context/AuthContext";

export default function ClientDashboard() {
  const { user } = useAuth();
  const [projects, setProjects] = useState([]);
  const [talent, setTalent] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getClientProjects().catch(() => []), getTalentPool().catch(() => [])])
      .then(([p, t]) => { setProjects(p); setTalent(t); })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading dashboard…" /></div>;

  return (
    <div>
      <PageHeader title={`Welcome, ${user?.company || user?.name}`} subtitle="Track your project requirements, review talent, and join sprint demos" />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Active Projects" value={projects.length} icon={Briefcase} tone="primary" />
        <StatCard label="Talent Pool Matches" value={talent.length} icon={Users} tone="gold" />
        <StatCard label="Upcoming Demos" value={1} icon={MonitorCheck} tone="success" />
      </div>

      <Card className="mt-4">
        <CardHeader title="Your Project Submissions" />
        {projects.length === 0 ? (
          <p className="text-sm text-ink-400 py-4 text-center">No projects submitted yet.</p>
        ) : (
          <div className="flex flex-col divide-y divide-border">
            {projects.map((p) => (
              <div key={p.id} className="flex items-center justify-between py-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ink-800 truncate">{p.title}</p>
                  <p className="text-xs text-ink-400 mt-0.5">Submitted {p.submittedAt}</p>
                </div>
                <Badge tone={p.status === "Completed" ? "success" : p.status === "In Progress" ? "primary" : "warning"}>
                  {p.allocated ? p.status : "Awaiting batch"}
                </Badge>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
