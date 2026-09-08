import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ClipboardCheck, ShieldCheck, FileCheck2, History } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getPendingEmployeeRecords,
  updateEmployee,
  getEmployees,
  EMPLOYMENT_TYPES,
} from "../../services/hrService";

const humanize = (s) => (s || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());

// The backend doesn't record who approved an employee record or when (its
// audit_logs table isn't surfaced through an API), so keep a light local log
// of approvals made from this screen. It's an aid for the HR who did them, not
// a system of record — the durable list is every CONFIRMED employee below.
const APPROVAL_LOG_KEY = "msh_hr_pending_approvals";

const readApprovalLog = () => {
  try {
    return JSON.parse(localStorage.getItem(APPROVAL_LOG_KEY)) || {};
  } catch {
    return {};
  }
};

const recordApproval = (emp, byName) => {
  try {
    const log = readApprovalLog();
    log[emp.id] = {
      at: new Date().toISOString(),
      by: byName || "You",
      name: emp.name,
      employeeCode: emp.employeeCode,
    };
    localStorage.setItem(APPROVAL_LOG_KEY, JSON.stringify(log));
  } catch {
    /* localStorage unavailable — the CONFIRMED list still stands in */
  }
};

const fmtDate = (iso) => {
  if (!iso) return null;
  try {
    return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
  } catch {
    return null;
  }
};

const compLabel = (r) =>
  r.baseSalary && r.baseSalary > 0
    ? `₹${Number(r.baseSalary).toLocaleString("en-IN")} / mo`
    : r.hourlyRate && r.hourlyRate > 0
      ? `₹${Number(r.hourlyRate).toLocaleString("en-IN")} / hr`
      : "—";

export default function HrPendingEmployeeRecords() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [rows, setRows] = useState([]);
  const [confirmed, setConfirmed] = useState([]);
  const [loading, setLoading] = useState(true);

  const [editing, setEditing] = useState(null); // the pending record being reviewed
  const [values, setValues] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getPendingEmployeeRecords(), getEmployees().catch(() => [])])
      .then(([pending, all]) => {
        setRows(pending);
        setConfirmed(all.filter((e) => e.provisioningStatus === "CONFIRMED"));
      })
      .catch((e) => notify(e.message || "Could not load pending records.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Managers picker — any confirmed employee can be a reporting line.
  const managers = confirmed;

  // Approval history: every activated (CONFIRMED) record, newest approvals first.
  // Records approved from this screen carry a real "when / by whom"; older or
  // seeded ones fall back to their joining date.
  const history = useMemo(() => {
    const log = readApprovalLog();
    return confirmed
      .map((e) => ({ ...e, approvedAt: log[e.id]?.at || null, approvedBy: log[e.id]?.by || null }))
      .sort((a, b) => {
        if (a.approvedAt && b.approvedAt) return b.approvedAt.localeCompare(a.approvedAt);
        if (a.approvedAt) return -1;
        if (b.approvedAt) return 1;
        return String(b.dateOfJoining || "").localeCompare(String(a.dateOfJoining || ""));
      });
  }, [confirmed]);

  const openReview = (r) => {
    setEditing(r);
    setErrors({});
    setValues({
      department: r.department || "",
      designation: r.designation || "",
      employmentType: r.employmentType || "FULL_TIME",
      dateOfJoining: r.dateOfJoining || new Date().toISOString().slice(0, 10),
      compensationMode: r.hourlyRate != null ? "HOURLY" : "SALARIED",
      baseSalary: r.baseSalary && r.baseSalary > 0 ? String(r.baseSalary) : "",
      hourlyRate: r.hourlyRate && r.hourlyRate > 0 ? String(r.hourlyRate) : "",
      reportingManagerId: r.reportingManagerId ? String(r.reportingManagerId) : "",
    });
  };

  // Single exit point for the modal — always clears BOTH editing and values so
  // the body's `{editing && values && …}` guard can never see a half-open state
  // (a stale `values` with a null `editing` used to crash the render).
  const closeReview = () => {
    setEditing(null);
    setValues(null);
    setErrors({});
  };

  const submit = async (confirm) => {
    if (!editing || !values) return;
    const rules = { department: [required], designation: [required], dateOfJoining: [required] };
    if (values.compensationMode === "HOURLY") rules.hourlyRate = [required];
    else rules.baseSalary = [required];
    const v = validateForm(values, rules);
    setErrors(v);
    if (Object.keys(v).length) return;

    const target = editing; // capture — state is cleared before the awaits settle
    setSaving(true);
    try {
      await updateEmployee(target.id, { ...values, confirm });
      if (confirm) recordApproval(target, user?.name);
      notify(
        confirm ? `${target.name} approved — employee record is now active.` : "Draft saved.",
        { type: "success", title: confirm ? "Approved" : "Saved" }
      );
      closeReview();
      load();
    } catch (err) {
      // Keep the modal open so HR can retry without re-entering everything.
      notify(err?.message || "Could not save the record.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Pending Employee Records"
        subtitle="Staff who accepted their invite — fill in real compensation, designation and reporting line, then approve to activate their employee record"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Pending Employee Records" }]}
      />

      <Card>
        <Table
          loading={loading}
          data={rows}
          emptyTitle="Nothing waiting on you"
          emptyHint="Records auto-created when a staff member accepts their invite show up here for review."
          columns={[
            {
              key: "name",
              header: "Staff Member",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.employeeCode} · auto-provisioned</p>
                </div>
              ),
            },
            { key: "role", header: "Suggested Role", className: "text-left", render: (r) => `${r.designation} · ${r.department}` },
            { key: "dateOfJoining", header: "Joining", className: "text-left text-xs", render: (r) => r.dateOfJoining || "—" },
            {
              key: "status",
              header: "",
              className: "text-left",
              render: () => <Badge tone="warning">Pending HR review</Badge>,
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={FileCheck2}
                    onClick={() => navigate(`/hr/employee-documents/${r.userUuid}`)}>
                    Documents
                  </Button>
                  <Button size="sm" icon={ClipboardCheck} onClick={() => openReview(r)}>Review &amp; Approve</Button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* Approval history — who HR has approved, most recent first. */}
      <Card>
        <div className="flex items-center gap-2 mb-3">
          <History size={16} className="text-primary-600" />
          <h3 className="font-semibold text-ink-900">Approval history</h3>
          <span className="text-xs text-ink-500">Activated employee records — most recently approved first</span>
        </div>
        <Table
          loading={loading}
          data={history}
          emptyTitle="No approved records yet"
          emptyHint="Records you approve above move here so you can see who has been activated."
          columns={[
            {
              key: "name",
              header: "Staff Member",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.employeeCode}</p>
                </div>
              ),
            },
            { key: "role", header: "Role", className: "text-left", render: (r) => `${r.designation} · ${r.department}` },
            { key: "comp", header: "Compensation", className: "text-left text-xs", render: (r) => compLabel(r) },
            { key: "joining", header: "Joining", className: "text-left text-xs", render: (r) => r.dateOfJoining || "—" },
            {
              key: "approved",
              header: "Approved",
              className: "text-left text-xs",
              render: (r) =>
                r.approvedAt ? (
                  <div>
                    <p className="text-ink-700">{fmtDate(r.approvedAt)}</p>
                    <p className="text-ink-400">by {r.approvedBy}</p>
                  </div>
                ) : (
                  <span className="text-ink-400">—</span>
                ),
            },
            {
              key: "status",
              header: "",
              className: "text-right",
              render: () => <Badge tone="success">Active</Badge>,
            },
          ]}
        />
      </Card>

      <Modal
        open={!!editing}
        onClose={closeReview}
        title={editing ? `Review — ${editing.name}` : "Review"}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={closeReview} disabled={saving}>Cancel</Button>
            <Button variant="secondary" onClick={() => submit(false)} disabled={saving}>Save draft</Button>
            <Button icon={ShieldCheck} onClick={() => submit(true)} disabled={saving}>
              {saving ? "Saving…" : "Approve & Activate"}
            </Button>
          </>
        }
      >
        {editing && values && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-xs text-ink-500 bg-cream-50 border border-border rounded-lg px-3 py-2">
              Employee code <strong className="text-ink-800">{editing?.employeeCode}</strong> and the linked account are
              fixed. Approving activates attendance, leave and onboarding for this person.
            </p>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input label="Department" required value={values.department}
                onChange={(e) => setValues((s) => ({ ...s, department: e.target.value }))} error={errors.department} />
              <Input label="Designation" required value={values.designation}
                onChange={(e) => setValues((s) => ({ ...s, designation: e.target.value }))} error={errors.designation} />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <Select label="Employment Type" options={EMPLOYMENT_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
                value={values.employmentType}
                onChange={(e) => setValues((s) => ({ ...s, employmentType: e.target.value }))} />
              <Input label="Date of Joining" type="date" required value={values.dateOfJoining}
                onChange={(e) => setValues((s) => ({ ...s, dateOfJoining: e.target.value }))} error={errors.dateOfJoining} />
            </div>
            <div className="grid sm:grid-cols-2 gap-4">
              <Select label="Compensation"
                options={[{ value: "SALARIED", label: "Monthly salary" }, { value: "HOURLY", label: "Hourly rate (trainers)" }]}
                value={values.compensationMode}
                onChange={(e) => setValues((s) => ({ ...s, compensationMode: e.target.value }))} />
              {values.compensationMode === "HOURLY" ? (
                <Input label="Hourly Rate (₹)" type="number" min="0" required value={values.hourlyRate}
                  onChange={(e) => setValues((s) => ({ ...s, hourlyRate: e.target.value }))} error={errors.hourlyRate} />
              ) : (
                <Input label="Base Salary (₹ / month)" type="number" min="0" required value={values.baseSalary}
                  onChange={(e) => setValues((s) => ({ ...s, baseSalary: e.target.value }))} error={errors.baseSalary} />
              )}
            </div>
            <Select
              label="Reporting Manager"
              placeholder="— none —"
              options={managers.map((m) => ({ value: String(m.id), label: `${m.employeeCode} — ${m.name}` }))}
              value={values.reportingManagerId}
              onChange={(e) => setValues((s) => ({ ...s, reportingManagerId: e.target.value }))}
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
