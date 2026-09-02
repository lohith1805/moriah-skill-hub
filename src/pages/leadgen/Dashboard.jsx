import { useEffect, useState } from "react";
import { Target, PhoneCall, TrendingUp, Award, IndianRupee, Users, MessageSquare, ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import ApplyLeaveWidget from "../../components/widgets/ApplyLeaveWidget";
import AttendanceCheckinWidget from "../../components/widgets/AttendanceCheckinWidget";
import { getLeads, getTargets } from "../../services/crmService";
import { CURRENCY } from "../../utils/constants";

const STAGES = [
  "New Lead",
  "Contacted",
  "Demo Scheduled",
  "Plan Selected",
  "Payment Pending",
  "Won / Enrolled"
];

export default function LeadGenDashboard() {
  const [leads, setLeads] = useState([]);
  const [targets, setTargets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getLeads(), getTargets()]).then(([l, t]) => {
      setLeads(l);
      setTargets(t);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="flex justify-center py-24"><LoadingSpinner label="Loading CRM dashboard…" /></div>;

  const wonLeads = leads.filter((l) => l.stage === "Won / Enrolled" || l.stage === "Enrolled");
  const totalClosedRevenue = wonLeads.reduce((sum, l) => sum + (l.dealValue || 0), 0);
  const conversion = leads.length > 0 ? Math.round((wonLeads.length / leads.length) * 100) : 0;
  const topAgent = targets[0] || { agent: "—", revenue: 0 };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Lead Generator & Growth Portal"
        subtitle="Multi-source ingestion, sales funnel KPIs, and revenue conversion"
        action={
          <Link to="/leads/pipeline">
            <Button icon={ArrowRight}>Open Pipeline Board</Button>
          </Link>
        }
      />

      <AttendanceCheckinWidget role="Lead Gen" />

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Total Leads Ingested" value={leads.length} icon={Target} tone="primary" />
        <StatCard label="Closed Revenue" value={CURRENCY(totalClosedRevenue)} icon={IndianRupee} tone="success" trend={12} trendLabel="vs last month" />
        <StatCard label="Conversion Rate" value={`${conversion}%`} icon={TrendingUp} tone="gold" />
        <StatCard label="Top Sales Executive" value={topAgent.agent} icon={Award} tone="primary" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-1">
        {/* Sales Pipeline Funnel */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Sales Pipeline Funnel" subtitle="Distribution of leads across sales stages" />
            <Link to="/leads/pipeline" className="text-xs text-primary-700 hover:underline font-medium">View Board →</Link>
          </div>
          <div className="flex flex-col gap-3">
            {STAGES.map((stage) => {
              const count = leads.filter((l) => l.stage === stage || (stage === "New Lead" && l.stage === "New Inquiry") || (stage === "Won / Enrolled" && l.stage === "Enrolled")).length;
              const pct = leads.length > 0 ? Math.round((count / leads.length) * 100) : 0;
              return (
                <div key={stage} className="flex flex-col gap-1 text-left">
                  <div className="flex items-center justify-between text-xs sm:text-sm">
                    <span className="font-medium text-ink-700">{stage}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-ink-400 text-xs">{pct}%</span>
                      <Badge tone={stage === "Won / Enrolled" ? "success" : "primary"}>{count}</Badge>
                    </div>
                  </div>
                  <div className="w-full h-1.5 rounded-full bg-cream-100 overflow-hidden">
                    <div className={`h-full rounded-full ${stage === "Won / Enrolled" ? "bg-success-500" : "bg-primary-600"}`} style={{ width: `${Math.max(5, pct)}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </Card>

        {/* Executive Leaderboard & Targets */}
        <Card>
          <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
            <CardHeader title="Team Leaderboard & Quotas" subtitle="Monthly closed revenue attribution" />
            <Link to="/leads/targets" className="text-xs text-primary-700 hover:underline font-medium">View Quotas →</Link>
          </div>
          <div className="flex flex-col divide-y divide-border">
            {targets.map((t, idx) => (
              <div key={t.agent} className="flex items-center justify-between py-3 text-left">
                <div className="flex items-center gap-3">
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cream-100 font-semibold text-xs text-ink-700">
                    #{idx + 1}
                  </span>
                  <div>
                    <p className="text-sm font-medium text-ink-900">{t.agent}</p>
                    <p className="text-xs text-ink-500">{t.closed} deals won · {t.callsDone} calls logged</p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-sm font-bold text-ink-900 font-display">{CURRENCY(t.revenue)}</span>
                  <p className="text-[11px] text-success-600 font-medium">+{CURRENCY(t.commissionEarned)} comm.</p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <ApplyLeaveWidget role="Lead Generator" />
    </div>
  );
}
