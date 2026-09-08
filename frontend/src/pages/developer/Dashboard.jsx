import { useEffect, useState } from "react";
import { FolderKanban, Bug, Library, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import ApplyLeaveWidget from "../../components/widgets/ApplyLeaveWidget";
import AttendanceCheckinWidget from "../../components/widgets/AttendanceCheckinWidget";
import { getProjects, getBugChallenges } from "../../services/developerService";

export default function DeveloperDashboard() {
  const [projects, setProjects] = useState([]);
  const [bugs, setBugs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getProjects({ mine: true }).catch(() => []), getBugChallenges().catch(() => [])])
      .then(([p, b]) => { setProjects(p); setBugs(b); })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading dashboard…" /></div>;

  const myProjectIds = new Set(projects.map((p) => p.id));
  const myBugs = bugs.filter((b) => myProjectIds.has(b.projectId));

  return (
    <div>
      <PageHeader title="Developer Dashboard" subtitle="Content authoring, architecture, and simulated project management" />

      <AttendanceCheckinWidget role="Developer" />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-4">
        <StatCard label="Authored Projects" value={projects.length} icon={FolderKanban} tone="primary" />
        <StatCard label="Bug Challenges" value={myBugs.length} icon={Bug} tone="gold" />
        <StatCard label="Published" value={projects.filter((p) => p.status === "Published").length} icon={CheckCircle2} tone="success" />
      </div>

      <Card className="mt-4">
        <CardHeader title="Your Projects" subtitle="Projects you authored — drafts and published" />
        <div className="flex flex-col divide-y divide-border">
          {projects.map((p) => (
            <div key={p.id} className="flex items-center justify-between py-3 flex-wrap gap-2">
              <div>
                <p className="text-sm font-medium text-ink-900">{p.title}</p>
                <p className="text-xs text-ink-500 mt-0.5">{p.stack.join(" · ")} · {p.version}</p>
              </div>
              <Badge tone={p.status === "Published" ? "success" : "neutral"}>{p.status}</Badge>
            </div>
          ))}
        </div>
      </Card>

      <div className="mt-4">
        <ApplyLeaveWidget role="Developer" />
      </div>
    </div>
  );
}
