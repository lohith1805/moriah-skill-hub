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
import { useAuth } from "../../context/AuthContext";
import { validateForm, required } from "../../utils/validators";

const DIFFICULTIES = ["Beginner", "Intermediate", "Advanced"].map((d) => ({ value: d, label: d }));
const TRACKS = ["Full-Stack Development", "Data Analytics", "Product Design", "Backend Engineering"].map((t) => ({ value: t, label: t }));
// Editing a PUBLISHED/ARCHIVED project doesn't change it in place — the backend
// copies it forward into a NEW version (and archives the current one), so a new
// version identifier is required. Suggest the next one: bump a trailing number,
// else append "-2".
function bumpVersion(v) {
  const s = String(v || "v1").trim();
  const m = s.match(/^(.*?)(\d+)(\D*)$/);
  if (m) return `${m[1]}${Number(m[2]) + 1}${m[3]}`;
  return `${s}-2`;
}

const emptyProjectValues = {
  title: "",
  stack: "",
  difficulty: "Intermediate",
  track: "",
  description: "",
  files: [],
  starterRepo: "",
  referenceSolution: "",
  architectureDiagramUrl: "",
  swaggerSpec: "",
  erDiagram: "",
  videoTutorial: "",
  readmeContent: ""
};

export default function DeveloperProjects() {
  const { user } = useAuth();
  const myUuid = user?.uuid;
  // "mine" = my own projects (drafts + published, via ?mine=true); "all" = the
  // published catalogue everyone shares. You can only edit/publish your own.
  const [scope, setScope] = useState("mine");
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
    getProjects(scope === "mine" ? { mine: true } : {})
      .then((p) => setProjects(p.map((proj) => ({ ...proj, files: [] }))))
      .catch(() => setProjects([]))
      .finally(() => setLoading(false));

  useEffect(() => {
    setLoading(true);
    reload();
  }, [scope]); // eslint-disable-line react-hooks/exhaustive-deps

  const canEdit = (p) => !myUuid || !p.createdByUuid || p.createdByUuid === myUuid;

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
      track: p.track || "",
      description: p.description || "",
      status: p.status,
      version: p.version || "v1",
      // pre-filled only for a published/archived edit, which forces a version bump
      newVersion: p.status === "Draft" ? "" : bumpVersion(p.version),
      files: p.files || [],
      starterRepo: p.starterRepo || "",
      referenceSolution: p.referenceSolution || "",
      architectureDiagramUrl: p.architectureDiagramUrl || "",
      swaggerSpec: p.swaggerSpec || "",
      erDiagram: p.erDiagram || "",
      videoTutorial: p.videoTutorial || "",
      readmeContent: p.readmeContent || ""
    });
    setEditModalOpen(true);
  };

  const isPublishedEdit = editValues.status && editValues.status !== "Draft";

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    const rules = { title: [required], stack: [required], difficulty: [required] };
    if (isPublishedEdit) rules.newVersion = [required];
    const validation = validateForm(editValues, rules);
    setErrors(validation);
    if (Object.keys(validation).length) return;

    try {
      // A draft is edited in place; a published/archived project is copied
      // forward into a new version (the backend requires the identifier).
      await updateProject(editingId, {
        ...editValues,
        version: isPublishedEdit ? editValues.newVersion.trim() : undefined,
      });
      notify(
        isPublishedEdit ? `Published as ${editValues.newVersion.trim()} — the previous version is archived.` : "Project updated.",
        { type: "success", title: "Project Updated" }
      );
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

      <div className="flex items-center gap-1 mb-4 w-fit rounded-lg border border-border bg-cream-50 p-0.5">
        {[
          { key: "mine", label: "My projects" },
          { key: "all", label: "All published" },
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
                      <p className="text-xs text-ink-500 mt-0.5">{p.version} · {p.difficulty}{p.track ? ` · ${p.track}` : ""}</p>
                    </div>
                  </div>
                  <Badge tone={p.status === "Published" ? "success" : "neutral"}>{p.status}</Badge>
                </div>
                {!canEdit(p) && (
                  <p className="text-[11px] text-ink-400 mt-1.5">Authored by {p.createdBy || "another developer"}</p>
                )}
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
                {canEdit(p) ? (
                  <>
                    {p.status === "Draft" && (
                      <Button size="sm" variant="secondary" icon={Send} onClick={() => publish(p.id)}>Publish</Button>
                    )}
                    <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(p)}>Edit</Button>
                  </>
                ) : (
                  <span className="text-xs text-ink-400">Read-only</span>
                )}
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
          
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Tech stack (Comma-separated)" required placeholder="React, Node.js, PostgreSQL, Docker" value={values.stack} onChange={(e) => setValues((v) => ({ ...v, stack: e.target.value }))} error={errors.stack} />
            <Select label="Track" placeholder="Not tied to a track" hint="A Trainer/PM can only assign this project to a batch on the same track" options={TRACKS} value={values.track} onChange={(e) => setValues((v) => ({ ...v, track: e.target.value }))} />
          </div>
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
            <p className="text-xs text-ink-400 -mt-2 mb-3">All optional — add whichever exist now, and come back to fill in the rest once they're ready.</p>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <Input label="Architecture Diagram URL" placeholder="https://miro.com/board/..." value={values.architectureDiagramUrl} onChange={(e) => setValues((v) => ({ ...v, architectureDiagramUrl: e.target.value }))} />
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
          
          <div className="grid sm:grid-cols-3 gap-4">
            <Input label="Tech stack (Comma-separated)" required placeholder="React, Node.js, PostgreSQL" value={editValues.stack} onChange={(e) => setEditValues((v) => ({ ...v, stack: e.target.value }))} />
            <Select label="Track" placeholder="Not tied to a track" options={TRACKS} value={editValues.track} onChange={(e) => setEditValues((v) => ({ ...v, track: e.target.value }))} />
            <div>
              <label className="text-xs font-semibold text-ink-700">Current Status</label>
              <p className="mt-1 text-sm text-ink-900">{editValues.status || "—"} · {editValues.version}</p>
              <p className="text-[11px] text-ink-400 mt-0.5">Use “Publish” on the card to move a draft live.</p>
            </div>
          </div>

          {isPublishedEdit && (
            <div className="rounded-lg border border-warning-200 bg-warning-50 p-3">
              <Input
                label="New version identifier"
                required
                placeholder="e.g. v2"
                value={editValues.newVersion}
                onChange={(e) => setEditValues((v) => ({ ...v, newVersion: e.target.value }))}
                error={errors.newVersion}
              />
              <p className="text-[11px] text-warning-800 mt-1.5">
                This project is published. Saving creates a new version <strong>{editValues.newVersion || "…"}</strong> and archives{" "}
                <strong>{editValues.version}</strong>. Anything already using {editValues.version} keeps pointing at it.
              </p>
            </div>
          )}

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
            <p className="text-xs text-ink-400 -mt-2 mb-3">All optional — add whichever exist now, and come back to fill in the rest once they're ready.</p>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <Input label="Architecture Diagram URL" placeholder="https://miro.com/board/..." value={editValues.architectureDiagramUrl} onChange={(e) => setEditValues((v) => ({ ...v, architectureDiagramUrl: e.target.value }))} />
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
