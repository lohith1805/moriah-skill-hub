import { useEffect, useState } from "react";
import {
  FileText, Users, MonitorCheck, Calendar, Plus, ArrowRight,
  BarChart3, CheckCircle2, Video
} from "lucide-react";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import ApplyLeaveWidget from "../../components/widgets/ApplyLeaveWidget";
import AttendanceCheckinWidget from "../../components/widgets/AttendanceCheckinWidget";
import { getDocuments, getSprintResourcePlan, getMeetings } from "../../services/baService";

export default function BaDashboard() {
  const [docs, setDocs] = useState([]);
  const [plans, setPlans] = useState([]);
  const [meetings, setMeetings] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      getDocuments().catch(() => []),
      getSprintResourcePlan().catch(() => []),
      getMeetings().catch(() => []),
    ]).then(([d, p, m]) => {
      setDocs(d);
      setPlans(p);
      setMeetings(m);
    }).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading BA command dashboard…" /></div>;

  const totalDevHours = plans.reduce((s, p) => s + (p.devHours || 0), 0);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Business Analyst Portal"
        subtitle="Requirements authoring (BRD/SRS/FRS), sprint & resource planning, and client ceremony coordination"
        action={
          <Link to="/ba/documents">
            <Button icon={Plus}>Author New Document</Button>
          </Link>
        }
      />

      <AttendanceCheckinWidget role="Business Analyst" />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Authored Specifications" value={docs.length} icon={FileText} tone="primary" />
        <StatCard label="Active Sprint Plans" value={plans.length} icon={BarChart3} tone="gold" />
        <StatCard label="Total Dev Hours Planned" value={`${totalDevHours}h`} icon={MonitorCheck} tone="success" />
        <StatCard label="Upcoming Ceremonies" value={meetings.length} icon={Calendar} tone="primary" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-1">
        {/* Specifications List */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Authored Requirements (BRD / SRS / FRS)" subtitle="Recent specifications under review" />
            <Link to="/ba/documents" className="text-xs text-primary-700 hover:underline font-medium">All Specs →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {docs.map((d) => (
              <div key={d.id} className="py-3 flex items-center justify-between text-left">
                <div>
                  <p className="text-sm font-semibold text-ink-900">{d.title}</p>
                  <p className="text-xs text-ink-500">{d.type} · v{d.version} · {d.client}</p>
                </div>
                <Badge tone={d.status === "Approved" ? "success" : d.status === "Draft" ? "neutral" : "warning"}>
                  {d.status}
                </Badge>
              </div>
            ))}
          </div>
        </Card>

        {/* Upcoming Ceremonies & Meetings */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Client Ceremonies & Sprint Demos" subtitle="Scheduled meetings and demo reviews" />
            <Link to="/ba/meetings" className="text-xs text-primary-700 hover:underline font-medium">Manage Meetings →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {meetings.map((m) => (
              <div key={m.id} className="py-3 flex items-center justify-between text-left">
                <div>
                  <p className="text-sm font-semibold text-ink-900">{m.title}</p>
                  <p className="text-xs text-ink-500">{m.date} at {m.time} · {m.type}</p>
                </div>
                <Button size="xs" icon={Video} onClick={() => window.open(m.meetLink, "_blank")}>
                  Join
                </Button>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <ApplyLeaveWidget role="Business Analyst" />
    </div>
  );
}
