import { useEffect, useState } from "react";
import {
  IndianRupee, Users, GraduationCap, Target, TrendingUp,
  CalendarCheck, ClipboardList, Gauge, ArrowRight
} from "lucide-react";
import {
  XAxis, YAxis, Tooltip, ResponsiveContainer,
  CartesianGrid, AreaChart, Area
} from "recharts";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getExecutiveMetrics } from "../../services/adminService";
import { CURRENCY } from "../../utils/constants";

const FUNNEL_COLORS = ["#1E4A78", "#2563eb", "#7c3aed", "#d97706", "#16a34a", "#0891b2"];

// v_lead_funnel stores raw pipeline statuses — give them readable labels, and
// fall back to a title-cased version of anything not in the map.
const STAGE_LABELS = {
  NEW: "New Leads",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  DEMO_SCHEDULED: "Demo Scheduled",
  DEMO_COMPLETED: "Demo Completed",
  NEGOTIATION: "In Negotiation",
  WON: "Won",
  ENROLLED: "Enrolled",
  LOST: "Lost",
};
const stageLabel = (s) =>
  STAGE_LABELS[s] ||
  String(s || "")
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());

const pctChange = (series) => {
  if (!series || series.length < 2) return null;
  const prev = series[series.length - 2].total;
  const last = series[series.length - 1].total;
  if (!prev) return null;
  return Math.round(((last - prev) / prev) * 1000) / 10;
};

export default function AdminDashboard() {
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    getExecutiveMetrics()
      .then((m) => setMetrics(m))
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading executive metrics…" /></div>;
  if (error || !metrics) {
    return (
      <div className="flex flex-col items-center gap-3 py-24 text-center">
        <p className="text-ink-700 font-semibold">Couldn't load platform metrics.</p>
        <p className="text-sm text-ink-400">The metrics service didn't respond. Try again in a moment.</p>
        <Button variant="secondary" onClick={() => window.location.reload()}>Retry</Button>
      </div>
    );
  }

  const revenue = metrics.recentRevenue || [];
  const funnel = metrics.leadFunnel || [];
  const funnelMax = funnel.reduce((m, f) => Math.max(m, f.count), 0) || 1;
  const revenueDelta = pctChange(revenue);
  const velocityPct = metrics.plannedPoints
    ? Math.round((metrics.completedPoints / metrics.plannedPoints) * 100)
    : null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Master Executive Dashboard"
        subtitle="Platform-wide KPIs from the live metrics service: revenue, active enrollments, cohort health, and CRM funnel (MSH-FR-ADM-01)"
        action={
          <div className="flex gap-2">
            <Link to="/admin/reports">
              <Button variant="secondary">Export Reports</Button>
            </Link>
            <Link to="/admin/users">
              <Button icon={Users}>Manage Users</Button>
            </Link>
          </div>
        }
      />

      {/* Top High-Level KPI Matrix — every value here is straight from
          GET /api/v1/admin/metrics/overview */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">
        <StatCard label="Monthly Recurring Revenue (MRR)" value={CURRENCY(metrics.mrr)} icon={IndianRupee} tone="primary" />
        <StatCard label="Annual Run Rate (ARR)" value={CURRENCY(metrics.arr)} icon={TrendingUp} tone="gold" />
        <StatCard label="Active Student Enrollments" value={metrics.activeStudents} icon={Users} tone="primary" />
        <StatCard label="Avg Task Completion" value={`${metrics.avgTaskCompletion}%`} icon={GraduationCap} tone="success" />
        <StatCard label="Avg Quiz Score" value={`${metrics.avgQuizAverage}%`} icon={Target} tone="warning" />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Revenue Growth Trend */}
        <Card className="lg:col-span-2 text-left">
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader
              title="Revenue Trajectory"
              subtitle="Captured revenue per month (v_revenue_monthly)"
            />
            {revenueDelta != null && (
              <span className={`text-xs font-bold px-2.5 py-1 rounded ${revenueDelta >= 0 ? "text-success-700 bg-success-50" : "text-error-600 bg-error-50"}`}>
                {revenueDelta >= 0 ? "+" : ""}{revenueDelta}% MoM
              </span>
            )}
          </div>

          {revenue.length === 0 ? (
            <p className="text-sm text-ink-400 py-16 text-center">No captured revenue recorded yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={revenue}>
                <defs>
                  <linearGradient id="colorMrr" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#1E4A78" stopOpacity={0.4} />
                    <stop offset="95%" stopColor="#1E4A78" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#E4E1D8" vertical={false} />
                <XAxis dataKey="month" tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} tickFormatter={(v) => `₹${Math.round(v / 100000)}L`} />
                <Tooltip formatter={(v) => CURRENCY(v)} contentStyle={{ borderRadius: 12, border: "1px solid #E4E1D8", backgroundColor: "#FFFFFF" }} />
                <Area type="monotone" dataKey="total" name="Captured revenue" stroke="#1E4A78" strokeWidth={3} fillOpacity={1} fill="url(#colorMrr)" />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </Card>

        {/* CRM Funnel Conversion */}
        <Card className="text-left flex flex-col">
          <div className="flex items-center justify-between mb-2">
            <CardHeader title="CRM Sales Funnel" subtitle="Leads by pipeline stage" />
            <span className="text-xs font-bold text-success-700 bg-success-50 px-2 py-0.5 rounded">
              {metrics.crmConversion}% Conv.
            </span>
          </div>

          {funnel.length === 0 ? (
            <p className="text-sm text-ink-400 py-16 text-center">No leads in the pipeline yet.</p>
          ) : (
            <div className="flex flex-col gap-2.5 mt-3">
              {funnel.map((item, i) => (
                <div key={item.stage} className="flex flex-col gap-1">
                  <div className="flex justify-between text-xs">
                    <span className="font-medium text-ink-700">{stageLabel(item.stage)}</span>
                    <span className="font-bold text-ink-900">{item.count}</span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-cream-100 overflow-hidden">
                    <div className="h-full rounded-full" style={{ width: `${(item.count / funnelMax) * 100}%`, backgroundColor: FUNNEL_COLORS[i % FUNNEL_COLORS.length] }} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Cohort health + admin shortcuts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="text-left">
          <CardHeader title="Cohort Health" subtitle="Platform-wide training averages" />
          <div className="flex flex-col gap-3.5 mt-3 text-xs">
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><CalendarCheck size={14} className="text-primary-700" /> Avg Attendance</span>
              <span className="font-mono font-semibold text-ink-900">{metrics.avgAttendance}%</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><ClipboardList size={14} className="text-purple-700" /> Avg Task Completion</span>
              <span className="font-mono font-semibold text-ink-900">{metrics.avgTaskCompletion}%</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><Target size={14} className="text-amber-700" /> Avg Quiz Score</span>
              <span className="font-mono font-semibold text-ink-900">{metrics.avgQuizAverage}%</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><Gauge size={14} className="text-emerald-700" /> Sprint Velocity</span>
              <span className="font-mono font-semibold text-ink-900">
                {velocityPct != null ? `${velocityPct}%` : "—"}
                <span className="text-ink-400 font-sans ml-1">({metrics.completedPoints}/{metrics.plannedPoints} pts)</span>
              </span>
            </div>
          </div>
        </Card>

        {/* Global Admin Shortcuts */}
        <Card className="lg:col-span-2 text-left">
          <CardHeader title="Global Platform Configuration Modules" subtitle="Quick admin management shortcuts" />
          <div className="grid sm:grid-cols-2 gap-3 mt-3">
            <Link to="/admin/plans" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">Subscription & Pricing Engine</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Tier prices, coupons, and feature entitlements</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Configure Pricing <ArrowRight size={12} /></span>
            </Link>

            <Link to="/admin/users" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">User Directory & Roles</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Staff invites, role edits, mutes, and suspensions</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Manage Users <ArrowRight size={12} /></span>
            </Link>

            <Link to="/admin/transactions" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">Financial Ledger & Refunds</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Stripe / Razorpay transactions and gateway refunds</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">View Transactions <ArrowRight size={12} /></span>
            </Link>

            <Link to="/admin/audit-logs" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">Tamper-Proof Audit Trails</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Security logins, role edits, and grade changes</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Inspect Audit Logs <ArrowRight size={12} /></span>
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
}
