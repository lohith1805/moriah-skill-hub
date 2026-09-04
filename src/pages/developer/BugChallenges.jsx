import { useEffect, useState } from "react";
import { Bug, Plus, Edit, Trash2, Download, FileCode2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getProjects,
  getProjectChallenges,
  createChallenge,
  updateChallenge,
  deleteChallenge,
} from "../../services/developerService";

const DIFFICULTIES = ["Beginner", "Intermediate", "Advanced"].map((d) => ({ value: d, label: d }));
const emptyForm = { title: "", expectedBehaviour: "", difficulty: "" };

export default function DeveloperBugChallenges() {
  const { notify } = useToast();
  const [projects, setProjects] = useState([]);
  const [projectId, setProjectId] = useState("");
  const [challenges, setChallenges] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingChallenges, setLoadingChallenges] = useState(false);

  // Create
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState(emptyForm);
  const [brokenFile, setBrokenFile] = useState([]);
  const [testFile, setTestFile] = useState([]);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});

  // Edit
  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState(emptyForm);
  const [savingEdit, setSavingEdit] = useState(false);

  useEffect(() => {
    getProjects()
      .then((p) => {
        setProjects(p);
        if (p.length) setProjectId(String(p[0].id));
      })
      .catch(() => notify("Couldn't load projects.", { type: "error" }))
      .finally(() => setLoadingProjects(false));
  }, [notify]);

  const loadChallenges = (id) => {
    if (!id) return;
    setLoadingChallenges(true);
    getProjectChallenges(id)
      .then(setChallenges)
      .catch(() => notify("Couldn't load this project's challenges.", { type: "error" }))
      .finally(() => setLoadingChallenges(false));
  };

  useEffect(() => {
    loadChallenges(projectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const projectName = projects.find((p) => String(p.id) === String(projectId))?.title || "";

  const openCreate = () => {
    setValues(emptyForm);
    setBrokenFile([]);
    setTestFile([]);
    setErrors({});
    setModalOpen(true);
  };

  const submit = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { title: [required], expectedBehaviour: [required], difficulty: [required] });
    if (!(brokenFile[0] instanceof File)) v.brokenFile = "Attach the broken-code file students will fix.";
    setErrors(v);
    if (Object.keys(v).length) return;

    setSaving(true);
    try {
      await createChallenge(projectId, {
        title: values.title.trim(),
        expectedBehaviour: values.expectedBehaviour.trim(),
        difficulty: values.difficulty,
        brokenCodeFile: brokenFile[0],
        testScriptFile: testFile[0] instanceof File ? testFile[0] : undefined,
      });
      notify("Bug challenge added to this project.", { type: "success" });
      setModalOpen(false);
      loadChallenges(projectId);
    } catch (err) {
      notify(err?.message || "Couldn't create the challenge.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (c) => {
    setEditingId(c.id);
    setEditValues({ title: c.title, expectedBehaviour: c.expectedBehaviour || "", difficulty: c.difficulty || "" });
    setEditOpen(true);
  };

  const saveEdit = async (e) => {
    e.preventDefault();
    setSavingEdit(true);
    try {
      await updateChallenge(editingId, {
        title: editValues.title.trim(),
        expectedBehaviour: editValues.expectedBehaviour.trim(),
        difficulty: editValues.difficulty || undefined,
      });
      notify("Challenge updated.", { type: "success" });
      setEditOpen(false);
      loadChallenges(projectId);
    } catch (err) {
      notify(err?.message || "Couldn't update the challenge.", { type: "error" });
    } finally {
      setSavingEdit(false);
    }
  };

  const remove = async (c) => {
    try {
      await deleteChallenge(c.id);
      notify(`"${c.title}" deleted.`, { type: "success" });
      loadChallenges(projectId);
    } catch (err) {
      notify(err?.message || "Couldn't delete the challenge.", { type: "error" });
    }
  };

  return (
    <div>
      <PageHeader
        title="Bug Fixing Challenges"
        subtitle="Attach broken-code files to a project — students download, fix, and submit"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Bug Challenges" }]}
        action={
          <div className="flex items-center gap-2">
            <Select
              className="w-56"
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              options={projects.map((p) => ({ value: String(p.id), label: `${p.title}${p.status === "Published" ? "" : " (draft)"}` }))}
              placeholder={projects.length ? "Select a project" : "No projects"}
            />
            <Button icon={Plus} onClick={openCreate} disabled={!projectId}>New Challenge</Button>
          </div>
        }
      />

      {loadingProjects ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading…" /></div>
      ) : !projectId ? (
        <EmptyState icon={Bug} title="No projects yet" description="Create a project first, then attach bug-fix challenges to it." />
      ) : loadingChallenges ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading challenges…" /></div>
      ) : challenges.length === 0 ? (
        <EmptyState icon={Bug} title={`No challenges on ${projectName}`} description="Attach a broken-code file for students to fix." actionLabel="New Challenge" onAction={openCreate} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {challenges.map((c) => (
            <Card key={c.id} className="flex flex-col justify-between min-h-[170px]">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-error-50 text-error-500"><Bug size={16} /></div>
                    <p className="font-medium text-ink-900 text-left">{c.title}</p>
                  </div>
                  {c.difficulty && <Badge tone={c.difficulty === "Advanced" ? "error" : c.difficulty === "Intermediate" ? "warning" : "success"}>{c.difficulty}</Badge>}
                </div>
                {c.expectedBehaviour && <p className="text-xs text-ink-500 text-left mt-2">{c.expectedBehaviour}</p>}
                <div className="flex flex-wrap gap-3 mt-3 text-xs">
                  {c.brokenCodeUrl && (
                    <a href={c.brokenCodeUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline">
                      <FileCode2 size={12} /> Broken code
                    </a>
                  )}
                  {c.testScriptUrl && (
                    <a href={c.testScriptUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline">
                      <Download size={12} /> Test script
                    </a>
                  )}
                </div>
              </div>
              <div className="flex gap-2 mt-4 pt-3 border-t border-border/60 justify-end">
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(c)}>Edit</Button>
                <Button size="sm" variant="danger" icon={Trash2} onClick={() => remove(c)}>Delete</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Create */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={`New challenge — ${projectName}`}
        size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button loading={saving} onClick={submit}>Create</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Input label="Challenge title" required value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} placeholder="e.g. calculateCartTotal returns NaN on empty cart" />
          <Select label="Difficulty" required placeholder="Select difficulty" options={DIFFICULTIES} value={values.difficulty} onChange={(e) => setValues((v) => ({ ...v, difficulty: e.target.value }))} error={errors.difficulty} />
          <Textarea label="Expected behaviour (shown to the student)" required rows={3} value={values.expectedBehaviour} onChange={(e) => setValues((v) => ({ ...v, expectedBehaviour: e.target.value }))} error={errors.expectedBehaviour} placeholder="Describe what the code should do once fixed." />
          <FileUpload label="Broken code file" hint="The buggy source the student downloads and fixes (max 10MB)" onChange={setBrokenFile} />
          {errors.brokenFile && <p className="text-xs text-error-600 -mt-2">{errors.brokenFile}</p>}
          <FileUpload label="Test script (optional)" hint="A script the student can run to check their fix" onChange={setTestFile} />
        </form>
      </Modal>

      {/* Edit */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit challenge"
        footer={<>
          <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button loading={savingEdit} onClick={saveEdit}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={saveEdit}>
          <Input label="Challenge title" required value={editValues.title} onChange={(e) => setEditValues((v) => ({ ...v, title: e.target.value }))} />
          <Select label="Difficulty" placeholder="Select difficulty" options={DIFFICULTIES} value={editValues.difficulty} onChange={(e) => setEditValues((v) => ({ ...v, difficulty: e.target.value }))} />
          <Textarea label="Expected behaviour" rows={3} value={editValues.expectedBehaviour} onChange={(e) => setEditValues((v) => ({ ...v, expectedBehaviour: e.target.value }))} />
          <p className="text-xs text-ink-400 -mt-1">The broken-code and test-script files can't be changed after upload — delete and re-create the challenge to swap them.</p>
        </form>
      </Modal>
    </div>
  );
}
