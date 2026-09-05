import { useEffect, useState } from "react";
import mammoth from "mammoth";
import { Plus, Eye, ShieldCheck, FileText, UploadCloud, Download, Maximize2, Minimize2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import {
  getRequirementDocuments,
  getRequirementDocumentDetail,
  createRequirementDocument,
  approveRequirementDocument,
} from "../../services/baService";
import { getClientProjects } from "../../services/clientService";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import { validateForm, required } from "../../utils/validators";
import { downloadTextFile } from "../../utils/downloadTextFile";

const DOC_TYPES = [
  { value: "BRD", label: "Business Requirements Document (BRD)" },
  { value: "SRS", label: "System Requirements Specification (SRS)" },
  { value: "FRS", label: "Functional Requirements Specification (FRS)" },
  { value: "USER_STORY", label: "User Story" },
];

function typeTone(t) {
  if (t === "BRD") return "primary";
  if (t === "SRS") return "gold";
  if (t === "FRS") return "success";
  return "neutral";
}

const emptyValues = () => ({ docType: "BRD", title: "", content: "" });

const APPROVAL_ROLE_ORDER = ["CLIENT", "BUSINESS_ANALYST", "DEVELOPER"];
const APPROVAL_ROLE_LABEL = { CLIENT: "Client", BUSINESS_ANALYST: "BA", DEVELOPER: "Developer" };

// One badge per required sign-off slot for this doc type — replaces a single
// "Approved/In Review" flag now that BRD/FRS need CLIENT + BA + DEVELOPER
// and SRS/USER_STORY need only BA + DEVELOPER (RequirementDocumentApprovalService).
function ApprovalSlots({ approvals }) {
  const ordered = [...(approvals || [])].sort(
    (a, b) => APPROVAL_ROLE_ORDER.indexOf(a.role) - APPROVAL_ROLE_ORDER.indexOf(b.role)
  );
  if (!ordered.length) return <span className="text-xs text-ink-400">—</span>;
  return (
    <div className="flex flex-wrap gap-1">
      {ordered.map((a) => (
        <Badge key={a.role} tone={a.pending ? "neutral" : "success"} title={a.pending ? "Pending" : `Approved by ${a.approvedByName}`}>
          {APPROVAL_ROLE_LABEL[a.role] || a.role}{a.pending ? "" : " ✓"}
        </Badge>
      ))}
    </div>
  );
}

// Optional convenience: extract text from a .docx/.txt so a BA who already has
// the spec written up elsewhere doesn't have to retype it — the backend itself
// only ever stores plain text (`content`), there's no file-attachment column.
async function extractTextFromFile(file) {
  const name = file.name.toLowerCase();
  if (name.endsWith(".docx")) {
    const arrayBuffer = await file.arrayBuffer();
    const { value } = await mammoth.extractRawText({ arrayBuffer });
    return value.trim();
  }
  return (await file.text()).trim();
}

export default function BaDocuments() {
  const { notify } = useToast();
  const { user } = useAuth();
  const [projects, setProjects] = useState([]);
  const [projectId, setProjectId] = useState("");
  const [docs, setDocs] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingDocs, setLoadingDocs] = useState(false);

  // Create
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState(emptyValues());
  const [errors, setErrors] = useState({});
  const [importing, setImporting] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // View
  const [viewingId, setViewingId] = useState(null);
  const [viewingDoc, setViewingDoc] = useState(null);
  const [loadingView, setLoadingView] = useState(false);
  const [approvingId, setApprovingId] = useState(null);
  const [docExpanded, setDocExpanded] = useState(false);

  useEffect(() => {
    getClientProjects()
      .then((p) => {
        setProjects(p);
        if (p.length) setProjectId(String(p[0].id));
      })
      .catch(() => notify("Couldn't load client projects.", { type: "error" }))
      .finally(() => setLoadingProjects(false));
  }, [notify]);

  const loadDocs = (id) => {
    if (!id) return;
    setLoadingDocs(true);
    getRequirementDocuments({ clientProjectId: id })
      .then(setDocs)
      .catch(() => notify("Couldn't load requirement documents.", { type: "error" }))
      .finally(() => setLoadingDocs(false));
  };

  useEffect(() => {
    loadDocs(projectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const project = projects.find((p) => String(p.id) === String(projectId));

  const openCreate = () => {
    setValues(emptyValues());
    setErrors({});
    setModalOpen(true);
  };

  const handleImport = async (files) => {
    const file = files?.[0];
    if (!file) return;
    setImporting(true);
    try {
      const text = await extractTextFromFile(file);
      setValues((v) => ({ ...v, content: text }));
      notify(`Imported text from "${file.name}" — review it below before submitting.`, { type: "success" });
    } catch (err) {
      notify("Couldn't read that file — paste the content directly instead.", { type: "error" });
    } finally {
      setImporting(false);
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { title: [required], content: [required] });
    setErrors(v);
    if (Object.keys(v).length) return;

    setSubmitting(true);
    try {
      await createRequirementDocument({ clientProjectId: projectId, ...values });
      notify(`${values.docType} submitted for review.`, { type: "success", title: "Document created" });
      setModalOpen(false);
      loadDocs(projectId);
    } catch (err) {
      notify(err?.message || "Couldn't create this document.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const openView = (doc) => {
    setViewingId(doc.id);
    setViewingDoc(null);
    setDocExpanded(false);
    setLoadingView(true);
    getRequirementDocumentDetail(doc.id)
      .then(setViewingDoc)
      .catch((err) => notify(err?.message || "Couldn't load this document.", { type: "error" }))
      .finally(() => setLoadingView(false));
  };

  const approve = async (doc) => {
    setApprovingId(doc.id);
    try {
      await approveRequirementDocument(doc.id);
      notify(`"${doc.title}" approved.`, { type: "success" });
      loadDocs(projectId);
      if (viewingId === doc.id) openView(doc);
    } catch (err) {
      notify(err?.message || "Couldn't approve this document.", { type: "error" });
    } finally {
      setApprovingId(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Requirements Authoring Studio"
        subtitle="Author the BRD / SRS / FRS / user stories for a client's submitted project, then approve them for the developer team"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Authoring" }]}
        action={
          <div className="flex items-center gap-2">
            <Select
              className="w-64"
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              options={projects.map((p) => ({ value: String(p.id), label: `${p.title} — ${p.clientName}` }))}
              placeholder={projects.length ? "Select a client project" : "No client projects yet"}
            />
            <Button icon={Plus} onClick={openCreate} disabled={!projectId}>New Document</Button>
          </div>
        }
      />

      {loadingProjects ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading client projects…" /></div>
      ) : !projectId ? (
        <EmptyState icon={FileText} title="No client projects yet" description="A client's submitted project shows up here once it's in Client Project Review." />
      ) : (
        <Card>
          <div className="px-4 py-3 border-b border-border text-left">
            <h3 className="font-display font-semibold text-ink-900">{project?.title}</h3>
            <p className="text-xs text-ink-500 mt-0.5">{project?.clientName} · {project?.scope || "No scope description provided."}</p>
            <p className="text-xs text-ink-500 mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1">
              <span>BA: <strong className="text-ink-800">{project?.assignedBaName || "Unassigned"}</strong></span>
              <span>
                Developer: <strong className="text-ink-800">{project?.assignedDeveloperName || "Not yet assigned"}</strong>
                {!project?.assignedDeveloperName && (
                  <span className="text-ink-400"> — auto-assigned once a BA signs off a BRD/FRS</span>
                )}
              </span>
            </p>
          </div>

          <Table
            loading={loadingDocs}
            data={docs}
            emptyTitle="No requirement documents yet"
            emptyHint="Author a BRD, SRS, FRS, or user story for this project."
            columns={[
              { key: "docType", header: "Type", className: "text-left", render: (r) => <Badge tone={typeTone(r.docType)}>{r.docType.replace("_", " ")}</Badge> },
              { key: "title", header: "Title", className: "text-left font-medium text-ink-900" },
              { key: "version", header: "Version", className: "text-left font-mono text-xs", render: (r) => <span className="bg-cream-100 px-2 py-0.5 rounded font-semibold text-ink-800">v{r.version}</span> },
              { key: "status", header: "Sign-off", className: "text-left", render: (r) => <ApprovalSlots approvals={r.approvals} /> },
              { key: "authoredByName", header: "Authored by", className: "text-left text-xs text-ink-500" },
              {
                key: "devReview", header: "Dev Check", className: "text-left",
                render: (r) => r.devReviewedAt
                  ? <Badge tone="success">Reviewed by {r.devReviewedByName}</Badge>
                  : <Badge tone="neutral">Pending</Badge>,
              },
              {
                key: "action", header: "", className: "text-right",
                render: (r) => (
                  <div className="flex gap-2 justify-end">
                    <Button size="sm" variant="secondary" icon={Eye} onClick={() => openView(r)}>View</Button>
                    {r.status !== "APPROVED" && r.authoredByUuid !== user?.uuid && (
                      <Button size="sm" icon={ShieldCheck} loading={approvingId === r.id} onClick={() => approve(r)}>Sign off as BA</Button>
                    )}
                  </div>
                ),
              },
            ]}
          />
        </Card>
      )}

      {/* Create */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={`New requirement document — ${project?.title || ""}`}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button loading={submitting} icon={Plus} onClick={submit}>Submit for review</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Select
            label="Document type"
            required
            options={DOC_TYPES}
            value={values.docType}
            onChange={(e) => setValues((v) => ({ ...v, docType: e.target.value }))}
          />
          <Input
            label="Title"
            required
            placeholder="e.g. Storefront Checkout — Business Requirements"
            value={values.title}
            onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))}
            error={errors.title}
          />
          <div className="rounded-lg border border-dashed border-primary-300 bg-primary-50/40 p-3">
            <p className="text-xs font-semibold text-ink-700 mb-2 flex items-center gap-1.5">
              <UploadCloud size={14} className="text-primary-600" /> Import text from a file (optional)
            </p>
            <FileUpload hint=".docx or .txt — extracted straight into the content field below" accept=".docx,.txt" onChange={handleImport} />
            {importing && <p className="text-xs text-ink-500 mt-2">Reading file…</p>}
          </div>
          <Textarea
            label="Content"
            required
            rows={12}
            placeholder="Write (or paste) the full requirement text here…"
            value={values.content}
            onChange={(e) => setValues((v) => ({ ...v, content: e.target.value }))}
            error={errors.content}
          />
          <p className="text-xs text-ink-400 -mt-2">
            This creates version {(docs.filter((d) => d.docType === values.docType).sort((a, b) => b.version - a.version)[0]?.version || 0) + 1} of the {values.docType} for this project — earlier versions stay on record.
          </p>
        </form>
      </Modal>

      {/* View */}
      <Modal
        open={!!viewingId}
        onClose={() => setViewingId(null)}
        title={viewingDoc?.title || "Document"}
        size={docExpanded ? "full" : "lg"}
        footer={
          <div className="flex justify-between w-full items-center gap-3">
            {viewingDoc && <ApprovalSlots approvals={viewingDoc.approvals} />}
            <div className="flex gap-2 shrink-0">
              <Button
                variant="secondary"
                icon={Download}
                disabled={!viewingDoc}
                onClick={() => downloadTextFile(viewingDoc.title || "document", viewingDoc.content)}
              >
                Download
              </Button>
              {viewingDoc && viewingDoc.status !== "APPROVED" && viewingDoc.authoredByUuid !== user?.uuid && (
                <Button icon={ShieldCheck} loading={approvingId === viewingDoc.id} onClick={() => approve(viewingDoc)}>Sign off as BA</Button>
              )}
              <Button variant="secondary" onClick={() => setViewingId(null)}>Close</Button>
            </div>
          </div>
        }
      >
        {loadingView ? (
          <div className="flex justify-center py-12"><LoadingSpinner label="Loading document…" /></div>
        ) : viewingDoc && (
          <div className={`flex flex-col gap-4 text-left font-sans ${docExpanded ? "h-full" : ""}`}>
            <div className="flex justify-between items-start border-b border-border pb-3">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-primary-700">{viewingDoc.docType.replace("_", " ")} · v{viewingDoc.version}</span>
                <p className="text-xs text-ink-500 mt-1">
                  Authored by {viewingDoc.authoredByName || "—"}
                  {viewingDoc.approvedByName && <> · Approved by {viewingDoc.approvedByName}</>}
                  {viewingDoc.devReviewedAt && <> · Reviewed by developer {viewingDoc.devReviewedByName}</>}
                </p>
              </div>
              <Button size="sm" variant="ghost" icon={docExpanded ? Minimize2 : Maximize2} onClick={() => setDocExpanded((v) => !v)}>
                {docExpanded ? "Shrink" : "Full screen"}
              </Button>
            </div>
            {viewingDoc.status !== "APPROVED" && viewingDoc.authoredByUuid === user?.uuid && (
              <p className="text-xs text-ink-500 bg-cream-50 border border-border rounded-lg px-3 py-2">
                You authored this document — another Business Analyst needs to sign off the BA slot.
              </p>
            )}
            <div className={`p-4 bg-cream-50 rounded-lg border border-border text-sm leading-relaxed text-ink-800 whitespace-pre-wrap overflow-y-auto ${docExpanded ? "flex-1" : "max-h-[50vh]"}`}>
              {viewingDoc.content}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
