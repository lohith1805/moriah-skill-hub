import { useEffect, useState } from "react";
import { Users, KanbanSquare, GraduationCap, AlertTriangle, ArrowRight, ClipboardCheck } from "lucide-react";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ProgressBar from "../../components/ui/ProgressBar";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import ApplyLeaveWidget from "../../components/widgets/ApplyLeaveWidget";
import AttendanceCheckinWidget from "../../components/widgets/AttendanceCheckinWidget";
import { getBatches, getPipCases } from "../../services/trainerService";

export default function TrainerDashboard() {
  const [batches, setBatches] = useState([]);
  const [pipCases, setPipCases] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getBatches(), getPipCases()]).then(([b, p]) => {
      setBatches(b);
      setPipCases(p);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading trainer dashboard…" /></div>;

  const totalStudents = batches.reduce((s, b) => s + (b.students || 0), 0);
  const avgHealth = batches.length ? Math.round(batches.reduce((s, b) => s + (b.health || 0), 0) / batches.length) : 0;
  const activePip = pipCases.filter((p) => p.status !== "Resolved");

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Trainer / Project Manager Portal"
        subtitle="Batch health, sprint delivery, code review, and graduation readiness"
        action={
          <Link to="/trainer/standups">
            <Button icon={ArrowRight}>Run Today's Standup</Button>
          </Link>
        }
      />

      <AttendanceCheckinWidget role="Trainer / PM" />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Active Batches" value={batches.length} icon={Users} tone="primary" />
        <StatCard label="Total Students" value={totalStudents} icon={GraduationCap} tone="gold" />
        <StatCard label="Avg. Batch Health" value={`${avgHealth}%`} icon={KanbanSquare} tone={avgHealth >= 70 ? "success" : "warning"} />
        <StatCard label="Active PIP Cases" value={activePip.length} icon={AlertTriangle} tone={activePip.length > 0 ? "warning" : "success"} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-1">
        {/* Batch Health */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Batch Health" subtitle="Delivery + assessment signal per batch" />
            <Link to="/trainer/batches" className="text-xs text-primary-700 hover:underline font-medium">Manage Batches →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {batches.length === 0 ? (
              <p className="text-sm text-ink-400 py-6 text-center">No batches yet.</p>
            ) : (
              batches.map((b) => (
                <div key={b.id} className="py-3 flex flex-col gap-1.5 text-left">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-ink-900">{b.name}</span>
                    <span className="text-xs text-ink-500">{b.students} students</span>
                  </div>
                  <ProgressBar value={b.health || 0} tone={b.health >= 70 ? "success" : b.health >= 40 ? "gold" : "danger"} showValue size="sm" />
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Active PIP Cases */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Active PIP Cases" subtitle="Students on a Performance Improvement Plan" />
            <Link to="/trainer/pip" className="text-xs text-primary-700 hover:underline font-medium">Manage PIP →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {activePip.length === 0 ? (
              <p className="text-sm text-ink-400 py-6 text-center">No students currently on PIP. All caught up!</p>
            ) : (
              activePip.slice(0, 6).map((p) => (
                <div key={p.id} className="flex items-center justify-between py-3 text-left">
                  <div>
                    <p className="text-sm font-medium text-ink-900">{p.student}</p>
                    <p className="text-xs text-ink-500">{p.batch} · {p.reason}</p>
                  </div>
                  <Badge tone="warning">{p.status}</Badge>
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Quick Trainer Navigation Hub */}
        <Card className="lg:col-span-2">
          <CardHeader title="Trainer Operations Hub" subtitle="Module shortcuts" />
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3 text-left">
            <Link to="/trainer/sprints" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><KanbanSquare size={14} className="text-primary-700" /> Sprint Planning</span>
              <span className="text-[11px] text-ink-500">Plan and assign sprint work</span>
            </Link>
            <Link to="/trainer/code-review" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><ClipboardCheck size={14} className="text-purple-700" /> Code Review</span>
              <span className="text-[11px] text-ink-500">Review student submissions</span>
            </Link>
            <Link to="/trainer/analytics" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><Users size={14} className="text-success-700" /> Performance Analytics</span>
              <span className="text-[11px] text-ink-500">Track batch performance trends</span>
            </Link>
            <Link to="/trainer/graduation" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><GraduationCap size={14} className="text-amber-700" /> Graduation Approval</span>
              <span className="text-[11px] text-ink-500">Clear students ready to graduate</span>
            </Link>
          </div>
        </Card>
      </div>

      <ApplyLeaveWidget role="Trainer" />
    </div>
  );
}
