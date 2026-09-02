import { useEffect, useState } from "react";
import { Plus, Target, ChevronRight, Layers, FileText, CheckSquare, Calendar, Hash, Users, ShieldAlert } from "lucide-react";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Table from "../../components/ui/Table";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import Breadcrumbs from "../../components/widgets/Breadcrumbs";
import { getBatches, getSprints, createSprint, createTask, getSprintTasks, getStudentsForBatch } from "../../services/trainerService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { formatDate } from "../../utils/formatters";

export default function Sprints() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [sprints, setSprints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({ batchId: "", number: "", goal: "", startDate: "", endDate: "" });
  const [errors, setErrors] = useState({});

  // Selected Sprint Details & Backlog States
  const [selectedSprint, setSelectedSprint] = useState(null);
  const [sprintTasks, setSprintTasks] = useState([]);
  const [students, setStudents] = useState([]);
  const [openTaskModal, setOpenTaskModal] = useState(false);
  const [taskSaving, setTaskSaving] = useState(false);
  const [taskValues, setTaskValues] = useState({
    title: "",
    type: "User Story",
    epic: "",
    userStory: "",
    acceptanceCriteria: "",
    points: "3",
    dueDate: "",
    assignee: ""
  });
  const [taskErrors, setTaskErrors] = useState({});

  const load = (selectId = null) => 
    Promise.all([getBatches(), getSprints()]).then(([b, s]) => { 
      setBatches(b); 
      setSprints(s); 
      if (s.length > 0) {
        const active = selectId ? s.find(item => item.id === selectId) : s[0];
        setSelectedSprint(active || s[0]);
      }
      setLoading(false); 
    });

  useEffect(() => { load(); }, []);

  // Fetch tasks and students when selected sprint changes
  useEffect(() => {
    if (selectedSprint) {
      getSprintTasks(selectedSprint.id).then((data) => {
        setSprintTasks(data);
      });
      getStudentsForBatch(selectedSprint.batchId).then((data) => {
        setStudents(data);
      });
    } else {
      setSprintTasks([]);
      setStudents([]);
    }
  }, [selectedSprint]);

  const batchName = (id) => batches.find((b) => b.id === id)?.name || "—";
  const onChange = (e) => setValues((v) => ({ ...v, [e.target.name]: e.target.value }));
  const onTaskChange = (e) => setTaskValues((v) => ({ ...v, [e.target.name]: e.target.value }));

  const onCreate = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(values, { batchId: [required], goal: [required], startDate: [required], endDate: [required] });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    // Enforce 1-to-2 week sprints
    const start = new Date(values.startDate);
    const end = new Date(values.endDate);
    const diffTime = Math.abs(end - start);
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    if (diffDays < 5 || diffDays > 15) {
      notify("Sprint duration must be between 1 and 2 weeks (5 to 15 days).", { type: "warning", title: "Invalid Duration" });
      return;
    }

    setSaving(true);
    try {
      const scheduledNum = sprints.filter(s => s.batchId === values.batchId).length + 1;
      const res = await createSprint({ ...values, number: Number(values.number) || scheduledNum });
      notify("Sprint scheduled successfully.", { type: "success", title: "Sprint created" });
      setOpen(false);
      setValues({ batchId: "", number: "", goal: "", startDate: "", endDate: "" });
      load(res.id);
    } catch {
      notify("Couldn't create the sprint.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const onCreateTask = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(taskValues, { title: [required], dueDate: [required], assignee: [required] });
    setTaskErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    setTaskSaving(true);
    try {
      await createTask({
        ...taskValues,
        sprintId: selectedSprint.id
      });
      notify("Backlog task created and assigned successfully.", { type: "success" });
      setOpenTaskModal(false);
      setTaskValues({
        title: "",
        type: "User Story",
        epic: "",
        userStory: "",
        acceptanceCriteria: "",
        points: "3",
        dueDate: "",
        assignee: ""
      });
      // Refresh tasks
      const data = await getSprintTasks(selectedSprint.id);
      setSprintTasks(data);
    } catch {
      notify("Failed to create backlog item.", { type: "error" });
    } finally {
      setTaskSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumbs items={[{ label: "Sprint Planning" }]} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="font-display text-2xl font-bold text-ink-900">Sprint Planning & Backlog</h2>
          <p className="text-sm text-ink-500 mt-1">Configure 1-to-2 week agile cycles and manage product backlog items.</p>
        </div>
        <Button icon={Plus} onClick={() => setOpen(true)}>New Sprint</Button>
      </div>

      {loading ? (
        <LoadingSpinner label="Loading sprints…" />
      ) : sprints.length === 0 ? (
        <EmptyState icon={Target} title="No sprints scheduled" description="Create your first sprint to start assigning backlog tasks." />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
          {/* Left Column: Sprints List */}
          <div className="flex flex-col gap-3 lg:col-span-1">
            <p className="text-xs font-bold text-ink-400 uppercase tracking-wider px-1">Sprints List</p>
            {sprints.map((s) => {
              const isSelected = selectedSprint?.id === s.id;
              return (
                <div
                  key={s.id}
                  onClick={() => setSelectedSprint(s)}
                  className={`cursor-pointer text-left rounded-xl border p-4 transition-all duration-200 ${
                    isSelected
                      ? "border-primary-500 bg-primary-50/20 shadow-md ring-2 ring-primary-500/10"
                      : "border-border bg-white hover:border-ink-300 hover:shadow"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold text-primary-700 uppercase tracking-wide">
                      {batchName(s.batchId)}
                    </span>
                    <Badge tone={s.status === "Active" ? "success" : "neutral"}>
                      {s.status}
                    </Badge>
                  </div>
                  <h4 className="font-display font-bold text-ink-900 mt-1 flex items-center justify-between">
                    Sprint {s.number}
                    <ChevronRight size={16} className={`text-ink-400 transition-transform ${isSelected ? "translate-x-1" : ""}`} />
                  </h4>
                  <p className="text-xs text-ink-500 mt-2 truncate">{s.goal}</p>
                  <p className="text-[10px] text-ink-400 mt-1 font-medium">{formatDate(s.startDate)} → {formatDate(s.endDate)}</p>
                </div>
              );
            })}
          </div>

          {/* Right Column: Selected Sprint Backlog Panel */}
          <div className="lg:col-span-2">
            {selectedSprint ? (
              <Card>
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4 mb-4 text-left">
                  <div>
                    <span className="text-xs font-bold text-ink-400 uppercase tracking-wider">Active Sprint Config</span>
                    <h3 className="font-display font-extrabold text-xl text-ink-900 mt-0.5">
                      {batchName(selectedSprint.batchId)} — Sprint {selectedSprint.number}
                    </h3>
                    <p className="text-xs text-ink-500 mt-1 flex items-center gap-1.5">
                      <Calendar size={12} /> {formatDate(selectedSprint.startDate)} – {formatDate(selectedSprint.endDate)}
                    </p>
                  </div>
                  <Button icon={Plus} size="sm" onClick={() => setOpenTaskModal(true)}>Add Backlog Item</Button>
                </div>

                <div className="rounded-lg bg-cream-50/60 border border-border/80 p-4 mb-6 text-left">
                  <p className="text-xs font-bold text-primary-900 uppercase tracking-wider flex items-center gap-1">
                    <Target size={14} className="text-primary-600" /> Sprint Goal
                  </p>
                  <p className="text-sm text-ink-700 font-medium mt-1 leading-relaxed">
                    {selectedSprint.goal}
                  </p>
                </div>

                <div className="text-left mb-2.5">
                  <p className="text-xs font-bold text-ink-400 uppercase tracking-wider px-1">Agile Backlog Items</p>
                </div>

                {sprintTasks.length === 0 ? (
                  <div className="py-12 border-2 border-dashed border-border rounded-xl flex flex-col items-center justify-center text-center p-4">
                    <ShieldAlert size={28} className="text-ink-300" />
                    <p className="text-sm font-semibold text-ink-800 mt-2">Sprint Backlog Empty</p>
                    <p className="text-xs text-ink-500 mt-1 max-w-sm">
                      There are no backlog user stories or bugs scheduled for this sprint. Click "Add Backlog Item" to populate the agile board.
                    </p>
                  </div>
                ) : (
                  <Table
                    data={sprintTasks}
                    columns={[
                      { key: "type", header: "Type", render: (r) => (
                        <Badge tone={r.type === "Bug" ? "error" : r.type === "User Story" ? "info" : "neutral"} className="text-[10px] uppercase font-semibold">
                          {r.type || "Task"}
                        </Badge>
                      ) },
                      { key: "epic", header: "Epic", render: (r) => (
                        <span className="text-xs font-semibold text-primary-700 font-mono">{r.epic || "—"}</span>
                      ) },
                      { key: "title", header: "Backlog Item / Story Title", render: (r) => (
                        <div className="max-w-[200px] lg:max-w-[300px]">
                          <p className="font-semibold text-sm text-ink-900 truncate">{r.title}</p>
                          {r.userStory && <p className="text-[10px] text-ink-500 italic mt-0.5 truncate">{r.userStory}</p>}
                        </div>
                      ) },
                      { key: "assignee", header: "Assignee", render: (r) => (
                        <span className="text-xs text-ink-600 font-medium">{r.assignee}</span>
                      ) },
                      { key: "points", header: "Points", render: (r) => (
                        <span className="text-xs font-bold text-ink-900 bg-cream-100 py-0.5 px-2 rounded-full">{r.points} pts</span>
                      ) },
                      { key: "status", header: "Board Status", render: (r) => (
                        <Badge tone={r.status === "Completed" ? "success" : r.status === "Review" ? "info" : r.status === "In Progress" ? "gold" : "neutral"}>
                          {r.status === "Assigned" ? "Assigned (Weekly)" : r.status}
                        </Badge>
                      ) }
                    ]}
                  />
                )}
              </Card>
            ) : (
              <Card className="flex flex-col items-center justify-center text-center py-20 border border-border">
                <Target size={32} className="text-ink-300" />
                <p className="text-sm font-semibold text-ink-800 mt-2">Select a Sprint</p>
                <p className="text-xs text-ink-500 mt-1">Select a sprint cycle on the left to configure backlog stories.</p>
              </Card>
            )}
          </div>
        </div>
      )}

      {/* Modal: Add Sprint */}
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Schedule a New Sprint"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button onClick={onCreate} loading={saving}>Create Sprint</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={onCreate}>
          <Select label="Batch" name="batchId" placeholder="Select a batch" value={values.batchId} onChange={onChange} error={errors.batchId} required
            options={batches.map((b) => ({ value: b.id, label: b.name }))} />
          <Textarea label="Sprint Goal" name="goal" placeholder="e.g. Implement checkout modal and Stripe subscriptions integrations" value={values.goal} onChange={onChange} error={errors.goal} required />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start Date" name="startDate" type="date" value={values.startDate} onChange={onChange} error={errors.startDate} required />
            <Input label="End Date" name="endDate" type="date" value={values.endDate} onChange={onChange} error={errors.endDate} required />
          </div>
        </form>
      </Modal>

      {/* Modal: Add Backlog Item / User Story */}
      {selectedSprint && (
        <Modal
          open={openTaskModal}
          onClose={() => setOpenTaskModal(null)}
          title="Add Backlog Item"
          description="Configure user stories, epics, estimations, and assignees."
          footer={
            <>
              <Button variant="secondary" onClick={() => setOpenTaskModal(false)}>Cancel</Button>
              <Button onClick={onCreateTask} loading={taskSaving}>Assign to Sprint Board</Button>
            </>
          }
        >
          <form className="flex flex-col gap-4 text-left" onSubmit={onCreateTask}>
            <div className="grid grid-cols-2 gap-4">
              <Select
                label="Type"
                name="type"
                value={taskValues.type}
                onChange={onTaskChange}
                options={[
                  { value: "User Story", label: "User Story" },
                  { value: "Task", label: "Task" },
                  { value: "Bug", label: "Bug" }
                ]}
              />
              <Input
                label="Epic Tag"
                name="epic"
                placeholder="e.g. Authentication"
                value={taskValues.epic}
                onChange={onTaskChange}
              />
            </div>

            <Input
              label="Backlog Title / Task Name"
              name="title"
              placeholder="e.g. Develop login authentication APIs"
              value={taskValues.title}
              onChange={onTaskChange}
              error={taskErrors.title}
              required
            />

            <Textarea
              label="User Story Definition"
              name="userStory"
              placeholder="As a user, I want to authenticate so that I can see my details."
              value={taskValues.userStory}
              onChange={onTaskChange}
            />

            <Textarea
              label="Acceptance Criteria (Enter one criterion per line)"
              name="acceptanceCriteria"
              placeholder="Email validation is verified.&#10;Access token is returned on success.&#10;Unregistered credentials throw 401."
              value={taskValues.acceptanceCriteria}
              onChange={onTaskChange}
              rows={3}
            />

            <div className="grid grid-cols-3 gap-4">
              <Select
                label="Estimation Points"
                name="points"
                value={taskValues.points}
                onChange={onTaskChange}
                options={[
                  { value: "1", label: "1 pt" },
                  { value: "2", label: "2 pts" },
                  { value: "3", label: "3 pts" },
                  { value: "5", label: "5 pts" },
                  { value: "8", label: "8 pts" },
                  { value: "13", label: "13 pts" }
                ]}
              />
              <Input
                label="Due Date"
                name="dueDate"
                type="date"
                value={taskValues.dueDate}
                onChange={onTaskChange}
                error={taskErrors.dueDate}
                required
              />
              <Select
                label="Assignee"
                name="assignee"
                placeholder="Select student"
                value={taskValues.assignee}
                onChange={onTaskChange}
                error={taskErrors.assignee}
                required
                options={students.map((name) => ({ value: name, label: name }))}
              />
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
