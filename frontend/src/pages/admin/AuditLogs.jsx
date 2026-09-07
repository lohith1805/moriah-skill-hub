import { useEffect, useState } from "react";
import {
  ScrollText, ShieldAlert, Key, Download, Search, Filter,
  ShieldCheck, AlertTriangle, Info, CheckCircle2, User
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import { Input, Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getAuditLogs } from "../../services/adminService";
import { readLocalAuditLogs } from "../../utils/auditLog";
import { formatDateTime } from "../../utils/formatters";
import { useToast } from "../../context/ToastContext";

const CATEGORIES = [
  { value: "", label: "All Audit Categories" },
  { value: "SECURITY_LOGIN", label: "Security & Login Events" },
  { value: "ROLE_PERMISSION_CHANGE", label: "Role & Permission Edits" },
  { value: "FINANCIAL_TXN", label: "Financial & Gateway Transactions" },
  { value: "GRADE_CHANGE", label: "Academic Grade Changes" },
  { value: "DOCUMENT_GEN", label: "Document & Offer Issuances" },
  { value: "PIP_STATUS_CHANGE", label: "PIP Status Alterations" },
];

export default function AdminAuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [severityFilter, setSeverityFilter] = useState("");
  const { notify } = useToast();

  useEffect(() => {
    // Server trail (GET /api/v1/admin/audit) is authoritative; the client-side
    // trail (utils/auditLog.js) adds browser-only signals. Merge, newest first.
    getAuditLogs()
      .then((server) => server)
      .catch(() => [])
      .then((server) => {
        const merged = [...server, ...readLocalAuditLogs()].sort(
          (a, b) => new Date(b.timestamp || 0) - new Date(a.timestamp || 0)
        );
        setLogs(merged);
        setLoading(false);
      });
  }, []);

  const filteredLogs = logs.filter((l) => {
    const q = search.toLowerCase();
    const matchSearch =
      !q ||
      (l.action || "").toLowerCase().includes(q) ||
      (l.actor || "").toLowerCase().includes(q) ||
      (l.target || "").toLowerCase().includes(q);
    const matchCat = !categoryFilter || l.category === categoryFilter;
    const matchSev = !severityFilter || l.severity === severityFilter;
    return matchSearch && matchCat && matchSev;
  });

  const exportAuditLogs = () => {
    let csv = "ID,Timestamp,Category,Severity,Actor,Action,Target,IP_Address\n";
    filteredLogs.forEach((l) => {
      csv += `"${l.id}","${l.timestamp}","${l.category}","${l.severity}","${l.actor}","${l.action}","${l.target}","${l.ip || "127.0.0.1"}"\n`;
    });

    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify("Audit security logs exported to CSV.", { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Audit Trails & Security Logs"
        subtitle="Tamper-proof system activity logs, security login events, role modifications, and financial records (MSH-FR-ADM-04)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Audit Logs" }]}
        action={
          <Button icon={Download} onClick={exportAuditLogs}>
            Export Audit Trail (CSV)
          </Button>
        }
      />

      <Card>
        {/* Filters */}
        <div className="flex flex-col sm:flex-row gap-3 mb-4">
          <Input
            placeholder="Search by actor, action, or target…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="sm:max-w-xs"
          />
          <Select
            options={CATEGORIES}
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="sm:max-w-xs"
          />
          <Select
            options={[
              { value: "", label: "All Severities" },
              { value: "Info", label: "Info" },
              { value: "Warning", label: "Warning" },
              { value: "Critical", label: "Critical" }
            ]}
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            className="sm:max-w-xs"
          />
        </div>

        <Table
          loading={loading}
          data={filteredLogs}
          columns={[
            {
              key: "timestamp",
              header: "Timestamp",
              className: "text-left font-mono text-xs text-ink-500",
              render: (r) => formatDateTime(r.timestamp)
            },
            {
              key: "severity",
              header: "Severity",
              className: "text-left",
              render: (r) => (
                <Badge tone={r.severity === "Critical" ? "error" : r.severity === "Warning" ? "warning" : "primary"}>
                  {r.severity}
                </Badge>
              )
            },
            {
              key: "action",
              header: "Action / Event",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900 flex items-center gap-1.5">
                    <ScrollText size={13} className="text-primary-700" /> {r.action}
                  </p>
                  <p className="text-[10px] text-ink-400 font-mono mt-0.5">Category: {r.category}</p>
                </div>
              )
            },
            {
              key: "actor",
              header: "Actor (User / IP)",
              className: "text-left text-xs",
              render: (r) => (
                <div>
                  <p className="font-medium text-ink-800 flex items-center gap-1.5">
                    {r.actor}
                    {r.source === "local" && (
                      <span className="text-[9px] uppercase tracking-wide bg-cream-100 text-ink-500 border border-border rounded px-1 py-px">client</span>
                    )}
                  </p>
                  {r.ip && <p className="text-[10px] font-mono text-ink-400">{r.ip}</p>}
                </div>
              )
            },
            {
              key: "target",
              header: "Target Resource",
              className: "text-left font-mono text-xs text-ink-700",
              render: (r) => <span className="bg-cream-50 px-2 py-0.5 rounded border border-border">{r.target}</span>
            }
          ]}
        />
      </Card>
    </div>
  );
}
