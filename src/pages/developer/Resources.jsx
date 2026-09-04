import { useEffect, useMemo, useState } from "react";
import { FileCode2, Plus, Save, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select } from "../../components/ui/FormField";
import { getResourceLibrary, createResource, getProjects } from "../../services/developerService";
import { TRACKS, TRACK_LABELS } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

const RESOURCE_TYPES = [
  { value: "Cheat Sheet", label: "Cheat Sheet" },
  { value: "SDK Documentation", label: "SDK Documentation" },
  { value: "Starter Template", label: "Starter Template" },
  { value: "Shared Library", label: "Shared Library" },
  { value: "Boilerplate", label: "Boilerplate" }
];

export default function DeveloperResources() {
  const [items, setItems] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({ title: "", type: "Cheat Sheet", link: "", track: "", projectId: "" });
  const [errors, setErrors] = useState({});

  // Filters
  const [trackFilter, setTrackFilter] = useState("");
  const [projectFilter, setProjectFilter] = useState("");

  const { notify } = useToast();

  const load = () => {
    Promise.all([getResourceLibrary(), getProjects().catch(() => [])])
      .then(([d, p]) => { setItems(d); setProjects(p); })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const projectTitle = (id) => projects.find((p) => String(p.id) === String(id))?.title || `Project #${id}`;

  const filtered = useMemo(
    () => items.filter((r) =>
      (!trackFilter || !r.track || r.track === trackFilter) &&
      (!projectFilter || !r.projectId || String(r.projectId) === String(projectFilter))),
    [items, trackFilter, projectFilter],
  );

  const onChange = (e) => setValues((v) => ({ ...v, [e.target.name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { title: [required], link: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSaving(true);
    try {
      await createResource(values);
      notify("Resource added to the shared library.", { type: "success" });
      setModalOpen(false);
      setValues({ title: "", type: "Cheat Sheet", link: "", track: "", projectId: "" });
      load();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Resource Library"
        subtitle="Maintain shared libraries, cheat sheets, SDK documentation, and boilerplates accessible to assigned batches"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Resources" }]}
        action={<Button icon={Plus} onClick={() => setModalOpen(true)}>Add Resource</Button>}
      />

      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <Select
            className="w-56"
            label="Filter by track"
            value={trackFilter}
            onChange={(e) => setTrackFilter(e.target.value)}
            options={[{ value: "", label: "All tracks" }, ...TRACKS]}
          />
          <Select
            className="w-64"
            label="Filter by project"
            value={projectFilter}
            onChange={(e) => setProjectFilter(e.target.value)}
            options={[{ value: "", label: "All projects" }, ...projects.map((p) => ({ value: String(p.id), label: p.title }))]}
          />
        </div>
        <Table
          loading={loading}
          data={filtered}
          columns={[
            { key: "title", header: "Resource", render: (r) => <span className="flex items-center gap-2 text-ink-900 font-semibold"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Type", render: (r) => <Badge tone="primary">{r.type}</Badge> },
            { key: "track", header: "Track", render: (r) => r.track ? <Badge tone="neutral">{TRACK_LABELS[r.track] || r.track}</Badge> : <span className="text-xs text-ink-400">All</span> },
            { key: "project", header: "Project", render: (r) => r.projectId ? <span className="text-xs text-ink-600">{projectTitle(r.projectId)}</span> : <span className="text-xs text-ink-400">All</span> },
            { key: "updatedAt", header: "Updated" },
            { key: "action", header: "", render: (r) => (
              <a href={r.link} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline text-xs font-semibold">
                <Link2 size={12} /> Open Link
              </a>
            ) },
          ]}
        />
      </Card>

      {/* Modal: Add Resource */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Add Shared Resource"
        description="Publish developer utilities, API sheets, and libraries to students."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={Save} loading={saving} onClick={submit}>Publish Resource</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={submit}>
          <Input
            label="Resource Title"
            required
            placeholder="e.g. Docker Compose Boilerplate for Node & PG"
            value={values.title}
            onChange={onChange}
            name="title"
            error={errors.title}
          />
          <Select
            label="Resource Type"
            name="type"
            options={RESOURCE_TYPES}
            value={values.type}
            onChange={onChange}
          />
          <Select
            label="Track"
            name="track"
            value={values.track}
            onChange={onChange}
            placeholder="All tracks"
            options={TRACKS}
            hint="Scope this resource to one cohort track, or leave blank for everyone."
          />
          <Select
            label="Project"
            name="projectId"
            value={values.projectId}
            onChange={onChange}
            placeholder="All projects"
            options={projects.map((p) => ({ value: String(p.id), label: p.title }))}
            hint="Tie this resource to one project, or leave blank to show it everywhere."
          />
          <Input
            label="Resource Reference URL"
            required
            placeholder="e.g. https://github.com/myorg/docker-boilerplate"
            value={values.link}
            onChange={onChange}
            name="link"
            error={errors.link}
          />
        </form>
      </Modal>
    </div>
  );
}
