import { useEffect, useState } from "react";
import { Trophy, Target, TrendingUp, Phone, MessageCircle, Video, Award, IndianRupee, Sparkles, Flame, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import StatCard from "../../components/widgets/StatCard";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getTargets } from "../../services/crmService";
import { CURRENCY } from "../../utils/constants";

export default function LeadTargets() {
  const [targets, setTargets] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getTargets().then((t) => {
      setTargets(t);
      setLoading(false);
    });
  }, []);

  if (loading) {
    return <div className="flex justify-center py-24"><LoadingSpinner label="Loading performance quotas & commissions…" /></div>;
  }

  const currentExec = targets[0] || {};
  const totalTeamRevenue = targets.reduce((sum, t) => sum + (t.revenue || 0), 0);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Target & Commission Tracking"
        subtitle="Daily outreach quotas, monthly closed revenue, conversion velocity, and performance leaderboard"
        breadcrumbs={[{ label: "Dashboard", to: "/leads/dashboard" }, { label: "Targets & Commissions" }]}
      />

      {/* Top Stat Highlights */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          label="Your Monthly Revenue"
          value={CURRENCY(currentExec.revenue || 0)}
          icon={IndianRupee}
          tone="primary"
          trend={14}
          trendLabel="vs last month"
        />
        <StatCard
          label="Commission Earned"
          value={CURRENCY(currentExec.commissionEarned || 0)}
          icon={Award}
          tone="gold"
        />
        <StatCard
          label="Conversion Velocity"
          value={currentExec.conversionVelocity || "3.5 days"}
          icon={TrendingUp}
          tone="success"
        />
        <StatCard
          label="Team Monthly Run-rate"
          value={CURRENCY(totalTeamRevenue)}
          icon={Trophy}
          tone="primary"
        />
      </div>

      {/* Daily Outreach Quotas (Calls, WhatsApp, Demos) */}
      <Card>
        <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
          <div>
            <h3 className="font-display font-semibold text-ink-900 text-left">Today's Outreach Quotas (MSH-FR-CRM-05)</h3>
            <p className="text-xs text-ink-500 text-left">Daily targets for customer outreach, demo execution, and follow-ups</p>
          </div>
          <Badge tone="success" dot className="px-2.5 py-1">Quotas Active</Badge>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl border border-border bg-cream-50/50 flex flex-col justify-between">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-ink-800 flex items-center gap-1.5">
                <Phone size={15} className="text-primary-700" /> Phone Calls Logged
              </span>
              <span className="text-xs font-semibold text-ink-900">{currentExec.callsDone} / {currentExec.callsQuota}</span>
            </div>
            <ProgressBar value={Math.round((currentExec.callsDone / currentExec.callsQuota) * 100)} tone="primary" />
            <p className="text-[11px] text-ink-500 mt-2 text-left">Target: 40 connected dials / day</p>
          </div>

          <div className="p-4 rounded-xl border border-border bg-cream-50/50 flex flex-col justify-between">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-ink-800 flex items-center gap-1.5">
                <MessageCircle size={15} className="text-success-600" /> WhatsApp Pitches
              </span>
              <span className="text-xs font-semibold text-ink-900">{currentExec.whatsappDone} / {currentExec.whatsappQuota}</span>
            </div>
            <ProgressBar value={Math.round((currentExec.whatsappDone / currentExec.whatsappQuota) * 100)} tone="success" />
            <p className="text-[11px] text-ink-500 mt-2 text-left">Target: 50 template messages / day</p>
          </div>

          <div className="p-4 rounded-xl border border-border bg-cream-50/50 flex flex-col justify-between">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-ink-800 flex items-center gap-1.5">
                <Video size={15} className="text-purple-600" /> Zoom Sprint Demos
              </span>
              <span className="text-xs font-semibold text-ink-900">{currentExec.demosDone} / {currentExec.demosQuota}</span>
            </div>
            <ProgressBar value={Math.round((currentExec.demosDone / currentExec.demosQuota) * 100)} tone="gold" />
            <p className="text-[11px] text-ink-500 mt-2 text-left">Target: 10 live demo walkthroughs / day</p>
          </div>
        </div>
      </Card>

      {/* Commission Slabs & Team Leaderboard */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Commission Calculator Matrix */}
        <Card className="flex flex-col justify-between">
          <div>
            <CardHeader
              title="Commission Tier Matrix"
              subtitle="Performance incentives on closed revenue"
            />
            <div className="flex flex-col gap-3 mt-2">
              <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-white text-left">
                <div>
                  <p className="text-xs font-semibold text-ink-900">Tier 1 (₹0 - ₹1,00,000)</p>
                  <p className="text-[11px] text-ink-500">Base Commission</p>
                </div>
                <Badge tone="neutral">5%</Badge>
              </div>

              <div className="flex items-center justify-between p-3 rounded-lg border border-border bg-white text-left">
                <div>
                  <p className="text-xs font-semibold text-ink-900">Tier 2 (₹1,00,000 - ₹3,00,000)</p>
                  <p className="text-[11px] text-ink-500">Accelerated Tier</p>
                </div>
                <Badge tone="primary">8%</Badge>
              </div>

              <div className="flex items-center justify-between p-3 rounded-lg border border-gold-200 bg-gold-50/40 text-left">
                <div>
                  <p className="text-xs font-semibold text-ink-900">Tier 3 (&gt; ₹3,00,000)</p>
                  <p className="text-[11px] text-ink-500">Top Performer Booster</p>
                </div>
                <Badge tone="gold">10%</Badge>
              </div>
            </div>
          </div>

          <div className="p-3 mt-4 rounded-lg bg-cream-100 border border-border text-left">
            <span className="text-xs text-ink-600">Your total commission this cycle: </span>
            <span className="text-sm font-bold text-primary-900">{CURRENCY(currentExec.commissionEarned || 28500)}</span>
          </div>
        </Card>

        {/* Growth Executive Team Leaderboard */}
        <Card className="lg:col-span-2">
          <CardHeader
            title="Executive Leaderboard"
            subtitle="Ranked by monthly closed revenue and conversion velocity"
          />

          <div className="flex flex-col divide-y divide-border mt-1">
            {targets.map((t, idx) => (
              <div key={t.agent} className="py-3.5 flex items-center justify-between flex-wrap gap-3 text-left">
                <div className="flex items-center gap-3">
                  <div className={`flex h-9 w-9 items-center justify-center rounded-xl font-bold text-sm ${idx === 0 ? "bg-amber-100 text-amber-800" : idx === 1 ? "bg-slate-100 text-slate-700" : "bg-orange-100 text-orange-800"}`}>
                    {idx === 0 ? <Trophy size={18} className="text-amber-600" /> : `#${idx + 1}`}
                  </div>
                  <div>
                    <p className="font-semibold text-ink-900 flex items-center gap-1.5">
                      {t.agent}
                      {idx === 0 && <Badge tone="gold" className="text-[10px] px-1.5 py-0.2">Top Closer</Badge>}
                    </p>
                    <p className="text-xs text-ink-500">{t.role} · Avg. Velocity: {t.conversionVelocity}</p>
                  </div>
                </div>

                <div className="flex items-center gap-5">
                  <div className="text-right">
                    <p className="text-sm font-bold text-ink-900 font-display">{CURRENCY(t.revenue)}</p>
                    <p className="text-[11px] text-ink-500">{t.closed}/{t.targetEnrolled} enrolled</p>
                  </div>
                  <div className="w-24 hidden sm:block">
                    <ProgressBar value={Math.round((t.revenue / t.targetRevenue) * 100)} tone={idx === 0 ? "gold" : "primary"} size="sm" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
