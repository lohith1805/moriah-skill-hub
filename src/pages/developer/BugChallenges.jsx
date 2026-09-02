import { useEffect, useState } from "react";
import { Bug, Plus, Edit, Trash2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ProgressBar from "../../components/ui/ProgressBar";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getDevBugChallenges,
  createBugChallenge,
  updateBugChallenge,
  deleteBugChallenge,
} from "../../services/developerService";

const DIFFICULTIES = ["Beginner", "Intermediate", "Advanced"].map((d) => ({ value: d, label: d }));

const emptyForm = {
  title: "",
  project: "",
  difficulty: "",
  description: "",
  functionName: "",
  starterCode: "",
  tc1Name: "", tc1Args: "", tc1Expected: "",
  tc2Name: "", tc2Args: "", tc2Expected: "",
};

export default function DeveloperBugChallenges() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [projectsList, setProjectsList] = useState([]);

  // Create
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  // Edit
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState(emptyForm);

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getDevBugChallenges().then((data) => { setItems(data); setLoading(false); });
  };

  useEffect(() => {
    load();
    const savedProjects = localStorage.getItem("msh_developer_projects");
    if (savedProjects) setProjectsList(JSON.parse(savedProjects));
  }, []);

  const projectOptions = [
    { value: "NimbusCart", label: "NimbusCart" },
    { value: "FinLedger", label: "FinLedger" },
    { value: "PulseCRM", label: "PulseCRM" },
    ...projectsList.map((p) => ({ value: p.title, label: p.title })),
  ];

  const parseJsonField = (raw, fallback) => {
    if (!raw || !raw.trim()) return fallback;
    try { return JSON.parse(raw); } catch (e) { return undefined; }
  };

  // Builds the testCases array from the raw form fields, or sets a form
  // error and returns null if a JSON field doesn't parse.
  const buildTestCases = (form, setErrs) => {
    const testCases = [];
    const tc1Args = parseJsonField(form.tc1Args, undefined);
    const tc1Expected = parseJsonField(form.tc1Expected, undefined);
    if (tc1Args === undefined || tc1Expected === undefined) {
      setErrs((e) => ({ ...e, tc1Args: "Must be valid JSON, e.g. [5, 3] or [\"Save\", true]" }));
      return null;
    }
    testCases.push({ name: form.tc1Name, args: tc1Args, expected: tc1Expected });

    if (form.tc2Name.trim()) {
      const tc2Args = parseJsonField(form.tc2Args, undefined);
      const tc2Expected = parseJsonField(form.tc2Expected, undefined);
      if (tc2Args === undefined || tc2Expected === undefined) {
        setErrs((e) => ({ ...e, tc2Args: "Must be valid JSON, e.g. [2, 0] or [\"Submit\", false]" }));
        return null;
      }
      testCases.push({ name: form.tc2Name, args: tc2Args, expected: tc2Expected });
    }
    return testCases;
  };

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, {
      title: [required], project: [required], difficulty: [required],
      functionName: [required], starterCode: [required],
      tc1Name: [required], tc1Args: [required], tc1Expected: [required],
    });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const testCases = buildTestCases(values, setErrors);
    if (!testCases) return;

    setSaving(true);
    try {
      await createBugChallenge({
        title: values.title,
        project: values.project,
        difficulty: values.difficulty,
        description: values.description,
        functionName: values.functionName,
        starterCode: values.starterCode,
        testCases,
      });
      notify("Bug challenge created — real broken code, auto-graded on the Student side.", { type: "success", title: "Challenge Created" });
      setModalOpen(false);
      setValues(emptyForm);
      load();
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (c) => {
    setEditingId(c.id);
    setEditValues({
      title: c.title,
      project: c.project,
      difficulty: c.difficulty,
      description: c.description || "",
      functionName: c.functionName || "",
      starterCode: c.starterCode || "",
      tc1Name: c.testCases?.[0]?.name || "",
      tc1Args: c.testCases?.[0] ? JSON.stringify(c.testCases[0].args) : "",
      tc1Expected: c.testCases?.[0] ? JSON.stringify(c.testCases[0].expected) : "",
      tc2Name: c.testCases?.[1]?.name || "",
      tc2Args: c.testCases?.[1] ? JSON.stringify(c.testCases[1].args) : "",
      tc2Expected: c.testCases?.[1] ? JSON.stringify(c.testCases[1].expected) : "",
    });
    setErrors({});
    setEditModalOpen(true);
  };

  const onEditSave = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, {
      title: [required], project: [required], difficulty: [required],
      functionName: [required], starterCode: [required],
      tc1Name: [required], tc1Args: [required], tc1Expected: [required],
    });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const testCases = buildTestCases(editValues, setErrors);
    if (!testCases) return;

    await updateBugChallenge(editingId, {
      title: editValues.title,
      project: editValues.project,
      difficulty: editValues.difficulty,
      description: editValues.description,
      functionName: editValues.functionName,
      starterCode: editValues.starterCode,
      testCases,
    });
    notify("Bug challenge updated successfully.", { type: "success", title: "Challenge Updated" });
    setEditModalOpen(false);
    setEditingId(null);
    load();
  };

  const handleDelete = async (id) => {
    const target = items.find((c) => c.id === id);
    await deleteBugChallenge(id);
    notify(`Bug challenge "${target?.title}" deleted successfully.`, { type: "success", title: "Challenge Deleted" });
    load();
  };

  const testCaseFields = (form, setForm) => (
    <>
      <div className="rounded-lg border border-border p-3 bg-cream-50 flex flex-col gap-3">
        <p className="text-xs font-semibold text-ink-700">Test case 1 (required — defines when the bug is "fixed")</p>
        <Input label="Name" value={form.tc1Name} onChange={(e) => setForm((v) => ({ ...v, tc1Name: e.target.value }))} error={errors.tc1Name} required placeholder="e.g. Handles empty cart" />
        <div className="grid sm:grid-cols-2 gap-3">
          <Input label="Arguments (JSON array)" value={form.tc1Args} onChange={(e) => setForm((v) => ({ ...v, tc1Args: e.target.value }))} error={errors.tc1Args} required placeholder="[[]]" />
          <Input label="Expected result (JSON)" value={form.tc1Expected} onChange={(e) => setForm((v) => ({ ...v, tc1Expected: e.target.value }))} error={errors.tc1Expected} required placeholder="0" />
        </div>
      </div>
      <div className="rounded-lg border border-border p-3 bg-cream-50 flex flex-col gap-3">
        <p className="text-xs font-semibold text-ink-700">Test case 2 (optional)</p>
        <Input label="Name" value={form.tc2Name} onChange={(e) => setForm((v) => ({ ...v, tc2Name: e.target.value }))} placeholder="e.g. Handles negative prices" />
        <div className="grid sm:grid-cols-2 gap-3">
          <Input label="Arguments (JSON array)" value={form.tc2Args} onChange={(e) => setForm((v) => ({ ...v, tc2Args: e.target.value }))} error={errors.tc2Args} placeholder='[[{"price":-5}]]' />
          <Input label="Expected result (JSON)" value={form.tc2Expected} onChange={(e) => setForm((v) => ({ ...v, tc2Expected: e.target.value }))} placeholder="0" />
        </div>
      </div>
    </>
  );

  return (
    <div>
      <PageHeader
        title="Bug Fixing Challenges"
        subtitle="Author real broken code and test cases — students fix it in a live editor and get auto-graded"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Bug Challenges" }]}
        action={<Button icon={Plus} onClick={() => { setErrors({}); setValues(emptyForm); setModalOpen(true); }}>New Challenge</Button>}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading challenges…" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {items.map((c) => (
            <Card key={c.id} className="flex flex-col justify-between min-h-[170px]">
              <div>
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-error-50 text-error-500"><Bug size={16} /></div>
                    <div>
                      <p className="font-medium text-ink-900 text-left">{c.title}</p>
                      <p className="text-xs text-ink-500 text-left">{c.project} · <code>{c.functionName}</code></p>
                    </div>
                  </div>
                  <Badge tone={c.difficulty === "Advanced" ? "error" : "success"}>{c.difficulty}</Badge>
                </div>
                <div className="mt-4">
                  <ProgressBar value={c.attempts > 0 ? Math.round((c.solved / c.attempts) * 100) : 0} tone="primary" label={`${c.solved}/${c.attempts} solved · auto-tracked from student attempts`} />
                </div>
              </div>
              <div className="flex gap-2 mt-4 pt-3 border-t border-border/60 justify-end">
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(c)}>Edit</Button>
                <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(c.id)}>Delete</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create Bug Challenge"
        size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button loading={saving} onClick={submit}>Create</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Input label="Challenge title" required value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} placeholder="e.g. Buffer overflow in parsing util" />
          <Select label="Project" required placeholder="Select project" options={projectOptions} value={values.project} onChange={(e) => setValues((v) => ({ ...v, project: e.target.value }))} error={errors.project} />
          <Select label="Difficulty" required placeholder="Select difficulty" options={DIFFICULTIES} value={values.difficulty} onChange={(e) => setValues((v) => ({ ...v, difficulty: e.target.value }))} error={errors.difficulty} />
          <Textarea label="Bug description (shown to student)" value={values.description} onChange={(e) => setValues((v) => ({ ...v, description: e.target.value }))} rows={2} placeholder="e.g. This function is supposed to total the cart, but returns NaN when the cart is empty." />
          <Input label="Function name" required value={values.functionName} onChange={(e) => setValues((v) => ({ ...v, functionName: e.target.value }))} error={errors.functionName} placeholder="e.g. calculateCartTotal" />
          <Textarea label="Starter code (the broken version students see)" required value={values.starterCode} onChange={(e) => setValues((v) => ({ ...v, starterCode: e.target.value }))} error={errors.starterCode} rows={5}
            placeholder={"function calculateCartTotal(items) {\n  // Bug: crashes / returns wrong value — students fix this\n}"} />
          {testCaseFields(values, setValues)}
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Bug Challenge"
        size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setEditModalOpen(false)}>Cancel</Button>
          <Button onClick={onEditSave}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onEditSave}>
          <Input label="Challenge title" required value={editValues.title} onChange={(e) => setEditValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} />
          <Select label="Project" required placeholder="Select project" options={projectOptions} value={editValues.project} onChange={(e) => setEditValues((v) => ({ ...v, project: e.target.value }))} error={errors.project} />
          <Select label="Difficulty" required placeholder="Select difficulty" options={DIFFICULTIES} value={editValues.difficulty} onChange={(e) => setEditValues((v) => ({ ...v, difficulty: e.target.value }))} error={errors.difficulty} />
          <Textarea label="Bug description (shown to student)" value={editValues.description} onChange={(e) => setEditValues((v) => ({ ...v, description: e.target.value }))} rows={2} />
          <Input label="Function name" required value={editValues.functionName} onChange={(e) => setEditValues((v) => ({ ...v, functionName: e.target.value }))} error={errors.functionName} />
          <Textarea label="Starter code (the broken version students see)" required value={editValues.starterCode} onChange={(e) => setEditValues((v) => ({ ...v, starterCode: e.target.value }))} error={errors.starterCode} rows={5} />
          {testCaseFields(editValues, setEditValues)}
          <p className="text-xs text-ink-400 -mt-1">Solved/attempts are tracked automatically from real student submissions and can no longer be edited here.</p>
        </form>
      </Modal>
    </div>
  );
}
