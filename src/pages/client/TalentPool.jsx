import { useEffect, useState } from "react";
import {
  UserCheck, CalendarClock, Edit, Trash2, Users2, Video, Copy, Check, ThumbsUp, ThumbsDown,
  CheckCircle2, FileSignature, ShieldCheck, XCircle, FileText
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Avatar from "../../components/ui/Avatar";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import EmptyState from "../../components/ui/EmptyState";
import { Input, Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getTalentPool, getCandidateResume, requestRecruitment } from "../../services/clientService";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import { validateForm, required, isUrl } from "../../utils/validators";
import {
  REJECTED, stageTone, stageMessage,
  loadRecruitments, saveRecruitments, loadDocs, saveDocs
} from "../../utils/placementPipeline";
import { renderTemplateText } from "../../utils/letterTemplates";

// Generates a stable, shareable interview room link for a scheduled round.
// In a real backend this would come from a video-conferencing integration
// (Zoom/Google Meet/etc.); here we mint a local room code so both the
// client and the student land in the same "room".
function generateMeetingLink() {
  const roomCode =
    (typeof crypto !== "undefined" && crypto.randomUUID)
      ? crypto.randomUUID().split("-")[0]
      : Math.random().toString(36).slice(2, 10);
  return `${window.location.origin}/interview-room/${roomCode}`;
}

// Turns a stored resume data URL back into a Blob so it opens in a new tab
// via an object URL rather than navigating the page itself to a (possibly
// very long, and in some browsers blocked) data: URL.
function dataURLToBlob(dataURL) {
  const [header, base64] = dataURL.split(",");
  const mimeMatch = header.match(/data:(.*?);base64/);
  const mime = mimeMatch ? mimeMatch[1] : "application/octet-stream";
  const binary = atob(base64);
  const array = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
  return new Blob([array], { type: mime });
}

export default function ClientTalentPool() {
  const { user } = useAuth();
  const [talent, setTalent] = useState([]);
  const [loading, setLoading] = useState(true);
  const [recruitments, setRecruitments] = useState([]);

  // Modal states
  const [modalOpen, setModalOpen] = useState(false);
  const [targetCandidate, setTargetCandidate] = useState(null);
  const [values, setValues] = useState({ date: "", time: "", roundType: "Technical Interview", notes: "", meetingLink: "" });
  // Round types the Client/Interviewer conducts directly. The HR Round is a
  // separate, later stage HR schedules once this technical-side round is
  // approved — it no longer appears here as a selectable option. Only the
  // Technical Interview round is offered on the client side; Managerial
  // Round and System Design have been removed per requirements.
  const CLIENT_ROUND_TYPES = ["Technical Interview"];

  // Edit scheduling states
  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ date: "", time: "", roundType: "", notes: "", meetingLink: "" });

  const [errors, setErrors] = useState({});
  const [copiedId, setCopiedId] = useState(null);
  const [reviewingOffer, setReviewingOffer] = useState(null); // recruitment record under "Client Review & Signature"
  const { notify } = useToast();

  const handleCopyLink = async (id, link) => {
    try {
      await navigator.clipboard.writeText(link);
    } catch (e) {
      // Clipboard API may be unavailable (e.g. insecure context); ignore.
    }
    setCopiedId(id);
    notify("Interview link copied.", { type: "success" });
    setTimeout(() => setCopiedId((cur) => (cur === id ? null : cur)), 1500);
  };

  const reloadRecruitments = () => loadRecruitments().then(setRecruitments).catch(() => setRecruitments([]));

  useEffect(() => {
    getTalentPool()
      .then((t) => setTalent(t))
      .catch((e) => notify(e.message || "Could not load the talent pool.", { type: "error" }))
      .finally(() => setLoading(false));
    reloadRecruitments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Accepts either a Talent Pool candidate ({id, name, track, ...}) or an
  // existing recruitment record ({candidateId, candidateName, track, ...})
  // so both the candidate card and the pipeline table can open the same
  // scheduling modal.
  const openRecruitModal = (candidateOrRecruitment) => {
    setTargetCandidate({
      id: candidateOrRecruitment.id ?? candidateOrRecruitment.candidateId,
      name: candidateOrRecruitment.name ?? candidateOrRecruitment.candidateName,
      track: candidateOrRecruitment.track,
    });
    setValues({ date: "", time: "", roundType: "Technical Interview", notes: "", meetingLink: "" });
    setErrors({});
    setModalOpen(true);
  };

  // A placement is created server-side only when HR/ADMIN approve the
  // recruitment request — so "shortlisting" now submits that request.
  const handleShortlist = async (candidate) => {
    try {
      await requestRecruitment(candidate, { roleTitle: candidate.track || "Software Engineer" });
      notify(
        `Recruitment request sent for ${candidate.name}. Once HR approves it, they'll appear in your hiring pipeline.`,
        { type: "success", title: "Request submitted" }
      );
    } catch (err) {
      notify(err.message || "Could not submit the recruitment request.", { type: "error" });
    }
  };

  const handleViewResume = (candidate) => {
    const resume = getCandidateResume(candidate.name);
    if (!resume?.fileData) {
      notify("This candidate hasn't uploaded a resume yet.", { type: "error" });
      return;
    }
    try {
      const blob = dataURLToBlob(resume.fileData);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      notify("Could not open the resume file.", { type: "error" });
    }
  };

  const handleRecruitSave = (e) => {
    e.preventDefault();
    const validation = validateForm(values, {
      date: [required],
      time: [required],
      roundType: [required],
      // Only validate as a URL if the client actually typed one — the field
      // itself is optional, since we auto-generate a link when left blank.
      ...(values.meetingLink.trim() ? { meetingLink: [isUrl] } : {})
    });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    // Scheduling always follows shortlisting, so update the candidate's
    // existing "Shortlisted" record in place (preserving its id) rather
    // than creating a second, duplicate pipeline entry for the same
    // candidate. Falls back to creating a fresh record only if none exists
    // (e.g. legacy data from before shortlisting existed).
    const existing = recruitments.find((r) => r.candidateId === targetCandidate.id && r.stage === "Shortlisted");
    const meetingLink = values.meetingLink.trim() || generateMeetingLink();

    let updated;
    if (existing) {
      updated = recruitments.map((r) =>
        r.id === existing.id
          ? {
              ...r,
              date: values.date,
              time: values.time,
              roundType: values.roundType,
              notes: values.notes || "",
              stage: "Technical Round Scheduled",
              meetingLink,
            }
          : r
      );
    } else {
      const created = {
        id: `rec_${Date.now()}`,
        candidateId: targetCandidate.id,
        candidateName: targetCandidate.name,
        track: targetCandidate.track,
        clientName: user?.company || user?.name || "the recruiting company",
        date: values.date,
        time: values.time,
        roundType: values.roundType,
        notes: values.notes || "",
        stage: "Technical Round Scheduled",
        meetingLink,
      };
      updated = [created, ...recruitments];
    }

    setRecruitments(updated);
    saveRecruitments(updated);

    notify(`Interview scheduled for ${targetCandidate.name}.`, { type: "success", title: "Recruitment initiated" });
    setModalOpen(false);
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setEditValues({
      date: item.date,
      time: item.time,
      roundType: item.roundType,
      notes: item.notes || "",
      meetingLink: item.meetingLink || ""
    });
    setErrors({});
    setEditOpen(true);
  };

  const handleEditSave = (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, {
      date: [required],
      time: [required],
      roundType: [required],
      ...(editValues.meetingLink.trim() ? { meetingLink: [isUrl] } : {})
    });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const updated = recruitments.map((item) => {
      if (item.id === editingId) {
        return {
          ...item,
          date: editValues.date,
          time: editValues.time,
          roundType: editValues.roundType,
          notes: editValues.notes,
          // Keep the existing link if the client leaves this blank on
          // reschedule instead of silently swapping it for a new one.
          meetingLink: editValues.meetingLink.trim() || item.meetingLink || generateMeetingLink()
        };
      }
      return item;
    });

    setRecruitments(updated);
    saveRecruitments(updated);

    notify("Interview scheduling updated.", { type: "success" });
    setEditOpen(false);
    setEditingId(null);
  };

  const handleDelete = (id) => {
    const target = recruitments.find(r => r.id === id);
    const updated = recruitments.filter((r) => r.id !== id);
    setRecruitments(updated);
    saveRecruitments(updated);
    notify(`Interview request for ${target?.candidateName} cancelled.`, { type: "success" });
  };

  // Step 1 of the outcome flow: the Client/Interviewer marks the technical
  // interview itself as done. This unlocks the Approve/Reject decision — a
  // candidate can't be approved or rejected before their round is marked
  // complete.
  const handleMarkCompleted = (id) => {
    const target = recruitments.find((r) => r.id === id);
    const updated = recruitments.map((r) => (r.id === id ? { ...r, stage: "Technical Round Completed" } : r));
    setRecruitments(updated);
    saveRecruitments(updated);
    notify(`Technical interview with ${target?.candidateName} marked as completed.`, { type: "success" });
  };

  // Step 2: Client/Interviewer approves or rejects the technical round.
  // Approval hands the candidate to HR to schedule the HR Round — NOT
  // straight to Document Verification, since the HR round can never be
  // skipped. Rejection is terminal.
  const handleDecision = (id, decision) => {
    const target = recruitments.find((r) => r.id === id);
    const updated = recruitments.map((r) => {
      if (r.id !== id) return r;
      if (decision === "approve") {
        return {
          ...r,
          stage: "Technical Round Approved",
          technicalRoundApprovedAt: new Date().toISOString(),
          technicalRoundApprovedBy: user?.company || user?.name || "Client",
        };
      }
      return { ...r, stage: REJECTED, rejectedAt: "Technical Round" };
    });
    setRecruitments(updated);
    saveRecruitments(updated);
    notify(
      decision === "approve"
        ? `${target?.candidateName}'s technical round approved — HR will schedule the HR round next.`
        : `${target?.candidateName} marked as Rejected.`,
      { type: decision === "approve" ? "success" : "info" }
    );
  };

  const openOfferReview = (record) => setReviewingOffer(record);

  // Client Review & Signature step: the client either approves & signs the
  // offer letter (handing off to the student for their own approval +
  // signature) or rejects it outright, which is terminal.
  const handleClientOfferDecision = (decision) => {
    if (!reviewingOffer) return;
    const docs = loadDocs();
    const updatedDocs = docs.map((d) =>
      d.id === reviewingOffer.offerDocId
        ? { ...d, clientSignStatus: decision === "approve" ? "Signed" : "Rejected", clientSignedAt: new Date().toISOString() }
        : d
    );
    saveDocs(updatedDocs);

    const updated = recruitments.map((r) => {
      if (r.id !== reviewingOffer.id) return r;
      if (decision === "approve") {
        return { ...r, stage: "Client Signed", clientSignedAt: new Date().toISOString() };
      }
      return { ...r, stage: REJECTED, rejectedAt: "Offer Letter Created" };
    });
    setRecruitments(updated);
    saveRecruitments(updated);

    notify(
      decision === "approve"
        ? `Offer letter approved & signed — sent to ${reviewingOffer.candidateName} for their approval.`
        : "Offer letter rejected.",
      { type: decision === "approve" ? "success" : "info" }
    );
    setReviewingOffer(null);
  };

  const offerDoc = reviewingOffer ? loadDocs().find((d) => d.id === reviewingOffer.offerDocId) : null;

  return (
    <div>
      <PageHeader title="Talent Pool" subtitle="Review anonymized top performers ready for recruitment" breadcrumbs={[{ label: "Dashboard", to: "/client/dashboard" }, { label: "Talent Pool" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading talent pool…" /></div>
      ) : talent.length === 0 ? (
        <EmptyState
          icon={Users2}
          title="No graduates available yet"
          description="Candidates appear here once a trainer clears their graduation and HR finalizes their exit clearance."
        />
      ) : (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {talent.map((t) => {
              const candidateRecruitment = recruitments.find((r) => r.candidateId === t.id);
              return (
                <Card key={t.id} className="flex items-center gap-4 text-left">
                  <Avatar name={t.name} size={48} />
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-ink-900">{t.name}</p>
                    <p className="text-xs text-ink-500">{t.track} · Available {t.availability}</p>
                    <div className="flex flex-wrap gap-1.5 mt-2">
                      {t.skills.map((s) => <Badge key={s} tone="primary">{s}</Badge>)}
                    </div>
                    <div className="mt-2 max-w-[160px]">
                      <ProgressBar value={t.score} tone="success" showValue label="Performance score" size="sm" />
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-2 shrink-0">
                    {candidateRecruitment && (
                      <Badge tone={stageTone(candidateRecruitment.stage)}>{candidateRecruitment.stage}</Badge>
                    )}
                    <Button size="sm" variant="secondary" icon={FileText} onClick={() => handleViewResume(t)}>View Resume</Button>
                    {!candidateRecruitment ? (
                      <Button size="sm" icon={UserCheck} onClick={() => handleShortlist(t)}>Shortlist</Button>
                    ) : candidateRecruitment.stage === "Shortlisted" ? (
                      <Button size="sm" icon={CalendarClock} onClick={() => openRecruitModal(t)}>Schedule Interview</Button>
                    ) : null}
                  </div>
                </Card>
              );
            })}
          </div>

          {/* Hiring Pipeline Table */}
          {recruitments.length > 0 && (
            <Card>
              <h3 className="text-base font-semibold text-ink-900 mb-4 text-left">Your Hiring Pipeline</h3>
              <Table
                data={recruitments}
                columns={[
                  { key: "candidateName", header: "Candidate", className: "text-left font-medium text-ink-900" },
                  { key: "roundType", header: "Round Type", className: "text-left" },
                  { key: "date", header: "Interview Date", className: "text-left", render: (r) => (
                    r.date ? (
                      <span className="flex items-center gap-1"><CalendarClock size={14} className="text-ink-400" /> {r.date} at {r.time}</span>
                    ) : (
                      <span className="text-xs text-ink-400">Not scheduled yet</span>
                    )
                  ) },
                  { key: "stage", header: "Stage", className: "text-left", render: (r) => (
                    <Badge tone={stageTone(r.stage)}>{r.stage}</Badge>
                  ) },
                  { key: "meetingLink", header: "Interview Link", className: "text-left", render: (r) => (
                    r.meetingLink ? (
                      <div className="flex items-center gap-1">
                        <a
                          href={r.meetingLink}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-primary-700 hover:underline text-sm font-medium"
                        >
                          <Video size={14} /> Join
                        </a>
                        <button
                          type="button"
                          onClick={() => handleCopyLink(r.id, r.meetingLink)}
                          title="Copy interview link"
                          className="p-1 rounded text-ink-400 hover:text-ink-700 hover:bg-cream-100"
                        >
                          {copiedId === r.id ? <Check size={14} className="text-success-600" /> : <Copy size={14} />}
                        </button>
                      </div>
                    ) : (
                      <span className="text-xs text-ink-400">—</span>
                    )
                  ) },
                  { key: "outcome", header: "Interview Outcome", className: "text-left", render: (r) => (
                    r.stage === "Shortlisted" ? (
                      <Button size="sm" icon={CalendarClock} onClick={() => openRecruitModal(r)}>Schedule Interview</Button>
                    ) : r.stage === "Technical Round Scheduled" ? (
                      <Button size="sm" variant="secondary" icon={CheckCircle2} onClick={() => handleMarkCompleted(r.id)}>Mark Completed</Button>
                    ) : r.stage === "Technical Round Completed" ? (
                      <div className="flex gap-2">
                        <Button size="sm" variant="secondary" icon={ThumbsUp} onClick={() => handleDecision(r.id, "approve")}>Approve</Button>
                        <Button size="sm" variant="secondary" icon={ThumbsDown} onClick={() => handleDecision(r.id, "reject")}>Reject</Button>
                      </div>
                    ) : r.stage === "Offer Letter Created" ? (
                      <Button size="sm" icon={FileSignature} onClick={() => openOfferReview(r)}>Review & Sign Offer</Button>
                    ) : r.stage === "Placed" ? (
                      <span className="text-xs text-success-600 font-medium">Placement completed</span>
                    ) : r.stage === REJECTED ? (
                      <span className="text-xs text-ink-400">Not selected</span>
                    ) : (
                      <span className="text-xs text-ink-500">{stageMessage(r.stage, "client")}</span>
                    )
                  ) },
                  { key: "action", header: "", className: "text-right", render: (r) => (
                    <div className="flex gap-2 justify-end">
                      {r.stage === "Technical Round Scheduled" && (
                        <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Reschedule</Button>
                      )}
                      <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Cancel</Button>
                    </div>
                  ) },
                ]}
              />
            </Card>
          )}
        </div>
      )}

      {/* Schedule Interview Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={targetCandidate ? `Initiate Recruitment for ${targetCandidate.name}` : "Schedule Interview"}
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button onClick={handleRecruitSave}>Confirm Schedule</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleRecruitSave}>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Interview Date" type="date" required value={values.date} onChange={(e) => setValues((v) => ({ ...v, date: e.target.value }))} error={errors.date} />
            <Input label="Interview Time" type="time" required value={values.time} onChange={(e) => setValues((v) => ({ ...v, time: e.target.value }))} error={errors.time} />
          </div>
          <Select
            label="Technical Round Type"
            required
            placeholder="Select round"
            hint="The HR Round is scheduled separately by HR once this round is approved."
            options={CLIENT_ROUND_TYPES.map((r) => ({ value: r, label: r }))}
            value={values.roundType}
            onChange={(e) => setValues((v) => ({ ...v, roundType: e.target.value }))}
            error={errors.roundType}
          />
          <Input label="Recruitment Notes" placeholder="e.g. key focuses for interview round..." value={values.notes} onChange={(e) => setValues((v) => ({ ...v, notes: e.target.value }))} />
          <Input
            label="Interview Link"
            placeholder="Paste your own Zoom / Google Meet / Teams link (optional — we'll generate one if left blank)"
            value={values.meetingLink}
            onChange={(e) => setValues((v) => ({ ...v, meetingLink: e.target.value }))}
            error={errors.meetingLink}
          />
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Reschedule Interview"
        footer={<>
          <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button onClick={handleEditSave}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <div className="grid grid-cols-2 gap-4">
            <Input label="Interview Date" type="date" required value={editValues.date} onChange={(e) => setEditValues((v) => ({ ...v, date: e.target.value }))} error={errors.date} />
            <Input label="Interview Time" type="time" required value={editValues.time} onChange={(e) => setEditValues((v) => ({ ...v, time: e.target.value }))} error={errors.time} />
          </div>
          <Select
            label="Technical Round Type"
            required
            placeholder="Select round"
            options={CLIENT_ROUND_TYPES.map((r) => ({ value: r, label: r }))}
            value={editValues.roundType}
            onChange={(e) => setEditValues((v) => ({ ...v, roundType: e.target.value }))}
            error={errors.roundType}
          />
          <Input label="Recruitment Notes" placeholder="e.g. key focuses for interview round..." value={editValues.notes} onChange={(e) => setEditValues((v) => ({ ...v, notes: e.target.value }))} />
          <Input
            label="Interview Link"
            placeholder="Paste your own Zoom / Google Meet / Teams link (optional — leave blank to keep the current one)"
            value={editValues.meetingLink}
            onChange={(e) => setEditValues((v) => ({ ...v, meetingLink: e.target.value }))}
            error={errors.meetingLink}
          />
        </form>
      </Modal>

      {/* Client Review & Signature Modal */}
      <Modal
        open={!!reviewingOffer}
        onClose={() => setReviewingOffer(null)}
        title={reviewingOffer ? `Review Offer Letter — ${reviewingOffer.candidateName}` : "Review Offer Letter"}
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <Button variant="danger" icon={XCircle} onClick={() => handleClientOfferDecision("reject")}>Reject Offer</Button>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setReviewingOffer(null)}>Close</Button>
              <Button icon={ShieldCheck} onClick={() => handleClientOfferDecision("approve")}>Approve & Sign</Button>
            </div>
          </div>
        }
      >
        {reviewingOffer && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-sm text-ink-500">
              Review the placement offer letter HR issued for <strong>{reviewingOffer.candidateName}</strong>, then approve &amp; sign to send it on to the candidate, or reject it.
            </p>
            <div className="whitespace-pre-line font-mono text-xs leading-relaxed text-ink-800 bg-cream-50/50 p-4 rounded-lg border border-border/80">
              {offerDoc ? renderTemplateText(offerDoc) : "Offer letter not found."}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}