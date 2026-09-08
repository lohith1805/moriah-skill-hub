import { useEffect, useMemo, useState } from "react";
import { Rocket, Trash2, Users, Award, BarChart3, AlertCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Tabs from "../../components/ui/Tabs";
import StatCard from "../../components/widgets/StatCard";
import { Input, Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { TRACKS, TRACK_LABELS } from "../../utils/constants";
import {
  getBatches,
  getQuestionBanks,
  getPublishedAssessments,
  publishAssessmentFromBank,
  unpublishAssessment,
  getAssessmentResults,
} from "../../services/trainerService";

const STATUS_TONE = { SUBMITTED: "neutral", EXPIRED: "error", PENDING_MANUAL_GRADING: "warning", IN_PROGRESS: "neutral" };

export default function TrainerAssessments() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [banks, setBanks] = useState([]);
  const [published, setPublished] = useState([]);
  const [loading, setLoading] = useState(true);

  // Publish modal
  const [publishBank, setPublishBank] = useState(null);
  const [pform, setPform] = useState({ batchId: "", title: "", durationMinutes: "20", passingScore: "60", questionCount: "", maxAttempts: "1" });
  const [publishing, setPublishing] = useState(false);
  const [pformErrors, setPformErrors] = useState({});

  // Results
  const [rows, setRows] = useState([]);
  const [resultsLoading, setResultsLoading] = useState(true);
  const [fBatch, setFBatch] = useState("");
  const [fTrack, setFTrack] = useState("");
  const [fAssessment, setFAssessment] = useState("");

  const batchName = (id) => batches.find((b) => String(b.id) === String(id))?.name || `Batch ${id}`;

  const loadCore = () => {
    setLoading(true);
    Promise.all([
      getBatches({ scope: "mine" }).catch(() => []),
      getQuestionBanks().catch(() => []),
      getPublishedAssessments().catch(() => []),
    ]).then(([b, bk, pub]) => {
      setBatches(b);
      setBanks(bk);
      setPublished(pub);
      setLoading(false);
    });
  };

  const loadResults = () => {
    setResultsLoading(true);
    getAssessmentResults({
      batchId: fBatch || undefined,
      track: fTrack || undefined,
      assessmentId: fAssessment || undefined,
    })
      .then(setRows)
      .catch((e) => notify(e?.message || "Couldn't load results.", { type: "error" }))
      .finally(() => setResultsLoading(false));
  };

  useEffect(() => { loadCore(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { loadResults(); }, [fBatch, fTrack, fAssessment]); // eslint-disable-line react-hooks/exhaustive-deps

  const openPublish = (bank) => {
    setPublishBank(bank);
    setPformErrors({});
    // Defaults to every question in the bank — same "publish the whole bank" behavior as before
    // this field existed; the trainer only needs to touch it to publish a smaller random sample.
    setPform({
      batchId: batches[0] ? String(batches[0].id) : "",
      title: bank.title,
      durationMinutes: "20",
      passingScore: "60",
      questionCount: String(bank.questionCount || ""),
      maxAttempts: "1",
    });
  };

  const doPublish = async () => {
    const errors = {};
    if (!pform.batchId) errors.batchId = "Pick a batch.";
    const requestedCount = Number(pform.questionCount);
    if (!pform.questionCount || requestedCount < 1) {
      errors.questionCount = "Enter how many questions this assessment should draw from the bank.";
    } else if (publishBank && requestedCount > publishBank.questionCount) {
      errors.questionCount = `This bank only has ${publishBank.questionCount} question${publishBank.questionCount === 1 ? "" : "s"}.`;
    }
    setPformErrors(errors);
    if (Object.keys(errors).length) return;

    setPublishing(true);
    try {
      await publishAssessmentFromBank({
        bankId: publishBank.id,
        batchId: pform.batchId,
        title: pform.title,
        durationMinutes: pform.durationMinutes,
        passingScore: pform.passingScore,
        maxAttempts: pform.maxAttempts,
        // Only send a subset when the trainer actually narrowed it — sending the bank's own full
        // count is equivalent, but omitting it here keeps "publish everything" the literal
        // no-op it always was for a bank whose size hasn't changed since the modal opened.
        questionCount: requestedCount < (publishBank?.questionCount || 0) ? requestedCount : undefined,
      });
      notify(
        `"${pform.title || publishBank.title}" is live for ${batchName(pform.batchId)} — ${requestedCount} question${requestedCount === 1 ? "" : "s"} per attempt.`,
        { type: "success", title: "Published" }
      );
      setPublishBank(null);
      loadCore();
    } catch (err) {
      notify(err?.message || "Couldn't publish.", { type: "error" });
    } finally {
      setPublishing(false);
    }
  };

  const doUnpublish = async (a) => {
    try {
      await unpublishAssessment(a.id);
      notify(`"${a.title}" unpublished.`, { type: "success" });
      loadCore();
    } catch (err) {
      notify(err?.message || "Couldn't unpublish.", { type: "error" });
    }
  };

  const stats = useMemo(() => {
    const graded = rows.filter((r) => r.passed != null);
    const passed = graded.filter((r) => r.passed).length;
    const avg = graded.length ? Math.round(graded.reduce((s, r) => s + (r.percentage || 0), 0) / graded.length) : null;
    return {
      attempts: rows.length,
      passRate: graded.length ? Math.round((passed / graded.length) * 100) : null,
      avg,
      pendingManual: rows.filter((r) => r.status === "PENDING_MANUAL_GRADING").length,
    };
  }, [rows]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Assessments"
        subtitle="Publish a developer-authored question bank to one of your batches, then review the results"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Assessments" }]}
      />

      <Card>
        <Tabs
          tabs={[
            { key: "publish", label: `Question Banks (${banks.length})` },
            { key: "results", label: "Results" },
          ]}
        >
          {(active) => {
            if (active === "publish") {
              return (
                <div className="flex flex-col gap-6">
                  <div>
                    <p className="text-xs text-ink-500 mb-3">Reusable banks authored by the developer team. Publishing snapshots the questions into a live, batch-scoped assessment.</p>
                    <Table
                      loading={loading}
                      data={banks}
                      emptyTitle="No question banks yet"
                      columns={[
                        { key: "title", header: "Bank", className: "text-left font-medium text-ink-900" },
                        { key: "topic", header: "Topic", className: "text-left text-xs", render: (r) => <Badge tone="neutral">{r.topic}</Badge> },
                        { key: "questionCount", header: "Questions", className: "text-left", render: (r) => r.questionCount },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          <Button size="sm" icon={Rocket} disabled={r.questionCount === 0 || !batches.length} onClick={() => openPublish(r)}>Publish to batch</Button>
                        ) },
                      ]}
                    />
                  </div>

                  <div>
                    <p className="text-xs font-semibold text-ink-700 mb-2">Currently live</p>
                    <Table
                      loading={loading}
                      data={published}
                      emptyTitle="Nothing published yet"
                      columns={[
                        { key: "title", header: "Assessment", className: "text-left" },
                        { key: "batch", header: "Batch", className: "text-left text-xs", render: (r) => (r.batchId ? batchName(r.batchId) : "—") },
                        { key: "duration", header: "Duration", className: "text-left text-xs" },
                        { key: "passingScore", header: "Pass mark", className: "text-left text-xs", render: (r) => `${r.passingScore}%` },
                        { key: "active", header: "Status", className: "text-left", render: (r) => <Badge tone={r.active ? "success" : "neutral"}>{r.active ? "Live" : "Unpublished"}</Badge> },
                        { key: "action", header: "", className: "text-right", render: (r) => (
                          r.active ? <Button size="sm" variant="danger" icon={Trash2} onClick={() => doUnpublish(r)}>Unpublish</Button> : <span className="text-xs text-ink-400">—</span>
                        ) },
                      ]}
                    />
                  </div>
                </div>
              );
            }

            return (
              <div className="flex flex-col gap-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
                  <StatCard label="Attempts" value={stats.attempts} icon={Users} tone="primary" />
                  <StatCard label="Pass Rate" value={stats.passRate == null ? "—" : `${stats.passRate}%`} icon={Award} tone="success" />
                  <StatCard label="Average Score" value={stats.avg == null ? "—" : `${stats.avg}%`} icon={BarChart3} tone="gold" />
                  <StatCard label="Awaiting Manual Grading" value={stats.pendingManual} icon={AlertCircle} tone="warning" />
                </div>

                <div className="flex flex-col sm:flex-row gap-3">
                  <Select className="sm:max-w-xs" placeholder="All my batches" options={batches.map((b) => ({ value: String(b.id), label: b.name }))} value={fBatch} onChange={(e) => setFBatch(e.target.value)} />
                  <Select className="sm:max-w-xs" placeholder="All tracks" options={TRACKS} value={fTrack} onChange={(e) => setFTrack(e.target.value)} />
                  <Select className="sm:max-w-xs" placeholder="All assessments" options={published.map((a) => ({ value: String(a.id), label: a.title }))} value={fAssessment} onChange={(e) => setFAssessment(e.target.value)} />
                </div>

                <Table
                  loading={resultsLoading}
                  data={rows}
                  emptyTitle="No attempts match these filters"
                  columns={[
                    { key: "studentName", header: "Student", className: "text-left font-medium text-ink-900" },
                    { key: "assessmentTitle", header: "Assessment", className: "text-left" },
                    { key: "batchName", header: "Batch", className: "text-left text-xs" },
                    { key: "track", header: "Track", className: "text-left text-xs", render: (r) => (r.track ? (TRACK_LABELS[r.track] || r.track) : "—") },
                    { key: "attemptNumber", header: "Attempt", className: "text-left text-xs", render: (r) => `#${r.attemptNumber}` },
                    { key: "percentage", header: "Score", className: "text-left", render: (r) => (r.percentage == null ? <span className="text-xs text-ink-400">—</span> : <span className="font-semibold">{r.percentage}%</span>) },
                    {
                      key: "passed",
                      header: "Result",
                      className: "text-left",
                      render: (r) =>
                        r.status === "PENDING_MANUAL_GRADING" ? <Badge tone="warning">Manual grading</Badge>
                          : r.passed == null ? <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge>
                            : <Badge tone={r.passed ? "success" : "error"}>{r.passed ? "PASS" : "FAIL"}</Badge>,
                    },
                    { key: "submittedAt", header: "Submitted", className: "text-left text-xs text-ink-500", render: (r) => (r.submittedAt ? new Date(r.submittedAt).toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "—") },
                  ]}
                />
              </div>
            );
          }}
        </Tabs>
      </Card>

      <Modal
        open={!!publishBank}
        onClose={() => setPublishBank(null)}
        title={publishBank ? `Publish "${publishBank.title}"` : "Publish"}
        footer={<>
          <Button variant="secondary" onClick={() => setPublishBank(null)}>Cancel</Button>
          <Button icon={Rocket} loading={publishing} onClick={doPublish}>Publish</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={(e) => { e.preventDefault(); doPublish(); }}>
          <Select
            label="Batch"
            required
            placeholder={batches.length ? "Select a batch" : "You have no batches"}
            options={batches.map((b) => ({ value: String(b.id), label: `${b.name} · ${b.track}` }))}
            value={pform.batchId}
            onChange={(e) => setPform((v) => ({ ...v, batchId: e.target.value }))}
            error={pformErrors.batchId}
          />
          <Input label="Assessment title" value={pform.title} onChange={(e) => setPform((v) => ({ ...v, title: e.target.value }))} placeholder="Defaults to the bank name" />
          <Input
            label="Number of questions"
            required
            type="number"
            min="1"
            max={publishBank?.questionCount || undefined}
            value={pform.questionCount}
            onChange={(e) => setPform((v) => ({ ...v, questionCount: e.target.value }))}
            hint={publishBank ? `This bank has ${publishBank.questionCount} question${publishBank.questionCount === 1 ? "" : "s"} — a random subset is picked per attempt if you enter fewer.` : undefined}
            error={pformErrors.questionCount}
          />
          <div className="grid sm:grid-cols-3 gap-4">
            <Input label="Duration (minutes)" type="number" min="1" value={pform.durationMinutes} onChange={(e) => setPform((v) => ({ ...v, durationMinutes: e.target.value }))} />
            <Input label="Passing score (%)" type="number" min="1" max="100" value={pform.passingScore} onChange={(e) => setPform((v) => ({ ...v, passingScore: e.target.value }))} />
            <Input label="Max attempts" type="number" min="1" max="20" value={pform.maxAttempts} onChange={(e) => setPform((v) => ({ ...v, maxAttempts: e.target.value }))} />
          </div>
        </form>
      </Modal>
    </div>
  );
}
