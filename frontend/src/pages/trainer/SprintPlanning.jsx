import { useEffect, useState } from "react";
import { Plus, Target, Briefcase, Users, ListPlus } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getBatches, getSprints, createSprint, getStaffableClientProjects, getSprintTasks, createTask, getStudentsForBatch, assignTask } from "../../services/trainerService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

export default function TrainerSprintPlanning() {
  const [batches, setBatches] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [staffableProjects, setStaffableProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ batchId: "", number: "", goal: "", startDate: "", endDate: "", clientProjectId: "" });
  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  // Task creation, scoped to whichever sprint's "Add Task" was clicked.
  const [activeSprint, setActiveSprint] = useState(null);
  const [taskStudents, setTaskStudents] = useState([]);
  const [taskValues, setTaskValues] = useState({ title: "", points: "3", dueDate: "", assigneeUuid: "" });
  const [taskErrors, setTaskErrors] = useState({});
  const [taskSubmitting, setTaskSubmitting] = useState(false);

  const load = () => Promise.all([
    getBatches().catch(() => []),
    getSprints().catch(() => []),
    getStaffableClientProjects().catch(() => []),
    getSprintTasks().catch(() => []),
  ])
    .then(([b, s, p, t]) => { setBatches(b); setSprints(s); setStaffableProjects(p); setTasks(t); })
    .finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const openModal = () => {
    setValues({ batchId: "", number: "", goal: "", startDate: "", endDate: "", clientProjectId: "" });
    setErrors({});
    setModalOpen(true);
  };

  // Picking a BA-approved client requirement pre-fills the sprint goal from
  // it, so the trainer isn't retyping what the client and BA already agreed on.
  const pickClientProject = (id) => {
    const project = staffableProjects.find((p) => p.id === id);
    setValues((v) => ({
      ...v,
      clientProjectId: id,
      goal: project ? `Kickoff — ${project.title} (${project.client})` : v.goal,
    }));
  };

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { batchId: [required], number: [required], goal: [required], startDate: [required], endDate: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setSubmitting(true);
    try {
      await createSprint(values);
      notify(
        values.clientProjectId
          ? "Sprint scheduled and linked to the client's project — they'll now see live progress in their portal."
          : "Sprint scheduled successfully.",
        { type: "success", title: "Sprint created" }
      );
      setModalOpen(false);
      load();
    } finally {
      setSubmitting(false);
    }
  };

  const batchName = (id) => batches.find((b) => b.id === id)?.name || "—";

  const openTaskModal = (sprint) => {
    setActiveSprint(sprint);
    setTaskValues({ title: "", points: "3", dueDate: "", assigneeUuid: "" });
    setTaskErrors({});
    setTaskStudents([]);
    getStudentsForBatch(sprint.batchId).then(setTaskStudents).catch(() => setTaskStudents([]));
  };

  const closeTaskModal = () => setActiveSprint(null);

  const submitTask = async (e) => {
    e.preventDefault();
    const validation = validateForm(taskValues, { title: [required], dueDate: [required] });
    setTaskErrors(validation);
    if (Object.keys(validation).length) return;
    setTaskSubmitting(true);
    try {
      const created = await createTask({ sprintId: activeSprint.id, ...taskValues });
      if (taskValues.assigneeUuid) await assignTask(created.id, taskValues.assigneeUuid);
      notify(
        taskValues.assigneeUuid ? "Task created and assigned." : "Backlog item added — students pull it from their sprint board.",
        { type: "success", title: "Task created" }
      );
      setActiveSprint(null);
      load();
    } catch (err) {
      notify(err.message || "Failed to create the task.", { type: "error" });
    } finally {
      setTaskSubmitting(false);
    }
  };

  const tasksForSprint = (sprintId) => tasks.filter((t) => t.sprintId === sprintId);

  return (
    <div>
      <PageHeader
        title="Sprint Planning"
        subtitle="Configure 1–2 week agile sprints, backlog items, and acceptance criteria"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Sprint Planning" }]}
        action={<Button icon={Plus} onClick={openModal}>New Sprint</Button>}
      />

      {staffableProjects.length > 0 && (
        <Card className="mb-4 border-gold-300/60 bg-gold-50/40">
          <div className="flex items-start gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gold-100 text-gold-700 shrink-0">
              <Briefcase size={16} />
            </div>
            <div>
              <p className="text-sm font-medium text-ink-900">
                {staffableProjects.length} BA-approved requirement{staffableProjects.length > 1 ? "s are" : " is"} waiting to be staffed
              </p>
              <p className="text-xs text-ink-500 mt-1">
                {staffableProjects.map((p) => `${p.title} (${p.client})`).join(" · ")} — click "New Sprint" and pick one to kick off delivery.
              </p>
            </div>
          </div>
        </Card>
      )}

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading sprints…" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {sprints.map((s) => (
            <Card key={s.id}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-50 text-primary-700 shrink-0">
                    <Target size={16} />
                  </div>
                  <div>
                    <p className="font-medium text-ink-900">Sprint {s.number} · {batchName(s.batchId)}</p>
                    <p className="text-xs text-ink-500">{s.startDate} → {s.endDate}</p>
                  </div>
                </div>
                <Badge tone={s.status === "Active" ? "success" : "neutral"}>{s.status}</Badge>
              </div>
              <p className="text-sm text-ink-600 mt-3">{s.goal}</p>

              <div className="border-t border-border mt-4 pt-3">
                <div className="flex items-center justify-between mb-2">
                  <p className="text-xs font-semibold text-ink-700">{tasksForSprint(s.id).length} task{tasksForSprint(s.id).length === 1 ? "" : "s"} in this sprint</p>
                  <Button size="xs" variant="secondary" icon={ListPlus} onClick={() => openTaskModal(s)}>Add Task</Button>
                </div>
                {tasksForSprint(s.id).length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    {tasksForSprint(s.id).map((t) => (
                      <div key={t.id} className="flex items-center justify-between text-xs bg-cream-50 rounded-md px-2.5 py-1.5">
                        <span className="text-ink-700">{t.title} <span className="text-ink-400">· {t.assignee}</span></span>
                        <Badge tone={t.status === "Completed" ? "success" : t.status === "Review" ? "info" : "neutral"}>{t.status}</Badge>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create New Sprint"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button loading={submitting} onClick={submit}>Create Sprint</Button>
        </>}
      >
        <form className="flex flex-col gap-4" onSubmit={submit}>
          {staffableProjects.length > 0 && (
            <Select
              label="Link to an approved client requirement (optional)"
              placeholder="Not linked to a client requirement"
              options={staffableProjects.map((p) => ({ value: p.id, label: `${p.title} — ${p.client}` }))}
              value={values.clientProjectId}
              onChange={(e) => pickClientProject(e.target.value)}
            />
          )}
          <Select
            label="Batch"
            required
            placeholder="Select batch"
            hint={values.clientProjectId ? "This is the final batch assignment for this client requirement — it updates the Client portal immediately." : undefined}
            options={batches.map((b) => ({ value: b.id, label: b.name }))}
            value={values.batchId}
            onChange={(e) => setValues((v) => ({ ...v, batchId: e.target.value }))}
            error={errors.batchId}
          />
          <Input label="Sprint number" type="number" required placeholder="5" value={values.number} onChange={(e) => setValues((v) => ({ ...v, number: e.target.value }))} error={errors.number} />
          <Textarea label="Sprint goal" required placeholder="e.g. Implement payment webhook handling" value={values.goal} onChange={(e) => setValues((v) => ({ ...v, goal: e.target.value }))} error={errors.goal} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start date" type="date" required value={values.startDate} onChange={(e) => setValues((v) => ({ ...v, startDate: e.target.value }))} error={errors.startDate} />
            <Input label="End date" type="date" required value={values.endDate} onChange={(e) => setValues((v) => ({ ...v, endDate: e.target.value }))} error={errors.endDate} />
          </div>
        </form>
      </Modal>

      <Modal
        open={!!activeSprint}
        onClose={closeTaskModal}
        title="Add a task"
        description={activeSprint ? `Sprint ${activeSprint.number} · ${batchName(activeSprint.batchId)}` : ""}
        footer={<>
          <Button variant="secondary" onClick={closeTaskModal}>Cancel</Button>
          <Button loading={taskSubmitting} onClick={submitTask}>Add Task</Button>
        </>}
      >
        <form className="flex flex-col gap-4" onSubmit={submitTask}>
          <Input label="Task title" required placeholder="e.g. Build login & OAuth screen" value={taskValues.title} onChange={(e) => setTaskValues((v) => ({ ...v, title: e.target.value }))} error={taskErrors.title} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Points" type="number" min="1" value={taskValues.points} onChange={(e) => setTaskValues((v) => ({ ...v, points: e.target.value }))} />
            <Input label="Due date" type="date" required value={taskValues.dueDate} onChange={(e) => setTaskValues((v) => ({ ...v, dueDate: e.target.value }))} error={taskErrors.dueDate} />
          </div>
          <Select
            label="Assign to (optional)"
            placeholder={taskStudents.length ? "Leave unassigned — students self-pull" : "No students enrolled in this batch"}
            value={taskValues.assigneeUuid}
            onChange={(e) => setTaskValues((v) => ({ ...v, assigneeUuid: e.target.value }))}
            options={taskStudents
              .filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP")
              .map((s) => ({ value: s.userUuid, label: s.name }))}
          />
          <p className="text-xs text-ink-500">Unassigned items sit in the Backlog for students to pull.</p>
        </form>
      </Modal>
    </div>
  );
}