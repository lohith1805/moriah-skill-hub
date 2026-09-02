import { useEffect, useState } from "react";
import { CalendarClock, Video, Briefcase, CalendarX2, ThumbsUp, ThumbsDown, FileSignature, ShieldCheck, UploadCloud, CheckCircle2, Users, Eye, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import FileUpload from "../../components/ui/FileUpload";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import {
  REJECTED, stageTone, stageIndex, stageMessage, loadRecruitments, saveRecruitments, loadDocs, saveDocs,
  freshDocumentChecklist, REQUIRED_DOCUMENTS, fileToDataURL
} from "../../utils/placementPipeline";
import { renderTemplateText } from "../../utils/letterTemplates";

// Mirrors the generator in client/TalentPool.jsx. Kept here too so that if
// a student opens this page before any backfill has run on the client side,
// they still get a real, persisted link instead of "Link pending" forever.
function generateMeetingLink() {
  const roomCode =
    (typeof crypto !== "undefined" && crypto.randomUUID)
      ? crypto.randomUUID().split("-")[0]
      : Math.random().toString(36).slice(2, 10);
  return `${window.location.origin}/interview-room/${roomCode}`;
}

export default function StudentInterviews() {
  const { user } = useAuth();
  const [interviews, setInterviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [signingOffer, setSigningOffer] = useState(null); // recruitment record at "Student Signature"
  const [viewingOffer, setViewingOffer] = useState(null); // recruitment record at "Student Approval" being previewed
  const [uploadingFor, setUploadingFor] = useState(null); // recruitment record the doc-upload modal is open for
  const [pendingFiles, setPendingFiles] = useState({}); // { [docKey]: File[] } while the upload modal is open
  const { notify } = useToast();

  // Docs the student themselves is responsible for attaching (interview
  // feedback form is HR/interviewer-authored, so it's excluded here).
  const studentDocs = REQUIRED_DOCUMENTS.filter((d) => d.uploadedBy === "student");

  const load = () => {
    // Recruitment/interview records are created by a Corporate Client from
    // the Talent Pool screen and stored locally; a student only sees the
    // rows that were scheduled against their own name.
    try {
      const all = loadRecruitments();
      let didBackfill = false;
      const withLinks = all.map((r) => {
        if (r.meetingLink) return r;
        didBackfill = true;
        return { ...r, meetingLink: generateMeetingLink() };
      });
      if (didBackfill) {
        saveRecruitments(withLinks);
      }
      const mine = user ? withLinks.filter((r) => r.candidateName === user.name) : [];
      setInterviews(mine);
    } catch (e) {
      setInterviews([]);
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, [user]);

  // Student Approval step: candidate reviews the (already client-signed)
  // offer and decides to move forward or decline.
  const handleApproval = (id, decision) => {
    const all = loadRecruitments();
    const updated = all.map((r) => {
      if (r.id !== id) return r;
      if (decision === "approve") {
        return { ...r, stage: "Student Signature", studentApprovalStatus: "Approved" };
      }
      return { ...r, stage: REJECTED, rejectedAt: "Student Approval", studentApprovalStatus: "Rejected" };
    });
    saveRecruitments(updated);
    setInterviews(updated.filter((r) => r.candidateName === user?.name));
    notify(
      decision === "approve"
        ? "Offer approved — please add your digital signature to finish."
        : "Offer declined.",
      { type: decision === "approve" ? "success" : "info" }
    );
    setViewingOffer(null);
  };

  // Lets the student download a copy of the offer letter for their own
  // records — simulated file, since there's no real file storage backend.
  const downloadOfferLetter = (record) => {
    const doc = loadDocs().find((d) => d.id === record?.offerDocId);
    const blob = new Blob([doc ? renderTemplateText(doc) : ""], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `offer_letter_${(record?.candidateName || "candidate").toLowerCase().replace(/\s+/g, "_")}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    notify("Downloading your offer letter…", { type: "info" });
  };

  // Final step: student e-signs, and the system automatically marks the
  // placement Completed/Placed. This is the ONLY path that can produce
  // stage "Placed" — it can never be reached directly from interview
  // approval, since Document Verification, Placement Confirmation, the
  // offer letter, and both signatures all have to happen first.
  const handleSign = () => {
    if (!signingOffer) return;
    const docs = loadDocs();
    const updatedDocs = docs.map((d) =>
      d.id === signingOffer.offerDocId ? { ...d, signStatus: "Digitally Signed", signedAt: new Date().toISOString() } : d
    );
    saveDocs(updatedDocs);

    const all = loadRecruitments();
    const updated = all.map((r) =>
      r.id === signingOffer.id
        ? { ...r, stage: "Placed", studentSignedAt: new Date().toISOString() }
        : r
    );
    saveRecruitments(updated);
    setInterviews(updated.filter((r) => r.candidateName === user?.name));
    notify("Digital signature captured — your placement is now complete! 🎉", { type: "success", title: "Placement Completed" });
    setSigningOffer(null);
  };

  const offerDoc = signingOffer ? loadDocs().find((d) => d.id === signingOffer.offerDocId) : null;
  const viewingOfferDoc = viewingOffer ? loadDocs().find((d) => d.id === viewingOffer.offerDocId) : null;

  // Document Verification step: the student uploads their own resume, ID
  // proof and education certificates here so HR has something to actually
  // verify. HR's "Confirm Documents Verified" stays disabled per-document
  // until studentUploaded is true (see hr/Documents.jsx).
  const openUpload = (record) => {
    const checklist = record.documents && record.documents.length ? record.documents : freshDocumentChecklist();
    setPendingFiles(
      Object.fromEntries(
        studentDocs.map((d) => {
          const existing = checklist.find((c) => c.key === d.key);
          return [d.key, existing?.fileName ? [{ name: existing.fileName }] : []];
        })
      )
    );
    setUploadingFor(record);
  };

  const [savingUploads, setSavingUploads] = useState(false);

  const handleSaveUploads = async () => {
    if (!uploadingFor) return;
    const missing = studentDocs.filter((d) => !pendingFiles[d.key]?.length);
    if (missing.length) {
      notify(`Please attach a file for: ${missing.map((d) => d.label).join(", ")}.`, { type: "error", title: "Missing documents" });
      return;
    }

    setSavingUploads(true);
    try {
      // Only real File objects (a fresh attach/replace in this session) get
      // converted to a data URL — a doc that's still showing its previously
      // saved fileName untouched is left exactly as it was.
      const fileDataByKey = {};
      await Promise.all(
        studentDocs.map(async (d) => {
          const picked = pendingFiles[d.key]?.[0];
          if (picked instanceof File) {
            fileDataByKey[d.key] = { dataUrl: await fileToDataURL(picked), name: picked.name, type: picked.type };
          }
        })
      );

      const all = loadRecruitments();
      const updated = all.map((r) => {
        if (r.id !== uploadingFor.id) return r;
        const checklist = r.documents && r.documents.length ? r.documents : freshDocumentChecklist();
        return {
          ...r,
          documents: checklist.map((d) => {
            const picked = fileDataByKey[d.key];
            if (!picked) return d;
            // A fresh (re)upload always resets the doc back to "Pending" so
            // HR sees it needs review again, even if it was Rejected before.
            return {
              ...d,
              studentUploaded: true,
              fileName: picked.name,
              fileData: picked.dataUrl,
              fileType: picked.type,
              uploadedAt: new Date().toISOString(),
              docStatus: "Pending",
            };
          }),
        };
      });
      saveRecruitments(updated);
      setInterviews(updated.filter((r) => r.candidateName === user?.name));
      notify("Documents uploaded — HR can now verify them.", { type: "success", title: "Uploaded" });
      setUploadingFor(null);
    } catch (e) {
      notify("Something went wrong saving your documents. Please try again.", { type: "error" });
    } finally {
      setSavingUploads(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="My Interviews"
        subtitle="Interviews scheduled with you by recruiting companies"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "My Interviews" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading interviews…" /></div>
      ) : interviews.length === 0 ? (
        <EmptyState
          icon={CalendarX2}
          title="No interviews scheduled yet"
          description="Once a corporate client initiates recruitment against your profile in the Talent Pool, your interview details and join link will show up here."
        />
      ) : (
        <div className="flex flex-col gap-4">
          {interviews.map((i) => {
            const hrRoundScheduled = stageIndex(i.stage) >= stageIndex("HR Round Scheduled");
            const checklist = i.documents && i.documents.length ? i.documents : freshDocumentChecklist();
            const allStudentDocsUploaded = studentDocs.every((d) => checklist.find((c) => c.key === d.key)?.studentUploaded);
            const anyDocRejected = i.stage === "Document Verification" && studentDocs.some((d) => checklist.find((c) => c.key === d.key)?.docStatus === "Rejected");
            return (
              <Card key={i.id} className="flex flex-col gap-4 text-left">
                <div className="flex flex-col md:flex-row md:items-center gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <p className="font-semibold text-ink-900">{i.track}</p>
                      <Badge tone={stageTone(i.stage)}>{i.stage}</Badge>
                    </div>
                    {i.notes && (
                      <p className="text-sm text-ink-500 mt-2 italic">"{i.notes}"</p>
                    )}
                    <p className="text-xs text-ink-400 mt-2">{stageMessage(i.stage, "student")}</p>
                  </div>
                  <div className="shrink-0 flex flex-col gap-2 items-end">
                    {i.stage === "Technical Round Scheduled" && i.meetingLink ? (
                      <Button icon={Video} onClick={() => window.open(i.meetingLink, "_blank", "noopener,noreferrer")}>
                        Join Technical Round
                      </Button>
                    ) : i.stage === "HR Round Scheduled" && i.hrRoundMeetingLink ? (
                      <Button icon={Video} onClick={() => window.open(i.hrRoundMeetingLink, "_blank", "noopener,noreferrer")}>
                        Join HR Round
                      </Button>
                    ) : i.stage === "Document Verification" ? (
                      <Button size="sm" variant={anyDocRejected ? "danger" : "primary"} icon={UploadCloud} onClick={() => openUpload(i)}>
                        {anyDocRejected ? "Re-upload Rejected Documents" : allStudentDocsUploaded ? "Update Documents" : "Upload Documents"}
                      </Button>
                    ) : i.stage === "Student Approval" ? (
                      <Button size="sm" icon={Eye} onClick={() => setViewingOffer(i)}>View Offer Letter</Button>
                    ) : i.stage === "Student Signature" ? (
                      <Button icon={FileSignature} onClick={() => setSigningOffer(i)}>Sign Offer Letter</Button>
                    ) : i.stage === "Placed" ? (
                      <span className="text-xs text-success-600 font-medium flex items-center gap-1"><ShieldCheck size={14} /> Placement completed</span>
                    ) : i.stage === REJECTED ? (
                      <span className="text-xs text-ink-400">Not selected this time</span>
                    ) : (
                      <Badge tone="warning">In progress</Badge>
                    )}
                    {i.stage === "Document Verification" && allStudentDocsUploaded && !anyDocRejected && (
                      <span className="text-[11px] text-success-600 flex items-center gap-1"><CheckCircle2 size={12} /> Submitted — awaiting HR verification</span>
                    )}
                    {anyDocRejected && (
                      <span className="text-[11px] text-error-600 font-medium flex items-center gap-1">⚠ HR rejected a document — action required</span>
                    )}
                  </div>
                </div>

                {/* Per-document verification checklist — lets the student see
                    exactly which of their uploads is Pending, Approved, or
                    Rejected (and needs re-uploading) without opening the modal. */}
                {i.stage === "Document Verification" && (
                  <div className="flex flex-col gap-2 border-t border-border pt-3">
                    <p className="text-xs font-semibold text-ink-700">Document Verification Checklist</p>
                    {studentDocs.map((d) => {
                      const entry = checklist.find((c) => c.key === d.key) || d;
                      const status = entry.docStatus || (entry.verified ? "Approved" : entry.studentUploaded ? "Pending" : null);
                      return (
                        <div
                          key={d.key}
                          className={`flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-xs ${
                            status === "Approved"
                              ? "border-success-200 bg-success-50"
                              : status === "Rejected"
                              ? "border-error-200 bg-error-50"
                              : "border-border bg-cream-50/50"
                          }`}
                        >
                          <span className="flex flex-col min-w-0">
                            <span className="text-ink-800 font-medium">{d.label}</span>
                            {status === "Rejected" && (
                              <span className="text-error-600 mt-0.5">Rejected by HR — please re-upload this document.</span>
                            )}
                            {!status && <span className="text-ink-400 mt-0.5">Not uploaded yet</span>}
                          </span>
                          {status === "Approved" && <Badge tone="success">Approved</Badge>}
                          {status === "Rejected" && <Badge tone="error">Rejected</Badge>}
                          {status === "Pending" && <Badge tone="warning">Pending review</Badge>}
                        </div>
                      );
                    })}
                  </div>
                )}

                {/* Two-round breakdown */}
                <div className="grid sm:grid-cols-2 gap-3 border-t border-border pt-3">
                  <div className="rounded-lg border border-border px-3 py-2">
                    <p className="text-xs font-semibold text-ink-700 flex items-center gap-1.5"><Briefcase size={13} className="text-ink-400" /> Technical Round · {i.roundType} (Client)</p>
                    <p className="text-xs text-ink-500 flex items-center gap-1 mt-1">
                      <CalendarClock size={13} className="text-ink-400" /> {i.date} at {i.time}
                    </p>
                  </div>
                  <div className="rounded-lg border border-border px-3 py-2">
                    <p className="text-xs font-semibold text-ink-700 flex items-center gap-1.5"><Users size={13} className="text-ink-400" /> HR Round (Moriah HR)</p>
                    {hrRoundScheduled ? (
                      <p className="text-xs text-ink-500 flex items-center gap-1 mt-1">
                        <CalendarClock size={13} className="text-ink-400" /> {i.hrRoundDate} at {i.hrRoundTime}
                      </p>
                    ) : (
                      <p className="text-xs text-ink-400 mt-1">Scheduled by HR after your technical round is approved</p>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Document Upload Modal — Document Verification step */}
      <Modal
        open={!!uploadingFor}
        onClose={() => setUploadingFor(null)}
        title="Upload Your Documents"
        description="HR can only verify a document once you've uploaded it here."
        footer={
          <div className="flex justify-end gap-2 w-full">
            <Button variant="secondary" onClick={() => setUploadingFor(null)}>Cancel</Button>
            <Button icon={UploadCloud} loading={savingUploads} onClick={handleSaveUploads}>Submit Documents</Button>
          </div>
        }
      >
        {uploadingFor && (
          <div className="flex flex-col gap-4 text-left font-sans">
            {studentDocs.map((d) => {
              const checklist = uploadingFor.documents && uploadingFor.documents.length ? uploadingFor.documents : freshDocumentChecklist();
              const entry = checklist.find((c) => c.key === d.key);
              return (
                <div key={d.key} className="flex flex-col gap-1.5">
                  {entry?.docStatus === "Rejected" && (
                    <p className="text-xs text-error-600 font-medium">Rejected by HR — please attach a new file.</p>
                  )}
                  <FileUpload
                    label={d.label}
                    required
                    accept=".pdf,.png,.jpg,.jpeg"
                    hint="PDF, PNG or JPG — up to 10MB"
                    initialFiles={pendingFiles[d.key] || []}
                    onChange={(files) => setPendingFiles((v) => ({ ...v, [d.key]: files }))}
                  />
                </div>
              );
            })}
          </div>
        )}
      </Modal>

      {/* Student Digital Signature Modal */}
      <Modal
        open={!!signingOffer}
        onClose={() => setSigningOffer(null)}
        title="Sign Your Offer Letter"
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <Button variant="secondary" icon={Download} onClick={() => downloadOfferLetter(signingOffer)}>Download</Button>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setSigningOffer(null)}>Cancel</Button>
              <Button icon={ShieldCheck} onClick={handleSign}>Digitally Sign & Complete Placement</Button>
            </div>
          </div>
        }
      >
        {signingOffer && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-sm text-ink-500">
              This offer letter has already been reviewed and signed by <strong>{signingOffer.clientName || "the recruiting company"}</strong>. Add your digital signature below to finalize your placement.
            </p>
            <div className="whitespace-pre-line font-mono text-xs leading-relaxed text-ink-800 bg-cream-50/50 p-4 rounded-lg border border-border/80">
              {offerDoc ? renderTemplateText(offerDoc) : "Offer letter not found."}
            </div>
          </div>
        )}
      </Modal>

      {/* Offer Letter Preview — Student Approval step: view, download, accept or reject */}
      <Modal
        open={!!viewingOffer}
        onClose={() => setViewingOffer(null)}
        title="Your Offer Letter"
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <Button variant="danger" icon={ThumbsDown} onClick={() => handleApproval(viewingOffer.id, "reject")}>Reject</Button>
            <div className="flex gap-2">
              <Button variant="secondary" icon={Download} onClick={() => downloadOfferLetter(viewingOffer)}>Download</Button>
              <Button icon={ThumbsUp} onClick={() => handleApproval(viewingOffer.id, "approve")}>Accept Offer</Button>
            </div>
          </div>
        }
      >
        {viewingOffer && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-sm text-ink-500">
              Congratulations! <strong>{viewingOffer.clientName || "The recruiting company"}</strong> has sent you a placement offer. Review it below, then download a copy, accept to move on to signing, or reject if you'd like to decline.
            </p>
            <div className="whitespace-pre-line font-mono text-xs leading-relaxed text-ink-800 bg-cream-50/50 p-4 rounded-lg border border-border/80">
              {viewingOfferDoc ? renderTemplateText(viewingOfferDoc) : "Offer letter not found."}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}