import { useEffect, useState } from "react";
import {
  Wallet, Download, Plus, Edit, Trash2, CheckCircle2, Eye,
  Building, IndianRupee, ShieldCheck, Printer, Sparkles
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import StatCard from "../../components/widgets/StatCard";
import { Input, Select } from "../../components/ui/FormField";
import { getPayroll, getEmployees } from "../../services/hrService";
import { CURRENCY } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

export default function HrPayroll() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [viewingSlip, setViewingSlip] = useState(null);

  // Edit modal
  const [editOpen, setEditOpen] = useState(false);
  const [editingItem, setEditingItem] = useState(null);
  const [editValues, setEditValues] = useState({
    name: "",
    type: "",
    baseSalary: "",
    hourlyRate: "",
    completedHours: "",
    hra: "",
    deductions: "",
    status: ""
  });

  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getPayroll().then((data) => {
      setRows(data);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

  const persist = (data) => {
    setRows(data);
    localStorage.setItem("msh_hr_payroll", JSON.stringify(data));
  };

  const runAllPayroll = async () => {
    setRunning(true);
    await new Promise((r) => setTimeout(r, 600));
    const updated = rows.map((r) => ({ ...r, status: "Processed" }));
    persist(updated);
    setRunning(false);
    notify("Monthly payroll batch executed successfully for all staff & trainers.", { type: "success", title: "Payroll Run Complete" });
  };

  const openEdit = (item) => {
    setEditingItem(item);
    setEditValues({
      name: item.name,
      type: item.type,
      baseSalary: String(item.baseSalary || 0),
      hourlyRate: String(item.hourlyRate || 1200),
      completedHours: String(item.completedHours || 40),
      hra: String(item.hra || 0),
      deductions: String(item.deductions || 0),
      status: item.status
    });
    setEditOpen(true);
  };

  const handleEditSave = (e) => {
    e.preventDefault();
    const isHourly = editValues.type.includes("Trainer") || editValues.type.includes("Hourly");
    let base = Number(editValues.baseSalary) || 0;
    let hra = Number(editValues.hra) || 0;
    let ded = Number(editValues.deductions) || 0;
    let net = 0;

    if (isHourly) {
      const rate = Number(editValues.hourlyRate) || 1200;
      const hrs = Number(editValues.completedHours) || 40;
      base = rate * hrs;
      net = base - ded;
    } else {
      net = base + hra - ded;
    }

    const updated = rows.map((r) => {
      if (r.id === editingItem.id) {
        return {
          ...r,
          type: editValues.type,
          baseSalary: base,
          hourlyRate: Number(editValues.hourlyRate) || 1200,
          completedHours: Number(editValues.completedHours) || 40,
          hra,
          deductions: ded,
          net,
          status: editValues.status
        };
      }
      return r;
    });

    persist(updated);
    notify("Payroll computation updated.", { type: "success" });
    setEditOpen(false);
  };

  const handleDelete = (id) => {
    const target = rows.find((r) => r.id === id);
    const updated = rows.filter((r) => r.id !== id);
    persist(updated);
    notify(`Payroll entry for "${target?.name}" deleted.`, { type: "success" });
  };

  const totalPayrollDisbursement = rows.reduce((s, r) => s + (r.net || 0), 0);
  const processedCount = rows.filter((r) => r.status === "Processed").length;

  const downloadPayslipText = (item) => {
    const slipContent = `=====================================================
            MORIAH SKILL HUB INC.
           OFFICIAL SALARY PAYSLIP
=====================================================
Pay Period: ${item.payPeriod || "August 2026"}
Employee Name: ${item.name}
Role / Designation: ${item.role}
Department: ${item.department || "Operations"}
Pay Type: ${item.type}

-----------------------------------------------------
EARNINGS & COMPUTATION
-----------------------------------------------------
Base / Session Pay:    ₹${(item.baseSalary || 0).toLocaleString('en-IN')}
${item.hourlyRate ? `(Hourly Rate: ₹${item.hourlyRate} x ${item.completedHours || 40} hrs)` : ""}
HRA Allowance:         ₹${(item.hra || 0).toLocaleString('en-IN')}
Gross Earnings:        ₹${((item.baseSalary || 0) + (item.hra || 0)).toLocaleString('en-IN')}

-----------------------------------------------------
DEDUCTIONS
-----------------------------------------------------
PF / Tax Deductions:   ₹${(item.deductions || 0).toLocaleString('en-IN')}

-----------------------------------------------------
NET SALARY DISBURSED:  ₹${(item.net || 0).toLocaleString('en-IN')}
-----------------------------------------------------
Status: ${item.status}
Digitally Generated by Moriah Skill Hub Payroll Engine.
=====================================================`;

    const blob = new Blob([slipContent], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `Payslip_${item.name.replace(/\s+/g, "_")}_${item.payPeriod || "Aug2026"}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify(`Payslip for ${item.name} downloaded.`, { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Payroll & Compensation Management"
        subtitle="Automated salary computation for hourly trainer sessions, employee base salaries, and payslip generation"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Payroll" }]}
        action={
          <Button icon={Wallet} loading={running} onClick={runAllPayroll}>
            Execute Batch Payroll ({rows.length - processedCount} Pending)
          </Button>
        }
      />

      {/* Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Total Monthly Disbursement"
          value={CURRENCY(totalPayrollDisbursement)}
          icon={IndianRupee}
          tone="primary"
        />
        <StatCard
          label="Staff & Trainers Enrolled"
          value={rows.length}
          icon={Building}
          tone="gold"
        />
        <StatCard
          label="Disbursement Status"
          value={`${processedCount} / ${rows.length} Paid`}
          icon={CheckCircle2}
          tone={processedCount === rows.length && rows.length > 0 ? "success" : "warning"}
        />
      </div>

      <Card>
        <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
          <div>
            <h3 className="font-display font-semibold text-ink-900">Monthly Compensation Ledger (MSH-FR-HR-04)</h3>
            <p className="text-xs text-ink-500">Hourly trainer sessions and salaried staff salary breakdown</p>
          </div>
          <Badge tone={processedCount === rows.length && rows.length > 0 ? "success" : "gold"}>
            {processedCount === rows.length && rows.length > 0 ? "All Disbursed" : "Run In Progress"}
          </Badge>
        </div>

        <Table
          loading={loading}
          data={rows}
          columns={[
            {
              key: "name",
              header: "Employee / Trainer",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.role} · {r.type}</p>
                </div>
              )
            },
            {
              key: "base",
              header: "Base / Session Pay",
              className: "text-left",
              render: (r) => (
                <div>
                  <p className="font-medium text-ink-800">{CURRENCY(r.baseSalary)}</p>
                  {r.type.includes("Trainer") && (
                    <p className="text-[10px] text-ink-400 font-mono">₹{r.hourlyRate}/hr × {r.completedHours || 40}h</p>
                  )}
                </div>
              )
            },
            {
              key: "hra",
              header: "Allowances / HRA",
              className: "text-left",
              render: (r) => CURRENCY(r.hra || 0)
            },
            {
              key: "deductions",
              header: "PF & Tax",
              className: "text-left",
              render: (r) => <span className="text-error-600">-{CURRENCY(r.deductions || 0)}</span>
            },
            {
              key: "net",
              header: "Net Disbursed",
              className: "text-left",
              render: (r) => <span className="font-bold text-ink-900 font-display">{CURRENCY(r.net)}</span>
            },
            {
              key: "status",
              header: "Status",
              className: "text-left",
              render: (r) => <Badge tone={r.status === "Processed" ? "success" : "warning"}>{r.status}</Badge>
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingSlip(r)}>Payslip</Button>
                  <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Edit</Button>
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* Edit Payroll Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title={`Edit Compensation: ${editingItem?.name}`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button onClick={handleEditSave}>Save Computation</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Select
            label="Payroll Category"
            options={[
              { value: "Monthly Salaried", label: "Monthly Salaried Employee" },
              { value: "Hourly Trainer", label: "Hourly Trainer / PM" }
            ]}
            value={editValues.type}
            onChange={(e) => setEditValues((v) => ({ ...v, type: e.target.value }))}
          />

          {editValues.type.includes("Trainer") ? (
            <div className="grid sm:grid-cols-2 gap-4">
              <Input
                label="Hourly Session Rate (₹)"
                type="number"
                value={editValues.hourlyRate}
                onChange={(e) => setEditValues((v) => ({ ...v, hourlyRate: e.target.value }))}
              />
              <Input
                label="Completed Training Hours"
                type="number"
                value={editValues.completedHours}
                onChange={(e) => setEditValues((v) => ({ ...v, completedHours: e.target.value }))}
              />
            </div>
          ) : (
            <div className="grid sm:grid-cols-2 gap-4">
              <Input
                label="Base Salary (₹)"
                type="number"
                value={editValues.baseSalary}
                onChange={(e) => setEditValues((v) => ({ ...v, baseSalary: e.target.value }))}
              />
              <Input
                label="HRA & Allowances (₹)"
                type="number"
                value={editValues.hra}
                onChange={(e) => setEditValues((v) => ({ ...v, hra: e.target.value }))}
              />
            </div>
          )}

          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="PF & Statutory Deductions (₹)"
              type="number"
              value={editValues.deductions}
              onChange={(e) => setEditValues((v) => ({ ...v, deductions: e.target.value }))}
            />
            <Select
              label="Disbursement Status"
              options={[
                { value: "Pending", label: "Pending" },
                { value: "Processed", label: "Processed" }
              ]}
              value={editValues.status}
              onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))}
            />
          </div>
        </form>
      </Modal>

      {/* Payslip Generator & Modal */}
      <Modal
        open={!!viewingSlip}
        onClose={() => setViewingSlip(null)}
        title="Official Monthly Payslip (MSH-FR-HR-04)"
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <span className="text-xs text-ink-400">Digitally sealed & verified payslip document</span>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setViewingSlip(null)}>Close</Button>
              <Button icon={Download} onClick={() => downloadPayslipText(viewingSlip)}>Download Payslip</Button>
            </div>
          </div>
        }
      >
        {viewingSlip && (
          <div className="p-5 bg-white rounded-xl border border-border text-left font-sans flex flex-col gap-4">
            <div className="flex justify-between items-start border-b border-border pb-3">
              <div>
                <h3 className="font-display font-bold text-lg text-primary-900">MORIAH SKILL HUB</h3>
                <p className="text-xs text-ink-500">Corporate HR & Payroll Division</p>
              </div>
              <div className="text-right">
                <Badge tone="success">Payment Disbursed</Badge>
                <p className="text-xs text-ink-400 mt-1 font-mono">{viewingSlip.payPeriod || "August 2026"}</p>
              </div>
            </div>

            <div className="grid sm:grid-cols-2 gap-4 text-xs bg-cream-50/70 p-3 rounded-lg border border-border">
              <div>
                <p><span className="text-ink-500">Employee Name:</span> <strong className="text-ink-900">{viewingSlip.name}</strong></p>
                <p className="mt-1"><span className="text-ink-500">Designation:</span> <strong className="text-ink-900">{viewingSlip.role}</strong></p>
              </div>
              <div>
                <p><span className="text-ink-500">Department:</span> <strong className="text-ink-900">{viewingSlip.department || "Operations"}</strong></p>
                <p className="mt-1"><span className="text-ink-500">Compensation Model:</span> <strong className="text-ink-900">{viewingSlip.type}</strong></p>
              </div>
            </div>

            {/* Salary Breakdown Table */}
            <div className="border border-border rounded-lg overflow-hidden text-xs">
              <table className="w-full text-left">
                <thead className="bg-cream-100/80 border-b border-border">
                  <tr>
                    <th className="p-2.5 font-semibold text-ink-800">Earnings</th>
                    <th className="p-2.5 font-semibold text-ink-800 text-right">Amount (₹)</th>
                    <th className="p-2.5 font-semibold text-ink-800 border-l border-border">Deductions</th>
                    <th className="p-2.5 font-semibold text-ink-800 text-right">Amount (₹)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  <tr>
                    <td className="p-2.5">Base Salary / Hourly Sessions</td>
                    <td className="p-2.5 text-right font-medium">{CURRENCY(viewingSlip.baseSalary)}</td>
                    <td className="p-2.5 border-l border-border">Provident Fund (PF)</td>
                    <td className="p-2.5 text-right text-error-600 font-medium">{CURRENCY(Math.round(viewingSlip.deductions * 0.7))}</td>
                  </tr>
                  <tr>
                    <td className="p-2.5">HRA & Special Allowances</td>
                    <td className="p-2.5 text-right font-medium">{CURRENCY(viewingSlip.hra || 0)}</td>
                    <td className="p-2.5 border-l border-border">Professional Tax (PT)</td>
                    <td className="p-2.5 text-right text-error-600 font-medium">{CURRENCY(Math.round(viewingSlip.deductions * 0.3))}</td>
                  </tr>
                  <tr className="bg-cream-50/50 font-bold border-t border-border">
                    <td className="p-2.5">Total Gross Earnings</td>
                    <td className="p-2.5 text-right">{CURRENCY((viewingSlip.baseSalary || 0) + (viewingSlip.hra || 0))}</td>
                    <td className="p-2.5 border-l border-border">Total Deductions</td>
                    <td className="p-2.5 text-right text-error-600">{CURRENCY(viewingSlip.deductions || 0)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Net Amount Box */}
            <div className="p-3 bg-primary-50 border border-primary-100 rounded-lg flex items-center justify-between">
              <div>
                <p className="text-xs text-primary-700 font-medium">Net Take-Home Pay</p>
                <p className="text-[11px] text-ink-400">Transferred via Direct Bank NEFT / IMPS</p>
              </div>
              <span className="text-xl font-bold font-display text-primary-900">{CURRENCY(viewingSlip.net)}</span>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
