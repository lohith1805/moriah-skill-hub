import { useEffect, useState } from "react";
import { Calendar, Hash, Target, FileText, CheckSquare, Layers, HelpCircle, MessageSquare } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import KanbanBoard from "../../components/widgets/KanbanBoard";
import Modal from "../../components/ui/Modal";
import Button from "../../components/ui/Button";
import { getMyTasks, getMySprints, updateTaskStatus } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

const KANBAN_COLUMNS = ["Backlog", "Weekly Assignments", "Daily Tasks", "Review", "Completed"];

export default function StudentTasks() {
  const [tasks, setTasks] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedTask, setSelectedTask] = useState(null);
  const { notify } = useToast();

  const load = () => {
    Promise.all([getMyTasks(), getMySprints()]).then(([t, s]) => {
      setTasks(t);
      setSprints(s);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

  const move = async (taskId, columnLabel) => {
    const statusMap = {
      "Backlog": "Backlog",
      "Weekly Assignments": "Assigned",
      "Daily Tasks": "In Progress",
      "Review": "Review",
      "Completed": "Completed"
    };
    const status = statusMap[columnLabel];
    const targetTask = tasks.find(t => t.id === taskId);

    // Enforce FRS PIP Sprint task restrictions
    if (status === "In Progress" && targetTask) {
      // 1. Check Project Milestone > 48 Hours overdue
      const now = Date.now();
      const hasOverdueMilestone = tasks.some(t => 
        t.id !== taskId &&
        t.status !== "Completed" && 
        t.due && 
        (now - new Date(t.due).getTime() > 48 * 60 * 60 * 1000)
      );

      let hasPipBlocker = false;
      try {
        const rawPip = localStorage.getItem("msh_pip_records");
        if (rawPip) {
          hasPipBlocker = JSON.parse(rawPip).some(p => p.status === "In Recovery" && p.reason === "Project Delay");
        }
      } catch (e) {}

      if (hasOverdueMilestone || hasPipBlocker) {
        notify("Access Restricted: You are blocked from picking subsequent sprint tasks due to a project milestone more than 48 hours overdue! Please clear your backlog first.", {
          type: "error",
          title: "Sprint Board Locked"
        });
        load();
        return;
      }

      // 2. Check Quiz Average < 60% blocks Advanced Tasks (8+ points)
      if (targetTask.points >= 8) {
        let quizAvg = 84; // default fallback
        try {
          const rawAttempts = localStorage.getItem("msh_assessment_attempts");
          if (rawAttempts) {
            const attempts = JSON.parse(rawAttempts);
            if (attempts.length > 0) {
              quizAvg = attempts.reduce((sum, a) => sum + a.score, 0) / attempts.length;
            }
          }
        } catch (e) {}

        if (quizAvg < 60) {
          notify("Access Restricted: Your quiz average is below 60%. Access to advanced sprint tasks (8+ points) is restricted until you clear a mandatory re-test.", {
            type: "error",
            title: "Advanced Task Locked"
          });
          load();
          return;
        }
      }
    }
    
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, status } : t)));
    await updateTaskStatus(taskId, status);
    notify(`Task moved to ${columnLabel}.`, { type: "success" });
  };

  const toggleCriteria = (task, criteria) => {
    const completed = task.completedCriteria || [];
    const newCompleted = completed.includes(criteria)
      ? completed.filter(c => c !== criteria)
      : [...completed, criteria];
      
    const updatedTask = { ...task, completedCriteria: newCompleted };
    
    // Save back to local storage
    try {
      let allTasks = [];
      const raw = localStorage.getItem("msh_sprint_tasks");
      allTasks = raw ? JSON.parse(raw) : [];
      const idx = allTasks.findIndex(t => t.id === task.id);
      if (idx > -1) {
        allTasks[idx] = updatedTask;
        localStorage.setItem("msh_sprint_tasks", JSON.stringify(allTasks));
      }
    } catch (e) {
      console.warn("Failed to save criteria:", e);
    }
    
    // Update local state
    setTasks(prev => prev.map(t => t.id === task.id ? updatedTask : t));
    if (selectedTask && selectedTask.id === task.id) {
      setSelectedTask(updatedTask);
    }
  };

  // Map backend statuses to kanban columns
  const displayTasks = tasks.map(t => {
    const displayMap = {
      "Backlog": "Backlog",
      "Assigned": "Weekly Assignments",
      "In Progress": "Daily Tasks",
      "Review": "Review",
      "Completed": "Completed"
    };
    return { ...t, status: displayMap[t.status] || t.status };
  });

  return (
    <div>
      <PageHeader title="Sprint Board" subtitle="Drag tasks across the board as you make progress" breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Sprint Board" }]} />

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

      <Card padding={false} className="p-4">
        {loading ? (
          <div className="flex justify-center py-16"><LoadingSpinner label="Loading sprint board…" /></div>
        ) : (
          <KanbanBoard
            columns={KANBAN_COLUMNS}
            items={displayTasks}
            onMove={move}
            renderCard={(t) => (
              <div onClick={() => setSelectedTask(t)} className="cursor-pointer group text-left">
                <div className="flex items-center justify-between gap-2 mb-2">
                  <Badge tone={t.type === "Bug" ? "error" : t.type === "User Story" ? "info" : "neutral"} className="text-[10px] uppercase font-semibold">
                    {t.type || "Task"}
                  </Badge>
                  <span className="text-[10px] font-bold text-primary-700 font-mono tracking-wider bg-primary-50 px-1.5 py-0.5 rounded shrink-0">
                    {t.epic || "General"}
                  </span>
                </div>
                <p className="text-sm font-semibold text-ink-900 group-hover:text-primary-600 transition-colors leading-snug">
                  {t.title}
                </p>
                <div className="flex items-center justify-between mt-4 pt-2.5 border-t border-border/40 text-[10px] text-ink-500">
                  <span className="flex items-center gap-1"><Calendar size={11} /> {t.due}</span>
                  <span className="flex items-center gap-1 font-bold text-ink-800"><Hash size={11} /> {t.points} pts</span>
                </div>
                {t.githubPr && <Badge tone="success" className="mt-2 text-[9px] w-full text-center block">PR Linked</Badge>}
              </div>
            )}
          />
        )}
      </Card>

      {/* Task Detail Modal */}
      {selectedTask && (
        <Modal
          open={!!selectedTask}
          onClose={() => setSelectedTask(null)}
          title={selectedTask.title}
          description={`Sprint Point Weight: ${selectedTask.points} pts · Due Date: ${selectedTask.due}`}
          footer={
            <Button variant="primary" fullWidth onClick={() => setSelectedTask(null)}>
              Close Panel
            </Button>
          }
        >
          <div className="flex flex-col gap-5 text-left mt-3">
            {/* Header Attributes */}
            <div className="flex items-center gap-3">
              <div className="flex flex-col gap-0.5">
                <span className="text-[10px] text-ink-400 font-bold uppercase">Epic Reference</span>
                <span className="text-xs font-semibold text-primary-800 bg-primary-50 px-2 py-0.5 rounded border border-primary-100 flex items-center gap-1">
                  <Layers size={12} /> {selectedTask.epic || "General"}
                </span>
              </div>
              <div className="flex flex-col gap-0.5">
                <span className="text-[10px] text-ink-400 font-bold uppercase">Work Classification</span>
                <Badge tone={selectedTask.type === "Bug" ? "error" : selectedTask.type === "User Story" ? "info" : "neutral"} className="text-xs">
                  {selectedTask.type || "Task"}
                </Badge>
              </div>
            </div>

            {/* User Story */}
            {selectedTask.userStory && (
              <div className="rounded-lg bg-slate-900/50 border border-slate-800 p-4">
                <p className="text-xs text-slate-400 font-bold uppercase flex items-center gap-1.5 mb-1.5">
                  <FileText size={13} className="text-[#635BFF]" /> Agile User Story
                </p>
                <p className="text-sm text-slate-200 italic leading-relaxed">
                  "{selectedTask.userStory}"
                </p>
              </div>
            )}

            {/* Acceptance Criteria Checklist */}
            <div className="border-t border-border pt-4">
              <p className="text-xs text-ink-500 font-bold uppercase flex items-center gap-1.5 mb-3">
                <CheckSquare size={13} className="text-success-600" /> Acceptance Criteria Checklist
              </p>
              {selectedTask.acceptanceCriteria ? (
                <div className="flex flex-col gap-2.5">
                  {selectedTask.acceptanceCriteria.split("\n").filter(line => line.trim()).map((crit, idx) => {
                    const isCompleted = (selectedTask.completedCriteria || []).includes(crit);
                    return (
                      <label key={idx} className="flex items-start gap-2.5 cursor-pointer hover:bg-cream-50/50 p-2 rounded-lg border border-transparent hover:border-border/30 transition-all">
                        <input
                          type="checkbox"
                          checked={isCompleted}
                          onChange={() => toggleCriteria(selectedTask, crit)}
                          className="mt-0.5 h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500/20"
                        />
                        <span className={`text-sm select-none ${isCompleted ? "line-through text-ink-400" : "text-ink-700"}`}>
                          {crit}
                        </span>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="flex items-center gap-2 text-xs text-ink-400 bg-cream-50 p-3 rounded-lg border border-border/30">
                  <HelpCircle size={14} />
                  <span>No explicit acceptance criteria defined for this backlog task.</span>
                </div>
              )}
            </div>

            {/* Trainer Inline Code Comments */}
            {selectedTask.inlineComments && selectedTask.inlineComments.length > 0 && (
              <div className="border-t border-border pt-4">
                <p className="text-xs text-ink-500 font-bold uppercase flex items-center gap-1.5 mb-3">
                  <MessageSquare size={13} className="text-primary-600" /> Mentor Inline Code Comments
                </p>
                <div className="flex flex-col gap-3">
                  {selectedTask.inlineComments.map((c, idx) => (
                    <div key={idx} className="rounded-lg bg-primary-50/50 border border-primary-100 p-3 text-xs">
                      <div className="flex items-center justify-between gap-2 mb-1.5">
                        <span className="font-mono font-semibold text-primary-850 bg-primary-100/60 px-1.5 py-0.5 rounded">
                          {c.file} : Line {c.line}
                        </span>
                        <span className="text-[10px] text-ink-400 font-medium">By {c.author || "Trainer"}</span>
                      </div>
                      <p className="font-mono text-[11px] text-ink-700 bg-white border border-border p-1.5 rounded mb-2 leading-relaxed whitespace-pre truncate">
                        {c.codeLine}
                      </p>
                      <p className="text-ink-800 font-medium italic leading-relaxed">
                        "{c.comment}"
                      </p>
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