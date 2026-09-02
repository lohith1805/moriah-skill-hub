import { useEffect, useState } from "react";
import {
  Plus, Trash2, Eye, Download, ShieldCheck, CheckCircle2, FileText
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { getDocuments, saveDocuments } from "../../services/baService";
import { getDocReviews } from "../../services/developerService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

const DOC_TYPES = [
  { value: "BRD", label: "Business Requirements Document (BRD)" },
  { value: "SRS", label: "System Requirements Specification (SRS)" },
  { value: "FRS", label: "Functional Requirements Specification (FRS)" },
];

// Client-attached briefs/wireframes arrive tagged "CLIENT_DOC" (see
// clientService.submitProjectRequirement) — kept separate from BRD/SRS/FRS
// so a client's raw attachment is never mistaken for a BA-authored spec.
// It's shown for context in the inspector, but BA can't "upload" this type
// — only client submissions create it.
const CLIENT_DOC_TYPE = { value: "CLIENT_DOC", label: "Client-Submitted Brief" };

function typeBadge(t) {
  if (t === "CLIENT_DOC") return { label: "Client Brief", tone: "neutral" };
  if (t === "BRD") return { label: "BRD", tone: "primary" };
  if (t === "SRS") return { label: "SRS", tone: "gold" };
  if (t === "FRS") return { label: "FRS", tone: "success" };
  return { label: t, tone: "neutral" };
}

const emptyValues = () => ({
  projectId: "",
  title: "",
  client: "",
  summary: "",
  filesByType: { BRD: [], SRS: [], FRS: [] },
});

// Converts an uploaded File to a base64 data URL so it can be persisted in
// localStorage — this is what lets Download keep working after a reload,
// instead of only while the in-memory File blob from this session exists.
function fileToDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// A doc's files/fileData can come from an older single-file record
// (fileData as a plain base64 string, files without a `type`) as well as
// the current multi-type shape (fileData as { BRD, SRS, FRS }, each file
// tagged with its type). This normalizes either shape into a flat list of
// { type, name, size } so the table and inspector can treat them the same.
function normalizedFiles(doc) {
  return (doc?.files || []).map((f) => ({ ...f, type: f.type || doc.type || "BRD" }));
}

function getFileUrl(doc, type) {
  if (!doc?.fileData) return null;
  if (typeof doc.fileData === "string") {
    // Legacy record: one file, implicitly whatever doc.type was.
    return type === (doc.type || "BRD") ? doc.fileData : null;
  }
  return doc.fileData[type] || null;
}

export default function BaDocuments() {
  const [docs, setDocs] = useState([]);
  const [devReviews, setDevReviews] = useState({});
  const [loading, setLoading] = useState(true);
  const [viewingDoc, setViewingDoc] = useState(null);

  // Upload state
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState(emptyValues());
  const [errors, setErrors] = useState({});

  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([getDocuments(), getDocReviews()]).then(([d, r]) => {
      setDocs(d);
      setDevReviews(r);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

  // Existing projects a BA can attach new files to, instead of typing a
  // fresh title and accidentally creating a duplicate row for a requirement
  // a client already submitted (e.g. "microsoft — inevnt" showing up twice).
  const projectOptions = [
    { value: "", label: "+ Create New Project" },
    ...docs.map((d) => ({ value: d.id, label: `${d.title} — ${d.client}` })),
  ];

  const handleProjectSelect = (e) => {
    const id = e.target.value;
    if (!id) {
      setValues((v) => ({ ...v, projectId: "", title: "", client: "" }));
      return;
    }
    const proj = docs.find((d) => d.id === id);
    setValues((v) => ({
      ...v,
      projectId: id,
      title: proj?.title || "",
      client: proj?.client || "",
    }));
  };

  // A doc that hasn't had any real file attached yet (e.g. a client
  // submission) starts at v1.0 once one lands; re-uploads after that bump
  // the minor version instead.
  const bumpVersion = (doc) => {
    const hasAnyFile = doc.fileData && (typeof doc.fileData === "string" || Object.keys(doc.fileData).length);
    if (!hasAnyFile) return "1.0";
    const [major, minor] = String(doc.version || "1.0").split(".");
    return `${major || "1"}.${(parseInt(minor || "0", 10) + 1)}`;
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    const isExisting = !!values.projectId;
    const validation = isExisting ? {} : validateForm(values, { title: [required], client: [required] });
    const pickedTypes = DOC_TYPES.filter(({ value }) => values.filesByType[value]?.length);
    if (!pickedTypes.length) validation.files = "Upload at least one of the BRD, SRS, or FRS files.";
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSubmitting(true);
    try {
      const uploadedEntries = await Promise.all(
        pickedTypes.map(async ({ value: type }) => {
          const file = values.filesByType[type][0];
          const dataUrl = await fileToDataURL(file);
          return { type, file, dataUrl };
        })
      );

      let updated;
      if (isExisting) {
        const target = docs.find((d) => d.id === values.projectId);
        updated = docs.map((d) => {
          if (d.id !== values.projectId) return d;
          const nextFiles = normalizedFiles(d);
          const nextFileData = typeof d.fileData === "string"
            ? { [d.type || "BRD"]: d.fileData }
            : { ...(d.fileData || {}) };
          uploadedEntries.forEach(({ type, file, dataUrl }) => {
            const idx = nextFiles.findIndex((f) => f.type === type);
            const entry = { type, name: file.name, size: file.size };
            if (idx > -1) nextFiles[idx] = entry; else nextFiles.push(entry);
            nextFileData[type] = dataUrl;
          });
          return {
            ...d,
            summary: values.summary || d.summary,
            version: bumpVersion(d),
            updatedAt: new Date().toISOString().slice(0, 10),
            files: nextFiles,
            fileData: nextFileData,
          };
        });
        notify(`File${uploadedEntries.length > 1 ? "s" : ""} attached to "${target?.title}".`, { type: "success", title: "Document Uploaded" });
      } else {
        const files = uploadedEntries.map(({ type, file }) => ({ type, name: file.name, size: file.size }));
        const fileData = {};
        uploadedEntries.forEach(({ type, dataUrl }) => { fileData[type] = dataUrl; });
        const newDoc = {
          id: `doc_${Date.now()}`,
          title: values.title,
          client: values.client,
          version: "1.0",
          status: "Under Review",
          summary: values.summary || "",
          updatedAt: new Date().toISOString().slice(0, 10),
          files,
          fileData,
        };
        updated = [newDoc, ...docs];
        notify("Document(s) uploaded and queued for verification.", { type: "success", title: "Document Uploaded" });
      }

      setDocs(updated);
      await saveDocuments(updated);

      setModalOpen(false);
      setValues(emptyValues());
    } finally {
      setSubmitting(false);
    }
  };

  // Marks a spec as reviewed and signed off — this is the BA's sign-off
  // that unblocks the doc for sprint execution / client hand-off. It also
  // upserts a matching "staffable" client project record (msh_client_projects)
  // with batch left "Unassigned", which is the exact list Trainer's Sprint
  // Planning "New Sprint" dropdown pulls its client/project options from —
  // without this, an approved requirement never surfaces there.
  const handleVerify = async (id) => {
    const target = docs.find((d) => d.id === id);
    const hasOfficialSpec = normalizedFiles(target).some((f) => f.type === "BRD" || f.type === "SRS" || f.type === "FRS");
    if (!hasOfficialSpec) {
      notify("Upload at least a BRD, SRS, or FRS before verifying — a client's brief alone isn't a signed-off spec.", { type: "warning", title: "Nothing to verify yet" });
      return;
    }
    const updated = docs.map((d) => (d.id === id ? { ...d, status: "Approved", updatedAt: new Date().toISOString().slice(0, 10) } : d));
    setDocs(updated);
    await saveDocuments(updated);

    try {
      const raw = localStorage.getItem("msh_client_projects");
      const projects = raw ? JSON.parse(raw) : [];
      const projectId = `proj_${id}`;
      const existingIdx = projects.findIndex((p) => p.id === projectId);
      const projectRecord = {
        id: projectId,
        title: target?.title || "Untitled Requirement",
        client: target?.client || "Unknown Client",
        batch: existingIdx > -1 ? projects[existingIdx].batch : "Unassigned",
        milestone: existingIdx > -1 ? projects[existingIdx].milestone : "Requirements Approved",
        progress: existingIdx > -1 ? projects[existingIdx].progress : 0,
        demoDate: existingIdx > -1 ? projects[existingIdx].demoDate : null,
      };
      if (existingIdx > -1) {
        projects[existingIdx] = { ...projects[existingIdx], ...projectRecord };
      } else {
        projects.unshift(projectRecord);
      }
      localStorage.setItem("msh_client_projects", JSON.stringify(projects));
    } catch (err) {
      console.warn("[BaDocuments] Could not sync approved doc to client projects:", err.message);
    }

    notify(`"${target?.title}" verified — now staffable from Trainer's Sprint Planning.`, { type: "success" });
  };

  const handleDelete = async (id) => {
    const target = docs.find((d) => d.id === id);
    const updated = docs.filter((d) => d.id !== id);
    setDocs(updated);
    await saveDocuments(updated);
    notify(`Document "${target?.title}" removed.`, { type: "success" });
  };

  const downloadDoc = (doc, type) => {
    const url = getFileUrl(doc, type);
    if (!url) {
      notify("No file attached to this document.", { type: "warning" });
      return;
    }
    const fileMeta = normalizedFiles(doc).find((f) => f.type === type);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileMeta?.name || `${doc.title}-${type}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    notify("Document downloaded successfully.", { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Requirements Authoring Studio"
        subtitle="Upload Business Requirements (BRD), System Architecture Specs (SRS), and Functional Specs (FRS), then verify them for sprint execution"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Authoring" }]}
        action={
          <Button icon={Plus} onClick={() => { setErrors({}); setValues(emptyValues()); setModalOpen(true); }}>
            Upload Document
          </Button>
        }
      />

      <Card>
        <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
          <div>
            <h3 className="font-display font-semibold text-ink-900">Authored Specifications Repository (MSH-FR-BA-01)</h3>
            <p className="text-xs text-ink-500">Live documents bridging enterprise client briefs with sprint execution</p>
          </div>
          <Badge tone="primary" dot>Version Controlled</Badge>
        </div>

        <Table
          loading={loading}
          data={docs}
          emptyTitle="No documents uploaded yet"
          emptyHint="Upload a BRD, SRS, or FRS file to get started."
          columns={[
            {
              key: "title",
              header: "Document Title",
              className: "text-left font-medium text-ink-900",
              render: (r) => {
                const files = normalizedFiles(r);
                return (
                  <div>
                    <p className="font-semibold text-ink-900">{r.title}</p>
                    <p className="text-xs text-ink-500 line-clamp-1 mt-0.5">
                      {r.summary || (files.length ? files.map((f) => f.name).join(", ") : "No files attached")}
                    </p>
                  </div>
                );
              }
            },
            {
              key: "type",
              header: "Spec Type",
              className: "text-left",
              render: (r) => {
                const types = Array.from(new Set(normalizedFiles(r).map((f) => f.type)));
                if (!types.length) return <span className="text-xs text-ink-400">—</span>;
                const hasOfficialSpec = types.some((t) => t === "BRD" || t === "SRS" || t === "FRS");
                return (
                  <div className="flex flex-col gap-1 items-start">
                    <div className="flex flex-wrap gap-1">
                      {types.map((t) => {
                        const { label, tone } = typeBadge(t);
                        return <Badge key={t} tone={tone}>{label}</Badge>;
                      })}
                    </div>
                    {!hasOfficialSpec && (
                      <span className="text-[11px] text-warning-600 font-medium">Awaiting BA-authored BRD/SRS/FRS</span>
                    )}
                  </div>
                );
              }
            },
            { key: "client", header: "Client Partner", className: "text-left" },
            {
              key: "version",
              header: "Version",
              className: "text-left font-mono text-xs",
              render: (r) => <span className="bg-cream-100 px-2 py-0.5 rounded font-semibold text-ink-800">v{r.version}</span>
            },
            {
              key: "status",
              header: "Status",
              className: "text-left",
              render: (r) => (
                <Badge tone={r.status === "Approved" ? "success" : r.status === "Under Review" ? "warning" : "neutral"}>
                  {r.status}
                </Badge>
              )
            },
            { key: "updatedAt", header: "Updated", className: "text-left font-mono text-xs" },
            {
              key: "devReview",
              header: "Dev Check",
              className: "text-left",
              render: (r) =>
                devReviews[r.id]?.reviewed ? (
                  <Badge tone="success">Reviewed by {devReviews[r.id].reviewedBy}</Badge>
                ) : (
                  <Badge tone="neutral">Pending</Badge>
                ),
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingDoc(r)}>Inspect</Button>
                  {r.status !== "Approved" && (
                    <Button size="sm" icon={ShieldCheck} onClick={() => handleVerify(r.id)}>Verify</Button>
                  )}
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* Upload Document Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Upload Requirements Document (MSH-FR-BA-01)"
        description="Attach the BRD, SRS, and/or FRS files for this client requirement."
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button loading={submitting} icon={Plus} onClick={handleUpload}>Upload Document</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleUpload}>
          <Select
            label="Project"
            hint="Pick an existing project to attach these files to it, or create a new one."
            options={projectOptions}
            value={values.projectId}
            onChange={handleProjectSelect}
          />

          <Input
            label="Document Title"
            required
            placeholder="e.g. Project or requirement title"
            value={values.title}
            onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))}
            error={errors.title}
            disabled={!!values.projectId}
          />

          <Input
            label="Client / Corporate Partner"
            required
            placeholder="e.g. Client company name"
            value={values.client}
            onChange={(e) => setValues((v) => ({ ...v, client: e.target.value }))}
            error={errors.client}
            disabled={!!values.projectId}
          />

          <Textarea
            label="Summary (optional)"
            rows={3}
            placeholder="Brief high-level summary of the business goals..."
            value={values.summary}
            onChange={(e) => setValues((v) => ({ ...v, summary: e.target.value }))}
          />

          <div className="flex flex-col gap-3">
            <div>
              <p className="text-sm font-medium text-ink-700">Documents <span className="text-error-500">*</span></p>
              <p className="text-xs text-ink-400">Attach any combination of BRD, SRS, and FRS files — upload one, two, or all three at once.</p>
            </div>
            {errors.files && <p className="text-xs text-error-500">{errors.files}</p>}
            {DOC_TYPES.map(({ value, label }) => (
              <FileUpload
                key={value}
                label={label}
                hint="PDF, DOCX — up to 10MB"
                accept=".pdf,.doc,.docx"
                initialFiles={values.filesByType[value]}
                onChange={(files) => setValues((v) => ({ ...v, filesByType: { ...v.filesByType, [value]: files } }))}
              />
            ))}
          </div>
        </form>
      </Modal>

      {/* Inspect Document Modal */}
      <Modal
        open={!!viewingDoc}
        onClose={() => setViewingDoc(null)}
        title={viewingDoc?.title || "Document Viewer"}
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <Badge tone={viewingDoc?.status === "Approved" ? "success" : "warning"}>Status: {viewingDoc?.status}</Badge>
            <Button variant="secondary" onClick={() => setViewingDoc(null)}>Close</Button>
          </div>
        }
      >
        {viewingDoc && (
          <div className="p-4 bg-white rounded-xl border border-border flex flex-col gap-4 text-left font-sans">
            <div className="flex justify-between items-start border-b border-border pb-3">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-primary-700">SPECIFICATION · v{viewingDoc.version}</span>
                <h3 className="text-base font-bold text-ink-900 mt-1">{viewingDoc.title}</h3>
                <p className="text-xs text-ink-500">Client: {viewingDoc.client} · Updated: {viewingDoc.updatedAt}</p>
              </div>
              <Badge tone={viewingDoc.status === "Approved" ? "success" : "warning"}>{viewingDoc.status}</Badge>
            </div>

            {viewingDoc.summary && (
              <div className="p-3 bg-cream-50 rounded-lg border border-border text-xs leading-relaxed text-ink-800">
                <p className="font-semibold text-ink-900 mb-1">Summary:</p>
                <p>{viewingDoc.summary}</p>
              </div>
            )}

            <div className="flex flex-col gap-2">
              {[CLIENT_DOC_TYPE, ...DOC_TYPES].map(({ value, label }) => {
                const fileMeta = normalizedFiles(viewingDoc).find((f) => f.type === value);
                if (!fileMeta) return null;
                const isClientDoc = value === "CLIENT_DOC";
                return (
                  <div key={value} className={`flex items-center gap-2 p-3 rounded-lg border text-sm ${isClientDoc ? "border-dashed border-border bg-cream-50" : "border-border bg-white"}`}>
                    <FileText size={16} className={isClientDoc ? "text-ink-400 shrink-0" : "text-primary-500 shrink-0"} />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-semibold text-ink-900">
                        {isClientDoc ? "Client Brief" : value} <span className="font-normal text-ink-400">— {label}</span>
                      </p>
                      <p className="truncate text-ink-700">{fileMeta.name}</p>
                    </div>
                    <Button size="sm" variant="secondary" icon={Download} onClick={() => downloadDoc(viewingDoc, value)}>Download</Button>
                  </div>
                );
              })}
              {!normalizedFiles(viewingDoc).length && (
                <div className="flex items-center gap-2 p-3 rounded-lg border border-border bg-white text-sm text-ink-500">
                  <FileText size={16} className="text-ink-300 shrink-0" />
                  No files attached yet.
                </div>
              )}
              {normalizedFiles(viewingDoc).length > 0 &&
                !normalizedFiles(viewingDoc).some((f) => f.type === "BRD" || f.type === "SRS" || f.type === "FRS") && (
                  <div className="flex items-center gap-2 p-3 rounded-lg border border-warning-200 bg-warning-50 text-xs text-warning-700">
                    Only the client's brief is attached — author and upload the BRD/SRS/FRS above before verifying this requirement.
                  </div>
                )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}