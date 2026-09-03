import { useEffect, useState } from "react";
import { Plus, ShieldAlert, XCircle, CheckCircle, AlertTriangle, Users, BookOpen } from "lucide-react";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import Breadcrumbs from "../../components/widgets/Breadcrumbs";
import { getPipCases, triggerManualPip, removePipCase, updatePipCaseStatus, getBatches } from "../../services/trainerService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { PIP_TRIGGERS } from "../../utils/constants";
import { formatDate } from "../../utils/formatters";

export default function PIPManagement() {
  const { notify } = useToast();
  const [cases, setCases] = useState([]);
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({ student: "", batch: "", reason: "", severity: "Medium", note: "" });
  const [errors, setErrors] = useState({});
  const [removeTarget, setRemoveTarget] = useState(null);
  const [removing, setRemoving] = useState(false);

  // Review Dialog states
  const [reviewTarget, setReviewTarget] = useState(null);
  const [clearingProgress, setClearingProgress] = useState(false);
  const [repeatBatchName, setRepeatBatchName] = useState("");

  // Remediation metrics are computed from the same real data sources used
  // elsewhere in the app (attendance logs, sprint tasks, assessment
  // attempts) — no fabricated per-student values.
  const getRemediationMetrics = (studentName) => {
    let attendance = 0;
    try {
      const raw = localStorage.getItem("msh_attendance_logs");
      if (raw) {
        const logs = JSON.parse(raw).filter((l) => l.studentName === studentName);
        if (logs.length > 0) {
          const present = logs.filter((l) => l.status === "Present" || l.status === "Clocked In").length;
          attendance = Math.round((present / logs.length) * 100);
        }
      }
    } catch (e) {}

    let backlogTasks = 0;
    try {
      const raw = localStorage.getItem("msh_sprint_tasks");
      if (raw) {
        const now = Date.now();
        backlogTasks = JSON.parse(raw).filter(
          (t) => t.assignee === studentName && t.status !== "Completed" && t.due && new Date(t.due).getTime() < now
        ).length;
      }
    } catch (e) {}

    let quizAverage = 0;
    try {
      const raw = localStorage.getItem("msh_assessment_attempts");
      if (raw) {
        const attempts = JSON.parse(raw).filter((a) => a.studentName === studentName);
        if (attempts.length > 0) {
          quizAverage = Math.round(attempts.reduce((sum, a) => sum + (a.score || 0), 0) / attempts.length);
        }
      }
    } catch (e) {}

    return { attendance, backlogTasks, quizAverage };
  };

  const load = () => Promise.all([getPipCases(), getBatches()]).then(([data, bList]) => {
    setCases(data);
    setBatches(bList);
    setLoading(false);
  });

  useEffect(() => { load(); }, []);

  const onChange = (e) => setValues((v) => ({ ...v, [e.target.name]: e.target.value }));

  const onCreate = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(values, { student: [required], batch: [required], reason: [required] });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    setSaving(true);
    try {
      await triggerManualPip(values);
      notify(`PIP recommendation raised for ${values.student}.`, { type: "success", title: "PIP triggered" });
      setOpen(false);
      setValues({ student: "", batch: "", reason: "", severity: "Medium", note: "" });
      load();
    } catch (err) {
      notify(err.message || "Couldn't submit the PIP recommendation.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const onConfirmRemove = async () => {
    if (!removeTarget) return;
    setRemoving(true);
    try {
      await removePipCase(removeTarget.id);
      notify(`${removeTarget.student} has been taken off the PIP list.`, { type: "success", title: "PIP removed" });
      setRemoveTarget(null);
      load();
    } catch (err) {
      notify(err.message || "Couldn't remove the PIP case.", { type: "error" });
    } finally {
      setRemoving(false);
    }
  };

  const handleResolveAction = async (newStatus) => {
    if (!reviewTarget) return;
    if (newStatus === "Repeat Foundation" && !repeatBatchName) {
      notify("Please select a target foundation cohort for re-assignment.", { type: "warning" });
      return;
    }
    setClearingProgress(true);
    try {
      await updatePipCaseStatus(reviewTarget.id, newStatus, repeatBatchName);
      notify(`Student recovery resolution recorded as "${newStatus}".`, { type: "success" });
      setReviewTarget(null);
      setRepeatBatchName("");
      load();
    } catch (err) {
      notify(err.message || "Failed to record PIP action.", { type: "error" });
    } finally {
      setClearingProgress(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumbs items={[{ label: "PIP Management" }]} />
      <div className="flex flex-wrap items-center justify-between gap-3 text-left">
        <div>
          <h2 className="font-display text-2xl font-bold text-ink-900">PIP Management Portal</h2>
          <p className="text-sm text-ink-500 mt-1">Review flagged students, track remediation milestones, and sign off exit reviews.</p>
        </div>
        <Button icon={Plus} onClick={() => setOpen(true)}>Manual PIP recommendation</Button>
      </div>

      <Card>
        <Table
          loading={loading}
          data={cases}
          emptyTitle="No active PIP cases"
          emptyDescription="Flagged students will appear here automatically or via manual recommendation."
          columns={[
            { key: "student", header: "Student Name", sortable: true },
            { key: "batch", header: "Batch Cohort", sortable: true },
            { key: "reason", header: "Trigger Reason", sortable: true },
            { key: "severity", header: "Severity Level", render: (r) => <Badge tone={r.severity === "Critical" ? "error" : r.severity === "High" ? "warning" : "neutral"}>{r.severity}</Badge> },
            { key: "triggeredOn", header: "Flag Date", render: (r) => formatDate(r.triggeredOn) },
            { key: "status", header: "Resolution Status", render: (r) => (
              <Badge tone={r.status === "Terminated" ? "error" : r.status === "Resolved" ? "success" : r.status === "Repeat Foundation" ? "gold" : "info"}>
                {r.status}
              </Badge>
            ) },
            {
              key: "actions",
              header: "",
              render: (r) => (
                <div className="flex gap-2">
                  <Button variant="secondary" size="xs" onClick={() => openReview(r)} disabled={r.status === "Resolved" || r.status === "Terminated"}>
                    Review Recovery
                  </Button>
                  <Button variant="ghost" size="xs" icon={XCircle} onClick={() => setRemoveTarget(r)}>
                    Delete Case
                  </Button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Raise a manual PIP recommendation"
        description="Use this for qualitative concerns not auto-detected by the rule engine."
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="danger" onClick={onCreate} loading={saving} icon={ShieldAlert}>Trigger PIP</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={onCreate}>
          <Input label="Student name" name="student" value={values.student} onChange={onChange} error={errors.student} required />
          <Input label="Batch" name="batch" placeholder="e.g. FS-Batch-14" value={values.batch} onChange={onChange} error={errors.batch} required />
          <Select label="Trigger reason" name="reason" placeholder="Select a reason" value={values.reason} onChange={onChange} error={errors.reason} required
            options={PIP_TRIGGERS.map((t) => ({ value: t.reason, label: t.reason }))} />
          <Select label="Severity" name="severity" value={values.severity} onChange={onChange}
            options={["Medium", "High", "Critical"].map((s) => ({ value: s, label: s }))} />
          <Textarea label="Qualitative notes" name="note" placeholder="Explain the context for this recommendation." value={values.note} onChange={onChange} />
        </form>
      </Modal>

      {/* Modal: Recovery Track Review & Final Actions */}
      {reviewTarget && (() => {
        const metrics = getRemediationMetrics(reviewTarget.student);
        const passAttendance = metrics.attendance >= 85;
        const passBacklog = metrics.backlogTasks === 0;
        const passQuiz = metrics.quizAverage >= 70;
        const eligibleToClear = passAttendance && passBacklog && passQuiz;

        return (
          <Modal
            open={!!reviewTarget}
            onClose={() => setReviewTarget(null)}
            title="Remediation Tracker & Resolution"
            description={`Student: ${reviewTarget.student} · Batch: ${reviewTarget.batch || "Trainee"}`}
            size="lg"
            footer={
              <div className="flex gap-2 w-full justify-between">
                <Button variant="secondary" onClick={() => setReviewTarget(null)}>Close Panel</Button>
                <div className="flex gap-2">
                  <Button variant="danger" loading={clearingProgress} onClick={() => handleResolveAction("Terminated")}>
                    Terminate Candidate
                  </Button>
                  {eligibleToClear ? (
                    <Button variant="primary" loading={clearingProgress} onClick={() => handleResolveAction("Resolved")}>
                      Clear PIP (Pass)
                    </Button>
                  ) : (
                    <Button variant="secondary" disabled>
                      Clear PIP (Unmet)
                    </Button>
                  )}
                </div>
              </div>
            }
          >
            <div className="flex flex-col gap-5 text-left mt-3">
              <div className="bg-cream-50 p-4 rounded-xl border border-border/80">
                <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <ShieldAlert size={14} className="text-error-500" /> Active Trigger Case Notes
                </p>
                <p className="text-sm font-semibold text-ink-900">{reviewTarget.reason} PIP Case</p>
                <p className="text-xs text-ink-600 mt-1 italic leading-relaxed">"{reviewTarget.note || "No qualitative notes recorded."}"</p>
              </div>

              {/* Remediation Milestone checkmarks */}
              <div>
                <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 block">Remediation Criteria Ledger</p>
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between p-3 rounded-lg border bg-white border-border/50">
                    <div>
                      <p className="text-sm font-semibold text-ink-850">Cumulative Attendance Rate</p>
                      <p className="text-xs text-ink-500 mt-0.5">Target: &gt;= 85% · Current: <strong>{metrics.attendance}%</strong></p>
                    </div>
                    {passAttendance ? (
                      <Badge tone="success" className="flex items-center gap-1"><CheckCircle size={12} /> Target Met</Badge>
                    ) : (
                      <Badge tone="error" className="flex items-center gap-1"><AlertTriangle size={12} /> Target Unmet</Badge>
                    )}
                  </div>

                  <div className="flex items-center justify-between p-3 rounded-lg border bg-white border-border/50">
                    <div>
                      <p className="text-sm font-semibold text-ink-850">Backlogged Tasks Burn-Down</p>
                      <p className="text-xs text-ink-500 mt-0.5">Target: 0 Overdue Tasks · Current: <strong>{metrics.backlogTasks} tasks</strong></p>
                    </div>
                    {passBacklog ? (
                      <Badge tone="success" className="flex items-center gap-1"><CheckCircle size={12} /> Target Met</Badge>
                    ) : (
                      <Badge tone="error" className="flex items-center gap-1"><AlertTriangle size={12} /> Target Unmet</Badge>
                    )}
                  </div>

                  <div className="flex items-center justify-between p-3 rounded-lg border bg-white border-border/50">
                    <div>
                      <p className="text-sm font-semibold text-ink-850">Quiz / Assessment Average Score</p>
                      <p className="text-xs text-ink-500 mt-0.5">Target: &gt;= 70% · Current: <strong>{metrics.quizAverage}%</strong></p>
                    </div>
                    {passQuiz ? (
                      <Badge tone="success" className="flex items-center gap-1"><CheckCircle size={12} /> Target Met</Badge>
                    ) : (
                      <Badge tone="error" className="flex items-center gap-1"><AlertTriangle size={12} /> Target Unmet</Badge>
                    )}
                  </div>
                </div>
              </div>

              {/* Advanced Intervention: Repeat Foundational Module */}
              <div className="border-t border-border pt-4">
                <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-2 flex items-center gap-1">
                  <BookOpen size={13} className="text-gold-600" /> PM Intervention: Repeat Foundational Module
                </p>
                <p className="text-xs text-ink-450 leading-relaxed mb-3">
                  If the student is making effort but fails recovery thresholds, assign them to repeat the foundation syllabus in a different cohort.
                </p>
                <div className="flex flex-col sm:flex-row gap-3 items-end">
                  <Select
                    label="Select Foundation Batch"
                    placeholder="Choose batch"
                    value={repeatBatchName}
                    onChange={(e) => setRepeatBatchName(e.target.value)}
                    options={batches.map(b => ({ value: b.name, label: b.name }))}
                    className="flex-1"
                  />
                  <Button
                    variant="secondary"
                    loading={clearingProgress}
                    onClick={() => handleResolveAction("Repeat Foundation")}
                    className="shrink-0"
                  >
                    Reassign Cohort
                  </Button>
                </div>
              </div>
            </div>
          </Modal>
        );
      })()}

      <ConfirmDialog
        open={!!removeTarget}
        onClose={() => setRemoveTarget(null)}
        onConfirm={onConfirmRemove}
        loading={removing}
        tone="danger"
        title="Remove this PIP case?"
        description={removeTarget ? `${removeTarget.student} will be taken off the PIP list and their status will no longer count against ${removeTarget.batch || "their batch"}'s health score.` : ""}
        confirmLabel="Remove"
      />
    </div>
  );
}