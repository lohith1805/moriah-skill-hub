import { useEffect, useState } from "react";
import { Users, CalendarCheck, Wallet, FileText, ArrowRight, ShieldCheck, Briefcase } from "lucide-react";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import AttendanceCheckinWidget from "../../components/widgets/AttendanceCheckinWidget";
import { getEmployees, getLeaveRequests, getPayroll } from "../../services/hrService";
import { getRecruitments } from "../../services/placementService";
import { stageTone, stageIndex } from "../../utils/placementPipeline";
import { CURRENCY } from "../../utils/constants";

// One-line summary of a placement for the HR dashboard list — covers every
// technical-round outcome (passed AND rejected) plus the final hired state.
function placementSubline(r) {
  if (r.stage === "Rejected") {
    const where = r.rejectedAt ? ` at the ${r.rejectedAt}` : "";
    const why = r.rejectionReason || r.rejectReason;
    return `Closed${where}${why ? ` — ${why}` : ""}`;
  }
  if (r.stage === "Placed") return "Hired ✓";
  if (stageIndex(r.stage) >= stageIndex("Technical Round Approved")) return "Technical round: passed";
  if (["Technical Round Scheduled", "Technical Round Completed"].includes(r.stage)) return "Technical round in progress";
  return r.stage;
}

export default function HrDashboard() {
  const [employees, setEmployees] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [payrollRows, setPayrollRows] = useState([]);
  const [placements, setPlacements] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      getEmployees().catch(() => []),
      getLeaveRequests().catch(() => []),
      getPayroll().catch(() => []),
      getRecruitments().catch(() => []),
    ])
      .then(([e, l, p, pl]) => {
        setEmployees(e);
        setLeaves(l);
        setPayrollRows(p);
        setPlacements(pl);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading HR operations dashboard…" /></div>;

  const pendingLeaves = leaves.filter((l) => l.status === "Pending");
  const totalPayroll = payrollRows.reduce((s, r) => s + (r.net || 0), 0);
  const avgAttendance = employees.length > 0
    ? Math.round(employees.reduce((s, e) => s + (e.attendance || 90), 0) / employees.length)
    : 95;

  const hiredCount = placements.filter((r) => r.stage === "Placed").length;
  const activePlacements = placements
    .filter((r) => r.stage !== "Placed" && r.stage !== "Rejected")
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0));
  const recentPlacements = [...placements]
    .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0))
    .slice(0, 8);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Human Resources Portal"
        subtitle="Digital offer letters, biometric attendance, payroll, and exit compliance"
        action={
          <Link to="/hr/attendance">
            <Button icon={ArrowRight}>Open Attendance & Leave</Button>
          </Link>
        }
      />

      <AttendanceCheckinWidget role="HR" />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Total Staff & Interns" value={employees.length} icon={Users} tone="primary" />
        <StatCard label="Pending Leave Requests" value={pendingLeaves.length} icon={CalendarCheck} tone={pendingLeaves.length > 0 ? "warning" : "success"} />
        <StatCard label="Avg. Staff Attendance" value={`${avgAttendance}%`} icon={ShieldCheck} tone="success" />
        <StatCard label="Monthly Payroll Total" value={CURRENCY(totalPayroll)} icon={Wallet} tone="gold" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-1">
        {/* Pending Leave Approvals */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Pending Leave Approvals" subtitle="Time-off requests awaiting HR action" />
            <Link to="/hr/attendance" className="text-xs text-primary-700 hover:underline font-medium">Manage Leave →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {pendingLeaves.length === 0 ? (
              <p className="text-sm text-ink-400 py-6 text-center">No pending leave requests. All caught up!</p>
            ) : (
              pendingLeaves.map((l) => (
                <div key={l.id} className="flex items-center justify-between py-3 text-left">
                  <div>
                    <p className="text-sm font-medium text-ink-900">{l.employee}</p>
                    <p className="text-xs text-ink-500">{l.type} · {l.from} → {l.to}</p>
                  </div>
                  <Badge tone="warning">Pending Action</Badge>
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Quick HR Navigation Hub */}
        <Card>
          <CardHeader title="HR Compliance & Operations Hub" subtitle="Module shortcuts" />
          <div className="grid grid-cols-2 gap-3 mt-3 text-left">
            <Link to="/hr/documents" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><FileText size={14} className="text-purple-700" /> Letters & Agreements</span>
              <span className="text-[11px] text-ink-500">Offer letters & digital e-signatures</span>
            </Link>
            <Link to="/hr/payroll" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><Wallet size={14} className="text-success-700" /> Payroll Engine</span>
              <span className="text-[11px] text-ink-500">Hourly trainer sessions & payslips</span>
            </Link>
            <Link to="/hr/exit" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col gap-1">
              <span className="text-xs font-bold text-ink-900 flex items-center gap-1.5"><CalendarCheck size={14} className="text-amber-700" /> Exit Management</span>
              <span className="text-[11px] text-ink-500">Clearances & PIP oversight</span>
            </Link>
          </div>
        </Card>
      </div>

      {/* Placement pipeline — technical & HR round outcomes (passed and rejected) + hires */}
      <Card>
        <div className="flex items-center justify-between gap-3 flex-wrap mb-1">
          <CardHeader title="Placement Pipeline" subtitle="Client & HR interview rounds, offers and hires" />
          <Link to="/hr/documents" className="text-xs text-primary-700 hover:underline font-medium">Open pipeline →</Link>
        </div>
        <div className="flex gap-6 text-sm mb-3">
          <span className="text-ink-600">In pipeline <strong className="text-ink-900">{activePlacements.length}</strong></span>
          <span className="text-success-700">Hired <strong>{hiredCount}</strong></span>
          <span className="text-error-600">Closed <strong>{placements.filter((r) => r.stage === "Rejected").length}</strong></span>
        </div>
        <div className="flex flex-col divide-y divide-border">
          {recentPlacements.length === 0 ? (
            <p className="text-sm text-ink-400 py-6 text-center">No placements yet. Candidates a client shortlists on the Talent Pool land here.</p>
          ) : (
            recentPlacements.map((r) => (
              <div key={r.id} className="flex items-center justify-between py-3 text-left gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ink-900 truncate">{r.candidateName || "Candidate"}</p>
                  <p className="text-xs text-ink-500 truncate">
                    {r.clientName || "—"} · {placementSubline(r)}
                  </p>
                </div>
                <Badge tone={stageTone(r.stage)}>{r.stage}</Badge>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}
