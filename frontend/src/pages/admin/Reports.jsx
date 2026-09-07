import { useEffect, useState } from "react";
import {
  Users, Wallet, ScrollText, Download, IndianRupee, TrendingUp,
  GraduationCap, Target, CalendarCheck, BarChart3,
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import StatCard from "../../components/widgets/StatCard";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { getExecutiveMetrics, exportReport } from "../../services/adminService";
import { CURRENCY } from "../../utils/constants";

// The three exports the backend actually builds (POST /api/v1/admin/exports/{report}).
// Each maps 1:1 onto a real query — see ExportReport.java.
const EXPORTS = [
  {
    id: "USERS",
    title: "Platform User Directory",
    icon: Users,
    iconClass: "bg-primary-50 text-primary-700",
    description: "Every account: full name, email, roles, status, GitHub handle and join date. Mirrors the User Management list, unpaginated.",
  },
  {
    id: "REVENUE",
    title: "Revenue History",
    icon: Wallet,
    iconClass: "bg-gold-50 text-gold-700",
    description: "Captured revenue per month across the platform's whole history (v_revenue_monthly) — currency and total captured per period.",
  },
  {
    id: "AUDIT",
    title: "Audit & Security Log",
    icon: ScrollText,
    iconClass: "bg-success-50 text-success-700",
    description: "Every audited action: who did it, the action, the target entity, the resolved client IP and the timestamp. The full trail, unpaginated.",
  },
];

const FORMATS = [
  { value: "xlsx", label: "Excel (.xlsx)" },
  { value: "csv", label: "CSV (.csv)" },
  { value: "pdf", label: "PDF (.pdf)" },
];

const fmtMonth = (m) => {
  if (!m) return "—";
  const d = new Date(`${String(m).slice(0, 7)}-01T00:00:00`);
  return Number.isNaN(d.getTime()) ? m : d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
};

export default function AdminReports() {
  const { notify } = useToast();
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [format, setFormat] = useState({}); // { [reportId]: "xlsx" | "csv" | "pdf" }
  const [busy, setBusy] = useState(null); // reportId currently generating

  useEffect(() => {
    getExecutiveMetrics()
      .then(setMetrics)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const runExport = async (report) => {
    const fmt = format[report.id] || "xlsx";
    setBusy(report.id);
    try {
      const res = await exportReport(report.id, fmt);
      if (res?.downloadUrl) {
        window.open(res.downloadUrl, "_blank", "noopener,noreferrer");
        notify(
          `${report.title} generated (${res.rowCount ?? 0} rows) — download started.`,
          { type: "success", title: "Report exported" }
        );
      } else {
        notify("The export was generated but no download link came back.", { type: "warning" });
      }
    } catch (e) {
      notify(e?.message || "Could not generate the report.", { type: "error", title: "Export failed" });
    } finally {
      setBusy(null);
    }
  };

  const revenue = (metrics?.recentRevenue || []).slice(-6);
  const funnel = metrics?.leadFunnel || [];
  const funnelMax = Math.max(1, ...funnel.map((f) => f.count));

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Reporting Engine"
        subtitle="Live platform metrics and downloadable compliance exports — all from the backend (MSH-FR-ADM-05)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Reporting Engine" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading platform metrics…" /></div>
      ) : error ? (
        <Card><p className="text-sm text-ink-500 py-8 text-center">Couldn't load platform metrics from the backend.</p></Card>
      ) : (
        <>
          {/* Live KPI snapshot */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            <StatCard label="Monthly Recurring Revenue" value={CURRENCY(metrics.mrr)} icon={IndianRupee} tone="primary" />
            <StatCard label="Annual Run Rate" value={CURRENCY(metrics.arr)} icon={TrendingUp} tone="gold" />
            <StatCard label="Active Student Enrollments" value={metrics.activeStudents} icon={Users} tone="primary" />
            <StatCard label="CRM Conversion" value={`${metrics.crmConversion}%`} icon={Target} tone="warning" />
            <StatCard label="Avg Attendance" value={`${metrics.avgAttendance}%`} icon={CalendarCheck} tone="success" />
            <StatCard label="Avg Task Completion" value={`${metrics.avgTaskCompletion}%`} icon={GraduationCap} tone="success" />
            <StatCard label="Avg Quiz Score" value={`${metrics.avgQuizAverage}%`} icon={BarChart3} tone="warning" />
            <StatCard label="Sprint Velocity Ratio" value={metrics.velocityRatio} icon={TrendingUp} tone="primary" />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Revenue history preview */}
            <Card>
              <CardHeader title="Captured Revenue" subtitle="Last 6 months — v_revenue_monthly" />
              {revenue.length === 0 ? (
                <p className="text-sm text-ink-400 py-6 text-center">No revenue recorded yet.</p>
              ) : (
                <div className="flex flex-col divide-y divide-border mt-2">
                  {revenue.map((r) => (
                    <div key={r.month} className="flex items-center justify-between py-2.5 text-sm">
                      <span className="text-ink-600">{fmtMonth(r.month)}</span>
                      <span className="font-semibold text-ink-900 font-display">{CURRENCY(r.total)}</span>
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* Lead funnel preview */}
            <Card>
              <CardHeader title="Lead Funnel" subtitle="Current pipeline distribution — v_lead_funnel" />
              {funnel.length === 0 ? (
                <p className="text-sm text-ink-400 py-6 text-center">No leads captured yet.</p>
              ) : (
                <div className="flex flex-col gap-2.5 mt-2">
                  {funnel.map((f) => (
                    <div key={f.stage} className="flex flex-col gap-1 text-left">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-medium text-ink-700 capitalize">{String(f.stage || "").toLowerCase().replace(/_/g, " ")}</span>
                        <span className="font-bold text-ink-900">{f.count}</span>
                      </div>
                      <div className="w-full h-1.5 rounded-full bg-cream-100 overflow-hidden">
                        <div className="h-full rounded-full bg-primary-600" style={{ width: `${Math.max(4, (f.count / funnelMax) * 100)}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>

          {/* Downloadable exports */}
          <div>
            <h3 className="font-display font-semibold text-ink-900 mb-1">Compliance Exports</h3>
            <p className="text-xs text-ink-500 mb-4">Generated server-side from live data and delivered as a short-lived download link.</p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {EXPORTS.map((r) => (
                <Card key={r.id} className="text-left flex flex-col justify-between p-5">
                  <div>
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${r.iconClass}`}>
                        <r.icon size={22} />
                      </div>
                      <Badge tone="neutral">{r.id}</Badge>
                    </div>
                    <h4 className="font-semibold text-ink-900 text-sm">{r.title}</h4>
                    <p className="text-xs text-ink-500 mt-1 leading-relaxed">{r.description}</p>
                  </div>

                  <div className="mt-4 pt-3 border-t border-border flex flex-col gap-3">
                    <div className="flex gap-1.5">
                      {FORMATS.map((f) => {
                        const active = (format[r.id] || "xlsx") === f.value;
                        return (
                          <button
                            key={f.value}
                            type="button"
                            onClick={() => setFormat((s) => ({ ...s, [r.id]: f.value }))}
                            className={`text-[11px] px-2 py-1 rounded border transition-colors ${
                              active ? "border-primary-500 bg-primary-50 text-primary-800 font-semibold" : "border-border text-ink-500 hover:border-primary-300"
                            }`}
                          >
                            {f.value.toUpperCase()}
                          </button>
                        );
                      })}
                    </div>
                    <Button size="sm" icon={Download} loading={busy === r.id} onClick={() => runExport(r)}>
                      Generate &amp; Download
                    </Button>
                  </div>
                </Card>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
