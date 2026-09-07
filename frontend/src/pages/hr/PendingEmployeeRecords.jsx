import { useEffect, useState } from "react";
import { UserCheck, ClipboardCheck, ShieldCheck } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getPendingEmployeeRecords,
  updateEmployee,
  getEmployees,
  EMPLOYMENT_TYPES,
} from "../../services/hrService";

const humanize = (s) => (s || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());

export default function HrPendingEmployeeRecords() {
  const { notify } = useToast();
  const [rows, setRows] = useState([]);
  const [managers, setManagers] = useState([]);
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
        setManagers(all.filter((e) => e.provisioningStatus === "CONFIRMED"));
      })
      .catch((e) => notify(e.message || "Could not load pending records.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

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

  const submit = async (confirm) => {
    const rules = { department: [required], designation: [required], dateOfJoining: [required] };
    if (values.compensationMode === "HOURLY") rules.hourlyRate = [required];
    else rules.baseSalary = [required];
    const v = validateForm(values, rules);
    setErrors(v);
    if (Object.keys(v).length) return;

    setSaving(true);
    try {
      await updateEmployee(editing.id, { ...values, confirm });
      notify(
        confirm ? `${editing.name} approved — employee record is now active.` : "Draft saved.",
        { type: "success", title: confirm ? "Approved" : "Saved" }
      );
      setEditing(null);
      load();
    } catch (err) {
      notify(err.message || "Could not save the record.", { type: "error" });
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
                <Button size="sm" icon={ClipboardCheck} onClick={() => openReview(r)}>Review &amp; Approve</Button>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={editing ? `Review — ${editing.name}` : "Review"}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={saving}>Cancel</Button>
            <Button variant="secondary" onClick={() => submit(false)} disabled={saving}>Save draft</Button>
            <Button icon={ShieldCheck} onClick={() => submit(true)} disabled={saving}>
              {saving ? "Saving…" : "Approve & Activate"}
            </Button>
          </>
        }
      >
        {values && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-xs text-ink-500 bg-cream-50 border border-border rounded-lg px-3 py-2">
              Employee code <strong className="text-ink-800">{editing.employeeCode}</strong> and the linked account are
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
