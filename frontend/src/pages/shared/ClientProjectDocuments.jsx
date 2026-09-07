import { useEffect, useState } from "react";
import { Eye, CheckCircle2, Clock, FileText, Download, Maximize2, Minimize2, XCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import { Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../utils/constants";
import { downloadTextFile } from "../../utils/downloadTextFile";
import {
  getPendingMyApprovals,
  getClientProjectDocumentDetail,
  approveClientProjectDocument,
  rejectClientProjectDocument,
} from "../../services/requirementDocumentService";

const DASHBOARD_PATH_BY_ROLE = {
  [ROLES.CLIENT]: "/client/dashboard",
  [ROLES.DEVELOPER]: "/developer/dashboard",
  [ROLES.BUSINESS_ANALYST]: "/ba/dashboard",
};

const ROLE_ORDER = ["CLIENT", "BUSINESS_ANALYST", "DEVELOPER"];
const ROLE_LABEL = { CLIENT: "Client", BUSINESS_ANALYST: "Business Analyst", DEVELOPER: "Developer" };
const DOC_TYPE_LABEL = { BRD: "BRD", SRS: "SRS", FRS: "FRS", USER_STORY: "User Story", OTHER: "Other" };

function ApprovalSlots({ approvals }) {
  const ordered = [...(approvals || [])].sort((a, b) => ROLE_ORDER.indexOf(a.role) - ROLE_ORDER.indexOf(b.role));
  return (
    <div className="flex flex-wrap gap-1.5">
      {ordered.map((a) => (
        <Badge key={a.role} tone={a.pending ? "neutral" : "success"} title={a.pending ? "Pending" : `Approved by ${a.approvedByName}`}>
          {a.pending ? <Clock size={11} className="inline mr-1" /> : <CheckCircle2 size={11} className="inline mr-1" />}
          {ROLE_LABEL[a.role] || a.role}
        </Badge>
      ))}
    </div>
  );
}

/**
 * The one cross-role "Client Project Documents" section — a CLIENT, a
 * project's assigned DEVELOPER, and any BUSINESS_ANALYST (other than a
 * document's own author) all land here to see and act on the same inbox:
 * documents where their own sign-off slot is still open. One backend
 * endpoint (GET /requirement-documents/pending-my-approval) infers whose
 * turn it is, so this single component is mounted under all three portals —
 * see AppRoutes.jsx.
 */
export default function ClientProjectDocuments() {
  const { notify } = useToast();
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [viewing, setViewing] = useState(null); // { ...pending row, detail: null | {...} }
  const [detailLoading, setDetailLoading] = useState(false);
  const [approving, setApproving] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const load = () => {
    setLoading(true);
    getPendingMyApprovals()
      .then(setRows)
      .catch((e) => notify(e?.message || "Couldn't load your pending documents.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openRow = async (row) => {
    setViewing({ ...row, detail: null });
    setExpanded(false);
    setRejectMode(false);
    setRejectReason("");
    setDetailLoading(true);
    try {
      const detail = await getClientProjectDocumentDetail(row.documentId);
      setViewing({ ...row, detail });
    } catch (err) {
      notify(err?.message || "Couldn't load this document.", { type: "error" });
      setViewing(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const approve = async () => {
    if (!viewing) return;
    setApproving(true);
    try {
      await approveClientProjectDocument(viewing.documentId);
      notify(`Signed off on "${viewing.title}".`, { type: "success", title: "Approved" });
      setViewing(null);
      load();
    } catch (err) {
      notify(err?.message || "Couldn't record your approval. Please try again.", { type: "error" });
    } finally {
      setApproving(false);
    }
  };

  const reject = async (e) => {
    e.preventDefault();
    if (!viewing || !rejectReason.trim()) return;
    setRejecting(true);
    try {
      await rejectClientProjectDocument(viewing.documentId, rejectReason.trim());
      notify(`Rejected "${viewing.title}" — the author will need to submit a revised version.`, { type: "success", title: "Rejected" });
      setViewing(null);
      load();
    } catch (err) {
      notify(err?.message || "Couldn't record the rejection. Please try again.", { type: "error" });
    } finally {
      setRejecting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Project Documents"
        subtitle="Requirement documents waiting on your own sign-off — client scope confirmation, BA authoring, or developer build commitment"
        breadcrumbs={[
          { label: "Dashboard", to: DASHBOARD_PATH_BY_ROLE[user?.role] || "/" },
          { label: "Client Project Documents" },
        ]}
      />

      <Card>
        {!loading && rows.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="Nothing waiting on you"
            description="Once a client project document needs your sign-off, it will show up here."
          />
        ) : (
          <Table
            loading={loading}
            data={rows}
            columns={[
              {
                key: "title",
                header: "Document",
                className: "text-left font-medium text-ink-900",
                render: (r) => (
                  <div>
                    <p className="font-semibold text-ink-900">{r.title}</p>
                    <p className="text-xs text-ink-500 mt-0.5">{r.clientProjectTitle}</p>
                  </div>
                ),
              },
              { key: "docType", header: "Type", className: "text-left", render: (r) => <Badge tone="primary">{DOC_TYPE_LABEL[r.docType] || r.docType}</Badge> },
              { key: "version", header: "Version", className: "text-left text-xs", render: (r) => `v${r.version}` },
              { key: "authoredByName", header: "Authored By", className: "text-left text-xs" },
              {
                key: "approverRole",
                header: "Waiting On",
                className: "text-left",
                render: (r) => <Badge tone="warning">{ROLE_LABEL[r.approverRole] || r.approverRole} (you)</Badge>,
              },
              {
                key: "action",
                header: "",
                className: "text-right",
                render: (r) => (
                  <Button size="sm" icon={Eye} onClick={() => openRow(r)}>Review</Button>
                ),
              },
            ]}
          />
        )}
      </Card>

      <Modal
        open={!!viewing}
        onClose={() => setViewing(null)}
        title={viewing?.title || "Document"}
        size={expanded ? "full" : "lg"}
        footer={
          rejectMode ? (
            <>
              <Button variant="secondary" onClick={() => setRejectMode(false)}>Back</Button>
              <Button
                variant="danger"
                icon={XCircle}
                loading={rejecting}
                disabled={!rejectReason.trim()}
                onClick={reject}
              >
                Confirm Rejection
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="secondary"
                icon={Download}
                disabled={!viewing?.detail}
                onClick={() => downloadTextFile(viewing.title || "document", viewing.detail.content)}
              >
                Download
              </Button>
              <Button variant="secondary" onClick={() => setViewing(null)}>Close</Button>
              <Button variant="secondary" icon={XCircle} disabled={detailLoading} onClick={() => setRejectMode(true)}>
                Reject
              </Button>
              <Button icon={CheckCircle2} loading={approving} disabled={detailLoading} onClick={approve}>
                Approve as {ROLE_LABEL[viewing?.approverRole] || viewing?.approverRole}
              </Button>
            </>
          )
        }
      >
        {detailLoading || !viewing?.detail ? (
          <p className="text-sm text-ink-400 py-8 text-center">Loading…</p>
        ) : rejectMode ? (
          <form className="flex flex-col gap-4 text-left font-sans" onSubmit={reject}>
            <p className="text-sm text-ink-600">
              Rejecting <span className="font-semibold text-ink-900">"{viewing.title}"</span> — this kills the
              document immediately, even if other parties already signed off. The author will need to submit a
              revised version; this one can't be edited in place.
            </p>
            <Textarea
              label="Reason for rejection"
              required
              autoFocus
              rows={4}
              placeholder="e.g. Scope doesn't cover the reporting dashboard we discussed on the kickoff call."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
          </form>
        ) : (
          <div className={`flex flex-col gap-4 text-left font-sans ${expanded ? "h-full" : ""}`}>
            <div className="flex items-center justify-between border-b border-border pb-3">
              <div>
                <span className="text-xs font-bold uppercase tracking-wider text-primary-700">
                  {DOC_TYPE_LABEL[viewing.detail.docType] || viewing.detail.docType} · v{viewing.detail.version}
                </span>
                <p className="text-xs text-ink-500 mt-1">{viewing.clientProjectTitle} · Authored by {viewing.detail.authoredByName}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={viewing.detail.status === "APPROVED" ? "success" : "warning"}>{viewing.detail.statusLabel}</Badge>
                <Button size="sm" variant="ghost" icon={expanded ? Minimize2 : Maximize2} onClick={() => setExpanded((v) => !v)}>
                  {expanded ? "Shrink" : "Full screen"}
                </Button>
              </div>
            </div>
            <div>
              <p className="text-xs font-semibold text-ink-700 mb-1.5">Sign-off status</p>
              <ApprovalSlots approvals={viewing.detail.approvals} />
            </div>
            <div className={`p-3 bg-cream-50 rounded-lg border border-border text-sm leading-relaxed text-ink-800 whitespace-pre-wrap overflow-y-auto ${expanded ? "flex-1" : "max-h-96"}`}>
              {viewing.detail.content || <span className="text-ink-400">No content.</span>}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
