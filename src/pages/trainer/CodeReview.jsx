import { useEffect, useState } from "react";
import { GitFork, ThumbsUp, ThumbsDown, Star, Video, Play, MessageSquare, Plus, FileCode, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import Tabs from "../../components/ui/Tabs";
import { getReviewQueue, reviewSubmission } from "../../services/trainerService";
import { useToast } from "../../context/ToastContext";

// Simulated source files for the student's PR diff
const SIMULATED_FILES = [
  {
    name: "src/services/authService.js",
    lines: [
      "export async function loginUser(email, password) {",
      "  const user = await Database.find({ email });",
      "  if (!user) throw new Error('User not found');",
      "  // TODO: Implement password hashing comparisons!",
      "  if (user.password !== password) throw new Error('Invalid credentials');",
      "  return generateJWTToken(user);",
      "}"
    ]
  },
  {
    name: "src/components/widgets/KanbanBoard.jsx",
    lines: [
      "export default function KanbanBoard({ columns, items, onMove }) {",
      "  return (",
      "    <div className=\"grid grid-cols-5 gap-4\">",
      "      {columns.map(col => (",
      "        <Column key={col} title={col} items={items.filter(i => i.status === col)} />",
      "      ))}",
      "    </div>",
      "  );",
      "}"
    ]
  }
];

export default function TrainerCodeReview() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState(null);
  const [score, setScore] = useState(7);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { notify } = useToast();

  // Tab state inside modal
  const [modalTab, setModalTab] = useState("details");

  // Staged inline code comments
  const [stagedComments, setStagedComments] = useState([]);
  const [activeFileIdx, setActiveFileIdx] = useState(0);
  const [commentInputLine, setCommentInputLine] = useState(null);
  const [tempCommentText, setTempCommentText] = useState("");

  const loadSubmissions = () => {
    setLoading(true);
    getReviewQueue()
      .then(setTasks)
      .catch((e) => notify(e.message || "Couldn't load the review queue.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadSubmissions();
  }, []);

  const openReview = (task) => {
    setActive(task);
    setScore(task.reviewScore || 7);
    setComment(task.reviewComment || "");
    setStagedComments(task.inlineComments || []);
    setModalTab("details");
    setCommentInputLine(null);
    setTempCommentText("");
  };

  const decide = async (decision) => {
    setSubmitting(true);
    try {
      await reviewSubmission(active.id, {
        submissionId: active.submissionId,
        score,
        decision,
        comment,
        inlineComments: stagedComments,
      });
      notify(
        decision === "Approved" ? "Approved — the task is now complete." : "Changes requested — sent back to the student.",
        { type: decision === "Approved" ? "success" : "warning", title: "Review recorded" }
      );
      setActive(null);
      setComment("");
      setStagedComments([]);
      loadSubmissions();
    } catch (err) {
      notify(err.message || "Couldn't record the review.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const addStagedComment = (file, lineNum, codeLine) => {
    if (!tempCommentText.trim()) return;
    const newComment = {
      file,
      line: lineNum,
      codeLine,
      comment: tempCommentText,
      author: "Mentor Reviewer"
    };
    setStagedComments((prev) => [...prev, newComment]);
    setCommentInputLine(null);
    setTempCommentText("");
    notify("Inline comment staged.", { type: "success" });
  };

  return (
    <div>
      <PageHeader title="Code & Video Review" subtitle="Inspect GitHub pull requests, watch demo walkthroughs, write inline reviews, and score deliverables" breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Code Review" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading submissions…" /></div>
      ) : tasks.length === 0 ? (
        <EmptyState icon={GitFork} title="No pending reviews" description="Submitted pull requests or demo walkthroughs will appear here." />
      ) : (
        <div className="flex flex-col gap-3 text-left">
          {tasks.map((t) => (
            <Card key={t.id} className="flex items-center justify-between flex-wrap gap-3">
              <div>
                <p className="font-semibold text-ink-900 text-sm leading-snug">{t.title}</p>
                <p className="text-xs text-ink-500 mt-1 font-medium">Submitted by {t.assignee} · {t.points} pts</p>
                <div className="flex items-center gap-3 mt-2">
                  {t.githubPr && (
                    <a href={t.githubPr} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline text-xs font-semibold">
                      <GitFork size={13} /> View PR
                    </a>
                  )}
                  {t.videoUrl && (
                    <span className="flex items-center gap-1 text-success-700 text-xs font-semibold">
                      <Video size={13} /> Walkthrough Video Attached
                    </span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={t.status === "Completed" ? "success" : "info"}>{t.status}</Badge>
                <Button size="sm" onClick={() => openReview(t)} disabled={t.status === "Completed"}>Review</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Code Review Dialog Modal */}
      {active && (
        <Modal
          open={!!active}
          onClose={() => setActive(null)}
          title="Review Submission"
          description={active.title}
          size="lg"
          footer={<>
            <Button variant="danger" icon={ThumbsDown} loading={submitting} onClick={() => decide("Rejected")}>Reject Deliverable</Button>
            <Button icon={ThumbsUp} loading={submitting} onClick={() => decide("Approved")}>Approve & Complete</Button>
          </>}
        >
          <div className="flex flex-col gap-4 text-left mt-3">
            <Tabs 
              tabs={[
                { key: "details", label: "Review Details", icon: FileCode },
                { key: "diff", label: "PR Files Diff & Comments", icon: MessageSquare }
              ]}
              defaultTab={modalTab}
              onChange={setModalTab}
            >
              {(activeTab) => {
                if (activeTab === "details") {
                  return (
                    <div className="flex flex-col gap-4 mt-2">
                      {/* Video Walkthrough Player */}
                      {active.videoUrl && (
                        <div className="border border-border rounded-xl p-3 bg-cream-50/20">
                          <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-2 flex items-center gap-1">
                            <Play size={12} className="text-success-600" /> Play Demo Walkthrough Video
                          </p>
                          <div className="bg-black rounded-lg overflow-hidden flex items-center justify-center p-1.5 aspect-video w-full">
                            <video className="w-full h-full object-contain rounded" controls src="https://www.w3schools.com/html/mov_bbb.mp4" />
                          </div>
                          <p className="text-[10px] text-ink-400 mt-2">Recorded and uploaded via AWS S3 secure bucket hooks.</p>
                        </div>
                      )}

                      {/* Code Scoring */}
                      <div>
                        <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-2">Code Quality Score (1 to 10)</p>
                        <div className="flex items-center gap-1">
                          {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                            <button key={n} type="button" onClick={() => setScore(n)} className="p-0.5 hover:scale-110 transition-transform">
                              <Star size={22} className={n <= score ? "fill-gold-500 text-gold-500" : "text-cream-200"} />
                            </button>
                          ))}
                          <span className="ml-3 text-sm font-extrabold text-ink-900 bg-cream-100 px-2 py-0.5 rounded-full">{score} / 10</span>
                        </div>
                      </div>

                      <Textarea label="Review comments" placeholder="Enter general review notes, instructions or refactoring hints for the student..." value={comment} onChange={(e) => setComment(e.target.value)} rows={3} />
                    </div>
                  );
                }

                if (activeTab === "diff") {
                  const currentFile = SIMULATED_FILES[activeFileIdx];
                  return (
                    <div className="flex flex-col gap-4 mt-2">
                      <div className="flex justify-between items-center gap-3">
                        <span className="text-xs font-bold text-ink-405 uppercase">Select PR File Changes</span>
                        <div className="flex gap-1.5">
                          {SIMULATED_FILES.map((f, idx) => (
                            <button
                              key={f.name}
                              type="button"
                              onClick={() => { setActiveFileIdx(idx); setCommentInputLine(null); }}
                              className={`px-3 py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                                activeFileIdx === idx ? "border-primary-500 bg-primary-50 text-primary-700" : "border-border text-ink-600 hover:bg-cream-50"
                              }`}
                            >
                              {f.name.split("/").pop()}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Code Diff Display and Inline Comment Box */}
                      <div className="border border-border rounded-xl overflow-hidden bg-slate-900 font-mono text-[11px] text-slate-200 p-3 select-none">
                        <p className="text-[10px] text-slate-400 font-bold border-b border-slate-800 pb-2 mb-2 flex items-center gap-1">
                          {currentFile.name}
                        </p>
                        <div className="space-y-1">
                          {currentFile.lines.map((line, idx) => {
                            const lineNum = idx + 1;
                            const hasCommentInput = commentInputLine === lineNum;
                            const lineComments = stagedComments.filter(sc => sc.file === currentFile.name && sc.line === lineNum);

                            return (
                              <div key={idx} className="group relative flex flex-col hover:bg-slate-800/60 py-0.5 px-1 rounded transition-colors">
                                <div className="flex items-start gap-3">
                                  <span className="text-slate-500 w-6 text-right shrink-0 select-none">{lineNum}</span>
                                  <span className="whitespace-pre flex-1 text-slate-100">{line}</span>
                                  <button
                                    type="button"
                                    onClick={() => setCommentInputLine(hasCommentInput ? null : lineNum)}
                                    className="opacity-0 group-hover:opacity-100 text-primary-400 hover:text-primary-300 transition-opacity shrink-0 flex items-center gap-0.5 px-1 bg-slate-800 rounded border border-slate-700 font-sans text-[10px] cursor-pointer"
                                  >
                                    <Plus size={10} /> Comment
                                  </button>
                                </div>

                                {/* Comments left on this line */}
                                {lineComments.map((lc, cIdx) => (
                                  <div key={cIdx} className="bg-indigo-950 border border-indigo-800 text-indigo-200 rounded-lg p-2.5 my-1.5 ml-9 font-sans text-xs flex gap-2">
                                    <MessageSquare size={13} className="text-indigo-400 shrink-0 mt-0.5" />
                                    <div>
                                      <p className="font-semibold text-indigo-300">{lc.author || "Trainer"}</p>
                                      <p className="text-indigo-100 italic mt-0.5 leading-relaxed">"{lc.comment}"</p>
                                    </div>
                                  </div>
                                ))}

                                {/* Inline Comment TextBox */}
                                {hasCommentInput && (
                                  <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 my-1.5 ml-9 font-sans text-xs flex flex-col gap-2">
                                    <p className="font-semibold text-slate-350">Add Inline Comment to line {lineNum}</p>
                                    <textarea
                                      className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-slate-200 outline-none focus:border-primary-500 font-sans"
                                      placeholder="e.g. Please extract this database call into a separate repository layer..."
                                      rows={2}
                                      value={tempCommentText}
                                      onChange={(e) => setTempCommentText(e.target.value)}
                                    />
                                    <div className="flex justify-end gap-2 mt-1">
                                      <Button size="xs" variant="secondary" onClick={() => setCommentInputLine(null)}>Cancel</Button>
                                      <Button size="xs" onClick={() => addStagedComment(currentFile.name, lineNum, line)}>Stage Comment</Button>
                                    </div>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>

                      {stagedComments.length > 0 && (
                        <div className="bg-cream-50/50 border border-border/80 rounded-xl p-3">
                          <p className="text-xs font-bold text-ink-500 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                            <CheckCircle2 size={13} className="text-success-600" /> Staged Review Comments ({stagedComments.length})
                          </p>
                          <div className="space-y-1.5 max-h-32 overflow-y-auto">
                            {stagedComments.map((sc, idx) => (
                              <div key={idx} className="text-xs text-ink-705 flex justify-between gap-4 py-0.5 border-b border-border/40 pb-1">
                                <span><strong>{sc.file.split("/").pop()} : line {sc.line}</strong> — "{sc.comment}"</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                }
              }}
            </Tabs>
          </div>
        </Modal>
      )}
    </div>
  );
}