import { useEffect, useMemo, useState } from "react";
import { Bug, FileCode2, Download, CheckCircle2, Send, RotateCcw, Layers } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import {
  getMyBugChallenges,
  getMyBugChallengeSubmissions,
  submitBugChallenge,
} from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

const STATUS_TONE = { Submitted: "primary", Accepted: "success", "Needs work": "warning" };
const DIFF_TONE = { Beginner: "success", Intermediate: "warning", Advanced: "error" };

export default function StudentChallenges() {
  const { notify } = useToast();
  const [challenges, setChallenges] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [loading, setLoading] = useState(true);

  // Submit-fix modal
  const [target, setTarget] = useState(null);
  const [solutionCode, setSolutionCode] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getMyBugChallenges(), getMyBugChallengeSubmissions()])
      .then(([c, s]) => {
        setChallenges(c);
        setSubmissions(s);
      })
      .catch(() => {
        setChallenges([]);
        setSubmissions([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  // newest submission per challenge id
  const latestByChallenge = useMemo(() => {
    const map = {};
    for (const s of submissions) if (!map[s.challengeId]) map[s.challengeId] = s;
    return map;
  }, [submissions]);

  // group challenges under their project title
  const byProject = useMemo(() => {
    const groups = {};
    for (const c of challenges) {
      const key = c.project || "Other";
      (groups[key] = groups[key] || []).push(c);
    }
    return Object.entries(groups);
  }, [challenges]);

  const openSubmit = (c) => {
    setTarget(c);
    setSolutionCode(latestByChallenge[c.id]?.solutionCode || "");
    setNotes("");
  };

  const submit = async () => {
    if (!solutionCode.trim()) {
      notify("Paste your rewritten code before submitting.", { type: "error" });
      return;
    }
    setSubmitting(true);
    try {
      await submitBugChallenge(target.id, { solutionCode, notes });
      notify(`Fix submitted for "${target.title}".`, { type: "success" });
      setTarget(null);
      load();
    } catch (err) {
      notify(err.message || "Could not submit your fix.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Bug Challenges"
        subtitle="Read the broken code and the expected behaviour, rewrite the fix, and submit it for review"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Bug Challenges" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading challenges…" /></div>
      ) : challenges.length === 0 ? (
        <EmptyState icon={Bug} title="No bug challenges yet" description="Debugging exercises show up here once a developer adds them to your batch's projects." />
      ) : (
        <div className="flex flex-col gap-8 text-left">
          {byProject.map(([projectTitle, list]) => (
            <div key={projectTitle}>
              <div className="flex items-center gap-2 mb-3">
                <Layers size={15} className="text-primary-600" />
                <h3 className="font-semibold text-ink-900">{projectTitle}</h3>
                <span className="text-xs text-ink-400">{list.length} challenge{list.length === 1 ? "" : "s"}</span>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                {list.map((c) => {
                  const sub = latestByChallenge[c.id];
                  return (
                    <Card key={c.id} className="flex flex-col">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-start gap-2.5">
                          <Bug size={18} className="text-error-500 shrink-0 mt-0.5" />
                          <p className="font-semibold text-ink-900 leading-snug">{c.title}</p>
                        </div>
                        {sub
                          ? <Badge tone={STATUS_TONE[sub.statusLabel] || "neutral"}>{sub.statusLabel}</Badge>
                          : c.difficulty && <Badge tone={DIFF_TONE[c.difficulty] || "neutral"}>{c.difficulty}</Badge>}
                      </div>

                      {c.expectedBehaviour && (
                        <p className="text-sm text-ink-600 mt-3 leading-relaxed">
                          <span className="font-semibold text-ink-700">Expected: </span>{c.expectedBehaviour}
                        </p>
                      )}

                      {sub?.reviewerFeedback && (
                        <p className="text-xs text-ink-600 mt-3 rounded-lg bg-cream-50 border border-border/60 px-3 py-2">
                          <span className="font-semibold">Reviewer: </span>{sub.reviewerFeedback}
                          {sub.score != null && <span className="ml-1 text-ink-400">({sub.score}/100)</span>}
                        </p>
                      )}

                      <div className="mt-auto pt-4 flex flex-wrap items-center gap-3 text-sm">
                        {c.brokenCodeUrl && (
                          <a href={c.brokenCodeUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 text-primary-700 hover:underline font-medium">
                            <FileCode2 size={14} /> Broken code
                          </a>
                        )}
                        {c.testScriptUrl && (
                          <a href={c.testScriptUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 text-primary-700 hover:underline font-medium">
                            <Download size={14} /> Test script
                          </a>
                        )}
                        <Button
                          size="md"
                          variant={sub ? "secondary" : "primary"}
                          icon={sub ? RotateCcw : Send}
                          className="ml-auto"
                          onClick={() => openSubmit(c)}
                        >
                          {sub ? "Resubmit fix" : "Submit fix"}
                        </Button>
                      </div>
                    </Card>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={!!target}
        onClose={() => setTarget(null)}
        title={target ? `Submit fix — ${target.title}` : ""}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setTarget(null)}>Cancel</Button>
            <Button icon={Send} loading={submitting} onClick={submit}>Submit for review</Button>
          </>
        }
      >
        {target && (
          <div className="flex flex-col gap-4 text-left">
            {target.expectedBehaviour && (
              <div className="rounded-lg bg-cream-50 border border-border/60 p-3 text-sm">
                <p className="font-semibold text-ink-700 mb-1">Expected behaviour</p>
                <p className="text-ink-600">{target.expectedBehaviour}</p>
              </div>
            )}
            {target.brokenCodeUrl && (
              <a href={target.brokenCodeUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 text-xs text-primary-700 hover:underline font-semibold">
                <FileCode2 size={13} /> Open the broken code
              </a>
            )}
            <div>
              <label className="text-xs font-semibold text-ink-700">Your rewritten code</label>
              <textarea
                className="mt-1 w-full min-h-[260px] rounded-xl border border-border bg-primary-950 text-white font-mono text-xs p-4 focus:outline-none resize-y leading-relaxed"
                value={solutionCode}
                onChange={(e) => setSolutionCode(e.target.value)}
                placeholder={"// Paste your corrected version of the code here.\n// A developer or trainer will review it against the expected behaviour."}
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-ink-700">What did you change? <span className="font-normal text-ink-400">(optional)</span></label>
              <textarea
                className="mt-1 w-full rounded-xl border border-border p-3 text-sm focus:outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 resize-y"
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="e.g. The loop was off by one and the null check was missing before the .map call."
              />
            </div>
            <p className="text-xs text-ink-400 flex items-center gap-1">
              <CheckCircle2 size={12} /> You can resubmit if a reviewer marks it "Needs work".
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}
