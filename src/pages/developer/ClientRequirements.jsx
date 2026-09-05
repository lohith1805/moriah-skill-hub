import { useEffect, useState } from "react";
import { Eye, CheckCircle2, ClipboardCheck, Download, Maximize2, Minimize2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { getDevRequirementDocs, getRequirementDocDetail, markDocReviewed } from "../../services/developerService";
import { useToast } from "../../context/ToastContext";
import { downloadTextFile } from "../../utils/downloadTextFile";

const DOC_TYPE_LABEL = { BRD: "BRD", SRS: "SRS", FRS: "FRS", USER_STORY: "User Story", OTHER: "Other" };

/**
 * Read-only reference: the finalized specs a developer builds against, once
 * every party (client/BA/developer, per doc type) has signed off — see
 * RequirementDocumentApprovalService on the backend. Still-pending documents
 * needing THIS developer's own sign-off live under "Client Project
 * Documents" (pages/shared/ClientProjectDocuments), not here.
 */
export default function DeveloperClientRequirements() {
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewingDoc, setViewingDoc] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getDevRequirementDocs({ status: "APPROVED" })
      .then(setDocs)
      .catch((e) => notify(e.message || "Could not load requirement documents.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openDoc = async (doc) => {
    setViewingDoc(doc);
    setDetail(null);
    setExpanded(false);
    setDetailLoading(true);
    try {
      setDetail(await getRequirementDocDetail(doc.id));
    } catch (err) {
      notify(err?.message || "Couldn't load this document.", { type: "error" });
    } finally {
      setDetailLoading(false);
    }
  };

  const handleMarkReviewed = async (doc) => {
    await markDocReviewed(doc.id);
    notify(`"${doc.title}" marked as reviewed.`, { type: "success", title: "Document Reviewed" });
    load();
    if (viewingDoc?.id === doc.id) openDoc(doc);
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Requirements"
        subtitle="Finalized BRD / SRS / FRS specs — every required sign-off already recorded — read the content and mark it reviewed before you start building"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Client Requirements" }]}
      />

      <Card>
        <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
          <div>
            <h3 className="font-display font-semibold text-ink-900">Approved Requirements</h3>
            <p className="text-xs text-ink-500">
              Still-pending documents that need your own sign-off live under "Client Project Documents" in the sidebar.
            </p>
          </div>
          <Badge tone="primary" dot>Synced from BA</Badge>
        </div>

        <Table
          loading={loading}
          data={docs}
          emptyTitle="No approved requirement documents yet"
          emptyHint="Once every required party signs off a BRD, SRS, or FRS, it will show up here automatically."
          columns={[
            { key: "title", header: "Document Title", className: "text-left font-medium text-ink-900" },
            { key: "docType", header: "Spec Type", className: "text-left", render: (r) => <Badge tone="primary">{DOC_TYPE_LABEL[r.docType] || r.docType}</Badge> },
            { key: "version", header: "Version", className: "text-left text-xs", render: (r) => `v${r.version}` },
            { key: "authoredBy", header: "Authored By", className: "text-left text-xs" },
            {
              key: "devReview",
              header: "Your Review",
              className: "text-left",
              render: (r) =>
                r.devReviewed ? (
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
                  <Button size="sm" variant="secondary" icon={Eye} onClick={() => openDoc(r)}>Inspect</Button>
                  {!r.devReviewed && (
                    <Button size="sm" icon={ClipboardCheck} onClick={() => handleMarkReviewed(r)}>Mark Reviewed</Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={!!viewingDoc}
        onClose={() => setViewingDoc(null)}
        title={viewingDoc?.title || "Document Viewer"}
        size={expanded ? "full" : "lg"}
        footer={
          <div className="flex justify-between w-full items-center">
            <Badge tone="success">Status: {viewingDoc?.status}</Badge>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                icon={Download}
                disabled={!detail}
                onClick={() => downloadTextFile(detail.title || "document", detail.content)}
              >
                Download
              </Button>
              {viewingDoc && !viewingDoc.devReviewed && (
                <Button icon={ClipboardCheck} onClick={() => handleMarkReviewed(viewingDoc)}>Mark as Reviewed</Button>
              )}
              <Button variant="secondary" onClick={() => setViewingDoc(null)}>Close</Button>
            </div>
          </div>
        }
      >
        {detailLoading || !detail ? (
          <p className="text-sm text-ink-400 py-8 text-center">Loading…</p>
        ) : (
          <div className={`p-4 bg-white rounded-xl border border-border flex flex-col gap-4 text-left font-sans ${expanded ? "h-full" : ""}`}>
            <div className="flex justify-between items-start border-b border-border pb-3">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-primary-700">
                  {DOC_TYPE_LABEL[detail.docType] || detail.docType} · v{detail.version}
                </span>
                <h3 className="text-base font-bold text-ink-900 mt-1">{detail.title}</h3>
                <p className="text-xs text-ink-500">Authored by {detail.authoredBy} · Approved by {detail.approvedBy || "—"}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone="success">{detail.status}</Badge>
                <Button size="sm" variant="ghost" icon={expanded ? Minimize2 : Maximize2} onClick={() => setExpanded((v) => !v)}>
                  {expanded ? "Shrink" : "Full screen"}
                </Button>
              </div>
            </div>
            <div className={`p-3 bg-cream-50 rounded-lg border border-border text-sm leading-relaxed text-ink-800 whitespace-pre-wrap overflow-y-auto ${expanded ? "flex-1" : "max-h-96"}`}>
              {detail.content || <span className="text-ink-400">No content.</span>}
            </div>
            {detail.devReviewed && (
              <p className="text-xs text-ink-500 flex items-center gap-1">
                <CheckCircle2 size={14} className="text-success-500" />
                Reviewed by {detail.devReviewedBy} on {detail.devReviewedAt}
              </p>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
