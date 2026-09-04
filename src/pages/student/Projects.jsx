import { useEffect, useState } from "react";
import { Layers, Bug, FolderGit, FileCode2, Download, CheckCircle2, Circle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getMyProjects, getMyBugChallenges, attemptBugChallenge } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

export default function StudentProjects() {
  const { notify } = useToast();
  const [projects, setProjects] = useState([]);
  const [challenges, setChallenges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);

  const load = () => {
    setLoading(true);
    Promise.all([getMyProjects(), getMyBugChallenges()])
      .then(([p, c]) => {
        setProjects(p);
        setChallenges(c);
      })
      .catch(() => {
        setProjects([]);
        setChallenges([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const challengesFor = (title) => challenges.filter((c) => c.project === title);

  const toggleSolved = async (c) => {
    setBusyId(c.id);
    try {
      await attemptBugChallenge(c.id, !c.solved);
      setChallenges((prev) => prev.map((x) => (x.id === c.id ? { ...x, solved: !x.solved } : x)));
      notify(!c.solved ? `Marked "${c.title}" as fixed.` : `Reopened "${c.title}".`, { type: "success" });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <PageHeader
        title="Projects & Bug Challenges"
        subtitle="Practice projects and bug-fix challenges — download the broken code, fix it locally, then mark it done"
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
                      {list.map((c) => (
                        <div key={c.id} className="rounded-lg bg-cream-50 px-3 py-2.5">
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <Bug size={14} className="text-error-500 shrink-0" />
                              <p className="text-sm font-medium text-ink-900 text-left">{c.title}</p>
                            </div>
                            {c.difficulty && <Badge tone={c.difficulty === "Advanced" ? "error" : c.difficulty === "Intermediate" ? "warning" : "success"}>{c.difficulty}</Badge>}
                          </div>
                          {c.expectedBehaviour && <p className="text-xs text-ink-500 mt-1.5">{c.expectedBehaviour}</p>}
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
                            <button
                              type="button"
                              disabled={busyId === c.id}
                              onClick={() => toggleSolved(c)}
                              className={`ml-auto flex items-center gap-1 font-semibold ${c.solved ? "text-success-700" : "text-ink-500 hover:text-ink-800"}`}
                            >
                              {c.solved ? <CheckCircle2 size={14} /> : <Circle size={14} />}
                              {c.solved ? "Fixed" : "Mark as fixed"}
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
