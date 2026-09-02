import { useEffect, useState } from "react";
import { Eye, Download, FileText, CheckCircle2, ClipboardCheck } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getDocuments } from "../../services/baService";
import { getDocReviews, markDocReviewed } from "../../services/developerService";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";

const DOC_TYPES = [
  { value: "BRD", label: "Business Requirements Document (BRD)" },
  { value: "SRS", label: "System Requirements Specification (SRS)" },
  { value: "FRS", label: "Functional Requirements Specification (FRS)" },
];

// Client-attached briefs arrive tagged "CLIENT_DOC" — kept separate from
// BRD/SRS/FRS since it's the client's raw input, not the BA-authored spec.
const CLIENT_DOC_TYPE = { value: "CLIENT_DOC", label: "Client-Submitted Brief" };

function typeBadge(t) {
  if (t === "CLIENT_DOC") return { label: "Client Brief", tone: "neutral" };
  if (t === "BRD") return { label: "BRD", tone: "primary" };
  if (t === "SRS") return { label: "SRS", tone: "gold" };
  if (t === "FRS") return { label: "FRS", tone: "success" };
  return { label: t, tone: "neutral" };
}

// Same normalizer BA's Documents.jsx uses, kept local so this page has no
// write-access dependency on the BA module — it only ever reads documents.
function normalizedFiles(doc) {
  return (doc?.files || []).map((f) => ({ ...f, type: f.type || doc.type || "BRD" }));
}

function getFileUrl(doc, type) {
  if (!doc?.fileData) return null;
  if (typeof doc.fileData === "string") {
    return type === (doc.type || "BRD") ? doc.fileData : null;
  }
  return doc.fileData[type] || null;
}

export default function DeveloperClientRequirements() {
  const [docs, setDocs] = useState([]);
  const [reviews, setReviews] = useState({});
  const [loading, setLoading] = useState(true);
  const [viewingDoc, setViewingDoc] = useState(null);
  const { notify } = useToast();
  const { user } = useAuth();

  const load = () => {
    setLoading(true);
    Promise.all([getDocuments(), getDocReviews()]).then(([d, r]) => {
      // Developers build off signed-off requirements — surface Approved
      // docs first, but still show ones still Under Review so nothing is
      // a surprise once BA verifies them.
      setDocs(d);
      setReviews(r);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

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
  };

  const handleMarkReviewed = async (doc) => {
    await markDocReviewed(doc.id, user?.name || "Developer");
    const r = await getDocReviews();
    setReviews(r);
    notify(`"${doc.title}" marked as reviewed. BA and Trainer can see this was checked.`, {
      type: "success",
      title: "Document Reviewed",
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Requirements"
        subtitle="Read-only view of BRD / SRS / FRS files the Business Analyst has uploaded and verified — check them before you start building"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Client Requirements" }]}
      />

      <Card>
        <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
          <div>
            <h3 className="font-display font-semibold text-ink-900">Requirements Handed Off by BA (MSH-FR-BA-01)</h3>
            <p className="text-xs text-ink-500">
              These are the same specs the Business Analyst authors and verifies — you can inspect and download them here, but only BA can edit or delete.
            </p>
          </div>
          <Badge tone="primary" dot>Synced from BA</Badge>
        </div>

        <Table
          loading={loading}
          data={docs}
          emptyTitle="No requirement documents yet"
          emptyHint="Once a Business Analyst uploads a BRD, SRS, or FRS, it will show up here automatically."
          columns={[
            {
              key: "title",
              header: "Document Title",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.title}</p>
                  <p className="text-xs text-ink-500 line-clamp-1 mt-0.5">{r.client}</p>
                </div>
              ),
            },
            {
              key: "type",
              header: "Spec Type",
              className: "text-left",
              render: (r) => {
                const types = Array.from(new Set(normalizedFiles(r).map((f) => f.type)));
                if (!types.length) return <span className="text-xs text-ink-400">—</span>;
                return (
                  <div className="flex flex-wrap gap-1">
                    {types.map((t) => {
                      const { label, tone } = typeBadge(t);
                      return <Badge key={t} tone={tone}>{label}</Badge>;
                    })}
                  </div>
                );
              },
            },
            {
              key: "status",
              header: "BA Status",
              className: "text-left",
              render: (r) => (
                <Badge tone={r.status === "Approved" ? "success" : r.status === "Under Review" ? "warning" : "neutral"}>
                  {r.status}
                </Badge>
              ),
            },
            {
              key: "devReview",
              header: "Dev Check",
              className: "text-left",
              render: (r) =>
                reviews[r.id]?.reviewed ? (
                  <Badge tone="success"><CheckCircle2 size={12} className="inline mr-1" />Reviewed</Badge>
                ) : (
                  <Badge tone="neutral">Not checked yet</Badge>
                ),
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingDoc(r)}>Inspect</Button>
                  {!reviews[r.id]?.reviewed && (
                    <Button size="sm" icon={ClipboardCheck} onClick={() => handleMarkReviewed(r)}>Mark Reviewed</Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>

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
                  <p>No files attached to this requirement yet.</p>
                </div>
              )}
            </div>

            {!reviews[viewingDoc.id]?.reviewed && (
              <Button icon={ClipboardCheck} onClick={() => handleMarkReviewed(viewingDoc)}>Mark as Reviewed</Button>
            )}
            {reviews[viewingDoc.id]?.reviewed && (
              <p className="text-xs text-ink-500 flex items-center gap-1">
                <CheckCircle2 size={14} className="text-success-500" />
                Reviewed by {reviews[viewingDoc.id].reviewedBy} on {reviews[viewingDoc.id].reviewedAt}
              </p>
            )}
          </div>
        )}
        {loading && <LoadingSpinner label="Loading…" />}
      </Modal>
    </div>
  );
}
