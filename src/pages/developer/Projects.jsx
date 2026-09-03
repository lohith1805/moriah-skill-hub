import { useEffect, useState } from "react";
import { Plus, Layers, Send, Edit, GitFork, FileCode, Video, HelpCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Textarea, Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getProjects, createProject, updateProject, publishProject } from "../../services/developerService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

const DIFFICULTIES = ["Beginner", "Intermediate", "Advanced"].map((d) => ({ value: d, label: d }));
const STATUS_OPTIONS = [
  { value: "Draft", label: "Draft" },
  { value: "Published", label: "Published" }
];

const emptyProjectValues = {
  title: "",
  stack: "",
  difficulty: "Intermediate",
  description: "",
  files: [],
  starterRepo: "",
  referenceSolution: "",
  swaggerSpec: "",
  erDiagram: "",
  videoTutorial: "",
  readmeContent: ""
};

export default function DeveloperProjects() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // States for new project
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState(emptyProjectValues);
  
  // States for editing project
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ ...emptyProjectValues, status: "" });
  
  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const reload = () =>
    getProjects()
      .then((p) => setProjects(p.map((proj) => ({ ...proj, files: [] }))))
      .catch(() => setProjects([]))
      .finally(() => setLoading(false));

  useEffect(() => {
    reload();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { title: [required], stack: [required], difficulty: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setSubmitting(true);
    try {
      await createProject(values);
      notify("Project created as a draft.", { type: "success", title: "Project created" });
      setModalOpen(false);
      setValues(emptyProjectValues);
      await reload();
    } catch (err) {
      notify(err.message || "Could not create the project.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const publish = async (id) => {
    try {
      await publishProject(id);
      notify("Project published — it can now be attached to a task.", { type: "success" });
      await reload();
    } catch (err) {
      notify(err.message || "Could not publish the project.", { type: "error" });
    }
  };

  const openEdit = (p) => {
    setEditingId(p.id);
    setEditValues({
      title: p.title,
      stack: p.stack.join(", "),
      difficulty: p.difficulty,
      description: p.description || "",
      status: p.status,
      files: p.files || [],
      starterRepo: p.starterRepo || "",
      referenceSolution: p.referenceSolution || "",
      swaggerSpec: p.swaggerSpec || "",
      erDiagram: p.erDiagram || "",
      videoTutorial: p.videoTutorial || "",
      readmeContent: p.readmeContent || ""
    });
    setEditModalOpen(true);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { title: [required], stack: [required], difficulty: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    try {
      await updateProject(editingId, editValues);
      notify("Project updated.", { type: "success", title: "Project Updated" });
      setEditModalOpen(false);
      setEditingId(null);
      await reload();
    } catch (err) {
      notify(err.message || "Could not update the project.", { type: "error" });
    }
  };

  return (
    <div>
      <PageHeader
        title="Practice Projects"
        subtitle="Author reference projects for student batches — created as a DRAFT, then published"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Projects" }]}
        action={<Button icon={Plus} onClick={() => setModalOpen(true)}>New Project</Button>}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading projects…" /></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-left">
          {projects.map((p) => (
            <Card key={p.id} className="flex flex-col justify-between min-h-[180px]">
              <div>
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-50 text-primary-700"><Layers size={16} /></div>
                    <div>
                      <p className="font-semibold text-ink-900 leading-snug">{p.title}</p>
                      <p className="text-xs text-ink-500 mt-0.5">{p.version} · {p.difficulty}</p>
                    </div>
                  </div>
                  <Badge tone={p.status === "Published" ? "success" : "neutral"}>{p.status}</Badge>
                </div>
                <div className="flex flex-wrap gap-1.5 mt-3.5">
                  {p.stack.map((s) => <Badge key={s} tone="primary">{s}</Badge>)}
                </div>
                {p.starterRepo && (
                  <p className="text-[10px] font-mono text-ink-400 mt-3 truncate bg-cream-50 p-1.5 rounded border border-border/40">
                    Repo: {p.starterRepo}
                  </p>
                )}
              </div>
              <div className="flex gap-2 mt-4 pt-3 border-t border-border/60">
                {p.status === "Draft" && (
                  <Button size="sm" variant="secondary" icon={Send} onClick={() => publish(p.id)}>Publish</Button>
                )}
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(p)}>Edit</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Author New Project Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Author Industry Project Architecture"
        size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button loading={submitting} onClick={submit}>Save Project Draft</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <div className="grid sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <Input label="Project title" required placeholder="e.g. NimbusCart — E-commerce Microservices" value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} />
            </div>
            <Select label="Difficulty" required placeholder="Select difficulty" options={DIFFICULTIES} value={values.difficulty} onChange={(e) => setValues((v) => ({ ...v, difficulty: e.target.value }))} error={errors.difficulty} />
          </div>
          
          <Input label="Tech stack (Comma-separated)" required placeholder="React, Node.js, PostgreSQL, Docker" value={values.stack} onChange={(e) => setValues((v) => ({ ...v, stack: e.target.value }))} error={errors.stack} />
          <Textarea label="Description & milestone objectives" value={values.description} onChange={(e) => setValues((v) => ({ ...v, description: e.target.value }))} rows={2} />
          
          {/* FRS-DEV-02 Repository Starters */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><GitFork size={13} /> Source Code Repositories</h4>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input label="Boilerplate Starter Repo URL" placeholder="https://github.com/myorg/starter-kit" value={values.starterRepo} onChange={(e) => setValues((v) => ({ ...v, starterRepo: e.target.value }))} />
              <Input label="Reference Solution Branch URL" placeholder="https://github.com/myorg/starter-kit/tree/solution" value={values.referenceSolution} onChange={(e) => setValues((v) => ({ ...v, referenceSolution: e.target.value }))} />
            </div>
          </div>

          {/* FRS-DEV-03 Technical Documentation */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><FileCode size={13} /> Architecture Specs & Documentation</h4>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input label="Swagger / OpenAPI Spec URL" placeholder="https://swagger.myproject.com" value={values.swaggerSpec} onChange={(e) => setValues((v) => ({ ...v, swaggerSpec: e.target.value }))} />
              <Input label="Database ER Diagram URL" placeholder="https://dbdiagram.io/d/..." value={values.erDiagram} onChange={(e) => setValues((v) => ({ ...v, erDiagram: e.target.value }))} />
            </div>
            <div className="mt-3">
              <Textarea label="Setup README instructions (Markdown supported)" placeholder="1. Run npm install&#10;2. Configure environment variables&#10;3. Boot dev server via npm run dev" value={values.readmeContent} onChange={(e) => setValues((v) => ({ ...v, readmeContent: e.target.value }))} rows={3} />
            </div>
          </div>

          {/* FRS-DEV-04 Video walkthrough embedding */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><Video size={13} /> instructional Walkthrough Tutorial</h4>
            <Input label="Loom / YouTube / Vimeo Embed URL" placeholder="https://www.youtube.com/embed/dQw4w9WgXcQ" value={values.videoTutorial} onChange={(e) => setValues((v) => ({ ...v, videoTutorial: e.target.value }))} />
          </div>

          <FileUpload 
            label="Boilerplate Starter Files & Diagrams" 
            hint="Upload starter repo ZIP, Swagger JSON, or diagram images (max 10MB)" 
            accept=".zip,.pdf,.png,.jpg" 
            multiple 
            initialFiles={values.files}
            onChange={(uploadedFiles) => setValues((v) => ({ ...v, files: uploadedFiles }))}
          />
        </form>
      </Modal>

      {/* Edit Project Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Project Details & Architectures"
        size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setEditModalOpen(false)}>Cancel</Button>
          <Button onClick={handleEditSubmit}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSubmit}>
          <div className="grid sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <Input label="Project title" required placeholder="e.g. NimbusCart — E-commerce Microservices" value={editValues.title} onChange={(e) => setEditValues((v) => ({ ...v, title: e.target.value }))} />
            </div>
            <Select label="Difficulty" required placeholder="Select difficulty" options={DIFFICULTIES} value={editValues.difficulty} onChange={(e) => setEditValues((v) => ({ ...v, difficulty: e.target.value }))} />
          </div>
          
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Tech stack (Comma-separated)" required placeholder="React, Node.js, PostgreSQL" value={editValues.stack} onChange={(e) => setEditValues((v) => ({ ...v, stack: e.target.value }))} />
            <Select label="Project Status" options={STATUS_OPTIONS} value={editValues.status} onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))} />
          </div>
          
          <Textarea label="Description & milestone objectives" value={editValues.description} onChange={(e) => setEditValues((v) => ({ ...v, description: e.target.value }))} rows={2} />
          
          {/* FRS-DEV-02 Repository Starters */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><GitFork size={13} /> Source Code Repositories</h4>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input label="Boilerplate Starter Repo URL" placeholder="https://github.com/myorg/starter-kit" value={editValues.starterRepo} onChange={(e) => setEditValues((v) => ({ ...v, starterRepo: e.target.value }))} />
              <Input label="Reference Solution Branch URL" placeholder="https://github.com/myorg/starter-kit/tree/solution" value={editValues.referenceSolution} onChange={(e) => setEditValues((v) => ({ ...v, referenceSolution: e.target.value }))} />
            </div>
          </div>

          {/* FRS-DEV-03 Technical Documentation */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><FileCode size={13} /> Architecture Specs & Documentation</h4>
            <div className="grid sm:grid-cols-2 gap-4">
              <Input label="Swagger / OpenAPI Spec URL" placeholder="https://swagger.myproject.com" value={editValues.swaggerSpec} onChange={(e) => setEditValues((v) => ({ ...v, swaggerSpec: e.target.value }))} />
              <Input label="Database ER Diagram URL" placeholder="https://dbdiagram.io/d/..." value={editValues.erDiagram} onChange={(e) => setEditValues((v) => ({ ...v, erDiagram: e.target.value }))} />
            </div>
            <div className="mt-3">
              <Textarea label="Setup README instructions (Markdown supported)" placeholder="1. Run npm install&#10;2. Configure environment variables&#10;3. Boot dev server via npm run dev" value={editValues.readmeContent} onChange={(e) => setEditValues((v) => ({ ...v, readmeContent: e.target.value }))} rows={3} />
            </div>
          </div>

          {/* FRS-DEV-04 Video walkthrough embedding */}
          <div className="border-t border-border pt-4">
            <h4 className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-3 flex items-center gap-1.5"><Video size={13} /> instructional Walkthrough Tutorial</h4>
            <Input label="Loom / YouTube / Vimeo Embed URL" placeholder="https://www.youtube.com/embed/dQw4w9WgXcQ" value={editValues.videoTutorial} onChange={(e) => setEditValues((v) => ({ ...v, videoTutorial: e.target.value }))} />
          </div>

          <FileUpload 
            label="Boilerplate Starter Files & Diagrams" 
            hint="Upload files (max 10MB)" 
            accept=".zip,.pdf,.png,.jpg" 
            multiple 
            initialFiles={editValues.files}
            onChange={(uploadedFiles) => setEditValues((v) => ({ ...v, files: uploadedFiles }))}
          />
        </form>
      </Modal>
    </div>
  );
}
