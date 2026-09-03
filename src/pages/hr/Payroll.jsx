import { useEffect, useState } from "react";
import { Wallet, Download, Eye, Building, IndianRupee, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import StatCard from "../../components/widgets/StatCard";
import { Input } from "../../components/ui/FormField";
import { getPayroll, generatePayroll, getEmployees } from "../../services/hrService";
import { CURRENCY } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";

const STATUS_TONE = { DRAFT: "warning", FINALISED: "info", PAID: "success" };
const currentMonthYm = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
};

export default function HrPayroll() {
  const [month, setMonth] = useState(currentMonthYm());
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewingSlip, setViewingSlip] = useState(null);
  const { notify } = useToast();

  // Generate modal
  const [genOpen, setGenOpen] = useState(false);
  const [employees, setEmployees] = useState([]);
  const [workingDays, setWorkingDays] = useState("22");
  const [lines, setLines] = useState({}); // employeeId -> { presentDays, deductions, sessionHours }
  const [generating, setGenerating] = useState(false);

  const load = () => {
    setLoading(true);
    getPayroll(month)
      .then(setRows)
      .catch((e) => notify(e.message || "Could not load payroll.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [month]);

  const openGenerate = async () => {
    setGenOpen(true);
    setLines({});
    setWorkingDays("22");
    try {
      const emps = await getEmployees({ status: "ACTIVE" });
      setEmployees(emps);
      setLines(
        Object.fromEntries(emps.map((e) => [e.id, { presentDays: "22", deductions: "", sessionHours: "" }]))
      );
    } catch (e) {
      notify(e.message || "Could not load employees.", { type: "error" });
    }
  };

  const setLine = (id, patch) => setLines((s) => ({ ...s, [id]: { ...s[id], ...patch } }));

  const runGenerate = async () => {
    const payload = employees
      .filter((e) => lines[e.id] && lines[e.id].presentDays !== "")
      .map((e) => ({ employeeId: e.id, ...lines[e.id] }));
    if (!payload.length) {
      notify("Enter present days for at least one employee.", { type: "warning" });
      return;
    }
    setGenerating(true);
    try {
      await generatePayroll({ month, workingDays, lines: payload });
      notify(`Payroll generated for ${payload.length} employee${payload.length === 1 ? "" : "s"}.`, {
        type: "success",
        title: "Payroll Run",
      });
      setGenOpen(false);
      load();
    } catch (err) {
      notify(err.message || "Payroll generation failed (re-running the same month is rejected).", { type: "error" });
    } finally {
      setGenerating(false);
    }
  };

  const totalDisbursement = rows.reduce((s, r) => s + (r.net || 0), 0);
  const paidCount = rows.filter((r) => r.status === "PAID").length;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Payroll & Compensation"
        subtitle="Generate a month's payroll from attendance and view server-computed payslips"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Payroll" }]}
        action={
          <div className="flex items-end gap-2">
            <div>
              <label className="text-[10px] font-bold text-ink-400 uppercase tracking-wide block mb-1">Period</label>
              <input
                type="month"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                className="rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 bg-white"
              />
            </div>
            <Button icon={Wallet} onClick={openGenerate}>Generate Payroll</Button>
          </div>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Total Disbursement" value={CURRENCY(totalDisbursement)} icon={IndianRupee} tone="primary" />
        <StatCard label="Payslips This Period" value={rows.length} icon={Building} tone="gold" />
        <StatCard
          label="Paid"
          value={`${paidCount} / ${rows.length}`}
          icon={CheckCircle2}
          tone={rows.length > 0 && paidCount === rows.length ? "success" : "warning"}
        />
      </div>

      <Card>
        <div className="px-4 py-3 border-b border-border text-left">
          <h3 className="font-display font-semibold text-ink-900">Compensation Ledger — {month}</h3>
          <p className="text-xs text-ink-500">Gross, deductions and net are computed by the payroll engine.</p>
        </div>
        <Table
          loading={loading}
          data={rows}
          emptyTitle="No payroll for this period"
          emptyHint="Use “Generate Payroll” to run it."
          columns={[
            {
              key: "name",
              header: "Employee",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.employeeCode} · {r.presentDays}/{r.workingDays} days</p>
                </div>
              ),
            },
            { key: "gross", header: "Gross", className: "text-left", render: (r) => CURRENCY(r.gross) },
            { key: "deductions", header: "Deductions", className: "text-left", render: (r) => <span className="text-error-600">-{CURRENCY(r.deductions)}</span> },
            { key: "net", header: "Net", className: "text-left", render: (r) => <span className="font-bold text-ink-900 font-display">{CURRENCY(r.net)}</span> },
            { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge> },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingSlip(r)}>Payslip</Button>
              ),
            },
          ]}
        />
      </Card>

      {/* Generate modal */}
      <Modal
        open={genOpen}
        onClose={() => setGenOpen(false)}
        title={`Generate Payroll — ${month}`}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setGenOpen(false)} disabled={generating}>Cancel</Button>
            <Button icon={Wallet} onClick={runGenerate} disabled={generating}>{generating ? "Running…" : "Run Payroll"}</Button>
          </>
        }
      >
        <div className="flex flex-col gap-4 text-left font-sans">
          <Input
            label="Working days in the period"
            type="number"
            min="1"
            value={workingDays}
            onChange={(e) => setWorkingDays(e.target.value)}
          />
          {employees.length === 0 ? (
            <p className="text-sm text-ink-400">No active employees on file. Create employee records first.</p>
          ) : (
            <div className="border border-border rounded-xl divide-y divide-border max-h-[320px] overflow-y-auto">
              <div className="grid grid-cols-[1fr_90px_110px_110px] gap-2 px-3 py-2 bg-cream-50 text-[10px] font-bold uppercase text-ink-500">
                <span>Employee</span><span>Present</span><span>Session hrs</span><span>Deductions ₹</span>
              </div>
              {employees.map((e) => (
                <div key={e.id} className="grid grid-cols-[1fr_90px_110px_110px] gap-2 px-3 py-2 items-center text-sm">
                  <span className="truncate">{e.employeeCode} — {e.name}</span>
                  <input type="number" min="0" value={lines[e.id]?.presentDays ?? ""} onChange={(ev) => setLine(e.id, { presentDays: ev.target.value })} className="w-full rounded border border-border px-2 py-1 text-sm" />
                  <input type="number" min="0" step="0.5" placeholder="—" value={lines[e.id]?.sessionHours ?? ""} onChange={(ev) => setLine(e.id, { sessionHours: ev.target.value })} className="w-full rounded border border-border px-2 py-1 text-sm" />
                  <input type="number" min="0" placeholder="0" value={lines[e.id]?.deductions ?? ""} onChange={(ev) => setLine(e.id, { deductions: ev.target.value })} className="w-full rounded border border-border px-2 py-1 text-sm" />
                </div>
              ))}
            </div>
          )}
          <p className="text-[11px] text-ink-400">Re-running a period that already has payroll is rejected by the server.</p>
        </div>
      </Modal>

      {/* Payslip modal */}
      <Modal
        open={!!viewingSlip}
        onClose={() => setViewingSlip(null)}
        title="Payslip"
        size="md"
        footer={
          <div className="flex justify-end gap-2 w-full">
            <Button variant="secondary" onClick={() => setViewingSlip(null)}>Close</Button>
            {viewingSlip?.payslipUrl && (
              <Button icon={Download} onClick={() => window.open(viewingSlip.payslipUrl, "_blank")}>Download</Button>
            )}
          </div>
        }
      >
        {viewingSlip && (
          <div className="p-5 bg-white rounded-xl border border-border text-left font-sans flex flex-col gap-4">
            <div className="flex justify-between items-start border-b border-border pb-3">
              <div>
                <h3 className="font-display font-bold text-lg text-primary-900">MORIAH SKILL HUB</h3>
                <p className="text-xs text-ink-500">HR & Payroll</p>
              </div>
              <div className="text-right">
                <Badge tone={STATUS_TONE[viewingSlip.status] || "neutral"}>{viewingSlip.status}</Badge>
                <p className="text-xs text-ink-400 mt-1 font-mono">{viewingSlip.periodMonth}</p>
              </div>
            </div>
            <div className="text-xs bg-cream-50/70 p-3 rounded-lg border border-border">
              <p><span className="text-ink-500">Employee:</span> <strong>{viewingSlip.name}</strong> ({viewingSlip.employeeCode})</p>
              <p className="mt-1"><span className="text-ink-500">Attendance:</span> {viewingSlip.presentDays} / {viewingSlip.workingDays} working days
                {viewingSlip.sessionHours ? ` · ${viewingSlip.sessionHours} session hrs` : ""}</p>
            </div>
            <div className="flex flex-col gap-1.5 text-sm">
              <div className="flex justify-between"><span className="text-ink-600">Gross</span><span className="font-medium">{CURRENCY(viewingSlip.gross)}</span></div>
              <div className="flex justify-between"><span className="text-ink-600">Deductions</span><span className="text-error-600">-{CURRENCY(viewingSlip.deductions)}</span></div>
              <div className="flex justify-between border-t border-border pt-1.5 mt-1 font-bold font-display text-primary-900">
                <span>Net</span><span>{CURRENCY(viewingSlip.net)}</span>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
