import { useEffect, useState } from "react";
import { Library, FileCode2, Plus, Save, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select } from "../../components/ui/FormField";
import { getResourceLibrary, createResource } from "../../services/developerService";
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
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({ title: "", type: "Cheat Sheet", link: "", track: "" });
  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    getResourceLibrary().then((d) => { setItems(d); setLoading(false); });
  };

  useEffect(() => {
    load();
  }, []);

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
      setValues({ title: "", type: "Cheat Sheet", link: "", track: "" });
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
        <Table
          loading={loading}
          data={items}
          columns={[
            { key: "title", header: "Resource", render: (r) => <span className="flex items-center gap-2 text-ink-900 font-semibold"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Type", render: (r) => <Badge tone="primary">{r.type}</Badge> },
            { key: "track", header: "Track", render: (r) => r.track ? <Badge tone="neutral">{TRACK_LABELS[r.track] || r.track}</Badge> : <span className="text-xs text-ink-400">All</span> },
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
