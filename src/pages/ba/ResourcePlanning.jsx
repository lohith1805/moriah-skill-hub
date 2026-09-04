import { useEffect, useState } from "react";
import {
  BarChart3, Plus, Edit, Trash2, Users, Clock, Flame,
  CheckCircle2, AlertTriangle, Layers, Sparkles
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import StatCard from "../../components/widgets/StatCard";
import { Input, Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getSprintResourcePlan, saveSprintResourcePlan, getDocuments, getAssignableStudents, getAssignableDevelopers } from "../../services/baService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

export default function BaResourcePlanning() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  // Existing requirement docs from Requirements Authoring (MSH-FR-BA-01),
  // so a sprint plan is tied to a project the BA already has on file instead
  // of the project title being re-typed by hand — same "Project" picker
  // pattern as the Documents upload modal.
  const [projects, setProjects] = useState([]);

  // Real, named students/developers a BA can pick from — the live roster
  // of registered accounts (see baService.getAssignableStudents/Developers).
  // allocatedStudents/allocatedDevs stay as the target headcount; studentIds/
  // developerIds are the actual people picked against that headcount.
  const [availableStudents, setAvailableStudents] = useState([]);
  const [availableDevs, setAvailableDevs] = useState([]);

  // Modal states
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState({
    projectId: "",
    batch: "FS-Batch-12 (Full Stack Java)",
    client: "",
    projectTitle: "",
    allocatedStudents: "16",
    allocatedDevs: "2",
    storyPoints: "75",
    sprintDurationWeeks: "3",
    status: "Planned",
    studentIds: [],
    developerIds: []
  });

  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({});

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getSprintResourcePlan()
      .then((r) => setPlans(r))
      .catch(() => setPlans([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    getDocuments().then(setProjects).catch(() => setProjects([]));
    getAssignableStudents().then(setAvailableStudents).catch(() => setAvailableStudents([]));
    getAssignableDevelopers().then(setAvailableDevs).catch(() => setAvailableDevs([]));
  }, []);

  const resetValues = () => ({
    projectId: "",
    batch: "FS-Batch-12 (Full Stack Java)",
    client: "",
    projectTitle: "",
    allocatedStudents: "16",
    allocatedDevs: "2",
    storyPoints: "75",
    sprintDurationWeeks: "3",
    status: "Planned",
    studentIds: [],
    developerIds: []
  });

  // Toggle a person in/out of the roster, capped at the headcount number
  // entered above — can't pick more named people than the plan calls for.
  const toggleRosterMember = (setter, field, cap) => (id) => {
    setter((v) => {
      const current = v[field] || [];
      if (current.includes(id)) {
        return { ...v, [field]: current.filter((x) => x !== id) };
      }
      const capNum = Number(cap) || 0;
      if (capNum && current.length >= capNum) {
        notify(`You've already picked ${capNum} — raise the headcount above to add more.`, { type: "warning" });
        return v;
      }
      return { ...v, [field]: [...current, id] };
    });
  };

  const projectOptions = [
    { value: "", label: "+ Custom Project (type manually)" },
    ...projects.map((p) => ({ value: p.id, label: `${p.title} — ${p.client}` })),
  ];

  const handleProjectSelect = (e) => {
    const id = e.target.value;
    if (!id) {
      setValues((v) => ({ ...v, projectId: "", projectTitle: "", client: "" }));
      return;
    }
    const proj = projects.find((p) => p.id === id);
    setValues((v) => ({
      ...v,
      projectId: id,
      projectTitle: proj?.title || "",
      client: proj?.client || "",
    }));
  };

  const persist = (data) => {
    setPlans(data);
    saveSprintResourcePlan(data);
  };

  const handleCreate = (e) => {
    e.preventDefault();
    const validation = validateForm(values, { client: [required], projectTitle: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const sp = Number(values.storyPoints) || 60;
    const hoursPerPoint = 6;
    const totalDevHours = sp * hoursPerPoint;
    const students = Number(values.allocatedStudents) || 12;
    const devs = Number(values.allocatedDevs) || 2;
    const capacityPct = Math.min(100, Math.round((sp / (students * 4.5)) * 100));

    const assignedStudents = availableStudents.filter((s) => (values.studentIds || []).includes(s.id));
    const assignedDevelopers = availableDevs.filter((d) => (values.developerIds || []).includes(d.id));

    const newPlan = {
      id: `rp_${Date.now()}`,
      batch: values.batch,
      client: values.client,
      projectTitle: values.projectTitle,
      allocatedStudents: students,
      allocatedDevs: devs,
      storyPoints: sp,
      hoursPerPoint,
      devHours: totalDevHours,
      sprintDurationWeeks: Number(values.sprintDurationWeeks) || 3,
      capacityUtilizedPct: capacityPct,
      status: values.status || "Planned",
      assignedStudents,
      assignedDevelopers
    };

    const updated = [newPlan, ...plans];
    persist(updated);
    notify(`Resource plan saved with ${assignedStudents.length}/${students} students and ${assignedDevelopers.length}/${devs} developers actually picked.`, { type: "success" });
    setModalOpen(false);
    setValues(resetValues());
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setEditValues({
      batch: item.batch,
      client: item.client,
      projectTitle: item.projectTitle,
      allocatedStudents: String(item.allocatedStudents),
      allocatedDevs: String(item.allocatedDevs),
      storyPoints: String(item.storyPoints),
      sprintDurationWeeks: String(item.sprintDurationWeeks),
      status: item.status,
      studentIds: (item.assignedStudents || []).map((s) => s.id),
      developerIds: (item.assignedDevelopers || []).map((d) => d.id)
    });
    setEditOpen(true);
  };

  const handleEditSave = (e) => {
    e.preventDefault();
    const sp = Number(editValues.storyPoints) || 60;
    const hoursPerPoint = 6;
    const totalDevHours = sp * hoursPerPoint;
    const students = Number(editValues.allocatedStudents) || 12;
    const devs = Number(editValues.allocatedDevs) || 2;
    const capacityPct = Math.min(100, Math.round((sp / (students * 4.5)) * 100));
    const assignedStudents = availableStudents.filter((s) => (editValues.studentIds || []).includes(s.id));
    const assignedDevelopers = availableDevs.filter((d) => (editValues.developerIds || []).includes(d.id));

    const updated = plans.map((p) => {
      if (p.id === editingId) {
        return {
          ...p,
          batch: editValues.batch,
          client: editValues.client,
          projectTitle: editValues.projectTitle,
          allocatedStudents: students,
          allocatedDevs: devs,
          storyPoints: sp,
          hoursPerPoint,
          devHours: totalDevHours,
          sprintDurationWeeks: Number(editValues.sprintDurationWeeks) || 3,
          capacityUtilizedPct: capacityPct,
          status: editValues.status,
          assignedStudents,
          assignedDevelopers
        };
      }
      return p;
    });

    persist(updated);
    notify("Sprint resource plan updated.", { type: "success" });
    setEditOpen(false);
  };

  const handleDelete = (id) => {
    const target = plans.find((p) => p.id === id);
    const updated = plans.filter((p) => p.id !== id);
    persist(updated);
    notify(`Resource plan for "${target?.projectTitle}" removed.`, { type: "success" });
  };

  const totalDevHoursAll = plans.reduce((s, p) => s + (p.devHours || 0), 0);
  const totalStudentsStaffed = plans.reduce((s, p) => s + (p.allocatedStudents || 0), 0);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Sprint & Resource Planning"
        subtitle="Bridge corporate client needs with Developer setups; estimate sprint story points and developer hours"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Resource Planning" }]}
        action={
          <Button icon={Plus} onClick={() => { setErrors({}); setValues(resetValues()); setModalOpen(true); }}>
            New Sprint Resource Plan
          </Button>
        }
      />

      {/* Top Stat Highlights */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Total Dev Hours Estimated"
          value={`${totalDevHoursAll} hrs`}
          icon={Clock}
          tone="primary"
        />
        <StatCard
          label="Students Allocated Across Sprints"
          value={totalStudentsStaffed}
          icon={Users}
          tone="gold"
        />
        <StatCard
          label="Active Sprint Plans"
          value={plans.length}
          icon={BarChart3}
          tone="success"
        />
      </div>

      <Card>
        <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
          <div>
            <h3 className="font-display font-semibold text-ink-900">Sprint Capacity & Sizing Ledger (MSH-FR-BA-02)</h3>
            <p className="text-xs text-ink-500">Resource allocation mapping story points (1 SP = 6 dev hrs) and batch capacity</p>
          </div>
          <Badge tone="success" dot>Capacity Optimized</Badge>
        </div>

        <Table
          loading={loading}
          data={plans}
          columns={[
            {
              key: "projectTitle",
              header: "Project & Batch",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.projectTitle}</p>
                  <p className="text-xs text-ink-500">{r.client} · {r.batch}</p>
                </div>
              )
            },
            {
              key: "allocation",
              header: "Team Staffing",
              className: "text-left",
              render: (r) => {
                const studentCount = (r.assignedStudents || []).length;
                const devCount = (r.assignedDevelopers || []).length;
                return (
                  <div>
                    <p className="font-medium text-ink-800">
                      {r.allocatedStudents} Students
                      <span className={`ml-1.5 text-[10px] font-mono ${studentCount < r.allocatedStudents ? "text-warning-600" : "text-success-600"}`}>
                        ({studentCount}/{r.allocatedStudents} picked)
                      </span>
                    </p>
                    <p className="text-[10px] text-ink-400 font-mono">+{r.allocatedDevs} Staff Developers ({devCount}/{r.allocatedDevs} picked)</p>
                    {studentCount > 0 && (
                      <p className="text-[10px] text-ink-500 mt-1 line-clamp-1">{r.assignedStudents.map((s) => s.name).join(", ")}</p>
                    )}
                  </div>
                );
              }
            },
            {
              key: "storyPoints",
              header: "Story Points",
              className: "text-left",
              render: (r) => (
                <div>
                  <span className="font-bold text-ink-900 bg-cream-100 px-2 py-0.5 rounded text-xs">{r.storyPoints} SP</span>
                  <p className="text-[10px] text-ink-400 mt-0.5 font-mono">{r.devHours} Dev Hours</p>
                </div>
              )
            },
            {
              key: "capacity",
              header: "Capacity Load",
              className: "text-left",
              render: (r) => (
                <div className="w-32">
                  <ProgressBar
                    value={r.capacityUtilizedPct || 80}
                    tone={(r.capacityUtilizedPct || 80) > 90 ? "warning" : "primary"}
                    showValue
                    size="sm"
                  />
                  <p className="text-[10px] text-ink-400 mt-0.5 font-mono">{r.sprintDurationWeeks} Weeks Sprint</p>
                </div>
              )
            },
            {
              key: "status",
              header: "Sprint Status",
              className: "text-left",
              render: (r) => (
                <Badge tone={r.status === "In Progress" ? "success" : r.status === "Planned" ? "primary" : "neutral"}>
                  {r.status}
                </Badge>
              )
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Edit</Button>
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* New Resource Plan Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Estimate Sprint Story Points & Resources (MSH-FR-BA-02)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={BarChart3} onClick={handleCreate}>Save Resource Plan</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreate}>
          <Select
            label="Project"
            hint="Pick a project already on file from Requirements Authoring, or add one manually."
            options={projectOptions}
            value={values.projectId}
            onChange={handleProjectSelect}
          />

          <Input
            label="Project Deliverable Title"
            required
            placeholder="e.g. Enterprise Talent Analytics Portal"
            value={values.projectTitle}
            onChange={(e) => setValues((v) => ({ ...v, projectTitle: e.target.value }))}
            error={errors.projectTitle}
            disabled={!!values.projectId}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Client Partner"
              required
              placeholder="e.g. Client company name"
              value={values.client}
              onChange={(e) => setValues((v) => ({ ...v, client: e.target.value }))}
              error={errors.client}
              disabled={!!values.projectId}
            />
            <Select
              label="Target Batch (Capacity Estimate)"
              hint="For planning only — this does not enroll the batch. Trainer confirms the real assignment in Sprint Planning once the sprint kicks off."
              options={[
                { value: "FS-Batch-12 (Full Stack Java)", label: "FS-Batch-12 (Full Stack Java)" },
                { value: "FS-Batch-13 (MERN Agile)", label: "FS-Batch-13 (MERN Agile)" },
                { value: "FS-Batch-14 (AI & Cloud)", label: "FS-Batch-14 (AI & Cloud)" }
              ]}
              value={values.batch}
              onChange={(e) => setValues((v) => ({ ...v, batch: e.target.value }))}
            />
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Estimated Story Points"
              type="number"
              value={values.storyPoints}
              onChange={(e) => setValues((v) => ({ ...v, storyPoints: e.target.value }))}
              hint="Calculates developer hours as Story Points × 6 hrs/point"
            />
            <Input
              label="Sprint Duration (Weeks)"
              type="number"
              value={values.sprintDurationWeeks}
              onChange={(e) => setValues((v) => ({ ...v, sprintDurationWeeks: e.target.value }))}
            />
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Allocated Students"
              type="number"
              value={values.allocatedStudents}
              onChange={(e) => setValues((v) => ({ ...v, allocatedStudents: e.target.value }))}
              hint="Target headcount — pick the actual students below"
            />
            <Input
              label="Lead Staff Developers"
              type="number"
              value={values.allocatedDevs}
              onChange={(e) => setValues((v) => ({ ...v, allocatedDevs: e.target.value }))}
              hint="Target headcount — pick the actual developers below"
            />
          </div>

          <div>
            <p className="text-sm font-medium text-ink-700">Pick Students <span className="text-xs font-normal text-ink-400">({(values.studentIds || []).length}/{values.allocatedStudents || 0} picked)</span></p>
            {availableStudents.length === 0 ? (
              <p className="text-xs text-ink-400 mt-1">No registered students yet — Trainer registers students under Batches.</p>
            ) : (
              <div className="flex flex-col gap-1.5 max-h-40 overflow-y-auto mt-2 border border-border rounded-lg p-2">
                {availableStudents.map((s) => (
                  <label key={s.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 cursor-pointer hover:bg-cream-50 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                      checked={(values.studentIds || []).includes(s.id)}
                      onChange={() => toggleRosterMember(setValues, "studentIds", values.allocatedStudents)(s.id)}
                    />
                    <span className="text-ink-800">{s.name}</span>
                    <span className="text-xs text-ink-400 ml-auto">{s.batch}</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          <div>
            <p className="text-sm font-medium text-ink-700">Pick Developers <span className="text-xs font-normal text-ink-400">({(values.developerIds || []).length}/{values.allocatedDevs || 0} picked)</span></p>
            {availableDevs.length === 0 ? (
              <p className="text-xs text-ink-400 mt-1">No registered developers yet — invite them via Admin's User Management.</p>
            ) : (
              <div className="flex flex-col gap-1.5 max-h-40 overflow-y-auto mt-2 border border-border rounded-lg p-2">
                {availableDevs.map((d) => (
                  <label key={d.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 cursor-pointer hover:bg-cream-50 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                      checked={(values.developerIds || []).includes(d.id)}
                      onChange={() => toggleRosterMember(setValues, "developerIds", values.allocatedDevs)(d.id)}
                    />
                    <span className="text-ink-800">{d.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </form>
      </Modal>

      {/* Edit Plan Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit Sprint Plan & Capacity"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button onClick={handleEditSave}>Save Changes</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Input label="Project Title" required value={editValues.projectTitle} onChange={(e) => setEditValues((v) => ({ ...v, projectTitle: e.target.value }))} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Client Partner" required value={editValues.client} onChange={(e) => setEditValues((v) => ({ ...v, client: e.target.value }))} />
            <Input label="Story Points" type="number" value={editValues.storyPoints} onChange={(e) => setEditValues((v) => ({ ...v, storyPoints: e.target.value }))} />
          </div>
          <div className="grid sm:grid-cols-3 gap-3">
            <Input label="Students" type="number" value={editValues.allocatedStudents} onChange={(e) => setEditValues((v) => ({ ...v, allocatedStudents: e.target.value }))} />
            <Input label="Devs" type="number" value={editValues.allocatedDevs} onChange={(e) => setEditValues((v) => ({ ...v, allocatedDevs: e.target.value }))} />
            <Select
              label="Status"
              options={[{ value: "Planned", label: "Planned" }, { value: "In Progress", label: "In Progress" }, { value: "Completed", label: "Completed" }]}
              value={editValues.status}
              onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))}
            />
          </div>

          <div>
            <p className="text-sm font-medium text-ink-700">Pick Students <span className="text-xs font-normal text-ink-400">({(editValues.studentIds || []).length}/{editValues.allocatedStudents || 0} picked)</span></p>
            {availableStudents.length === 0 ? (
              <p className="text-xs text-ink-400 mt-1">No registered students yet.</p>
            ) : (
              <div className="flex flex-col gap-1.5 max-h-40 overflow-y-auto mt-2 border border-border rounded-lg p-2">
                {availableStudents.map((s) => (
                  <label key={s.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 cursor-pointer hover:bg-cream-50 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                      checked={(editValues.studentIds || []).includes(s.id)}
                      onChange={() => toggleRosterMember(setEditValues, "studentIds", editValues.allocatedStudents)(s.id)}
                    />
                    <span className="text-ink-800">{s.name}</span>
                    <span className="text-xs text-ink-400 ml-auto">{s.batch}</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          <div>
            <p className="text-sm font-medium text-ink-700">Pick Developers <span className="text-xs font-normal text-ink-400">({(editValues.developerIds || []).length}/{editValues.allocatedDevs || 0} picked)</span></p>
            {availableDevs.length === 0 ? (
              <p className="text-xs text-ink-400 mt-1">No registered developers yet.</p>
            ) : (
              <div className="flex flex-col gap-1.5 max-h-40 overflow-y-auto mt-2 border border-border rounded-lg p-2">
                {availableDevs.map((d) => (
                  <label key={d.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 cursor-pointer hover:bg-cream-50 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                      checked={(editValues.developerIds || []).includes(d.id)}
                      onChange={() => toggleRosterMember(setEditValues, "developerIds", editValues.allocatedDevs)(d.id)}
                    />
                    <span className="text-ink-800">{d.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </form>
      </Modal>
    </div>
  );
}