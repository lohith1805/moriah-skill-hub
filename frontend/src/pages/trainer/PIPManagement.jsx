import { useEffect, useState } from "react";
import { Plus, ShieldAlert, CheckCircle2, XCircle, Repeat } from "lucide-react";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { Select, Textarea } from "../../components/ui/FormField";
import Breadcrumbs from "../../components/widgets/Breadcrumbs";
import PipProgressPanel from "../../components/pip/PipProgressPanel";
import { getBatches, getStudentsForBatch } from "../../services/trainerService";
import {
  getPipCases, getPipProgress, addPipMilestone, deletePipMilestone, completePipMilestone,
  reviewPip, createManualPip, RULE_LABEL, PIP_STATUS_LABEL, SEVERITY_TONE,
} from "../../services/pipService";
import { useToast } from "../../context/ToastContext";
import { formatDate } from "../../utils/formatters";

const RULE_OPTIONS = Object.entries(RULE_LABEL).map(([value, label]) => ({ value, label }));
const SEVERITY_OPTIONS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((s) => ({ value: s, label: s[0] + s.slice(1).toLowerCase() }));

export default function PIPManagement() {
  const { notify } = useToast();
  const [cases, setCases] = useState([]);
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);

  // Manual-raise modal
  const [raiseOpen, setRaiseOpen] = useState(false);
  const [raiseValues, setRaiseValues] = useState({ batchId: "", studentUuid: "", ruleCode: "ATTENDANCE_LOW", severity: "MEDIUM", reason: "" });
  const [raiseStudents, setRaiseStudents] = useState([]);
  const [raising, setRaising] = useState(false);

  // Review-progress modal
  const [reviewCase, setReviewCase] = useState(null);
  const [progress, setProgress] = useState(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [panelBusy, setPanelBusy] = useState(false);
  const [outcomeBusy, setOutcomeBusy] = useState("");
  const [reviewNotes, setReviewNotes] = useState("");

  const load = () => Promise.all([getPipCases().catch(() => []), getBatches({ scope: "mine" }).catch(() => [])])
    .then(([c, b]) => { setCases(c); setBatches(b); })
    .finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  // ---- manual raise ----
  const openRaise = () => {
    setRaiseValues({ batchId: "", studentUuid: "", ruleCode: "ATTENDANCE_LOW", severity: "MEDIUM", reason: "" });
    setRaiseStudents([]);
    setRaiseOpen(true);
  };
  const pickRaiseBatch = (batchId) => {
    setRaiseValues((v) => ({ ...v, batchId, studentUuid: "" }));
    if (batchId) getStudentsForBatch(batchId).then(setRaiseStudents).catch(() => setRaiseStudents([]));
  };
  const submitRaise = async () => {
    const { batchId, studentUuid, ruleCode, severity, reason } = raiseValues;
    if (!batchId || !studentUuid || !reason.trim()) {
      notify("Pick a student and describe the concern.", { type: "warning" });
      return;
    }
    setRaising(true);
    try {
      await createManualPip({ studentUuid, batchId, ruleCode, reason, severity });
      notify("PIP raised — the student and their PM have been notified.", { type: "success", title: "PIP raised" });
      setRaiseOpen(false);
      load();
    } catch (err) {
      notify(err?.message || "Couldn't raise the PIP.", { type: "error" });
    } finally {
      setRaising(false);
    }
  };

  // ---- review progress ----
  const openReview = async (r) => {
    setReviewCase(r);
    setReviewNotes("");
    setProgress(null);
    setProgressLoading(true);
    try {
      setProgress(await getPipProgress(r.id));
    } catch (err) {
      notify(err?.message || "Couldn't load recovery progress.", { type: "error" });
    } finally {
      setProgressLoading(false);
    }
  };
  const refreshProgress = async () => {
    if (!reviewCase) return;
    try { setProgress(await getPipProgress(reviewCase.id)); } catch { /* keep prior */ }
  };

  const doAddMilestone = async (form) => {
    setPanelBusy(true);
    try { await addPipMilestone(reviewCase.id, form); await refreshProgress(); notify("Recovery task added.", { type: "success" }); }
    catch (err) { notify(err?.message || "Couldn't add the task.", { type: "error" }); }
    finally { setPanelBusy(false); }
  };
  const doCompleteMilestone = async (mid) => {
    setPanelBusy(true);
    try { await completePipMilestone(reviewCase.id, mid); await refreshProgress(); notify("Task verified.", { type: "success" }); }
    catch (err) { notify(err?.message || "Couldn't verify the task.", { type: "error" }); }
    finally { setPanelBusy(false); }
  };
  const doDeleteMilestone = async (mid) => {
    setPanelBusy(true);
    try { await deletePipMilestone(reviewCase.id, mid); await refreshProgress(); notify("Task removed.", { type: "success" }); }
    catch (err) { notify(err?.message || "Couldn't remove the task.", { type: "error" }); }
    finally { setPanelBusy(false); }
  };

  const doOutcome = async (outcomeKey) => {
    if (outcomeKey !== "cleared" && !reviewNotes.trim()) {
      notify("Add a note explaining this outcome.", { type: "warning" });
      return;
    }
    setOutcomeBusy(outcomeKey);
    try {
      await reviewPip(reviewCase.id, outcomeKey, reviewNotes || `Recovery review — outcome ${outcomeKey}.`);
      notify(
        outcomeKey === "cleared" ? `${reviewCase.student} cleared the PIP.`
          : outcomeKey === "terminated" ? `${reviewCase.student} marked as terminated.`
          : `${reviewCase.student} reassigned to repeat the foundation.`,
        { type: outcomeKey === "cleared" ? "success" : "info" }
      );
      setReviewCase(null);
      load();
    } catch (err) {
      notify(err?.message || "Couldn't record the outcome.", { type: "error" });
    } finally {
      setOutcomeBusy("");
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumbs items={[{ label: "PIP Management" }]} />
      <div className="flex flex-wrap items-center justify-between gap-3 text-left">
        <div>
          <h2 className="font-display text-2xl font-bold text-ink-900">PIP Management Portal</h2>
          <p className="text-sm text-ink-500 mt-1">
            Most PIPs are raised automatically by the nightly rule engine. Track recovery, build each student's
            task list, and sign off the review. An open PIP that meets every criterion after its 15-day window
            is auto-cleared even without a review.
          </p>
        </div>
        <Button icon={Plus} onClick={openRaise}>Raise a PIP</Button>
      </div>

      <Card>
        <Table
          loading={loading}
          data={cases}
          emptyTitle="No PIP cases"
          emptyDescription="Flagged students appear here automatically, or via a manual recommendation."
          columns={[
            { key: "student", header: "Student", sortable: true },
            { key: "batch", header: "Batch", sortable: true },
            { key: "reason", header: "Trigger", render: (r) => RULE_LABEL[r.ruleCode] || r.reason },
            { key: "severity", header: "Severity", render: (r) => <Badge tone={SEVERITY_TONE[r.severity] || "neutral"}>{r.severity}</Badge> },
            { key: "window", header: "Window", render: (r) => `${formatDate(r.startDate)} → ${formatDate(r.endDate)}` },
            { key: "status", header: "Status", render: (r) => (
              <Badge tone={r.status === "CLEARED" ? "success" : r.status === "TERMINATED" ? "error" : r.status === "REASSIGNED" ? "gold" : "info"}>
                {PIP_STATUS_LABEL[r.status] || r.status}
              </Badge>
            ) },
            { key: "actions", header: "", render: (r) => (
              <Button variant="secondary" size="sm" onClick={() => openReview(r)}>
                {r.open ? "Review progress" : "View"}
              </Button>
            ) },
          ]}
        />
      </Card>

      {/* Raise a PIP */}
      <Modal
        open={raiseOpen}
        onClose={() => setRaiseOpen(false)}
        title="Raise a PIP"
        description="For a qualitative concern the six nightly rules don't catch."
        footer={<>
          <Button variant="secondary" onClick={() => setRaiseOpen(false)}>Cancel</Button>
          <Button variant="danger" icon={ShieldAlert} loading={raising} onClick={submitRaise}>Raise PIP</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={(e) => { e.preventDefault(); submitRaise(); }}>
          <Select label="Batch" required placeholder="Select a batch" value={raiseValues.batchId}
            onChange={(e) => pickRaiseBatch(e.target.value)}
            options={batches.map((b) => ({ value: b.id, label: b.name }))} />
          <Select label="Student" required placeholder={raiseStudents.length ? "Select a student" : "Pick a batch first"}
            value={raiseValues.studentUuid}
            onChange={(e) => setRaiseValues((v) => ({ ...v, studentUuid: e.target.value }))}
            options={raiseStudents.filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP").map((s) => ({ value: s.userUuid, label: s.name }))} />
          <Select label="Recovery theme" required value={raiseValues.ruleCode}
            hint="Sets the first recovery task and how the pipeline groups this case."
            onChange={(e) => setRaiseValues((v) => ({ ...v, ruleCode: e.target.value }))} options={RULE_OPTIONS} />
          <Select label="Severity" value={raiseValues.severity}
            onChange={(e) => setRaiseValues((v) => ({ ...v, severity: e.target.value }))} options={SEVERITY_OPTIONS} />
          <Textarea label="Concern" required rows={3} placeholder="Explain what prompted this PIP." value={raiseValues.reason}
            onChange={(e) => setRaiseValues((v) => ({ ...v, reason: e.target.value }))} />
        </form>
      </Modal>

      {/* Review progress */}
      <Modal
        open={!!reviewCase}
        onClose={() => setReviewCase(null)}
        title={reviewCase ? `${reviewCase.student} — recovery` : "Recovery"}
        description={reviewCase ? `${reviewCase.batch} · ${RULE_LABEL[reviewCase.ruleCode] || reviewCase.reason}` : ""}
        size="lg"
        footer={reviewCase && reviewCase.open ? (
          <div className="flex flex-wrap gap-2 w-full justify-between items-center">
            <Button variant="secondary" onClick={() => setReviewCase(null)}>Close</Button>
            <div className="flex gap-2">
              <Button variant="danger" icon={XCircle} loading={outcomeBusy === "terminated"} onClick={() => doOutcome("terminated")}>Terminate</Button>
              <Button variant="secondary" icon={Repeat} loading={outcomeBusy === "reassigned"} onClick={() => doOutcome("reassigned")}>Reassign</Button>
              <Button
                icon={CheckCircle2}
                loading={outcomeBusy === "cleared"}
                disabled={!progress?.clearanceCriteriaMet}
                onClick={() => doOutcome("cleared")}
              >
                {progress?.clearanceCriteriaMet ? "Clear PIP" : "Clear (criteria unmet)"}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="secondary" onClick={() => setReviewCase(null)}>Close</Button>
        )}
      >
        {progressLoading ? (
          <div className="flex justify-center py-12"><LoadingSpinner label="Loading recovery progress…" /></div>
        ) : progress ? (
          <div className="flex flex-col gap-5">
            <PipProgressPanel
              progress={progress}
              editable={reviewCase?.open}
              busy={panelBusy}
              onAddMilestone={doAddMilestone}
              onCompleteMilestone={doCompleteMilestone}
              onDeleteMilestone={doDeleteMilestone}
            />
            {reviewCase?.open && (
              <div className="border-t border-border pt-4">
                <Textarea
                  label="Review notes (required to terminate or reassign)"
                  rows={2}
                  value={reviewNotes}
                  onChange={(e) => setReviewNotes(e.target.value)}
                  placeholder="What did the recovery look like? Why this outcome?"
                />
              </div>
            )}
            {!reviewCase?.open && reviewCase?.reviewNotes && (
              <div className="border-t border-border pt-4 text-sm text-ink-600">
                <span className="font-semibold text-ink-800">Outcome notes: </span>{reviewCase.reviewNotes}
              </div>
            )}
          </div>
        ) : (
          <p className="text-sm text-ink-400 py-8 text-center">No progress data.</p>
        )}
      </Modal>
    </div>
  );
}
