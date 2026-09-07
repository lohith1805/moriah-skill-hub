import { useEffect, useState } from "react";
import { CheckCircle2, Plus, Trash2, FileWarning, ShieldAlert, Edit } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Tabs from "../../components/ui/Tabs";
import Modal from "../../components/ui/Modal";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getEmployees,
  getExits,
  createExit,
  updateExit,
  completeExit,
  getDisciplinaryActions,
  createDisciplinaryAction,
  updateDisciplinaryAction,
  EXIT_TYPES,
  DISCIPLINARY_ACTION_TYPES,
  DISCIPLINARY_SEVERITIES,
  DISCIPLINARY_STATUSES,
  DEFAULT_EXIT_CHECKLIST,
} from "../../services/hrService";

const EXIT_STATUS_TONE = { INITIATED: "gold", IN_PROGRESS: "warning", COMPLETED: "success" };
const DISC_STATUS_TONE = { OPEN: "danger", ACKNOWLEDGED: "warning", RESOLVED: "success", ESCALATED: "danger" };
const humanize = (s) => (s || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
const checklistProgress = (list) => {
  if (!list?.length) return "0 / 0";
  return `${list.filter((c) => c.done).length} / ${list.length}`;
};

export default function HrExitManagement() {
  const [employees, setEmployees] = useState([]);
  const [exits, setExits] = useState([]);
  const [disciplinary, setDisciplinary] = useState([]);
  const [pipRecords, setPipRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirmComplete, setConfirmComplete] = useState(null);
  const { notify } = useToast();

  // Record-exit modal
  const [createOpen, setCreateOpen] = useState(false);
  const [values, setValues] = useState({
    employeeId: "",
    exitType: "RESIGNATION",
    lastWorkingDay: "",
    reason: "",
    noticePeriodDays: "30",
  });

  // Update-exit (checklist) modal
  const [editExit, setEditExit] = useState(null);

  // Disciplinary modal
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [noticeValues, setNoticeValues] = useState({
    employeeId: "",
    actionType: "WRITTEN_WARNING",
    severity: "MEDIUM",
    incidentDate: "",
    description: "",
  });
  const [editNotice, setEditNotice] = useState(null);

  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getEmployees(), getExits(), getDisciplinaryActions()])
      .then(([emps, ex, disc]) => {
        setEmployees(emps);
        setExits(ex);
        setDisciplinary(disc);
      })
      .catch((e) => notify(e.message || "Could not load exit / disciplinary data.", { type: "error" }))
      .finally(() => setLoading(false));

    try {
      const rawPip = localStorage.getItem("msh_pip_records");
      setPipRecords(rawPip ? JSON.parse(rawPip) : []);
    } catch {
      setPipRecords([]);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const employeeOptions = employees.map((e) => ({
    value: String(e.id),
    label: `${e.employeeCode} — ${e.name}`,
  }));

  const handleCreateExit = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { employeeId: [required], lastWorkingDay: [required] });
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      await createExit(values);
      notify("Exit clearance workflow initialised.", { type: "success" });
      setCreateOpen(false);
      setValues({ employeeId: "", exitType: "RESIGNATION", lastWorkingDay: "", reason: "", noticePeriodDays: "30" });
      load();
    } catch (err) {
      notify(err.message || "Could not create the exit record.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const openEditExit = (row) => {
    setEditExit({
      ...row,
      clearanceChecklist: row.clearanceChecklist?.length
        ? row.clearanceChecklist
        : DEFAULT_EXIT_CHECKLIST.map((label) => ({ label, done: false })),
      noticePeriodDays: row.noticePeriodDays ?? "",
    });
  };

  const toggleExitItem = (idx) =>
    setEditExit((s) => ({
      ...s,
      clearanceChecklist: s.clearanceChecklist.map((c, i) => (i === idx ? { ...c, done: !c.done } : c)),
    }));

  const handleSaveExit = async () => {
    setSaving(true);
    try {
      await updateExit(editExit.id, editExit);
      notify("Exit clearance updated.", { type: "success" });
      setEditExit(null);
      load();
    } catch (err) {
      notify(err.message || "Could not update the exit record.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const handleComplete = async () => {
    const row = confirmComplete;
    setConfirmComplete(null);
    try {
      await completeExit(row.id);
      notify(
        `Exit finalised — ${row.name}'s employee record is now ${row.exitType === "TERMINATION" ? "TERMINATED" : "EXITED"}.`,
        { type: "success" }
      );
      load();
    } catch (err) {
      notify(err.message || "Could not finalise the exit.", { type: "error" });
    }
  };

  const handleIssueNotice = async (e) => {
    e.preventDefault();
    const v = validateForm(noticeValues, {
      employeeId: [required],
      incidentDate: [required],
      description: [required],
    });
    setErrors(v);
    if (Object.keys(v).length) return;
    setSaving(true);
    try {
      await createDisciplinaryAction(noticeValues);
      notify("Disciplinary action recorded.", { type: "warning", title: "Disciplinary Action" });
      setNoticeOpen(false);
      setNoticeValues({ employeeId: "", actionType: "WRITTEN_WARNING", severity: "MEDIUM", incidentDate: "", description: "" });
      load();
    } catch (err) {
      notify(err.message || "Could not record the disciplinary action.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const handleSaveNotice = async () => {
    setSaving(true);
    try {
      await updateDisciplinaryAction(editNotice.id, editNotice);
      notify("Disciplinary action updated.", { type: "success" });
      setEditNotice(null);
      load();
    } catch (err) {
      notify(err.message || "Could not update the disciplinary action.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Performance & Exit Management"
        subtitle="Exit clearance workflows, PIP monitoring and formal disciplinary actions"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Exit & PIP" }]}
        action={
          <div className="flex gap-2">
            <Button variant="secondary" icon={FileWarning} onClick={() => { setErrors({}); setNoticeOpen(true); }}>
              Record Disciplinary Action
            </Button>
            <Button icon={Plus} onClick={() => { setErrors({}); setCreateOpen(true); }}>
              Record Exit Workflow
            </Button>
          </div>
        }
      />

      <Card>
        <Tabs
          tabs={[
            { key: "clearance", label: `Exit Clearances (${exits.length})` },
            { key: "pip", label: `Active PIP Monitoring (${pipRecords.length})` },
            { key: "disciplinary", label: `Disciplinary Actions (${disciplinary.length})` },
          ]}
        >
          {(active) => {
            if (active === "clearance") {
              return (
                <Table
                  loading={loading}
                  data={exits}
                  emptyTitle="No exit workflows"
                  emptyHint="Start one with “Record Exit Workflow”."
                  columns={[
                    {
                      key: "name",
                      header: "Employee",
                      className: "text-left font-medium text-ink-900",
                      render: (r) => (
                        <div>
                          <p className="font-semibold text-ink-900">{r.name}</p>
                          <p className="text-xs text-ink-500">{r.employeeCode} · {humanize(r.exitType)}</p>
                        </div>
                      ),
                    },
                    { key: "lastWorkingDay", header: "Last working day", className: "text-left", render: (r) => r.lastWorkingDay || "—" },
                    { key: "checklist", header: "Clearance", className: "text-left", render: (r) => checklistProgress(r.clearanceChecklist) },
                    {
                      key: "status",
                      header: "Status",
                      className: "text-left",
                      render: (r) => <Badge tone={EXIT_STATUS_TONE[r.status] || "neutral"}>{humanize(r.status)}</Badge>,
                    },
                    {
                      key: "action",
                      header: "",
                      className: "text-right",
                      render: (r) => (
                        <div className="flex gap-2 justify-end">
                          {r.status !== "COMPLETED" && (
                            <>
                              <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEditExit(r)}>Checklist</Button>
                              <Button size="sm" icon={CheckCircle2} onClick={() => setConfirmComplete(r)}>Finalise</Button>
                            </>
                          )}
                        </div>
                      ),
                    },
                  ]}
                />
              );
            }

            if (active === "pip") {
              return (
                <div className="flex flex-col gap-4 text-left">
                  <div className="p-3 bg-amber-50 rounded-lg border border-amber-200 text-amber-900 text-xs">
                    ⚡ <strong>Synced with the Trainer PIP engine</strong> (local) — active Performance Improvement Plans flagged by PMs/Trainers.
                  </div>
                  {pipRecords.length === 0 ? (
                    <p className="text-sm text-ink-400 py-8 text-center">No active PIP cases.</p>
                  ) : (
                    <Table
                      data={pipRecords}
                      columns={[
                        { key: "studentName", header: "Student", className: "text-left font-medium text-ink-900" },
                        { key: "triggerReason", header: "Trigger", className: "text-left" },
                        { key: "startDate", header: "Start date", className: "text-left" },
                        { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={r.status === "Active" ? "warning" : "success"}>{r.status}</Badge> },
                      ]}
                    />
                  )}
                </div>
              );
            }

            return (
              <Table
                loading={loading}
                data={disciplinary}
                emptyTitle="No disciplinary actions"
                columns={[
                  {
                    key: "name",
                    header: "Employee",
                    className: "text-left font-medium text-ink-900",
                    render: (r) => (
                      <div>
                        <p className="font-semibold text-ink-900">{r.name}</p>
                        <p className="text-xs text-ink-500">{r.employeeCode}</p>
                      </div>
                    ),
                  },
                  { key: "actionType", header: "Action", className: "text-left", render: (r) => <Badge tone="danger">{humanize(r.actionType)}</Badge> },
                  { key: "severity", header: "Severity", className: "text-left", render: (r) => humanize(r.severity) },
                  { key: "incidentDate", header: "Incident", className: "text-left", render: (r) => r.incidentDate || "—" },
                  { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={DISC_STATUS_TONE[r.status] || "neutral"}>{humanize(r.status)}</Badge> },
                  {
                    key: "action",
                    header: "",
                    className: "text-right",
                    render: (r) => (
                      <Button size="sm" variant="secondary" icon={Edit} onClick={() => setEditNotice({ ...r })}>Update</Button>
                    ),
                  },
                ]}
              />
            );
          }}
        </Tabs>
      </Card>

      {/* Record Exit Modal */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Record Employee Exit Workflow"
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)} disabled={saving}>Cancel</Button>
            <Button icon={Plus} onClick={handleCreateExit} disabled={saving}>{saving ? "Saving…" : "Initialise Workflow"}</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreateExit}>
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
            <Select
              label="Exit type"
              options={EXIT_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
              value={values.exitType}
              onChange={(e) => setValues((v) => ({ ...v, exitType: e.target.value }))}
            />
            <Input
              label="Last working day"
              type="date"
              required
              value={values.lastWorkingDay}
              onChange={(e) => setValues((v) => ({ ...v, lastWorkingDay: e.target.value }))}
              error={errors.lastWorkingDay}
            />
          </div>
          <Input
            label="Notice period (days)"
            type="number"
            min="0"
            value={values.noticePeriodDays}
            onChange={(e) => setValues((v) => ({ ...v, noticePeriodDays: e.target.value }))}
          />
          <Textarea
            label="Reason for separation"
            rows={2}
            value={values.reason}
            onChange={(e) => setValues((v) => ({ ...v, reason: e.target.value }))}
          />
        </form>
      </Modal>

      {/* Exit checklist modal */}
      <Modal
        open={!!editExit}
        onClose={() => setEditExit(null)}
        title={editExit ? `Exit Clearance — ${editExit.name}` : "Exit Clearance"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditExit(null)} disabled={saving}>Cancel</Button>
            <Button onClick={handleSaveExit} disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
          </>
        }
      >
        {editExit && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <Select
              label="Status"
              options={[
                { value: "INITIATED", label: "Initiated" },
                { value: "IN_PROGRESS", label: "In progress" },
              ]}
              value={editExit.status === "INITIATED" ? "INITIATED" : "IN_PROGRESS"}
              onChange={(e) => setEditExit((s) => ({ ...s, status: e.target.value }))}
            />
            <div className="flex flex-col gap-2">
              <span className="text-sm font-medium text-ink-900">Clearance checklist</span>
              {editExit.clearanceChecklist.map((c, i) => (
                <label key={i} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={c.done} onChange={() => toggleExitItem(i)} className="accent-primary-700" />
                  {c.label}
                </label>
              ))}
            </div>
            <Textarea
              label="Exit interview notes"
              rows={3}
              value={editExit.exitInterviewNotes}
              onChange={(e) => setEditExit((s) => ({ ...s, exitInterviewNotes: e.target.value }))}
            />
          </div>
        )}
      </Modal>

      {/* Record disciplinary modal */}
      <Modal
        open={noticeOpen}
        onClose={() => setNoticeOpen(false)}
        title="Record Disciplinary Action"
        footer={
          <>
            <Button variant="secondary" onClick={() => setNoticeOpen(false)} disabled={saving}>Cancel</Button>
            <Button icon={ShieldAlert} onClick={handleIssueNotice} disabled={saving}>{saving ? "Saving…" : "Record"}</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleIssueNotice}>
          <Select
            label="Employee"
            required
            placeholder={employeeOptions.length ? "Select an employee" : "No employees on file"}
            options={employeeOptions}
            value={noticeValues.employeeId}
            onChange={(e) => setNoticeValues((v) => ({ ...v, employeeId: e.target.value }))}
            error={errors.employeeId}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Action type"
              options={DISCIPLINARY_ACTION_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
              value={noticeValues.actionType}
              onChange={(e) => setNoticeValues((v) => ({ ...v, actionType: e.target.value }))}
            />
            <Select
              label="Severity"
              options={DISCIPLINARY_SEVERITIES.map((t) => ({ value: t, label: humanize(t) }))}
              value={noticeValues.severity}
              onChange={(e) => setNoticeValues((v) => ({ ...v, severity: e.target.value }))}
            />
          </div>
          <Input
            label="Incident date"
            type="date"
            required
            value={noticeValues.incidentDate}
            onChange={(e) => setNoticeValues((v) => ({ ...v, incidentDate: e.target.value }))}
            error={errors.incidentDate}
          />
          <Textarea
            label="Description"
            required
            rows={3}
            value={noticeValues.description}
            onChange={(e) => setNoticeValues((v) => ({ ...v, description: e.target.value }))}
            error={errors.description}
          />
        </form>
      </Modal>

      {/* Update disciplinary modal */}
      <Modal
        open={!!editNotice}
        onClose={() => setEditNotice(null)}
        title={editNotice ? `Disciplinary Action — ${editNotice.name}` : "Disciplinary Action"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditNotice(null)} disabled={saving}>Cancel</Button>
            <Button onClick={handleSaveNotice} disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
          </>
        }
      >
        {editNotice && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <Select
              label="Status"
              options={DISCIPLINARY_STATUSES.map((t) => ({ value: t, label: humanize(t) }))}
              value={editNotice.status}
              onChange={(e) => setEditNotice((s) => ({ ...s, status: e.target.value }))}
            />
            <Textarea
              label="Action taken"
              rows={2}
              value={editNotice.actionTaken}
              onChange={(e) => setEditNotice((s) => ({ ...s, actionTaken: e.target.value }))}
            />
            <Textarea
              label="Resolution notes"
              rows={2}
              value={editNotice.resolutionNotes}
              onChange={(e) => setEditNotice((s) => ({ ...s, resolutionNotes: e.target.value }))}
            />
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={!!confirmComplete}
        onClose={() => setConfirmComplete(null)}
        onConfirm={handleComplete}
        title="Finalise exit clearance?"
        description="This closes the workflow and flips the employee record to EXITED (or TERMINATED for a termination), stamping their exit date. It cannot be undone."
        confirmLabel="Finalise exit"
      />
    </div>
  );
}
