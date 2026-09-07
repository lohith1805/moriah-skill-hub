import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import {
  FileText, CheckCircle2, XCircle, Clock, UploadCloud, Eye, PartyPopper, ShieldCheck,
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { Textarea } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { ROLES } from "../../utils/constants";
import {
  getHrDocuments,
  uploadHrDocument,
  verifyHrDocument,
  ONBOARDING_DOC_SECTIONS,
} from "../../services/hrService";

const DASHBOARD_BY_ROLE = {
  [ROLES.HR]: "/hr/dashboard",
  [ROLES.DEVELOPER]: "/developer/dashboard",
  [ROLES.BUSINESS_ANALYST]: "/ba/dashboard",
  [ROLES.LEAD_GENERATOR]: "/leads/dashboard",
  [ROLES.TRAINER]: "/trainer/dashboard",
};

const STATUS_TONE = { Verified: "success", Pending: "warning", Rejected: "error" };

export default function OnboardingDocuments() {
  const { userUuid } = useParams(); // set only on the HR review route /hr/employee-documents/:userUuid
  const { user } = useAuth();
  const { notify } = useToast();
  const hrReview = !!userUuid;

  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(null); // section code being uploaded
  const [busyId, setBusyId] = useState(null); // doc id being verified
  const [viewing, setViewing] = useState(null); // doc to preview
  const [rejecting, setRejecting] = useState(null); // doc being rejected
  const [rejectReason, setRejectReason] = useState("");

  const load = () => {
    setLoading(true);
    getHrDocuments(hrReview ? { userUuid } : undefined)
      .then(setDocs)
      .catch((e) => notify(e?.message || "Couldn't load documents.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userUuid]);

  // Latest row per document type — a re-upload after a rejection is a new row.
  const latestByType = useMemo(() => {
    const map = {};
    for (const d of docs) {
      const cur = map[d.documentType];
      if (!cur || new Date(d.createdAt) > new Date(cur.createdAt)) map[d.documentType] = d;
    }
    return map;
  }, [docs]);

  const allVerified = ONBOARDING_DOC_SECTIONS.every((s) => latestByType[s.code]?.status === "Verified");

  const handleUpload = async (section, files) => {
    const file = files?.[0];
    if (!file) return;
    setUploading(section.code);
    try {
      await uploadHrDocument(file, section.code, hrReview ? userUuid : undefined);
      notify(`${section.label} uploaded — awaiting HR review.`, { type: "success" });
      load();
    } catch (e) {
      notify(e?.message || "Upload failed. PDF only, up to 10 MB.", { type: "error" });
    } finally {
      setUploading(null);
    }
  };

  const approve = async (doc) => {
    setBusyId(doc.id);
    try {
      await verifyHrDocument(doc.id, "Verified");
      notify("Document approved.", { type: "success" });
      load();
    } catch (e) {
      notify(e?.message || "Couldn't approve.", { type: "error" });
    } finally {
      setBusyId(null);
    }
  };

  const submitReject = async (e) => {
    e.preventDefault();
    if (!rejecting || !rejectReason.trim()) return;
    setBusyId(rejecting.id);
    try {
      await verifyHrDocument(rejecting.id, "Rejected", rejectReason.trim());
      notify("Document rejected — the joiner will be asked to re-upload.", { type: "success" });
      setRejecting(null);
      setRejectReason("");
      load();
    } catch (err) {
      notify(err?.message || "Couldn't reject.", { type: "error" });
    } finally {
      setBusyId(null);
    }
  };

  const title = hrReview ? "Employee Onboarding Documents" : "My Onboarding Documents";
  const subtitle = hrReview
    ? "Review, approve or reject each document. Upload the background-check report yourself."
    : "Upload each document as a PDF. HR reviews them — a rejected document turns red and can be re-uploaded.";

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={title}
        subtitle={subtitle}
        breadcrumbs={[{ label: "Dashboard", to: DASHBOARD_BY_ROLE[user?.role] || "/" }, { label: "Onboarding Documents" }]}
      />

      {!hrReview && allVerified && !loading && (
        <Card className="border-l-4 border-l-success-500 bg-gradient-to-br from-success-50/70 to-white">
          <div className="flex items-start gap-4 p-2">
            <PartyPopper className="text-success-600 shrink-0" size={40} />
            <div>
              <h3 className="font-display text-xl font-bold text-ink-900">Congratulations — your onboarding is complete!</h3>
              <p className="text-sm text-ink-600 mt-1">
                All your documents are verified and your background check has cleared. Your welcome kit is on its
                way to you. Welcome aboard. 🎉
              </p>
            </div>
          </div>
        </Card>
      )}

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading documents…" /></div>
      ) : (
        <div className="flex flex-col gap-4">
          {ONBOARDING_DOC_SECTIONS.map((section) => {
            const doc = latestByType[section.code];
            const status = doc?.status || "Not uploaded";
            const isHrSection = section.who === "HR";
            const canUpload = hrReview ? isHrSection : !isHrSection;
            const showUploader = canUpload && (!doc || status === "Rejected");
            const rejected = status === "Rejected";

            return (
              <Card
                key={section.code}
                className={rejected ? "border-l-4 border-l-error-500" : "border-l-4 border-l-border"}
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-start justify-between gap-3 flex-wrap">
                    <div className="flex items-start gap-3">
                      <FileText size={20} className="text-primary-600 mt-0.5 shrink-0" />
                      <div>
                        <p className="font-semibold text-ink-900">
                          {section.label}
                          {isHrSection && <span className="ml-2 text-[11px] font-medium text-ink-400">HR uploads this</span>}
                        </p>
                        <p className="text-xs text-ink-500 mt-0.5">{section.hint}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {status === "Verified" && <Badge tone="success"><CheckCircle2 size={11} className="inline mr-1" />Verified</Badge>}
                      {status === "Pending" && <Badge tone="warning"><Clock size={11} className="inline mr-1" />Awaiting review</Badge>}
                      {status === "Rejected" && <Badge tone="error"><XCircle size={11} className="inline mr-1" />Rejected</Badge>}
                      {status === "Not uploaded" && (
                        <Badge tone="neutral">{isHrSection && !hrReview ? "In progress" : "Not uploaded"}</Badge>
                      )}
                      {doc?.downloadUrl && (
                        <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewing(doc)}>View</Button>
                      )}
                    </div>
                  </div>

                  {rejected && doc?.rejectionReason && (
                    <p className="text-xs text-error-700 bg-error-50 border border-error-200 rounded-lg px-3 py-2">
                      Rejected by HR: <span className="italic">"{doc.rejectionReason}"</span>
                      {!hrReview && " — please upload a corrected version below."}
                    </p>
                  )}

                  {showUploader && (
                    <div className="rounded-lg border border-dashed border-primary-300 bg-primary-50/40 p-3">
                      <FileUpload
                        hint="PDF only, up to 10 MB"
                        accept=".pdf,application/pdf"
                        onChange={(files) => handleUpload(section, files)}
                      />
                      {uploading === section.code && <p className="text-xs text-ink-500 mt-2">Uploading…</p>}
                    </div>
                  )}

                  {hrReview && doc && status === "Pending" && (
                    <div className="flex items-center gap-2 justify-end">
                      <Button size="sm" variant="secondary" icon={XCircle} disabled={busyId === doc.id}
                        onClick={() => { setRejecting(doc); setRejectReason(""); }}>
                        Reject
                      </Button>
                      <Button size="sm" icon={ShieldCheck} loading={busyId === doc.id} onClick={() => approve(doc)}>
                        Approve
                      </Button>
                    </div>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Large document preview */}
      <Modal open={!!viewing} onClose={() => setViewing(null)} title={viewing?.documentType?.replace(/_/g, " ") || "Document"} size="full">
        {viewing?.downloadUrl ? (
          <iframe
            src={viewing.downloadUrl}
            title="Document preview"
            className="w-full h-[78vh] rounded-lg border border-border bg-white"
          />
        ) : (
          <p className="text-sm text-ink-400 py-8 text-center">No preview available.</p>
        )}
      </Modal>

      {/* Reject reason */}
      <Modal
        open={!!rejecting}
        onClose={() => setRejecting(null)}
        title="Reject document"
        footer={
          <>
            <Button variant="secondary" onClick={() => setRejecting(null)}>Cancel</Button>
            <Button variant="danger" icon={XCircle} disabled={!rejectReason.trim()} loading={busyId === rejecting?.id} onClick={submitReject}>
              Confirm Rejection
            </Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submitReject}>
          <p className="text-sm text-ink-600">
            The joiner will be notified and asked to re-upload{" "}
            <span className="font-semibold text-ink-900">{rejecting?.documentType?.replace(/_/g, " ")}</span>.
          </p>
          <Textarea
            label="Reason for rejection"
            required
            autoFocus
            rows={4}
            placeholder="e.g. The scan is cut off at the bottom — please re-upload the full page."
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
        </form>
      </Modal>
    </div>
  );
}
