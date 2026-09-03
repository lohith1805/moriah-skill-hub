import { useEffect, useState } from "react";
import {
  IndianRupee, Users, GraduationCap, AlertTriangle, Target, Server,
  TrendingUp, ShieldCheck, Activity, ArrowRight, Zap, CheckCircle2
} from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer,
  CartesianGrid, BarChart, Bar, AreaChart, Area
} from "recharts";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getExecutiveMetrics } from "../../services/adminService";
import { CURRENCY } from "../../utils/constants";

const REVENUE_GROWTH_DATA = [
  { month: "Apr 2026", mrr: 1200000, arr: 14400000 },
  { month: "May 2026", mrr: 1350000, arr: 16200000 },
  { month: "Jun 2026", mrr: 1520000, arr: 18240000 },
  { month: "Jul 2026", mrr: 1690000, arr: 20280000 },
  { month: "Aug 2026", mrr: 1850000, arr: 22200000 },
];

const CRM_FUNNEL_DATA = [
  { stage: "New Inquiries", count: 240, fill: "#1E4A78" },
  { stage: "Contacted", count: 180, fill: "#2563eb" },
  { stage: "Demo Attended", count: 110, fill: "#7c3aed" },
  { stage: "Plan Selected", count: 65, fill: "#d97706" },
  { stage: "Enrolled (Won)", count: 48, fill: "#16a34a" },
];

export default function AdminDashboard() {
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getExecutiveMetrics().then((m) => {
      setMetrics(m);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading executive master metrics…" /></div>;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Master Executive Dashboard"
        subtitle="Real-time platform-wide KPIs: MRR / ARR, Active Enrollments, Batch Pass Rates, and CRM Velocity (MSH-FR-ADM-01)"
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

      {/* Top High-Level KPI Matrix */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">
        <StatCard
          label="Monthly Recurring Revenue (MRR)"
          value={CURRENCY(metrics.mrr)}
          icon={IndianRupee}
          tone="primary"
          trend={12.4}
          trendLabel="vs last month"
        />
        <StatCard
          label="Annual Run Rate (ARR)"
          value={CURRENCY(metrics.arr || metrics.mrr * 12)}
          icon={TrendingUp}
          tone="gold"
        />
        <StatCard
          label="Active Student Enrollments"
          value={metrics.activeStudents}
          icon={Users}
          tone="primary"
          trend={8}
          trendLabel="vs last cohort"
        />
        <StatCard
          label="Batch Pass Rate"
          value={`${metrics.batchPassRate}%`}
          icon={GraduationCap}
          tone="success"
        />
        <StatCard
          label="Platform PIP Ratio"
          value={`${metrics.pipRatio}%`}
          icon={AlertTriangle}
          tone="warning"
          trend={-1.8}
          trendLabel="lower is better"
        />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Revenue Growth Trend */}
        <Card className="lg:col-span-2 text-left">
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader
              title="Revenue Trajectory (MRR / ARR Growth)"
              subtitle="5-Month consolidated platform trajectory in INR"
            />
            <Badge tone="success" dot className="px-2.5 py-1">Healthy +18% QoQ</Badge>
          </div>

          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={REVENUE_GROWTH_DATA}>
              <defs>
                <linearGradient id="colorMrr" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#1E4A78" stopOpacity={0.4} />
                  <stop offset="95%" stopColor="#1E4A78" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#E4E1D8" vertical={false} />
              <XAxis dataKey="month" tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} tickFormatter={(v) => `₹${v / 100000}L`} />
              <Tooltip formatter={(v) => CURRENCY(v)} contentStyle={{ borderRadius: 12, border: "1px solid #E4E1D8", backgroundColor: "#FFFFFF" }} />
              <Area type="monotone" dataKey="mrr" stroke="#1E4A78" strokeWidth={3} fillOpacity={1} fill="url(#colorMrr)" />
            </AreaChart>
          </ResponsiveContainer>
        </Card>

        {/* CRM Funnel Conversion */}
        <Card className="text-left flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <CardHeader
                title="CRM Sales Funnel"
                subtitle="Inquiries to Paid Enrollment"
              />
              <span className="text-xs font-bold text-success-700 bg-success-50 px-2 py-0.5 rounded">
                {metrics.crmConversion}% Conv.
              </span>
            </div>

            <div className="flex flex-col gap-2.5 mt-3">
              {CRM_FUNNEL_DATA.map((item) => (
                <div key={item.stage} className="flex flex-col gap-1">
                  <div className="flex justify-between text-xs">
                    <span className="font-medium text-ink-700">{item.stage}</span>
                    <span className="font-bold text-ink-900">{item.count}</span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-cream-100 overflow-hidden">
                    <div className="h-full rounded-full" style={{ width: `${(item.count / 240) * 100}%`, backgroundColor: item.fill }} />
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="pt-4 border-t border-border mt-4 flex items-center justify-between text-xs">
            <span className="text-ink-500">Pipeline Velocity:</span>
            <span className="font-semibold text-primary-800">3.5 Days avg. cycle</span>
          </div>
        </Card>
      </div>

      {/* Global System Health & Quick Module Controls */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="text-left">
          <CardHeader title="System & Infrastructure SLA" subtitle="Uptime, latency, and security monitors" />
          <div className="flex flex-col gap-3.5 mt-3 text-xs">
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><Server size={14} className="text-primary-700" /> Platform Server Status</span>
              <Badge tone="success" dot>{metrics.serverStatus}</Badge>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><Activity size={14} className="text-purple-700" /> API Latency (p95)</span>
              <span className="font-mono font-semibold text-ink-900">112ms</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-cream-50 border border-border">
              <span className="text-ink-700 flex items-center gap-2"><ShieldCheck size={14} className="text-emerald-700" /> 2FA & Audit Trail</span>
              <Badge tone="success">Active & Enforced</Badge>
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
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Configure Pricing →</span>
            </Link>

            <Link to="/admin/users" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">User Directory & Granular RBAC</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Role permissions, mutes, invites, and suspensions</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Manage Users →</span>
            </Link>

            <Link to="/admin/transactions" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">Financial Ledger & Refunds</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Stripe/Razorpay transactions and instant refunds</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">View Transactions →</span>
            </Link>

            <Link to="/admin/audit-logs" className="p-3.5 rounded-xl border border-border bg-cream-50/50 hover:bg-cream-100 transition-colors flex flex-col justify-between">
              <div>
                <p className="text-xs font-bold text-ink-900">Tamper-Proof Audit Trails</p>
                <p className="text-[11px] text-ink-500 mt-0.5">Security logins, role edits, and grade changes</p>
              </div>
              <span className="text-xs font-semibold text-primary-800 mt-2 flex items-center gap-1">Inspect Audit Logs →</span>
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
}
