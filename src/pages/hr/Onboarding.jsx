import { useEffect, useState } from "react";
import { UserPlus, FileCheck, Edit } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getEmployees,
  getOnboardings,
  createOnboarding,
  updateOnboarding,
  ONBOARDING_STATUSES,
  DEFAULT_ONBOARDING_CHECKLIST,
} from "../../services/hrService";

const STATUS_TONE = {
  NOT_STARTED: "neutral",
  IN_PROGRESS: "gold",
  COMPLETED: "success",
  CANCELLED: "danger",
};
const humanize = (s) => (s || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
const progress = (list) => (list?.length ? `${list.filter((c) => c.done).length} / ${list.length}` : "0 / 0");

export default function HrOnboarding() {
  const [employees, setEmployees] = useState([]);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const { notify } = useToast();

  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState({ employeeId: "", startDate: "", buddyUuid: "" });
  const [editItem, setEditItem] = useState(null);
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getEmployees(), getOnboardings()])
      .then(([emps, rows]) => {
        setEmployees(emps);
        setItems(rows);
      })
      .catch((e) => notify(e.message || "Could not load onboarding data.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const employeeOptions = employees.map((e) => ({ value: String(e.id), label: `${e.employeeCode} — ${e.name}` }));

  const handleCreate = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { employeeId: [required], startDate: [required] });
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      await createOnboarding(values);
      notify("Onboarding workflow started.", { type: "success", title: "Workflow Started" });
      setModalOpen(false);
      setValues({ employeeId: "", startDate: "", buddyUuid: "" });
      load();
    } catch (err) {
      notify(err.message || "Could not start onboarding.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (row) => {
    setEditItem({
      ...row,
      checklist: row.checklist?.length
        ? row.checklist
        : DEFAULT_ONBOARDING_CHECKLIST.map((label) => ({ label, done: false })),
      buddyUuid: row.buddyUuid || "",
    });
  };

  const toggleItem = (idx) =>
    setEditItem((s) => ({
      ...s,
      checklist: s.checklist.map((c, i) => (i === idx ? { ...c, done: !c.done } : c)),
    }));

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateOnboarding(editItem.id, editItem);
      notify("Onboarding record updated.", { type: "success" });
      setEditItem(null);
      load();
    } catch (err) {
      notify(err.message || "Could not update the onboarding record.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Employee Onboarding"
        subtitle="Track documentation, buddy assignment and the joining checklist for each new hire"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Onboarding" }]}
        action={
          <Button icon={UserPlus} onClick={() => { setErrors({}); setModalOpen(true); }}>
            Start Onboarding
          </Button>
        }
      />

      <Card>
        <Table
          loading={loading}
          data={items}
          emptyTitle="No onboarding records"
          emptyHint="Start one with “Start Onboarding”."
          columns={[
            {
              key: "name",
              header: "Employee",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.employeeCode} · Start: {r.startDate || "—"}</p>
                </div>
              ),
            },
            { key: "checklist", header: "Checklist", className: "text-left", render: (r) => progress(r.checklist) },
            {
              key: "status",
              header: "Status",
              className: "text-left",
              render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{humanize(r.status)}</Badge>,
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Manage</Button>
              ),
            },
          ]}
        />
      </Card>

      {/* Start onboarding modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Start Onboarding Workflow"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>Cancel</Button>
            <Button icon={FileCheck} onClick={handleCreate} disabled={saving}>{saving ? "Saving…" : "Start Onboarding"}</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreate}>
          <Select
            label="Employee"
            required
            placeholder={employeeOptions.length ? "Select an employee" : "No employees on file"}
            options={employeeOptions}
            value={values.employeeId}
            onChange={(e) => setValues((v) => ({ ...v, employeeId: e.target.value }))}
            error={errors.employeeId}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Start date"
              type="date"
              required
              value={values.startDate}
              onChange={(e) => setValues((v) => ({ ...v, startDate: e.target.value }))}
              error={errors.startDate}
            />
            <Input
              label="Buddy UUID (optional)"
              placeholder="another user's uuid"
              value={values.buddyUuid}
              onChange={(e) => setValues((v) => ({ ...v, buddyUuid: e.target.value }))}
            />
          </div>
        </form>
      </Modal>

      {/* Manage onboarding modal */}
      <Modal
        open={!!editItem}
        onClose={() => setEditItem(null)}
        title={editItem ? `Onboarding — ${editItem.name}` : "Onboarding"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditItem(null)} disabled={saving}>Cancel</Button>
            <Button onClick={handleSave} disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
          </>
        }
      >
        {editItem && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="grid sm:grid-cols-2 gap-4">
              <Input
                label="Start date"
                type="date"
                value={editItem.startDate || ""}
                onChange={(e) => setEditItem((s) => ({ ...s, startDate: e.target.value }))}
              />
              <Select
                label="Status"
                options={ONBOARDING_STATUSES.map((t) => ({ value: t, label: humanize(t) }))}
                value={editItem.status}
                onChange={(e) => setEditItem((s) => ({ ...s, status: e.target.value }))}
              />
            </div>
            <Input
              label="Buddy UUID"
              placeholder="another user's uuid"
              value={editItem.buddyUuid}
              onChange={(e) => setEditItem((s) => ({ ...s, buddyUuid: e.target.value }))}
            />
            <div className="flex flex-col gap-2">
              <span className="text-sm font-medium text-ink-900">Joining checklist</span>
              {editItem.checklist.map((c, i) => (
                <label key={i} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={c.done} onChange={() => toggleItem(i)} className="accent-primary-700" />
                  {c.label}
                </label>
              ))}
            </div>
            <Textarea
              label="Notes"
              rows={3}
              value={editItem.notes}
              onChange={(e) => setEditItem((s) => ({ ...s, notes: e.target.value }))}
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
