import { useEffect, useState } from "react";
import { Plus, Send, Edit, Trash2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { getMyRequirements, submitProjectRequirement, updateMyRequirement, deleteMyRequirement } from "../../services/clientService";

// This page used to keep its own private, disconnected copy of "requirements"
// in localStorage. It now calls through clientService, which writes straight
// into the same BA Documents queue (src/pages/ba/Documents.jsx) -- so a
// submission here is what a BA actually reviews, and the status shown below
// is the BA's real, current decision (not something the client can set
// themselves).
export default function ClientProjects() {
  const [reqs, setReqs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ title: "", scope: "", files: [] });

  // Edit states -- title/scope/files only; status is BA-owned.
  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ title: "", scope: "", files: [] });

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getMyRequirements().then((docs) => {
      setReqs(
        docs.map((d) => ({
          id: d.id,
          title: d.title,
          scope: d.summary,
          status: d.status,
          type: d.type,
          version: d.version,
          date: d.updatedAt,
          files: (d.files || []).map((f) => new File([""], f.name, { type: "application/pdf" })),
        }))
      );
      setLoading(false);
    });
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { title: [required], scope: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setSubmitting(true);
    try {
      await submitProjectRequirement({ title: values.title, scope: values.scope, files: values.files });
      notify("Requirement submitted to our Business Analyst team for scoping.", { type: "success", title: "Submitted" });
      setModalOpen(false);
      setValues({ title: "", scope: "", files: [] });
      load();
    } finally {
      setSubmitting(false);
    }
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setEditValues({ title: item.title, scope: item.scope, files: item.files || [] });
    setErrors({});
    setEditOpen(true);
  };

  const onEditSave = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { title: [required], scope: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;
    // Pass the raw File objects through (not just {name, size}) so
    // clientService.updateMyRequirement can read any newly-picked file's
    // real content and keep it downloadable for the BA.
    await updateMyRequirement(editingId, {
      title: editValues.title,
      summary: editValues.scope,
      files: editValues.files || [],
    });
    notify("Requirement details updated successfully.", { type: "success" });
    setEditOpen(false);
    setEditingId(null);
    load();
  };

  const handleDelete = async (id) => {
    const target = reqs.find((r) => r.id === id);
    await deleteMyRequirement(id);
    notify(`Requirement "${target?.title}" deleted successfully.`, { type: "success" });
    load();
  };

  const downloadAttachedFile = (file) => {
    const blob = new Blob([""], { type: "application/pdf" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = file.name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify(`Downloading: ${file.name}`, { type: "info" });
  };

  return (
    <div>
      <PageHeader
        title="My Project Requirements"
        subtitle="Submit real-world business challenges for our BA team to scope"
        breadcrumbs={[{ label: "Dashboard", to: "/client/dashboard" }, { label: "Project Requirements" }]}
        action={<Button icon={Plus} onClick={() => { setErrors({}); setValues({ title: "", scope: "", files: [] }); setModalOpen(true); }}>Submit New Requirement</Button>}
      />

      <Card className="mb-4">
        <p className="text-sm text-ink-500">
          Your submitted requirements go straight to our Business Analyst team's review queue. A BA reads your
          brief and authors the formal BRD, SRS, and FRS specs from it — you don't need to prepare those yourself.
          Once approved and staffed against a training batch, it becomes a live project you can track under Sprint Demo Reviews.
        </p>
      </Card>

      <Card>
        <h3 className="text-base font-semibold text-ink-900 mb-4 text-left">Your Submissions</h3>
        <Table
          loading={loading}
          data={reqs}
          columns={[
            { key: "title", header: "Requirement Title", className: "text-left font-medium text-ink-900", render: (r) => (
              <div>
                <p className="font-semibold text-ink-900 text-left">{r.title}</p>
                {r.files && r.files.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-1">
                    {r.files.map((file, idx) => (
                      <span key={idx} className="inline-flex items-center text-xs text-primary-700 bg-primary-50 px-2 py-0.5 rounded font-mono hover:underline cursor-pointer" onClick={() => downloadAttachedFile(file)}>
                        {file.name}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ) },
            { key: "scope", header: "Business Scope", className: "text-left max-w-md truncate", render: (r) => r.scope },
            { key: "date", header: "Last Updated", className: "text-left" },
            { key: "status", header: "BA Status", className: "text-left", render: (r) => <Badge tone={r.status === "Approved" ? "success" : r.status === "Under Review" ? "warning" : "neutral"}>{r.status}</Badge> },
            { key: "action", header: "", className: "text-right", render: (r) => (
              <div className="flex gap-2 justify-end">
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)} disabled={r.status === "Approved"}>Edit</Button>
                <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)} disabled={r.status === "Approved"}>Delete</Button>
              </div>
            ) },
          ]}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Submit Project Requirement"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button icon={Send} loading={submitting} onClick={submit}>Submit</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Input label="Project title" required placeholder="e.g. Inventory Management Portal" value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} />
          <Textarea label="Business challenge & scope" required rows={4} value={values.scope} onChange={(e) => setValues((v) => ({ ...v, scope: e.target.value }))} error={errors.scope} />
          <FileUpload
            label="Project brief / reference documents"
            hint="Briefs, wireframes, screenshots — our BA team will turn this into the formal BRD/SRS/FRS"
            multiple
            initialFiles={values.files}
            onChange={(uploaded) => setValues((v) => ({ ...v, files: uploaded }))}
          />
        </form>
      </Modal>

      {/* Edit Modal -- title/scope/files only; BA owns status & version */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit Project Requirement"
        footer={<>
          <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button onClick={onEditSave}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onEditSave}>
          <Input label="Project title" required value={editValues.title} onChange={(e) => setEditValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} />
          <Textarea label="Business challenge & scope" required rows={4} value={editValues.scope} onChange={(e) => setEditValues((v) => ({ ...v, scope: e.target.value }))} error={errors.scope} />
          <FileUpload
            label="Project brief / reference documents"
            hint="Briefs, wireframes, screenshots — our BA team will turn this into the formal BRD/SRS/FRS"
            multiple
            initialFiles={editValues.files}
            onChange={(uploaded) => setEditValues((v) => ({ ...v, files: uploaded }))}
          />
        </form>
      </Modal>
    </div>
  );
}