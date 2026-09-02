import { useEffect, useState } from "react";
import { Layers, Bug, ArrowLeft, Terminal, Check, FolderGit, FileCode, PlayCircle, Eye, EyeOff, ChevronDown, ChevronUp, Link2, BookOpen } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ProgressBar from "../../components/ui/ProgressBar";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getMyProjects, getMyBugChallenges, getBugChallengeDetails, attemptBugChallenge } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

export default function StudentProjects() {
  const [projects, setProjects] = useState([]);
  const [challenges, setChallenges] = useState([]);
  const [loading, setLoading] = useState(true);
  const { notify } = useToast();

  // Expanded project starter kit drawers
  const [expandedProjects, setExpandedProjects] = useState({});

  // Active challenge workspace
  const [activeChallenge, setActiveChallenge] = useState(null);
  const [loadingChallenge, setLoadingChallenge] = useState(false);
  const [code, setCode] = useState("");
  const [testResults, setTestResults] = useState([]);
  const [consoleOutput, setConsoleOutput] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    // Read projects from localStorage to ensure newly Developer-published projects are visible
    let localProjects = [];
    try {
      const raw = localStorage.getItem("msh_developer_projects");
      if (raw) {
        localProjects = JSON.parse(raw).filter(p => p.status === "Published");
      }
    } catch(e) {}

    Promise.all([getMyProjects(), getMyBugChallenges()]).then(([p, c]) => {
      // Merge localStorage dynamic data if present
      if (localProjects.length > 0) {
        setProjects(localProjects);
      } else {
        setProjects(p);
      }
      setChallenges(c);
      setLoading(false);
    });
  };

  useEffect(() => { load(); }, []);

  const toggleExpand = (id) => {
    setExpandedProjects((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const challengesFor = (title) => challenges.filter((c) => c.project === title);

  const openChallenge = async (challenge) => {
    setLoadingChallenge(true);
    try {
      const detail = await getBugChallengeDetails(challenge.id);
      setActiveChallenge(detail);
      setCode(detail.starterCode || "");
      setTestResults([]);
      setConsoleOutput("");
    } catch (e) {
      notify("Couldn't load this challenge.", { type: "error" });
    } finally {
      setLoadingChallenge(false);
    }
  };

  const closeChallenge = () => {
    setActiveChallenge(null);
    load();
  };

  const runTests = () => {
    setConsoleOutput("Initializing sandbox runner...\nCompiling script...\n");
    setTimeout(() => {
      let passedCount = 0;
      const logs = [];
      const results = activeChallenge.testCases.map((tc) => {
        const passed = tc.testFn(code);
        if (passed) { passedCount++; logs.push(`✓ [PASS] ${tc.name}`); }
        else logs.push(`✗ [FAIL] ${tc.name}\n  Expected output not matched. Input: ${tc.inputDesc}`);
        return { ...tc, passed };
      });
      setTestResults(results);
      setConsoleOutput(
        `Sandbox Run Summary:\n------------------\n${logs.join("\n")}` +
        `\n\nScore: ${passedCount}/${activeChallenge.testCases.length} tests passed.` +
        (passedCount === activeChallenge.testCases.length ? "\n\nSuccess! All checks cleared." : "\n\nWarning: Bug isn't fully fixed yet.")
      );
      return results;
    }, 450);
  };

  const submitFix = async () => {
    setSubmitting(true);
    try {
      const results = activeChallenge.testCases.map((tc) => ({ ...tc, passed: tc.testFn(code) }));
      setTestResults(results);
      const allPassed = results.length > 0 && results.every((r) => r.passed);
      await attemptBugChallenge(activeChallenge.id, allPassed);
      notify(
        allPassed ? `Nice work — "${activeChallenge.title}" is fixed!` : `Not quite — ${results.filter((r) => r.passed).length}/${results.length} tests passed. Keep trying.`,
        { type: allPassed ? "success" : "info" }
      );
      if (allPassed) closeChallenge();
    } finally {
      setSubmitting(false);
    }
  };

  if (activeChallenge) {
    return (
      <div>
        <PageHeader
          title={activeChallenge.title}
          subtitle={activeChallenge.project}
          breadcrumbs={[{ label: "Projects & Challenges", to: "/student/projects" }]}
          action={<Button variant="ghost" size="sm" icon={ArrowLeft} onClick={closeChallenge}>Back</Button>}
        />

        {loadingChallenge ? (
          <div className="flex justify-center py-16"><LoadingSpinner label="Loading challenge…" /></div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 text-left">
            <div className="flex flex-col gap-4">
              <Card>
                <Badge tone={activeChallenge.difficulty === "Advanced" ? "error" : "success"} className="mb-2">{activeChallenge.difficulty}</Badge>
                <p className="text-sm text-ink-700 whitespace-pre-line">{activeChallenge.description || "Fix the bug in this function so all tests pass."}</p>
              </Card>
              <Card>
                <p className="text-xs font-semibold text-ink-600 mb-2">Unit Verification Tests</p>
                <div className="flex flex-col gap-2">
                  {activeChallenge.testCases.map((tc) => {
                    const runResult = testResults.find((r) => r.id === tc.id);
                    return (
                      <div key={tc.id} className="flex items-center justify-between bg-cream-50 border border-border rounded-lg p-2.5 text-xs">
                        <span className="font-medium text-ink-700">{tc.name}</span>
                        {runResult ? (
                          <Badge tone={runResult.passed ? "success" : "error"}>{runResult.passed ? "Passed" : "Failed"}</Badge>
                        ) : (
                          <Badge tone="neutral">Untested</Badge>
                        )}
                      </div>
                    );
                  })}
                </div>
              </Card>
            </div>

            <div className="flex flex-col gap-3 min-h-[360px]">
              <div className="flex-1 flex flex-col border border-border rounded-xl overflow-hidden bg-primary-900">
                <div className="bg-primary-950 px-4 py-2 border-b border-primary-800 flex items-center justify-between">
                  <span className="text-xs text-primary-200 font-mono">{activeChallenge.functionName}.js</span>
                  <Button size="sm" variant="secondary" className="bg-primary-800 border-primary-700 text-white hover:bg-primary-700" icon={Terminal} onClick={runTests}>Run Tests</Button>
                </div>
                <textarea
                  className="flex-1 w-full bg-transparent text-white font-mono text-xs p-4 focus:outline-none resize-none leading-relaxed min-h-[240px]"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  spellCheck={false}
                />
              </div>
              <div className="h-32 bg-primary-950 border border-primary-800 rounded-xl p-3 font-mono text-[11px] text-emerald-400 overflow-y-auto leading-relaxed whitespace-pre-wrap">
                {consoleOutput || "Terminal logs idle. Press 'Run Tests' above to execute specs."}
              </div>
              <Button icon={Check} loading={submitting} onClick={submitFix}>Submit Fix</Button>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Projects & Bug Challenges"
        subtitle="Explore multi-tier architectures assigned to your batch, download boilerplates, specs, Swagger APIs, and debug coding bugs"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Projects & Challenges" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading projects…" /></div>
      ) : projects.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="No projects assigned yet"
          description="Your cohort has no published projects assigned at the moment."
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 text-left">
          {projects.map((p) => {
            const isExpanded = expandedProjects[p.id];
            const hasResource = p.starterRepo || p.referenceSolution || p.swaggerSpec || p.erDiagram || p.videoTutorial || p.readmeContent;

            return (
              <Card key={p.id} className="flex flex-col h-fit">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-50 text-primary-700"><Layers size={16} /></div>
                    <div>
                      <p className="font-semibold text-ink-900 leading-snug">{p.title}</p>
                      <p className="text-xs text-ink-500 mt-0.5">{p.version} · {p.difficulty}</p>
                    </div>
                  </div>
                </div>
                <div className="flex flex-wrap gap-1.5 mt-3">
                  {(p.stack || []).map((s) => <Badge key={s} tone="primary">{s}</Badge>)}
                </div>
                {p.description && <p className="text-sm text-ink-600 mt-3">{p.description}</p>}

                {/* Setup Architecture Drawer */}
                {hasResource && (
                  <div className="mt-4 pt-3 border-t border-border/40">
                    <button
                      onClick={() => toggleExpand(p.id)}
                      className="flex items-center justify-between w-full text-xs font-bold text-primary-700 hover:text-primary-800 transition-colors uppercase tracking-wider"
                    >
                      <span className="flex items-center gap-1"><BookOpen size={13} /> Project Architecture &amp; Starter Kit</span>
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>

                    {isExpanded && (
                      <div className="mt-4 space-y-4 bg-cream-50/30 p-3.5 rounded-xl border border-border/50 animate-fadeIn">
                        {/* Repository & Boilerplates */}
                        {(p.starterRepo || p.referenceSolution) && (
                          <div>
                            <span className="text-[10px] font-bold text-ink-450 uppercase block mb-1.5">Deliverable Codebases</span>
                            <div className="flex flex-col gap-2">
                              {p.starterRepo && (
                                <a href={p.starterRepo} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-ink-700 hover:text-primary-700 font-semibold bg-white p-2 rounded-lg border border-border/40">
                                  <FolderGit size={13} className="text-primary-600" /> View Boilerplate Starter Repository
                                </a>
                              )}
                              {p.referenceSolution && (
                                <a href={p.referenceSolution} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-ink-700 hover:text-primary-700 font-semibold bg-white p-2 rounded-lg border border-border/40">
                                  <FolderGit size={13} className="text-indigo-600" /> View Reference Solution Branch
                                </a>
                              )}
                            </div>
                          </div>
                        )}

                        {/* Specs & Diagrams */}
                        {(p.swaggerSpec || p.erDiagram) && (
                          <div>
                            <span className="text-[10px] font-bold text-ink-450 uppercase block mb-1.5">Architecture Specifications</span>
                            <div className="flex flex-col gap-2">
                              {p.swaggerSpec && (
                                <a href={p.swaggerSpec} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-ink-700 hover:text-primary-700 font-semibold bg-white p-2 rounded-lg border border-border/40">
                                  <FileCode size={13} className="text-primary-600" /> Open Swagger/OpenAPI Documentation
                                </a>
                              )}
                              {p.erDiagram && (
                                <a href={p.erDiagram} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-ink-700 hover:text-primary-700 font-semibold bg-white p-2 rounded-lg border border-border/40">
                                  <Link2 size={13} className="text-indigo-600" /> View Database ER Model Diagram
                                </a>
                              )}
                            </div>
                          </div>
                        )}

                        {/* Video Tutorials Embedding */}
                        {p.videoTutorial && (
                          <div>
                            <span className="text-[10px] font-bold text-ink-450 uppercase block mb-1.5 flex items-center gap-1"><PlayCircle size={12} className="text-success-600" /> Video Tutorial Walkthrough</span>
                            <div className="aspect-video bg-black rounded-lg overflow-hidden border border-border mt-1">
                              <iframe
                                className="w-full h-full"
                                src={p.videoTutorial}
                                title=" Walkthrough tutorial"
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                                allowFullScreen
                              />
                            </div>
                          </div>
                        )}

                        {/* Setup README content */}
                        {p.readmeContent && (
                          <div>
                            <span className="text-[10px] font-bold text-ink-450 uppercase block mb-1.5">App setup guidelines (README)</span>
                            <pre className="text-[11px] text-ink-700 bg-white p-3 rounded-lg border border-border/50 font-mono whitespace-pre-wrap leading-relaxed max-h-36 overflow-y-auto">
                              {p.readmeContent}
                            </pre>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Bug challenges section */}
                <div className="border-t border-border mt-4 pt-3">
                  <p className="text-xs font-semibold text-ink-700 mb-2">Bug Challenges</p>
                  {challengesFor(p.title).length === 0 ? (
                    <p className="text-xs text-ink-400">No debugging challenges linked to this project yet.</p>
                  ) : (
                    <div className="flex flex-col gap-2">
                      {challengesFor(p.title).map((c) => (
                        <div key={c.id} className="rounded-lg bg-cream-50 px-3 py-2.5">
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <Bug size={14} className="text-error-500 shrink-0" />
                              <p className="text-sm font-medium text-ink-900 text-left">{c.title}</p>
                            </div>
                            <Badge tone={c.difficulty === "Advanced" ? "error" : "success"}>{c.difficulty}</Badge>
                          </div>
                          <div className="mt-2">
                            <ProgressBar value={c.attempts > 0 ? Math.round((c.solved / c.attempts) * 100) : 0} tone="primary" size="sm" label={`${c.solved}/${c.attempts} solved so far`} />
                          </div>
                          <div className="flex gap-2 mt-2">
                            <Button size="sm" variant="secondary" icon={Bug} onClick={() => openChallenge(c)}>Open Challenge</Button>
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
