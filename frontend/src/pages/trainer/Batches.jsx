import { useEffect, useState } from "react";
import { Plus, Users, FolderKanban, UserPlus, GraduationCap, Edit } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import EmptyState from "../../components/ui/EmptyState";
import Tabs from "../../components/ui/Tabs";
import { Input, Select } from "../../components/ui/FormField";
import {
  getBatches,
  createBatch,
  getAssignableProjects,
  getBatchProjects,
  assignBatchProjects,
  getAllStudents,
  updateStudentBatch,
  createStudent,
  getStudentsForBatch,
  getPendingAllocations,
} from "../../services/trainerService";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import { validateForm, required, isEmail } from "../../utils/validators";
import { usePagination } from "../../hooks/usePagination";
import Pagination from "../../components/ui/Pagination";
import { useSearchParams } from "react-router-dom";

const TRACKS = ["Full-Stack Development", "Data Analytics", "Product Design", "Backend Engineering"].map((t) => ({ value: t, label: t }));

export default function TrainerBatches() {
  const { user } = useAuth();
  const myUuid = user?.uuid;
  const [activeTab, setActiveTab] = useState("batches");
  // "mine" = batches I'm the PM of (the ones I can actually act on); "all" = the
  // whole org's, read-only. A second PM would otherwise see every batch with
  // action buttons that just error with "you are not PM of this batch".
  const [scope, setScope] = useState("mine");

  // Batches state
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ name: "", track: "", startDate: "", endDate: "", capacity: "", secondaryMentor: "" });
  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  // Assign Projects state
  const [assignBatch, setAssignBatch] = useState(null);
  const [assignableProjects, setAssignableProjects] = useState([]);
  const [checkedProjectIds, setCheckedProjectIds] = useState([]);
  const [assignSaving, setAssignSaving] = useState(false);

  // Students state
  const [students, setStudents] = useState([]);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [studentSearch, setStudentSearch] = useState("");
  const [pendingAllocs, setPendingAllocs] = useState([]);

  // Create Student Modal state
  const [addStudentOpen, setAddStudentOpen] = useState(false);
  const [addStudentSubmitting, setAddStudentSubmitting] = useState(false);
  const [studentValues, setStudentValues] = useState({ name: "", email: "", phone: "", track: "Full-Stack Development", batch: "", password: "Password123" });
  const [studentErrors, setStudentErrors] = useState({});

  // Change Student Batch state
  const [changeBatchStudent, setChangeBatchStudent] = useState(null);
  const [changeBatchValue, setChangeBatchValue] = useState("");
  const [changeBatchSubmitting, setChangeBatchSubmitting] = useState(false);

  // View Batch Students (roster popup, opened from the Batches tab) — pulled
  // live from GET /api/v1/batches/{id}/students.
  const [viewBatch, setViewBatch] = useState(null);
  const [viewBatchRoster, setViewBatchRoster] = useState([]);
  const [rosterLoading, setRosterLoading] = useState(false);

  useEffect(() => {
    if (!viewBatch) {
      setViewBatchRoster([]);
      return;
    }
    setRosterLoading(true);
    getStudentsForBatch(viewBatch.id)
      .then((rows) =>
        setViewBatchRoster(
          rows.map((s) => ({ id: s.userUuid, name: s.name, email: s.email, batch: viewBatch.name, status: s.status }))
        )
      )
      .catch(() => setViewBatchRoster([]))
      .finally(() => setRosterLoading(false));
  }, [viewBatch]);

  const [searchParams] = useSearchParams();
  const query = searchParams.get("search")?.toLowerCase() || "";

  const isMine = (b) => !!myUuid && b.pmUuid === myUuid;
  const mineCount = batches.filter(isMine).length;
  const scopedBatches = scope === "mine" ? batches.filter(isMine) : batches;

  const filteredBatches = scopedBatches.filter(
    (b) => b.name.toLowerCase().includes(query) || b.track.toLowerCase().includes(query)
  );

  const filteredStudents = students.filter(
    (s) =>
      s.name.toLowerCase().includes(studentSearch.toLowerCase()) ||
      s.email.toLowerCase().includes(studentSearch.toLowerCase()) ||
      (s.batch || "Unassigned").toLowerCase().includes(studentSearch.toLowerCase())
  );

  const batchesPagination = usePagination(filteredBatches, 6);
  const studentsPagination = usePagination(filteredStudents, 8);

  const load = () => {
    setLoading(true);
    getBatches()
      .then((b) => setBatches(b))
      .catch(() => setBatches([]))
      .finally(() => setLoading(false));
  };

  const loadStudents = () => {
    setStudentsLoading(true);
    getAllStudents()
      .then(setStudents)
      .finally(() => setStudentsLoading(false));
    getPendingAllocations()
      .then(setPendingAllocs)
      .catch(() => setPendingAllocs([]));
  };

  useEffect(() => {
    load();
    loadStudents();
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], track: [required], startDate: [required], endDate: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setSubmitting(true);
    try {
      await createBatch(values);
      notify("Batch created and ready for student allocation.", { type: "success", title: "Batch created" });
      setModalOpen(false);
      setValues({ name: "", track: "", startDate: "", endDate: "", capacity: "", secondaryMentor: "" });
      load();
    } finally {
      setSubmitting(false);
    }
  };

  const openAssignModal = async (batch) => {
    setAssignBatch(batch);
    setAssignableProjects([]);
    setCheckedProjectIds([]);
    try {
      const [candidates, assigned] = await Promise.all([
        getAssignableProjects(batch.track),
        getBatchProjects(batch.id),
      ]);
      setAssignableProjects(candidates);
      setCheckedProjectIds(assigned.map((p) => p.id));
    } catch (err) {
      notify(err.message || "Couldn't load projects for this batch.", { type: "error" });
    }
  };

  const toggleProject = (id) => {
    setCheckedProjectIds((prev) => (prev.includes(id) ? prev.filter((pid) => pid !== id) : [...prev, id]));
  };

  const saveAssignments = async () => {
    setAssignSaving(true);
    try {
      await assignBatchProjects(assignBatch.id, checkedProjectIds);
      notify(`Projects assigned to ${assignBatch.name} — same track only, so students never see a mismatched project.`, { type: "success", title: "Projects assigned" });
      setAssignBatch(null);
    } catch (err) {
      notify(err.message || "Couldn't save this assignment.", { type: "error" });
    } finally {
      setAssignSaving(false);
    }
  };

  const handleAddStudentSubmit = async (e) => {
    e.preventDefault();
    const validation = validateForm(studentValues, {
      name: [required],
      email: [required, isEmail],
      phone: [required],
      track: [required],
      password: [required],
    });
    setStudentErrors(validation);
    if (Object.keys(validation).length) return;
    
    setAddStudentSubmitting(true);
    try {
      await createStudent({
        name: studentValues.name,
        email: studentValues.email,
        phone: studentValues.phone,
        track: studentValues.track,
        batch: studentValues.batch || null,
        password: studentValues.password,
      });
      notify(`${studentValues.name} registered successfully.`, { type: "success", title: "Student created" });
      setAddStudentOpen(false);
      setStudentValues({ name: "", email: "", phone: "", track: "Full-Stack Development", batch: "", password: "Password123" });
      loadStudents();
      load(); // refresh batches student count
    } catch (err) {
      notify(err.message || "Failed to create student.", { type: "error" });
    } finally {
      setAddStudentSubmitting(false);
    }
  };

  const openChangeBatchModal = (student) => {
    setChangeBatchStudent(student);
    setChangeBatchValue(student.batch || "");
  };

  const handleChangeBatchSubmit = async () => {
    if (!changeBatchStudent) return;
    setChangeBatchSubmitting(true);
    try {
      await updateStudentBatch(changeBatchStudent.id, changeBatchValue);
      notify(`Batch assignment updated for ${changeBatchStudent.name}.`, { type: "success" });
      setChangeBatchStudent(null);
      loadStudents();
      load(); // refresh batches student count
    } catch (err) {
      notify(err.message || "Failed to update batch.", { type: "error" });
    } finally {
      setChangeBatchSubmitting(false);
    }
  };

  // Build options for batches list — filtered to only the batches whose
  // track matches the student in question, so a Full-Stack student never
  // sees a Data Analytics batch (or vice versa) in the picker. Falls back
  // to showing every batch when there's no track to filter by yet (e.g.
  // the picker briefly before a track is chosen).
  const batchOptionsForTrack = (track) => [
    { value: "", label: "Unassigned" },
    ...batches
      .filter((b) => !track || b.track === track)
      .map((b) => ({ value: b.name, label: b.name })),
  ];
  const BATCH_OPTIONS = batchOptionsForTrack(studentValues.track);
  const CHANGE_BATCH_OPTIONS = batchOptionsForTrack(changeBatchStudent?.track);


  return (
    <div>
      <PageHeader
        title="Batches & Students"
        subtitle="Configure cohorts, manage student allocations, and monitor batch health"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Batches" }]}
        action={
          activeTab === "batches" ? (
            <Button icon={Plus} onClick={() => setModalOpen(true)}>New Batch</Button>
          ) : (
            <Button icon={UserPlus} onClick={() => setAddStudentOpen(true)}>Add Student</Button>
          )
        }
      />

      <Card>
        <Tabs
          tabs={[
            { key: "batches", label: "Batches", icon: Users },
            { key: "students", label: "Students", icon: GraduationCap },
          ]}
          defaultTab={activeTab}
          onChange={setActiveTab}
        >
          {(active) => active === "batches" ? (
            <>
              <div className="flex items-center gap-1 mb-4 w-fit rounded-lg border border-border bg-cream-50 p-0.5">
                {[
                  { key: "mine", label: `My batches (${mineCount})` },
                  { key: "all", label: `All batches (${batches.length})` },
                ].map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    onClick={() => setScope(t.key)}
                    className={`text-xs font-semibold px-3 py-1.5 rounded-md transition-colors ${
                      scope === t.key ? "bg-white text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-700"
                    }`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
              {scope === "all" && (
                <p className="text-xs text-ink-500 mb-3">
                  Showing every cohort for visibility. You can only edit batches where you're the PM — the rest are read-only.
                </p>
              )}
              <Table
                loading={loading}
                data={batchesPagination.pageItems}
                columns={[
                  { key: "name", header: "Batch", render: (r) => (
                    <div>
                      <p className="font-semibold text-ink-900">{r.name}</p>
                      {r.secondaryMentor && (
                        <p className="text-[10px] text-ink-400 font-medium">Mentor: {r.secondaryMentor}</p>
                      )}
                    </div>
                  ) },
                  { key: "pm", header: "PM", render: (r) => (
                    isMine(r)
                      ? <Badge tone="primary">You</Badge>
                      : <span className="text-sm text-ink-600">{r.pmName || "—"}</span>
                  ) },
                  { key: "track", header: "Track" },
                  { key: "students", header: "Students", render: (r) => (
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); setViewBatch(r); }}
                      className="flex items-center gap-1 text-primary-700 hover:underline cursor-pointer"
                    >
                      <Users size={13} className="text-ink-400" /> {r.students}
                    </button>
                  ) },
                  { key: "dates", header: "Duration", render: (r) => `${r.startDate} → ${r.endDate}` },
                  { key: "health", header: "Health", render: (r) => r.health === null
                    ? <Badge tone="neutral">No activity yet</Badge>
                    : <div className="w-28"><ProgressBar value={r.health} tone={r.health > 80 ? "success" : r.health > 50 ? "gold" : "warning"} showValue={false} size="sm" /></div> },
                  { key: "status", header: "Status", render: (r) => <Badge tone={r.status === "Active" ? "success" : r.status === "Onboarding" ? "neutral" : "gold"}>{r.status}</Badge> },
                  { key: "action", header: "", render: (r) => (
                    isMine(r)
                      ? <Button size="sm" variant="secondary" icon={FolderKanban} onClick={() => openAssignModal(r)}>Assign Projects</Button>
                      : <span className="text-xs text-ink-400">Read-only</span>
                  ) },
                ]}
              />
              <Pagination page={batchesPagination.page} totalPages={batchesPagination.totalPages} onChange={batchesPagination.goTo} totalItems={batchesPagination.totalItems} pageSize={batchesPagination.pageSize} />
            </>
          ) : (
            <div>
              {pendingAllocs.length > 0 && (
                <div className="mb-5 rounded-lg border border-warning-200 bg-warning-50/60 p-4">
                  <p className="text-sm font-semibold text-warning-700 mb-1">
                    Assignment pending — {pendingAllocs.length} paid student{pendingAllocs.length > 1 ? "s" : ""} with no matching batch yet
                  </p>
                  <p className="text-xs text-ink-500 mb-3">
                    They're placed automatically the moment you create a batch for their track. You can also add them to a batch by hand.
                  </p>
                  <Table
                    data={pendingAllocs}
                    columns={[
                      { key: "name", header: "Student", render: (r) => (
                        <div>
                          <p className="font-semibold text-ink-900">{r.name}</p>
                          <p className="text-xs text-ink-400">{r.email}</p>
                        </div>
                      )},
                      { key: "track", header: "Track", render: (r) => <span className="text-sm text-ink-600">{r.track}</span> },
                      { key: "plan", header: "Plan", render: (r) => <Badge tone="neutral">{r.plan}</Badge> },
                      { key: "requestedAt", header: "Since", render: (r) => <span className="text-xs text-ink-500">{r.requestedAt}</span> },
                    ]}
                  />
                </div>
              )}
              <div className="flex flex-col sm:flex-row gap-3 mb-4">
                <Input placeholder="Search students by name, email or batch…" value={studentSearch} onChange={(e) => setStudentSearch(e.target.value)} className="sm:max-w-md" />
              </div>
              <Table
                loading={studentsLoading}
                data={studentsPagination.pageItems}
                columns={[
                  { key: "name", header: "Student", render: (r) => (
                    <div>
                      <p className="font-semibold text-ink-900">{r.name}</p>
                      <p className="text-xs text-ink-400">{r.phone}</p>
                    </div>
                  )},
                  { key: "email", header: "Email", render: (r) => <span className="text-sm text-ink-600">{r.email}</span> },
                  { key: "track", header: "Track", render: (r) => <span className="text-sm text-ink-600">{r.track || "Full-Stack Development"}</span> },
                  { key: "batch", header: "Assigned Batch", render: (r) => r.batch ? <Badge tone="success">{r.batch}</Badge> : <Badge tone="warning">Unassigned</Badge> },
                  { key: "action", header: "", render: (r) => (
                    <Button size="sm" variant="secondary" icon={Edit} onClick={() => openChangeBatchModal(r)}>Change Batch</Button>
                  )},
                ]}
              />
              <Pagination page={studentsPagination.page} totalPages={studentsPagination.totalPages} onChange={studentsPagination.goTo} totalItems={studentsPagination.totalItems} pageSize={studentsPagination.pageSize} />
            </div>
          )}
        </Tabs>
      </Card>

      {/* Create New Batch Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create New Batch"
        description="Define batch name, track, and schedule."
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button loading={submitting} onClick={submit}>Create Batch</Button>
        </>}
      >
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <Input label="Batch name" required placeholder="FS-Batch-15" value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <Select label="Track" required placeholder="Select track" options={TRACKS} value={values.track} onChange={(e) => setValues((v) => ({ ...v, track: e.target.value }))} error={errors.track} />
          <div className="grid grid-cols-2 gap-4">
            <Input label="Start date" type="date" required value={values.startDate} onChange={(e) => setValues((v) => ({ ...v, startDate: e.target.value }))} error={errors.startDate} />
            <Input label="End date" type="date" required value={values.endDate} onChange={(e) => setValues((v) => ({ ...v, endDate: e.target.value }))} error={errors.endDate} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Student capacity" type="number" placeholder="30" value={values.capacity} onChange={(e) => setValues((v) => ({ ...v, capacity: e.target.value }))} />
            <Input label="Secondary Mentor" placeholder="e.g. John Smith" value={values.secondaryMentor} onChange={(e) => setValues((v) => ({ ...v, secondaryMentor: e.target.value }))} />
          </div>
        </form>
      </Modal>

      {/* Assign Projects Modal */}
      <Modal
        open={!!assignBatch}
        onClose={() => setAssignBatch(null)}
        title="Assign Projects"
        description={assignBatch ? `Curate published, ${assignBatch.track || "same-track"} projects for ${assignBatch.name}'s own screen — every published project is still visible to every student either way.` : ""}
        footer={<>
          <Button variant="secondary" onClick={() => setAssignBatch(null)}>Cancel</Button>
          <Button loading={assignSaving} onClick={saveAssignments}>Assign Projects</Button>
        </>}
      >
        {assignableProjects.length === 0 ? (
          <EmptyState
            icon={FolderKanban}
            title="No published projects on this track yet"
            description={`Ask a Developer to author and publish a ${assignBatch?.track || ""} project before you can assign it here.`}
          />
        ) : (
          <div className="flex flex-col gap-2 max-h-80 overflow-y-auto">
            {assignableProjects.map((p) => (
              <label key={p.id} className="flex items-start gap-3 rounded-lg border border-border px-3 py-2.5 cursor-pointer hover:bg-cream-50">
                <input
                  type="checkbox"
                  className="mt-1 h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                  checked={checkedProjectIds.includes(p.id)}
                  onChange={() => toggleProject(p.id)}
                />
                <div>
                  <p className="text-sm font-medium text-ink-900">{p.title}</p>
                  <p className="text-xs text-ink-500">{p.difficulty}{p.domain ? ` · ${p.domain}` : ""}</p>
                </div>
              </label>
            ))}
          </div>
        )}
      </Modal>

      {/* Add Student Modal */}
      <Modal
        open={addStudentOpen}
        onClose={() => setAddStudentOpen(false)}
        title="Add Student"
        description="Register a student and assign them to a batch directly."
        footer={<>
          <Button variant="secondary" onClick={() => setAddStudentOpen(false)}>Cancel</Button>
          <Button loading={addStudentSubmitting} onClick={handleAddStudentSubmit}>Add Student</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={handleAddStudentSubmit}>
          <Input label="Full name" required placeholder="Jane Doe" value={studentValues.name} onChange={(e) => setStudentValues((v) => ({ ...v, name: e.target.value }))} error={studentErrors.name} />
          <Input label="Email address" required type="email" placeholder="jane@example.com" value={studentValues.email} onChange={(e) => setStudentValues((v) => ({ ...v, email: e.target.value }))} error={studentErrors.email} />
          <Input label="Phone number" required placeholder="+91 98765 43210" value={studentValues.phone} onChange={(e) => setStudentValues((v) => ({ ...v, phone: e.target.value }))} error={studentErrors.phone} />
          <Select
            label="Learning Track"
            required
            options={TRACKS}
            value={studentValues.track}
            onChange={(e) => setStudentValues((v) => ({ ...v, track: e.target.value, batch: "" }))}
            error={studentErrors.track}
          />
          <Select
            label="Assign to Batch"
            hint={studentValues.track ? `Showing only ${studentValues.track} batches` : "Pick a Learning Track first to see matching batches"}
            options={BATCH_OPTIONS}
            value={studentValues.batch}
            onChange={(e) => setStudentValues((v) => ({ ...v, batch: e.target.value }))}
          />
          <Input label="Initial Password" required type="password" value={studentValues.password} onChange={(e) => setStudentValues((v) => ({ ...v, password: e.target.value }))} error={studentErrors.password} />
        </form>
      </Modal>

      {/* Change Batch Modal */}
      <Modal
        open={!!changeBatchStudent}
        onClose={() => setChangeBatchStudent(null)}
        title="Change Batch Allocation"
        description={changeBatchStudent ? `Reallocate ${changeBatchStudent.name} to a different cohort.` : ""}
        footer={<>
          <Button variant="secondary" onClick={() => setChangeBatchStudent(null)}>Cancel</Button>
          <Button loading={changeBatchSubmitting} onClick={handleChangeBatchSubmit}>Update Batch</Button>
        </>}
      >
        <div className="flex flex-col gap-4 text-left">
          <Select
            label="Cohort/Batch"
            hint={changeBatchStudent?.track ? `Showing only ${changeBatchStudent.track} batches — matches this student's registered track` : undefined}
            options={CHANGE_BATCH_OPTIONS}
            value={changeBatchValue}
            onChange={(e) => setChangeBatchValue(e.target.value)}
          />
        </div>
      </Modal>
      {/* View Batch Students */}
      <Modal
        open={!!viewBatch}
        onClose={() => setViewBatch(null)}
        title={viewBatch ? `Students in ${viewBatch.name}` : "Students"}
        description={viewBatch ? `${viewBatch.track} · ${viewBatchRoster.length} student${viewBatchRoster.length === 1 ? "" : "s"}` : ""}
        footer={<Button variant="secondary" onClick={() => setViewBatch(null)}>Close</Button>}
      >
        {rosterLoading ? (
          <p className="text-sm text-ink-400 py-8 text-center">Loading roster…</p>
        ) : viewBatchRoster.length === 0 ? (
          <EmptyState
            icon={GraduationCap}
            title="No students enrolled yet"
            description="Students are enrolled via payment/allocation or an admin add."
          />
        ) : (
          <div className="flex flex-col gap-2 max-h-80 overflow-y-auto">
            {viewBatchRoster.map((s) => (
              <div key={s.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
                <div>
                  <p className="text-sm font-semibold text-ink-900">{s.name}</p>
                  <p className="text-xs text-ink-500">{s.email}</p>
                </div>
                <span className="text-xs text-ink-400">{s.status}</span>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
}