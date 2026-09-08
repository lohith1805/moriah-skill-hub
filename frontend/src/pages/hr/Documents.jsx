import { useEffect, useRef, useState } from "react";
import {
  FileText, Send, Eye, Download, Trash2, Plus, CheckCircle2,
  ShieldCheck, Stamp, PenTool, ClipboardCheck, FileCheck2,
  UploadCloud, ThumbsUp, ThumbsDown, Briefcase, CalendarClock, FolderCheck,
  Users, Video, CalendarPlus, Lock
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Tabs from "../../components/ui/Tabs";
import EmptyState from "../../components/ui/EmptyState";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { DEFAULT_TEMPLATES, renderTemplateText } from "../../utils/letterTemplates";
import { downloadPdf } from "../../utils/pdf";
import {
  REJECTED, stageTone, stageIndex,
  loadRecruitments, saveRecruitments, saveDocs,
  freshDocumentChecklist, freshOtherDocumentChecklist, dataURLToBlob,
} from "../../utils/placementPipeline";
import { getEmployees } from "../../services/hrService";
import { getClients } from "../../services/clientService";
import { getPlacementCandidates } from "../../services/placementService";

// Mirrors the generator in client/TalentPool.jsx / student/Interviews.jsx —
// used here so HR can mint a room link when scheduling the HR Round.
function generateMeetingLink() {
  const roomCode =
    (typeof crypto !== "undefined" && crypto.randomUUID)
      ? crypto.randomUUID().split("-")[0]
      : Math.random().toString(36).slice(2, 10);
  return `${window.location.origin}/interview-room/${roomCode}`;
}

const INITIAL_DOCS = [];

export default function HrDocuments() {
  const [modalOpen, setModalOpen] = useState(false);
  const [docs, setDocs] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [viewingDoc, setViewingDoc] = useState(null);
  const [recruitments, setRecruitments] = useState([]);
  const [viewingStudentDoc, setViewingStudentDoc] = useState(null); // { recruitmentId, candidateName, doc }
  const uploadInputRef = useRef(null);
  const uploadTargetRef = useRef(null);

  // Form values — recruitmentId (hidden) links a generated offer letter
  // back to a placement pipeline record so the pipeline can auto-advance.
  const [values, setValues] = useState({
    name: "",
    type: "student_offer",
    track: "Full Stack MERN Track",
    designation: "Full Stack Developer",
    department: "Engineering",
    ctc: "₹7,50,000 / annum",
    clientName: "",
    recruitmentId: "",
    candidateUuid: "",
    employeeId: "",
  });

  const [errors, setErrors] = useState({});
  // Picker sources for the Generate Letter modal — the recipient and the
  // recruiting company are chosen from real records, never free-typed.
  const [clientOptions, setClientOptions] = useState([]);        // [{id, companyName, ...}]
  const [candidateOptions, setCandidateOptions] = useState([]);  // client-shortlisted students
  const [employeeOptions, setEmployeeOptions] = useState([]);    // for employment_contract
  // File the HR user attaches to the offer letter itself when generating it
  // from the "Create Offer Letter" pipeline action.
  const [offerLetterFile, setOfferLetterFile] = useState([]);
  const { notify } = useToast();

  // ---- HR Round scheduling (separate from the Client's Technical Round) ----
  const [hrRoundModalOpen, setHrRoundModalOpen] = useState(false);
  const [hrRoundTarget, setHrRoundTarget] = useState(null);
  const [hrRoundValues, setHrRoundValues] = useState({ date: "", time: "", notes: "", meetingLink: "" });
  const [hrRoundErrors, setHrRoundErrors] = useState({});

  // Employee onboarding/KYC document review lives on its own per-employee page
  // (/hr/employee-documents/:userUuid, reached from Pending Employee Records /
  // Onboarding), not as a tab here.

  const loadPipeline = () => {
    loadRecruitments().then(setRecruitments).catch(() => setRecruitments([]));
  };

  useEffect(() => {
    const savedTemplates = localStorage.getItem("msh_hr_document_templates");
    if (savedTemplates) {
      let parsed = [];
      try {
        parsed = JSON.parse(savedTemplates);
      } catch (e) {
        parsed = [];
      }
      // Migrate: append any DEFAULT_TEMPLATES entries (e.g. a newly added
      // template) that aren't in what was previously saved, so existing
      // browsers pick up new templates without losing any custom ones.
      const missing = DEFAULT_TEMPLATES.filter((dt) => !parsed.some((p) => p.value === dt.value));
      const merged = missing.length ? [...parsed, ...missing] : parsed;
      if (missing.length) {
        localStorage.setItem("msh_hr_document_templates", JSON.stringify(merged));
      }
      setTemplates(merged);
    } else {
      localStorage.setItem("msh_hr_document_templates", JSON.stringify(DEFAULT_TEMPLATES));
      setTemplates(DEFAULT_TEMPLATES);
    }

    const savedDocs = localStorage.getItem("msh_hr_documents");
    if (savedDocs) {
      let parsed = [];
      try {
        parsed = JSON.parse(savedDocs);
      } catch (e) {
        parsed = [];
      }
      setDocs(parsed);
    } else {
      localStorage.setItem("msh_hr_documents", JSON.stringify(INITIAL_DOCS));
      setDocs(INITIAL_DOCS);
    }

    loadPipeline();
    getClients().then(setClientOptions).catch(() => setClientOptions([]));
    getPlacementCandidates().then(setCandidateOptions).catch(() => setCandidateOptions([]));
    getEmployees({ status: "ACTIVE" }).then(setEmployeeOptions).catch(() => setEmployeeOptions([]));
  }, []);

  const persistDocs = (data) => {
    setDocs(data);
    saveDocs(data);
  };

  const persistRecruitments = (data) => {
    setRecruitments(data);
    saveRecruitments(data);
  };

  const handleGenerate = (e) => {
    e.preventDefault();
    const rules = { name: [required], type: [required] };
    if (values.type === "student_offer" || values.type === "placement_confirmation") {
      rules.clientName = [required];
    }
    const validation = validateForm(values, rules);
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const attachedFile = offerLetterFile[0];
    const newDoc = {
      id: `doc_${Date.now()}`,
      name: values.name,
      type: values.type,
      track: values.track,
      designation: values.designation,
      department: values.department,
      ctc: values.ctc,
      clientName: values.clientName || undefined,
      recruitmentId: values.recruitmentId || undefined,
      date: new Date().toISOString().slice(0, 10),
      signStatus: "Sent for e-Sign",
      signedAt: null,
      clientSignStatus: values.recruitmentId ? "Pending" : undefined,
      clientSignedAt: null,
      uploadedFileName: attachedFile?.name,
      uploadedAt: attachedFile ? new Date().toISOString() : null,
    };

    const updatedDocs = [newDoc, ...docs];
    persistDocs(updatedDocs);

    // If this letter was generated from the placement pipeline (Placement
    // Confirmed -> Offer Letter Created step), write the letter's fields into
    // the placement itself (Placement.details, via persistRecruitments ->
    // advancePlacement) so the Client and the Student render the exact same
    // letter from the backend — offerFieldsFor() reads these keys.
    if (values.recruitmentId) {
      const updatedRecruitments = recruitments.map((r) =>
        r.id === values.recruitmentId
          ? {
              ...r,
              stage: "Offer Letter Created",
              offerType: values.type,
              offerCtc: values.ctc,
              offerDesignation: values.designation,
              offerDepartment: values.department,
              offerTrack: values.track,
              offerClientName: values.clientName || undefined,
              offerCreatedAt: new Date().toISOString(),
              ctc: values.ctc,
            }
          : r
      );
      persistRecruitments(updatedRecruitments);
    }

    notify(`Digital ${templates.find(t => t.value === values.type)?.label} generated and dispatched for e-signature.`, { type: "success", title: "Document Generated" });
    setModalOpen(false);
    setValues({ name: "", type: "student_offer", track: "Full Stack MERN Track", designation: "Full Stack Developer", department: "Engineering", ctc: "₹7,50,000 / annum", clientName: "", recruitmentId: "", candidateUuid: "", employeeId: "" });
    setOfferLetterFile([]);
  };

  // Opens the Generate modal pre-filled for a specific placement pipeline
  // candidate (called from the "Create Offer Letter" action in the
  // Document Verification tab, once a candidate reaches "Placement
  // Confirmed").
  const openOfferLetterFor = (record) => {
    setErrors({});
    setOfferLetterFile([]);
    setValues({
      name: record.candidateName,
      type: "placement_confirmation",
      track: record.track || "Full Stack MERN Track",
      designation: "Full Stack Developer",
      department: "Engineering",
      ctc: record.ctc || "₹7,50,000 / annum",
      clientName: record.clientName || "",
      recruitmentId: record.id,
      candidateUuid: record.candidateUuid || "",
      employeeId: "",
    });
    setModalOpen(true);
  };

  // HR's final step: once the client and the student have both signed their own
  // offer letter (backend-authorized actions on their own dashboards), HR marks
  // the placement complete. This is the only path to the terminal PLACED stage.
  const markPlacementComplete = (recruitmentId) => {
    const target = recruitments.find((r) => r.id === recruitmentId);
    const updated = recruitments.map((r) =>
      r.id === recruitmentId ? { ...r, stage: "Placed" } : r
    );
    persistRecruitments(updated);
    notify(`Placement finalised for ${target?.candidateName}. 🎉`, { type: "success", title: "Placed" });
  };

  const triggerUpload = (doc) => {
    uploadTargetRef.current = doc;
    uploadInputRef.current?.click();
  };

  const handleUploadFile = (e) => {
    const file = e.target.files?.[0];
    const doc = uploadTargetRef.current;
    if (!file || !doc) return;
    const updated = docs.map((d) => (d.id === doc.id ? { ...d, uploadedFileName: file.name, uploadedAt: new Date().toISOString() } : d));
    persistDocs(updated);
    notify(`"${file.name}" attached to ${doc.name}'s document.`, { type: "success", title: "Uploaded" });
    e.target.value = "";
  };

  const getDocRenderText = (doc) => renderTemplateText(doc, templates);

  const downloadDocument = (doc) => {
    const text = getDocRenderText(doc);
    const file = `${doc.name.toLowerCase().replace(/\s+/g, "_")}_${doc.type}`;
    downloadPdf(file, doc.name || "HR Document", [
      { lines: String(text).split(/\n{2,}/).flatMap((p) => [p.trim(), ""]) },
    ]);
    notify("Document downloaded as PDF.", { type: "success" });
  };

  const handleDelete = (id) => {
    const target = docs.find((d) => d.id === id);
    const updated = docs.filter((d) => d.id !== id);
    persistDocs(updated);
    notify(`Document for "${target?.name}" removed.`, { type: "success" });
  };

  // ---- Document Verification pipeline actions ----

  const toggleDocVerified = (recruitmentId, docKey) => {
    const updated = recruitments.map((r) => {
      if (r.id !== recruitmentId) return r;
      const checklist = r.documents && r.documents.length ? r.documents : freshDocumentChecklist();
      return {
        ...r,
        documents: checklist.map((d) => (d.key === docKey ? { ...d, verified: !d.verified } : d)),
      };
    });
    persistRecruitments(updated);
  };

  // Approve/Reject a single student-uploaded document. Approving marks it
  // verified (feeds into the existing "all verified" gate). Rejecting
  // clears the student's upload so it goes back to "Awaiting upload" and
  // the student has to re-submit it from "My Interviews".
  const handleStudentDocDecision = (recruitmentId, docKey, decision) => {
    const target = recruitments.find((r) => r.id === recruitmentId);
    const doc = (target?.documents || []).find((d) => d.key === docKey);
    const updated = recruitments.map((r) => {
      if (r.id !== recruitmentId) return r;
      const checklist = r.documents && r.documents.length ? r.documents : freshDocumentChecklist();
      return {
        ...r,
        documents: checklist.map((d) => {
          if (d.key !== docKey) return d;
          if (decision === "approve") {
            return { ...d, docStatus: "Approved", verified: true };
          }
          return { ...d, docStatus: "Rejected", verified: false, studentUploaded: false, fileName: null, fileData: null, fileType: null, uploadedAt: null };
        }),
      };
    });
    persistRecruitments(updated);
    setViewingStudentDoc(null);
    notify(
      decision === "approve"
        ? `${doc?.label || "Document"} approved for ${target?.candidateName}.`
        : `${doc?.label || "Document"} rejected — ${target?.candidateName} will need to re-upload it.`,
      { type: decision === "approve" ? "success" : "error" }
    );
  };

  // Downloads the real file the student attached (persisted as a base64
  // data URL) — falls back to an empty mock blob only for legacy records
  // saved before real file storage existed.
  const downloadStudentDoc = (doc) => {
    if (!doc?.fileName) return;
    try {
      const blob = doc.fileData ? dataURLToBlob(doc.fileData) : new Blob([""], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      notify(`Downloading: ${doc.fileName}`, { type: "info" });
    } catch (e) {
      notify("Could not download this file.", { type: "error" });
    }
  };

  // Opens the student's uploaded file in a new browser tab so HR can review
  // it full-size outside the inspector modal (mirrors Client > Talent
  // Pool's resume viewer).
  const openStudentDocInNewTab = (doc) => {
    if (!doc?.fileData) return;
    try {
      const blob = dataURLToBlob(doc.fileData);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      notify("Could not open this file.", { type: "error" });
    }
  };

  const confirmDocumentsVerified = (recruitmentId) => {
    const target = recruitments.find((r) => r.id === recruitmentId);
    const updated = recruitments.map((r) =>
      r.id === recruitmentId ? { ...r, docsVerifiedAt: new Date().toISOString() } : r
    );
    persistRecruitments(updated);
    notify(`All documents verified for ${target?.candidateName}.`, { type: "success" });
  };

  const confirmPlacement = (recruitmentId) => {
    const target = recruitments.find((r) => r.id === recruitmentId);
    const updated = recruitments.map((r) =>
      r.id === recruitmentId ? { ...r, placementConfirmedAt: new Date().toISOString() } : r
    );
    persistRecruitments(updated);
    notify(`Placement confirmed for ${target?.candidateName}. You can now create the offer letter.`, { type: "success" });
  };

  const sendForClientReview = (recruitmentId) => {
    const target = recruitments.find((r) => r.id === recruitmentId);
    const updated = recruitments.map((r) =>
      r.id === recruitmentId ? { ...r, stage: "Offer Letter Created" } : r
    );
    persistRecruitments(updated);
    notify(`Offer letter sent to ${target?.clientName || "the client"} for review & signature.`, { type: "success" });
  };

  // ---- HR Round: scheduled + run by HR only after the Technical Round
  // (taken by the Client) has been approved. ----

  const openHrRoundSchedule = (record) => {
    setHrRoundTarget(record);
    setHrRoundValues({ date: "", time: "", notes: "", meetingLink: "" });
    setHrRoundErrors({});
    setHrRoundModalOpen(true);
  };

  const handleHrRoundSchedule = (e) => {
    e.preventDefault();
    const validation = validateForm(hrRoundValues, { date: [required], time: [required] });
    setHrRoundErrors(validation);
    if (Object.keys(validation).length) return;

    const updated = recruitments.map((r) =>
      r.id === hrRoundTarget.id
        ? {
            ...r,
            stage: "HR Round Scheduled",
            hrRoundDate: hrRoundValues.date,
            hrRoundTime: hrRoundValues.time,
            hrRoundNotes: hrRoundValues.notes || "",
            hrRoundMeetingLink: hrRoundValues.meetingLink.trim() || generateMeetingLink(),
          }
        : r
    );
    persistRecruitments(updated);
    notify(`HR round scheduled for ${hrRoundTarget?.candidateName}.`, { type: "success" });
    setHrRoundModalOpen(false);
    setHrRoundTarget(null);
  };

  // Step 1: HR marks its own round as done — unlocks Approve/Reject, same
  // pattern as the Client's technical-round outcome flow.
  const hrRoundMarkCompleted = (id) => {
    const target = recruitments.find((r) => r.id === id);
    const updated = recruitments.map((r) => (r.id === id ? { ...r, stage: "HR Round Completed" } : r));
    persistRecruitments(updated);
    notify(`HR round with ${target?.candidateName} marked as completed.`, { type: "success" });
  };

  // Step 2: HR approves or rejects the HR round. Approval moves the
  // candidate into Document Verification — the last gate before placement
  // paperwork. Rejection is terminal.
  const hrRoundDecision = (id, decision) => {
    const target = recruitments.find((r) => r.id === id);
    const updated = recruitments.map((r) => {
      if (r.id !== id) return r;
      if (decision === "approve") {
        return { ...r, stage: "Document Verification", hrRoundApprovedAt: new Date().toISOString() };
      }
      return { ...r, stage: REJECTED, rejectedAt: "HR Round" };
    });
    persistRecruitments(updated);
    notify(
      decision === "approve"
        ? `${target?.candidateName}'s HR round approved — moved to document verification.`
        : `${target?.candidateName} marked as Rejected.`,
      { type: decision === "approve" ? "success" : "info" }
    );
  };

  // ---- Other required documents (non-gating) ----

  const toggleOtherDoc = (recruitmentId, docKey) => {
    const updated = recruitments.map((r) => {
      if (r.id !== recruitmentId) return r;
      const checklist = r.otherDocuments && r.otherDocuments.length ? r.otherDocuments : freshOtherDocumentChecklist();
      return {
        ...r,
        otherDocuments: checklist.map((d) =>
          d.key === docKey ? { ...d, uploaded: !d.uploaded, fileName: !d.uploaded ? `${docKey}_${r.candidateName.replace(/\s+/g, "_")}.pdf` : null } : d
        ),
      };
    });
    persistRecruitments(updated);
  };

  // Candidates who've cleared interview approval — i.e. everything from
  // Document Verification onward — are the ones with real HR paperwork.
  const pipelineCandidates = recruitments.filter((r) => stageIndex(r.stage) >= stageIndex("Document Verification"));
  const awaitingHrSchedule = recruitments.filter((r) => r.stage === "Technical Round Approved");
  const hrRoundScheduledList = recruitments.filter((r) => r.stage === "HR Round Scheduled");
  const hrRoundCompletedList = recruitments.filter((r) => r.stage === "HR Round Completed");
  const inVerification = recruitments.filter((r) => r.stage === "Document Verification" && !r.docsVerifiedAt);
  const verifiedAwaitingPlacement = recruitments.filter((r) => r.stage === "Document Verification" && r.docsVerifiedAt && !r.placementConfirmedAt);
  const placementConfirmedAwaitingOffer = recruitments.filter((r) => r.stage === "Document Verification" && r.placementConfirmedAt);
  const offerCreatedAwaitingSend = recruitments.filter((r) => r.stage === "Offer Letter Created");
  const bothSignedAwaitingPlacement = recruitments.filter((r) => r.stage === "Student Signed");

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Letters & Certifications"
        subtitle="Interview documents, HR document verification, offer letters and placement paperwork — all in one place"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Letters & Certifications" }]}
        action={
          <Button icon={Plus} onClick={() => { setErrors({}); setOfferLetterFile([]); setValues((v) => ({ ...v, name: "", clientName: "", recruitmentId: "", candidateUuid: "", employeeId: "" })); setModalOpen(true); }}>
            Generate Agreement / Letter
          </Button>
        }
      />

      <Tabs
        tabs={[
          { key: "interview", label: "Interview Documents", icon: FileCheck2 },
          { key: "hrround", label: "HR Round", icon: Users },
          { key: "verification", label: "Document Verification", icon: ClipboardCheck },
          { key: "offers", label: "Offer Letter", icon: FileText },
          { key: "other", label: "Other Documents", icon: FolderCheck },
        ]}
      >
        {(active) => (
          <>
            {/* ---------------- Interview Documents ---------------- */}
            {active === "interview" && (
              <Card>
                <div className="px-1 pb-4 text-left">
                  <h3 className="font-display font-semibold text-ink-900">Interview-Stage Candidates</h3>
                  <p className="text-xs text-ink-500">Candidates whose interview was approved by the Client/Interviewer, with their current pipeline stage</p>
                </div>
                {pipelineCandidates.length === 0 ? (
                  <EmptyState
                    icon={Briefcase}
                    title="No approved candidates yet"
                    description="Once a Corporate Client approves a candidate's interview, they'll show up here."
                  />
                ) : (
                  <Table
                    data={pipelineCandidates}
                    columns={[
                      { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                      { key: "track", header: "Track", className: "text-left" },
                      { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                      { key: "date", header: "Interview Date", className: "text-left", render: (r) => (
                        <span className="flex items-center gap-1"><CalendarClock size={14} className="text-ink-400" /> {r.date} at {r.time}</span>
                      ) },
                      { key: "roundType", header: "Round", className: "text-left" },
                      { key: "stage", header: "Current Stage", className: "text-left", render: (r) => <Badge tone={stageTone(r.stage)}>{r.stage}</Badge> },
                    ]}
                  />
                )}
              </Card>
            )}

            {/* ---------------- HR Round ---------------- */}
            {active === "hrround" && (
              <div className="flex flex-col gap-6">
                <Card>
                  <div className="px-1 pb-4 text-left flex items-center justify-between">
                    <div>
                      <h3 className="font-display font-semibold text-ink-900">Awaiting HR Round Scheduling</h3>
                      <p className="text-xs text-ink-500">Candidates whose Technical Round was approved by the client — schedule their HR round to continue</p>
                    </div>
                    <Badge tone={awaitingHrSchedule.length ? "warning" : "success"} dot>{awaitingHrSchedule.length} pending</Badge>
                  </div>
                  {awaitingHrSchedule.length === 0 ? (
                    <EmptyState
                      icon={Users}
                      title="Nothing to schedule"
                      description="Candidates land here once the Corporate Client approves their Technical Round."
                    />
                  ) : (
                    <Table
                      data={awaitingHrSchedule}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "track", header: "Track", className: "text-left" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={CalendarPlus} onClick={() => openHrRoundSchedule(r)}>Schedule HR Round</Button>
                        ) },
                      ]}
                    />
                  )}
                </Card>

                {hrRoundScheduledList.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">HR Round Scheduled</h3>
                      <p className="text-xs text-ink-500">Join the round and mark it completed once it's done, to unlock the Approve/Reject decision</p>
                    </div>
                    <Table
                      data={hrRoundScheduledList}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "hrRoundDate", header: "HR Round Date", className: "text-left", render: (r) => (
                          <span className="flex items-center gap-1"><CalendarClock size={14} className="text-ink-400" /> {r.hrRoundDate} at {r.hrRoundTime}</span>
                        ) },
                        { key: "hrRoundMeetingLink", header: "Link", className: "text-left", render: (r) => (
                          <a href={r.hrRoundMeetingLink} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-primary-700 hover:underline text-sm font-medium">
                            <Video size={14} /> Join
                          </a>
                        ) },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" variant="secondary" icon={CheckCircle2} onClick={() => hrRoundMarkCompleted(r.id)}>Mark Completed</Button>
                        ) },
                      ]}
                    />
                  </Card>
                )}

                {hrRoundCompletedList.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">HR Round Completed — Awaiting Decision</h3>
                    </div>
                    <Table
                      data={hrRoundCompletedList}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <div className="flex gap-2 justify-end">
                            <Button size="sm" variant="secondary" icon={ThumbsUp} onClick={() => hrRoundDecision(r.id, "approve")}>Approve</Button>
                            <Button size="sm" variant="secondary" icon={ThumbsDown} onClick={() => hrRoundDecision(r.id, "reject")}>Reject</Button>
                          </div>
                        ) },
                      ]}
                    />
                  </Card>
                )}
              </div>
            )}

            {/* ---------------- Document Verification ---------------- */}
            {active === "verification" && (
              <div className="flex flex-col gap-6">
                <Card>
                  <div className="px-1 pb-4 text-left flex items-center justify-between">
                    <div>
                      <h3 className="font-display font-semibold text-ink-900">Pending Document Verification</h3>
                      <p className="text-xs text-ink-500">Verify each required document before placement can be confirmed</p>
                    </div>
                    <Badge tone={inVerification.length ? "warning" : "success"} dot>{inVerification.length} pending</Badge>
                  </div>

                  {inVerification.length === 0 ? (
                    <EmptyState
                      icon={ClipboardCheck}
                      title="Nothing awaiting verification"
                      description="Candidates land here once both the Technical Round (Client) and HR Round (HR) are approved."
                    />
                  ) : (
                    <div className="flex flex-col gap-4">
                      {inVerification.map((r) => {
                        const checklist = r.documents && r.documents.length ? r.documents : freshDocumentChecklist();
                        const allVerified = checklist.every((d) => d.verified);
                        return (
                          <div key={r.id} className="border border-border rounded-xl p-4 text-left">
                            <div className="flex items-center justify-between flex-wrap gap-2 mb-3">
                              <div>
                                <p className="font-semibold text-ink-900">{r.candidateName}</p>
                                <p className="text-xs text-ink-500">{r.track} · {r.clientName || "Recruiting company"}</p>
                              </div>
                              <Badge tone={stageTone(r.stage)}>{r.stage}</Badge>
                            </div>
                            <div className="flex flex-col gap-2">
                              {checklist.map((d) => {
                                const awaitingStudentUpload = d.uploadedBy === "student" && !d.studentUploaded;
                                const isStudentDoc = d.uploadedBy === "student";
                                const status = d.docStatus || (d.verified ? "Approved" : awaitingStudentUpload ? null : "Pending");
                                return (
                                  <div
                                    key={d.key}
                                    className={`flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-sm text-left transition-colors ${
                                      status === "Approved"
                                        ? "border-success-200 bg-success-50"
                                        : status === "Rejected"
                                        ? "border-error-200 bg-error-50"
                                        : awaitingStudentUpload
                                        ? "border-border bg-cream-100"
                                        : "border-border"
                                    }`}
                                  >
                                    <span className="flex flex-col min-w-0">
                                      <span className="text-ink-800">{d.label}</span>
                                      {d.fileName && <span className="text-[11px] text-ink-400 truncate">📎 {d.fileName}</span>}
                                    </span>
                                    <div className="flex items-center gap-1.5 shrink-0">
                                      {awaitingStudentUpload ? (
                                        <span className="text-xs text-ink-400 flex items-center gap-1"><Lock size={12} /> Awaiting upload</span>
                                      ) : isStudentDoc ? (
                                        <>
                                          {status === "Approved" && <Badge tone="success">Approved</Badge>}
                                          {status === "Rejected" && <Badge tone="error">Rejected</Badge>}
                                          <button
                                            type="button"
                                            title="View document"
                                            onClick={() => setViewingStudentDoc({ recruitmentId: r.id, candidateName: r.candidateName, doc: d })}
                                            className="p-1.5 rounded text-ink-500 hover:text-primary-700 hover:bg-cream-100"
                                          >
                                            <Eye size={15} />
                                          </button>
                                          <button
                                            type="button"
                                            title="Download document"
                                            onClick={() => downloadStudentDoc(d)}
                                            className="p-1.5 rounded text-ink-500 hover:text-primary-700 hover:bg-cream-100"
                                          >
                                            <Download size={15} />
                                          </button>
                                          <button
                                            type="button"
                                            title="Approve document"
                                            onClick={() => handleStudentDocDecision(r.id, d.key, "approve")}
                                            className="p-1.5 rounded text-success-600 hover:bg-success-50"
                                          >
                                            <ThumbsUp size={15} />
                                          </button>
                                          <button
                                            type="button"
                                            title="Reject document"
                                            onClick={() => handleStudentDocDecision(r.id, d.key, "reject")}
                                            className="p-1.5 rounded text-error-600 hover:bg-error-50"
                                          >
                                            <ThumbsDown size={15} />
                                          </button>
                                        </>
                                      ) : (
                                        <button
                                          type="button"
                                          onClick={() => toggleDocVerified(r.id, d.key)}
                                          className={`text-xs px-2 py-1 rounded ${d.verified ? "text-success-700" : "text-ink-400 hover:text-primary-700"}`}
                                        >
                                          {d.verified ? <span className="flex items-center gap-1"><CheckCircle2 size={14} /> Verified</span> : "Mark verified"}
                                        </button>
                                      )}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                            <div className="flex justify-end mt-3">
                              <Button size="sm" icon={FileCheck2} disabled={!allVerified} onClick={() => confirmDocumentsVerified(r.id)}>
                                Confirm Documents Verified
                              </Button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </Card>

                {verifiedAwaitingPlacement.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">Documents Verified — Awaiting Placement Confirmation</h3>
                    </div>
                    <Table
                      data={verifiedAwaitingPlacement}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={CheckCircle2} onClick={() => confirmPlacement(r.id)}>Confirm Placement</Button>
                        ) },
                      ]}
                    />
                  </Card>
                )}

                {placementConfirmedAwaitingOffer.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">Placement Confirmed — Awaiting Offer Letter</h3>
                    </div>
                    <Table
                      data={placementConfirmedAwaitingOffer}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={Send} onClick={() => openOfferLetterFor(r)}>Create Offer Letter</Button>
                        ) },
                      ]}
                    />
                  </Card>
                )}

                {offerCreatedAwaitingSend.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">Offer Letter Created — Ready to Send</h3>
                    </div>
                    <Table
                      data={offerCreatedAwaitingSend}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={Send} onClick={() => sendForClientReview(r.id)}>Send for Client Review & Signature</Button>
                        ) },
                      ]}
                    />
                  </Card>
                )}

                {bothSignedAwaitingPlacement.length > 0 && (
                  <Card>
                    <div className="px-1 pb-4 text-left">
                      <h3 className="font-display font-semibold text-ink-900">Both Parties Signed — Awaiting Final Confirmation</h3>
                      <p className="text-xs text-ink-500">The client and the candidate have signed the offer letter. Mark the placement complete to finish.</p>
                    </div>
                    <Table
                      data={bothSignedAwaitingPlacement}
                      columns={[
                        { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                        { key: "clientName", header: "Recruiting Company", className: "text-left", render: (r) => r.clientName || "—" },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={CheckCircle2} onClick={() => markPlacementComplete(r.id)}>Mark Placement Complete</Button>
                        ) },
                      ]}
                    />
                  </Card>
                )}
              </div>
            )}

            {/* ---------------- Offer Letter (ledger) ---------------- */}
            {active === "offers" && (
              <div className="flex flex-col gap-6">
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
                  {templates.map((tpl) => (
                    <Card key={tpl.value} className="flex flex-col justify-between p-4 text-left hover:border-primary-300 transition-colors">
                      <div>
                        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-50 text-primary-700 mb-3">
                          <FileText size={20} />
                        </div>
                        <p className="font-semibold text-ink-900 text-sm">{tpl.label}</p>
                        <p className="text-xs text-ink-500 mt-1 line-clamp-2">Automated e-signature and platform sealing</p>
                      </div>
                      <Button
                        size="sm"
                        variant="secondary"
                        className="mt-4"
                        fullWidth
                        onClick={() => {
                          setErrors({});
                          setOfferLetterFile([]);
                          setValues((v) => ({ ...v, type: tpl.value, name: "", clientName: "", recruitmentId: "", candidateUuid: "", employeeId: "" }));
                          setModalOpen(true);
                        }}
                      >
                        Generate Draft
                      </Button>
                    </Card>
                  ))}
                </div>

                <Card>
                  <div className="px-4 py-3 border-b border-border text-left flex items-center justify-between">
                    <div>
                      <h3 className="font-display font-semibold text-ink-900">Generated & Signed Documents (MSH-FR-HR-02)</h3>
                      <p className="text-xs text-ink-500">Offer letters, agreements & placement documents with digital signature verification status</p>
                    </div>
                    <Badge tone="success" dot>E-Signature Ready</Badge>
                  </div>

                  <Table
                    loading={false}
                    data={docs}
                    columns={[
                      {
                        key: "name",
                        header: "Recipient / Signee",
                        className: "text-left font-medium text-ink-900",
                        render: (r) => (
                          <div>
                            <p className="font-semibold text-ink-900">{r.name}</p>
                            <p className="text-xs text-ink-500">{r.designation || r.track || "Candidate"}</p>
                          </div>
                        )
                      },
                      {
                        key: "type",
                        header: "Agreement Type",
                        className: "text-left",
                        render: (r) => (
                          <Badge tone="primary">
                            {templates.find((t) => t.value === r.type)?.label || r.type}
                          </Badge>
                        )
                      },
                      { key: "date", header: "Date Issued", className: "text-left" },
                      {
                        key: "signStatus",
                        header: "Status",
                        className: "text-left",
                        render: (r) => (
                          <div className="flex flex-col gap-1">
                            {r.recruitmentId && (
                              <Badge tone={r.clientSignStatus === "Signed" ? "success" : r.clientSignStatus === "Rejected" ? "error" : "warning"} className="w-fit">
                                Client: {r.clientSignStatus || "Pending"}
                              </Badge>
                            )}
                            {r.signStatus === "Digitally Signed" ? (
                              <Badge tone="success" className="flex items-center gap-1 w-fit">
                                <ShieldCheck size={12} /> Candidate Signed
                              </Badge>
                            ) : (
                              <Badge tone="warning" className="flex items-center gap-1 w-fit">
                                <PenTool size={12} /> Sent for e-Sign
                              </Badge>
                            )}
                            {r.uploadedFileName && (
                              <span className="text-[11px] text-ink-400">📎 {r.uploadedFileName}</span>
                            )}
                          </div>
                        )
                      },
                      {
                        key: "action",
                        header: "",
                        className: "text-right",
                        render: (r) => (
                          <div className="flex gap-1.5 justify-end flex-wrap">
                            <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingDoc(r)}>View</Button>
                            <Button size="sm" variant="secondary" icon={Download} onClick={() => downloadDocument(r)}>Download</Button>
                            <Button size="sm" variant="secondary" icon={UploadCloud} onClick={() => triggerUpload(r)}>Upload</Button>
                            <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
                          </div>
                        )
                      }
                    ]}
                  />
                </Card>
              </div>
            )}

            {/* ---------------- Other Documents ---------------- */}
            {active === "other" && (
              <Card>
                <div className="px-1 pb-4 text-left">
                  <h3 className="font-display font-semibold text-ink-900">Other Required Documents</h3>
                  <p className="text-xs text-ink-500">Supplementary paperwork tracked per candidate (doesn't gate the placement pipeline)</p>
                </div>
                {pipelineCandidates.length === 0 ? (
                  <EmptyState
                    icon={FolderCheck}
                    title="No candidates yet"
                    description="Other required documents will appear here once a candidate's interview is approved."
                  />
                ) : (
                  <div className="flex flex-col gap-4">
                    {pipelineCandidates.map((r) => {
                      const checklist = r.otherDocuments && r.otherDocuments.length ? r.otherDocuments : freshOtherDocumentChecklist();
                      return (
                        <div key={r.id} className="border border-border rounded-xl p-4 text-left">
                          <p className="font-semibold text-ink-900 mb-2">{r.candidateName}</p>
                          <div className="grid sm:grid-cols-3 gap-2">
                            {checklist.map((d) => (
                              <button
                                key={d.key}
                                type="button"
                                onClick={() => toggleOtherDoc(r.id, d.key)}
                                className={`flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-sm text-left transition-colors ${d.uploaded ? "border-success-200 bg-success-50 text-success-700" : "border-border hover:border-primary-300"}`}
                              >
                                <span>{d.label}</span>
                                {d.uploaded ? <CheckCircle2 size={16} /> : <UploadCloud size={14} className="text-ink-400" />}
                              </button>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </Card>
            )}
          </>
        )}
      </Tabs>

      {/* Hidden input backing the ledger "Upload" action */}
      <input ref={uploadInputRef} type="file" className="hidden" onChange={handleUploadFile} />

      {/* HR Round Scheduling Modal */}
      <Modal
        open={hrRoundModalOpen}
        onClose={() => setHrRoundModalOpen(false)}
        title={hrRoundTarget ? `Schedule HR Round — ${hrRoundTarget.candidateName}` : "Schedule HR Round"}
        description="This round is conducted by HR after the client has approved the candidate's Technical Round."
        footer={<>
          <Button variant="secondary" onClick={() => setHrRoundModalOpen(false)}>Cancel</Button>
          <Button onClick={handleHrRoundSchedule}>Confirm Schedule</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleHrRoundSchedule}>
          <div className="grid grid-cols-2 gap-4">
            <Input label="HR Round Date" type="date" required value={hrRoundValues.date} onChange={(e) => setHrRoundValues((v) => ({ ...v, date: e.target.value }))} error={hrRoundErrors.date} />
            <Input label="HR Round Time" type="time" required value={hrRoundValues.time} onChange={(e) => setHrRoundValues((v) => ({ ...v, time: e.target.value }))} error={hrRoundErrors.time} />
          </div>
          <Input label="Round Notes" placeholder="e.g. key focuses for the HR round..." value={hrRoundValues.notes} onChange={(e) => setHrRoundValues((v) => ({ ...v, notes: e.target.value }))} />
          <Input
            label="Interview Link"
            placeholder="Paste your own Zoom / Google Meet / Teams link (optional — we'll generate one if left blank)"
            value={hrRoundValues.meetingLink}
            onChange={(e) => setHrRoundValues((v) => ({ ...v, meetingLink: e.target.value }))}
            error={hrRoundErrors.meetingLink}
          />
        </form>
      </Modal>

      {/* Generate Document Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Generate Official Letter / Agreement"
        description="Generates legally binding offer letters and agreements pre-populated with candidate terms."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={Send} onClick={handleGenerate}>Generate & Dispatch for Signature</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleGenerate}>
          <Select
            label="Document Template"
            required
            options={templates.map((t) => ({ value: t.value, label: t.label }))}
            value={values.type}
            onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
          />
          {values.type === "employment_contract" ? (
            <Select
              label="Employee"
              required
              placeholder={employeeOptions.length ? "Select an employee" : "No employee records yet"}
              options={employeeOptions.map((emp) => ({
                value: String(emp.id),
                label: `${emp.name}${emp.designation ? ` — ${emp.designation}` : ""}`,
              }))}
              value={values.employeeId || ""}
              onChange={(e) => {
                const emp = employeeOptions.find((x) => String(x.id) === e.target.value);
                setValues((v) => ({
                  ...v,
                  employeeId: e.target.value,
                  name: emp?.name || "",
                  designation: emp?.designation || v.designation,
                  department: emp?.department || v.department,
                  recruitmentId: "",
                }));
              }}
              error={errors.name}
            />
          ) : (
            <Select
              label="Recipient — client-shortlisted candidate"
              required
              placeholder={candidateOptions.length ? "Select a candidate" : "No client-shortlisted candidates yet"}
              options={candidateOptions.map((c) => ({
                value: c.candidateUuid,
                label:
                  `${c.candidateName} — ${c.clientContactName || "client"}` +
                  `${c.graduated ? " · graduated" : ""} (${c.stage})`,
              }))}
              value={values.candidateUuid || ""}
              onChange={(e) => {
                const c = candidateOptions.find((x) => x.candidateUuid === e.target.value);
                setValues((v) => ({
                  ...v,
                  candidateUuid: e.target.value,
                  name: c?.candidateName || "",
                  clientName: c?.clientName || c?.clientContactName || v.clientName,
                  track: c?.track || v.track,
                  designation: c?.designation || v.designation,
                  department: c?.department || v.department,
                  ctc: c?.ctc || v.ctc,
                  recruitmentId: c?.placementId ? String(c.placementId) : "",
                }));
              }}
              error={errors.name}
            />
          )}

          {(values.type === "student_offer" || values.type === "placement_confirmation") && (
            <Select
              label="Recruiting Company"
              required
              placeholder={clientOptions.length ? "Select a client company" : "No client companies yet"}
              options={clientOptions.map((c) => ({ value: c.companyName, label: c.companyName }))}
              value={values.clientName || ""}
              onChange={(e) => setValues((v) => ({ ...v, clientName: e.target.value }))}
              error={errors.clientName}
            />
          )}

          {values.type === "student_offer" && (
            <Input
              label="Learning / Sprint Track"
              placeholder="e.g. Full Stack Engineering Track"
              value={values.track}
              onChange={(e) => setValues((v) => ({ ...v, track: e.target.value }))}
            />
          )}

          {(values.type === "employment_contract" || values.type === "placement_confirmation") && (
            <div className="grid sm:grid-cols-2 gap-4">
              <Input
                label="Designation / Role"
                placeholder="e.g. Full Stack Developer"
                value={values.designation}
                onChange={(e) => setValues((v) => ({ ...v, designation: e.target.value }))}
              />
              <Input
                label="Annual CTC (₹)"
                placeholder="e.g. ₹8,00,000 / annum"
                value={values.ctc}
                onChange={(e) => setValues((v) => ({ ...v, ctc: e.target.value }))}
              />
            </div>
          )}

          {values.recruitmentId && (
            <FileUpload
              label="Offer Letter Document"
              hint="Upload the signed-ready offer letter file (PDF, PNG or JPG — up to 10MB). Optional — you can also attach it later from the Offer Letter ledger."
              accept=".pdf,.png,.jpg,.jpeg"
              initialFiles={offerLetterFile}
              onChange={setOfferLetterFile}
            />
          )}
        </form>
      </Modal>

      {/* High-Fidelity Letter Preview Modal */}
      <Modal
        open={!!viewingDoc}
        onClose={() => setViewingDoc(null)}
        title="Official Document & E-Signature Preview"
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            {viewingDoc?.signStatus === "Digitally Signed" ? (
              <Badge tone="success">✅ Verified Tamper-Proof Signature</Badge>
            ) : (
              <span className="text-xs text-ink-400">
                The client and student sign this on their own dashboards.
              </span>
            )}
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setViewingDoc(null)}>Close</Button>
              <Button icon={Download} onClick={() => { downloadDocument(viewingDoc); setViewingDoc(null); }}>Download Document</Button>
            </div>
          </div>
        }
      >
        {viewingDoc && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="p-6 bg-white border border-border rounded-xl shadow-sm font-serif leading-relaxed text-sm text-ink-900">
              <div className="flex justify-between items-start border-b border-border pb-4 mb-4 font-sans">
                <div>
                  <h3 className="font-bold text-lg text-primary-900 tracking-wide font-display">MORIAH SKILL HUB</h3>
                  <p className="text-xs text-ink-500">Corporate Learning & Sprint Incubation Portal</p>
                </div>
                <div className="text-right">
                  <Badge tone={viewingDoc.signStatus === "Digitally Signed" ? "success" : "warning"}>
                    {viewingDoc.signStatus}
                  </Badge>
                  <p className="text-[11px] text-ink-400 mt-1 font-mono">{viewingDoc.id.toUpperCase()}</p>
                </div>
              </div>

              <div className="whitespace-pre-line font-mono text-xs leading-relaxed text-ink-800 bg-cream-50/50 p-4 rounded-lg border border-border/80">
                {getDocRenderText(viewingDoc)}
              </div>

              {/* Digital E-Signature Stamp Footer */}
              <div className="mt-6 pt-4 border-t border-border flex justify-between items-end font-sans">
                <div>
                  <div className="flex items-center gap-1 text-primary-800 font-semibold text-xs mb-1">
                    <Stamp size={15} /> Moriah Skill Hub Official Stamp
                  </div>
                  <p className="text-[10px] text-ink-400 font-mono">Issued by HR Operations · Validated Platform Certificate</p>
                </div>
                <div className="text-right">
                  {viewingDoc.signStatus === "Digitally Signed" ? (
                    <div className="p-2 rounded bg-success-50 border border-success-200 text-success-800 text-[11px]">
                      <p className="font-bold">Digitally Signed by: {viewingDoc.name}</p>
                      <p className="text-[9px] font-mono text-success-600">Timestamp: {viewingDoc.signedAt || new Date().toISOString()}</p>
                    </div>
                  ) : (
                    <p className="text-xs text-amber-600 italic">Awaiting candidate e-signature...</p>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* Student Uploaded Document — View / Download / Approve / Reject */}
      <Modal
        open={!!viewingStudentDoc}
        onClose={() => setViewingStudentDoc(null)}
        title={viewingStudentDoc ? `${viewingStudentDoc.doc.label} — ${viewingStudentDoc.candidateName}` : "Document"}
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <Button variant="danger" icon={ThumbsDown} onClick={() => handleStudentDocDecision(viewingStudentDoc.recruitmentId, viewingStudentDoc.doc.key, "reject")}>
              Reject
            </Button>
            <div className="flex gap-2">
              <Button variant="secondary" icon={Download} onClick={() => downloadStudentDoc(viewingStudentDoc.doc)}>Download</Button>
              <Button icon={ThumbsUp} onClick={() => handleStudentDocDecision(viewingStudentDoc.recruitmentId, viewingStudentDoc.doc.key, "approve")}>
                Approve
              </Button>
            </div>
          </div>
        }
      >
        {viewingStudentDoc && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="flex items-center justify-between">
              <p className="text-sm text-ink-500">Document submitted by the candidate for HR verification.</p>
              {viewingStudentDoc.doc.docStatus === "Approved" && <Badge tone="success">Approved</Badge>}
              {viewingStudentDoc.doc.docStatus === "Rejected" && <Badge tone="error">Rejected</Badge>}
            </div>

            {viewingStudentDoc.doc.fileData ? (
              <div className="flex flex-col gap-2">
                {viewingStudentDoc.doc.fileType?.startsWith("image/") ? (
                  <div className="rounded-lg border border-border overflow-hidden bg-cream-50/50 flex justify-center">
                    {/* eslint-disable-next-line jsx-a11y/img-redundant-alt */}
                    <img src={viewingStudentDoc.doc.fileData} alt={viewingStudentDoc.doc.fileName || "Uploaded document"} className="max-h-[55vh] object-contain" />
                  </div>
                ) : (
                  <iframe
                    title={viewingStudentDoc.doc.fileName || "Document preview"}
                    src={viewingStudentDoc.doc.fileData}
                    className="w-full h-[55vh] rounded-lg border border-border bg-white"
                  />
                )}
                <button
                  type="button"
                  onClick={() => openStudentDocInNewTab(viewingStudentDoc.doc)}
                  className="self-start text-xs font-medium text-primary-700 hover:underline"
                >
                  Open in a new tab
                </button>
              </div>
            ) : (
              <div className="rounded-lg border border-border bg-cream-50/50 p-6 flex flex-col items-center gap-2 text-center">
                <FileText size={32} className="text-ink-400" />
                <p className="text-sm font-medium text-ink-800">{viewingStudentDoc.doc.fileName || "No file name available"}</p>
                <p className="text-xs text-ink-400">No preview available for this document.</p>
              </div>
            )}

            <p className="text-xs text-ink-400">
              {viewingStudentDoc.doc.fileName} · Uploaded {viewingStudentDoc.doc.uploadedAt ? new Date(viewingStudentDoc.doc.uploadedAt).toLocaleString() : "recently"}
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}