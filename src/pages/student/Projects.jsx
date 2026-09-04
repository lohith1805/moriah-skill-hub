import { useEffect, useMemo, useState } from "react";
import { Layers, Bug, FolderGit, FileCode2, Download, CheckCircle2, Send, RotateCcw } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import {
  getMyProjects,
  getMyBugChallenges,
  getMyBugChallengeSubmissions,
  submitBugChallenge,
} from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

const STATUS_TONE = { Submitted: "primary", Accepted: "success", "Needs work": "warning" };

export default function StudentProjects() {
  const { notify } = useToast();
  const [projects, setProjects] = useState([]);
  const [challenges, setChallenges] = useState([]);
  const [submissions, setSubmissions] = useState([]);
  const [loading, setLoading] = useState(true);

  // Submit-fix modal
  const [target, setTarget] = useState(null); // the challenge being answered
  const [solutionCode, setSolutionCode] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([getMyProjects(), getMyBugChallenges(), getMyBugChallengeSubmissions()])
      .then(([p, c, s]) => {
        setProjects(p);
        setChallenges(c);
        setSubmissions(s);
      })
      .catch(() => {
        setProjects([]);
        setChallenges([]);
        setSubmissions([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const challengesFor = (title) => challenges.filter((c) => c.project === title);

  // newest submission per challenge id
  const latestByChallenge = useMemo(() => {
    const map = {};
    for (const s of submissions) if (!map[s.challengeId]) map[s.challengeId] = s;
    return map;
  }, [submissions]);

  const openSubmit = (c) => {
    setTarget(c);
    const prev = latestByChallenge[c.id];
    setSolutionCode(prev?.solutionCode || "");
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
        title="Projects & Bug Challenges"
        subtitle="Read the broken code and the expected behaviour, rewrite the fix, and submit it for review"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Projects & Challenges" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading projects…" /></div>
      ) : projects.length === 0 ? (
        <EmptyState icon={Layers} title="No projects published yet" description="Practice projects show up here once a developer publishes them." />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 text-left">
          {projects.map((p) => {
            const list = challengesFor(p.title);
            return (
              <Card key={p.id} className="flex flex-col h-fit">
                <div className="flex items-center gap-2">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-50 text-primary-700"><Layers size={16} /></div>
                  <div>
                    <p className="font-semibold text-ink-900 leading-snug">{p.title}</p>
                    <p className="text-xs text-ink-500 mt-0.5">{[p.difficulty, p.domain].filter(Boolean).join(" · ")}</p>
                  </div>
                </div>

                {(p.stack || []).length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    {p.stack.map((s) => <Badge key={s} tone="primary">{s}</Badge>)}
                  </div>
                )}
                {p.description && <p className="text-sm text-ink-600 mt-3">{p.description}</p>}

                {p.starterRepo && (
                  <a href={p.starterRepo} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-primary-700 hover:underline font-semibold mt-3">
                    <FolderGit size={13} /> Starter repository
                  </a>
                )}

                <div className="border-t border-border mt-4 pt-3">
                  <p className="text-xs font-semibold text-ink-700 mb-2">Bug Challenges</p>
                  {list.length === 0 ? (
                    <p className="text-xs text-ink-400">No debugging challenges on this project yet.</p>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {list.map((c) => {
                        const sub = latestByChallenge[c.id];
                        return (
                          <div key={c.id} className="rounded-lg bg-cream-50 px-3 py-2.5">
                            <div className="flex items-start justify-between gap-2">
                              <div className="flex items-center gap-2">
                                <Bug size={14} className="text-error-500 shrink-0" />
                                <p className="text-sm font-medium text-ink-900 text-left">{c.title}</p>
                              </div>
                              {sub
                                ? <Badge tone={STATUS_TONE[sub.statusLabel] || "neutral"}>{sub.statusLabel}</Badge>
                                : c.difficulty && <Badge tone={c.difficulty === "Advanced" ? "error" : c.difficulty === "Intermediate" ? "warning" : "success"}>{c.difficulty}</Badge>}
                            </div>
                            {c.expectedBehaviour && (
                              <p className="text-xs text-ink-500 mt-1.5">
                                <span className="font-semibold text-ink-600">Expected: </span>{c.expectedBehaviour}
                              </p>
                            )}
                            {sub?.reviewerFeedback && (
                              <p className="text-xs text-ink-600 mt-1.5 rounded bg-white border border-border/60 px-2 py-1.5">
                                <span className="font-semibold">Reviewer: </span>{sub.reviewerFeedback}
                                {sub.score != null && <span className="ml-1 text-ink-400">({sub.score}/100)</span>}
                              </p>
                            )}
                            <div className="flex flex-wrap items-center gap-3 mt-2 text-xs">
                              {c.brokenCodeUrl && (
                                <a href={c.brokenCodeUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline">
                                  <FileCode2 size={12} /> Broken code
                                </a>
                              )}
                              {c.testScriptUrl && (
                                <a href={c.testScriptUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline">
                                  <Download size={12} /> Test script
                                </a>
                              )}
                              <Button
                                size="xs"
                                variant={sub ? "secondary" : "primary"}
                                icon={sub ? RotateCcw : Send}
                                className="ml-auto"
                                onClick={() => openSubmit(c)}
                              >
                                {sub ? "Resubmit fix" : "Submit fix"}
                              </Button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </Card>
            );
          })}
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
