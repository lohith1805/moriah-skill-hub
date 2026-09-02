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
    Promise.all([getClientProjects(), getTalentPool()]).then(([p, t]) => { setProjects(p); setTalent(t); setLoading(false); });
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
        <CardHeader title="Project Progress" />
        <div className="flex flex-col gap-4">
          {projects.map((p) => (
            <div key={p.id}>
              <div className="flex items-center justify-between mb-1">
                <p className="text-sm font-medium text-ink-800">{p.title}</p>
                <Badge tone="gold">{p.milestone}</Badge>
              </div>
              <ProgressBar value={p.progress} tone="primary" showValue={true} />
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
