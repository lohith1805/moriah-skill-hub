import { useState } from "react";
import { CheckCircle2, Circle, XCircle, Clock, AlertTriangle, Plus, Trash2, ListChecks } from "lucide-react";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import ProgressBar from "../ui/ProgressBar";
import { Input, Textarea } from "../ui/FormField";

// Shared recovery-progress panel — rendered read-only on the student's PIP page
// and with `editable` on the PM's "Review Progress" panel. `progress` is the
// shape pipService.getMyPipProgress() / getPipProgress() returns.
export default function PipProgressPanel({
  progress,
  editable = false,
  busy = false,
  onAddMilestone,
  onCompleteMilestone,
  onDeleteMilestone,
}) {
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ title: "", description: "", dueDate: "" });

  if (!progress) return null;

  const windowPct = progress.windowTotalDays > 0
    ? Math.round((progress.daysElapsed / progress.windowTotalDays) * 100)
    : 0;
  const milestonePct = progress.milestonesTotal > 0
    ? Math.round((progress.milestonesCompleted / progress.milestonesTotal) * 100)
    : 0;

  const submitAdd = async () => {
    if (!form.title.trim() || !form.dueDate) return;
    await onAddMilestone?.(form);
    setForm({ title: "", description: "", dueDate: "" });
    setAdding(false);
  };

  return (
    <div className="flex flex-col gap-5 text-left">
      {/* Window */}
      <div>
        <div className="flex items-center justify-between mb-1.5">
          <p className="text-xs font-bold text-ink-500 uppercase tracking-wide">Recovery window</p>
          <span className="text-xs text-ink-500">
            {progress.windowElapsed
              ? "Window ended"
              : `Day ${progress.daysElapsed} of ${progress.windowTotalDays} · ${progress.daysRemaining} left`}
          </span>
        </div>
        <ProgressBar value={windowPct} tone={progress.windowElapsed ? "error" : "primary"} size="sm" showValue={false} />
        <p className="text-[11px] text-ink-400 mt-1">{progress.startDate} → {progress.endDate}</p>
      </div>

      {/* Milestone checklist */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <p className="text-xs font-bold text-ink-500 uppercase tracking-wide flex items-center gap-1.5">
            <ListChecks size={13} /> Recovery tasks — {progress.milestonesCompleted}/{progress.milestonesTotal}
          </p>
          {editable && (
            <Button size="sm" variant="secondary" icon={Plus} onClick={() => setAdding((a) => !a)}>Add task</Button>
          )}
        </div>
        {progress.milestonesTotal > 0 && (
          <div className="mb-3"><ProgressBar value={milestonePct} tone="success" size="sm" showValue={false} /></div>
        )}

        {editable && adding && (
          <div className="rounded-lg border border-border bg-cream-50 p-3 mb-3 flex flex-col gap-2.5">
            <Input label="Task" placeholder="e.g. Rework the auth module PR with the review comments" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
            <Textarea label="Details (optional)" rows={2} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
            <Input label="Due date" type="date" value={form.dueDate} onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))} />
            <div className="flex gap-2 justify-end">
              <Button size="sm" variant="ghost" onClick={() => setAdding(false)}>Cancel</Button>
              <Button size="sm" loading={busy} onClick={submitAdd} disabled={!form.title.trim() || !form.dueDate}>Add</Button>
            </div>
          </div>
        )}

        <div className="flex flex-col gap-2">
          {progress.milestones.length === 0 && (
            <p className="text-xs text-ink-400 py-3 text-center bg-cream-50 rounded-lg border border-border/40">
              No recovery tasks yet{editable ? " — add the first one above." : ". Your PM will add them shortly."}
            </p>
          )}
          {progress.milestones.map((m) => (
            <div key={m.id} className="flex items-start gap-2.5 rounded-lg border border-border/50 bg-white p-2.5">
              {m.done ? (
                <CheckCircle2 size={16} className="text-success-600 shrink-0 mt-0.5" />
              ) : m.missed ? (
                <XCircle size={16} className="text-error-500 shrink-0 mt-0.5" />
              ) : (
                <Circle size={16} className="text-ink-300 shrink-0 mt-0.5" />
              )}
              <div className="flex-1 min-w-0">
                <p className={`text-sm font-medium ${m.done ? "line-through text-ink-400" : "text-ink-800"}`}>{m.title}</p>
                {m.description && <p className="text-xs text-ink-500 mt-0.5 whitespace-pre-line">{m.description}</p>}
                <p className="text-[11px] text-ink-400 mt-0.5 flex items-center gap-1">
                  <Clock size={11} /> due {m.dueDate}{m.missed ? " · missed" : ""}
                </p>
              </div>
              {editable && !m.done && !m.missed && (
                <div className="flex gap-1 shrink-0">
                  <Button size="sm" variant="ghost" icon={CheckCircle2} loading={busy} onClick={() => onCompleteMilestone?.(m.id)}>Verify</Button>
                  <Button size="sm" variant="ghost" icon={Trash2} loading={busy} onClick={() => onDeleteMilestone?.(m.id)} />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Clearance gates */}
      <div>
        <p className="text-xs font-bold text-ink-500 uppercase tracking-wide mb-2">
          Clearance criteria {progress.clearanceCriteriaMet
            ? <Badge tone="success" className="ml-1">All met</Badge>
            : <Badge tone="warning" className="ml-1">Not yet met</Badge>}
        </p>
        <div className="flex flex-col gap-2">
          {progress.gates.map((g) => (
            <div key={g.key} className="flex items-center justify-between rounded-lg border border-border/50 bg-white p-2.5">
              <div>
                <p className="text-sm font-medium text-ink-800">
                  {g.label}
                  {!g.hard && <span className="text-[10px] text-ink-400 font-normal ml-1.5">(advisory)</span>}
                </p>
                <p className="text-xs text-ink-500 mt-0.5">
                  {g.current == null
                    ? (g.met ? "No sprint tasks in this window — not blocking" : "No data yet")
                    : typeof g.current === "number"
                      ? `Current ${g.current}${g.unit || ""}${g.target != null ? ` · target ${g.key === "overdue" ? "" : "≥ "}${g.target}${g.unit || ""}` : ""}`
                      : g.current}
                </p>
              </div>
              {g.met ? (
                <Badge tone="success" className="flex items-center gap-1"><CheckCircle2 size={12} /> Met</Badge>
              ) : (
                <Badge tone={g.hard ? "error" : "neutral"} className="flex items-center gap-1"><AlertTriangle size={12} /> {g.hard ? "Not met" : "Below"}</Badge>
              )}
            </div>
          ))}
        </div>
        <p className="text-[11px] text-ink-400 mt-2">
          A PIP clears when task completion and the weekly review are both met (attendance and overdue tasks are advisory).
        </p>
      </div>

      {/* Outstanding sprint tasks */}
      {progress.outstandingTasks.length > 0 && (
        <div>
          <p className="text-xs font-bold text-ink-500 uppercase tracking-wide mb-2">Still-open sprint tasks</p>
          <div className="flex flex-col gap-1.5">
            {progress.outstandingTasks.map((t) => (
              <div key={t.id} className="flex items-center justify-between text-xs rounded-md bg-cream-50 border border-border/40 px-2.5 py-1.5">
                <span className="text-ink-700 truncate">{t.title}</span>
                <span className="flex items-center gap-2 shrink-0">
                  {t.overdue && <Badge tone="error" className="text-[9px]">Overdue</Badge>}
                  <Badge tone="neutral" className="text-[9px]">{t.status}</Badge>
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
