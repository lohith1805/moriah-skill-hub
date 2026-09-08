import { useEffect, useState } from "react";
import { Wallet, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import EmptyState from "../../components/ui/EmptyState";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { getMyPayslips } from "../../services/hrService";
import { CURRENCY } from "../../utils/constants";

const STATUS_TONE = { FINALISED: "success", PAID: "primary", DRAFT: "neutral" };
const DASHBOARD_BY_ROLE = {
  hr: "/hr/dashboard", trainer: "/trainer/dashboard", developer: "/developer/dashboard",
  business_analyst: "/ba/dashboard", lead_generator: "/leads/dashboard",
};
const fmtMonth = (d) => {
  if (!d) return "—";
  try {
    return new Date(d).toLocaleDateString(undefined, { month: "long", year: "numeric" });
  } catch {
    return d;
  }
};

export default function Payslips() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyPayslips()
      .then(setRows)
      .catch((e) => notify(e?.message || "Couldn't load your payslips.", { type: "error" }))
      .finally(() => setLoading(false));
  }, [notify]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My Payslips"
        subtitle="Your finalised payslips — download the PDF for any month. HR is also emailed a copy to you when it's generated."
        breadcrumbs={[{ label: "Dashboard", to: DASHBOARD_BY_ROLE[user?.role] || "/" }, { label: "My Payslips" }]}
      />

      <Card>
        {!loading && rows.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title="No payslips yet"
            description="Your payslip will appear here once HR runs payroll for a month you were employed."
          />
        ) : (
          <Table
            loading={loading}
            data={rows}
            emptyTitle="No payslips yet"
            columns={[
              {
                key: "periodMonth",
                header: "Period",
                className: "text-left font-medium text-ink-900",
                render: (r) => fmtMonth(r.periodMonth),
              },
              {
                key: "days",
                header: "Days",
                className: "text-left text-xs",
                render: (r) =>
                  r.sessionHours != null
                    ? `${r.sessionHours} session hrs`
                    : `${r.presentDays ?? "—"} / ${r.workingDays ?? "—"} present${r.lopDays > 0 ? ` · ${r.lopDays} LOP` : ""}`,
              },
              { key: "gross", header: "Gross", className: "text-left", render: (r) => CURRENCY(r.gross) },
              {
                key: "deductions",
                header: "Deductions",
                className: "text-left",
                render: (r) => (r.deductions > 0 ? <span className="text-error-600">-{CURRENCY(r.deductions)}</span> : "—"),
              },
              {
                key: "net",
                header: "Net Pay",
                className: "text-left font-semibold text-ink-900",
                render: (r) => CURRENCY(r.net),
              },
              {
                key: "status",
                header: "",
                className: "text-left",
                render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge>,
              },
              {
                key: "download",
                header: "",
                className: "text-right",
                render: (r) =>
                  r.payslipUrl ? (
                    <Button as="a" href={r.payslipUrl} target="_blank" rel="noopener noreferrer" download size="sm" variant="secondary" icon={Download}>
                      Payslip
                    </Button>
                  ) : (
                    <span className="text-xs text-ink-400">No PDF</span>
                  ),
              },
            ]}
          />
        )}
      </Card>
    </div>
  );
}
