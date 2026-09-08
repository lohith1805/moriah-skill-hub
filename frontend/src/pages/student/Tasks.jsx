import { useEffect, useState } from "react";
import { Calendar, Hash, Target, FileText, CheckSquare, Layers, HelpCircle, MessageSquare, Sun, CalendarRange } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import KanbanBoard from "../../components/widgets/KanbanBoard";
import Modal from "../../components/ui/Modal";
import Button from "../../components/ui/Button";
import { getMyTasks, getMySprints, updateTaskStatus } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

// Real task statuses — no more columns that are secretly a task type.
const KANBAN_COLUMNS = ["To Do", "In Progress", "In Review", "Completed"];

// backend status label -> board column
const STATUS_TO_COLUMN = {
  Backlog: "To Do",
  Assigned: "To Do",
  "In Progress": "In Progress",
  Review: "In Review",
  Completed: "Completed",
  Rejected: "In Progress", // changes-requested — back on the student's plate
};

export default function StudentTasks() {
  const [tasks, setTasks] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedTask, setSelectedTask] = useState(null);
  const { notify } = useToast();

  const load = () => {
    Promise.all([getMyTasks(), getMySprints()])
      .then(([t, s]) => {
        setTasks(t);
        setSprints(s);
      })
      .catch((e) => notify(e.message || "Could not load your tasks.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const move = async (taskId, columnLabel) => {
    // The only student-driven move is "start work" (To Do -> In Progress). The
    // service handles pull-then-start for a task that's still BACKLOG. Every
    // other transition is the PM's or the PR-review flow's.
    if (columnLabel !== "In Progress") {
      notify("Submit a PR from Submissions to send a task for review — your PM moves the rest.", { type: "info" });
      return;
    }
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, status: "In Progress" } : t)));
    const res = await updateTaskStatus(taskId, "In Progress");
    if (res?.started || res?.pulled) {
      notify("Task started — it's now in progress.", { type: "success" });
    } else if (res && res.ok === false) {
      notify(res.error || "Couldn't move that task.", { type: "error" });
    }
    load();
  };

  const toggleCriteria = (task, criteria) => {
    const completed = task.completedCriteria || [];
    const newCompleted = completed.includes(criteria)
      ? completed.filter((c) => c !== criteria)
      : [...completed, criteria];
    const updatedTask = { ...task, completedCriteria: newCompleted };
    setTasks((prev) => prev.map((t) => (t.id === task.id ? updatedTask : t)));
    if (selectedTask && selectedTask.id === task.id) setSelectedTask(updatedTask);
  };

  const displayTasks = tasks.map((t) => ({ ...t, status: STATUS_TO_COLUMN[t.status] || t.status }));
  const dailyTasks = displayTasks.filter((t) => t.type === "Daily");
  const weeklyTasks = displayTasks.filter((t) => t.type !== "Daily");

  const renderCard = (t) => (
    <div onClick={() => setSelectedTask(t)} className="cursor-pointer group text-left">
      <div className="flex items-center justify-between gap-2 mb-2">
        <Badge tone={t.type === "Daily" ? "info" : "neutral"} className="text-[10px] uppercase font-semibold">
          {t.type === "Daily" ? "Daily" : "Weekly"}
        </Badge>
        {t.type !== "Daily" && (
          <span className="text-[10px] font-bold text-primary-700 font-mono tracking-wider bg-primary-50 px-1.5 py-0.5 rounded shrink-0">
            {t.epic || "General"}
          </span>
        )}
      </div>
      <p className="text-sm font-semibold text-ink-900 group-hover:text-primary-600 transition-colors leading-snug">{t.title}</p>
      <div className="flex items-center justify-between mt-4 pt-2.5 border-t border-border/40 text-[10px] text-ink-500">
        <span className="flex items-center gap-1"><Calendar size={11} /> {t.due || "—"}</span>
        <span className="flex items-center gap-1 font-bold text-ink-800"><Hash size={11} /> {t.points} pts</span>
      </div>
      {t.githubPr && <Badge tone="success" className="mt-2 text-[9px] w-full text-center block">PR Linked</Badge>}
    </div>
  );

  const section = (title, icon, subtitle, items) => (
    <Card padding={false} className="p-4">
      <div className="flex items-center gap-2 mb-3 px-1">
        {icon}
        <div>
          <p className="text-sm font-semibold text-ink-800">{title}</p>
          <p className="text-xs text-ink-400">{subtitle}</p>
        </div>
        <Badge tone="neutral" className="ml-auto">{items.length}</Badge>
      </div>
      {items.length === 0 ? (
        <p className="text-xs text-ink-400 text-center py-8">Nothing here right now.</p>
      ) : (
        <KanbanBoard columns={KANBAN_COLUMNS} items={items} onMove={move} renderCard={renderCard} />
      )}
    </Card>
  );

  return (
    <div>
      <PageHeader
        title="Sprint Board"
        subtitle="Daily tasks and weekly assignments — drag a card to In Progress to start it"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Sprint Board" }]}
      />

      {!loading && sprints.length > 0 && (
        <div className="flex flex-col gap-3 mb-4">
          {sprints.map((s) => (
            <Card key={s.id} className="flex items-start justify-between flex-wrap gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <p className="font-medium text-ink-900">Sprint {s.number}</p>
                  <Badge tone={s.status === "Active" ? "success" : "neutral"}>{s.status}</Badge>
                </div>
                <p className="text-sm text-ink-600 mt-1 flex items-center gap-1.5"><Target size={14} /> {s.goal}</p>
                <p className="text-xs text-ink-500 mt-1.5 flex items-center gap-1.5">
                  <Calendar size={12} /> {s.startDate} – {s.endDate}
                </p>
              </div>
            </Card>
          ))}
        </div>
      )}

      {loading ? (
        <Card><div className="flex justify-center py-16"><LoadingSpinner label="Loading sprint board…" /></div></Card>
      ) : (
        <div className="flex flex-col gap-4">
          {section("Daily Tasks", <Sun size={16} className="text-info-600 shrink-0" />, "Short pieces of work to keep momentum day to day", dailyTasks)}
          {section("Weekly Assignments", <CalendarRange size={16} className="text-primary-600 shrink-0" />, "The larger deliverables due across the sprint", weeklyTasks)}
        </div>
      )}

      {selectedTask && (
        <Modal
          open={!!selectedTask}
          onClose={() => setSelectedTask(null)}
          title={selectedTask.title}
          description={`${selectedTask.type === "Daily" ? "Daily task" : "Weekly assignment"} · ${selectedTask.points} pts · Due ${selectedTask.due || "—"}`}
          footer={<Button variant="primary" fullWidth onClick={() => setSelectedTask(null)}>Close Panel</Button>}
        >
          <div className="flex flex-col gap-5 text-left mt-3">
            <div className="flex items-center gap-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-[10px] text-ink-400 font-bold uppercase">Type</span>
                <Badge tone={selectedTask.type === "Daily" ? "info" : "neutral"} className="text-xs">
                  {selectedTask.type === "Daily" ? "Daily task" : "Weekly assignment"}
                </Badge>
              </div>
              {selectedTask.type !== "Daily" && (
                <div className="flex flex-col gap-0.5">
                  <span className="text-[10px] text-ink-400 font-bold uppercase">Epic</span>
                  <span className="text-xs font-semibold text-primary-800 bg-primary-50 px-2 py-0.5 rounded border border-primary-100 flex items-center gap-1">
                    <Layers size={12} /> {selectedTask.epic || "General"}
                  </span>
                </div>
              )}
            </div>

            {selectedTask.description && (
              <div className="rounded-lg bg-cream-50 border border-border/50 p-4">
                <p className="text-xs text-ink-500 font-bold uppercase flex items-center gap-1.5 mb-1.5">
                  <FileText size={13} className="text-primary-600" /> Details
                </p>
                <p className="text-sm text-ink-700 whitespace-pre-line leading-relaxed">{selectedTask.description}</p>
              </div>
            )}

            <div className="border-t border-border pt-4">
              <p className="text-xs text-ink-500 font-bold uppercase flex items-center gap-1.5 mb-3">
                <CheckSquare size={13} className="text-success-600" /> Acceptance Criteria
              </p>
              {selectedTask.acceptanceCriteria ? (
                <div className="flex flex-col gap-2.5">
                  {selectedTask.acceptanceCriteria.split("\n").filter((line) => line.trim()).map((crit, idx) => {
                    const isCompleted = (selectedTask.completedCriteria || []).includes(crit);
                    return (
                      <label key={idx} className="flex items-start gap-2.5 cursor-pointer hover:bg-cream-50/50 p-2 rounded-lg border border-transparent hover:border-border/30 transition-all">
                        <input
                          type="checkbox"
                          checked={isCompleted}
                          onChange={() => toggleCriteria(selectedTask, crit)}
                          className="mt-0.5 h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500/20"
                        />
                        <span className={`text-sm select-none ${isCompleted ? "line-through text-ink-400" : "text-ink-700"}`}>{crit}</span>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="flex items-center gap-2 text-xs text-ink-400 bg-cream-50 p-3 rounded-lg border border-border/30">
                  <HelpCircle size={14} />
                  <span>No explicit acceptance criteria for this task.</span>
                </div>
              )}
            </div>

            {selectedTask.inlineComments && selectedTask.inlineComments.length > 0 && (
              <div className="border-t border-border pt-4">
                <p className="text-xs text-ink-500 font-bold uppercase flex items-center gap-1.5 mb-3">
                  <MessageSquare size={13} className="text-primary-600" /> Mentor Comments
                </p>
                <div className="flex flex-col gap-3">
                  {selectedTask.inlineComments.map((c, idx) => (
                    <div key={idx} className="rounded-lg bg-primary-50/50 border border-primary-100 p-3 text-xs">
                      <p className="text-ink-800 font-medium italic leading-relaxed">"{c.comment}"</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}
