import { useEffect, useState, useRef } from "react";
import { Clock, CheckCircle2, PlayCircle, Award, ArrowLeft, ArrowRight, Terminal, Check, X, AlertCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ProgressBar from "../../components/ui/ProgressBar";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getAssessments, getAssessmentDetails, submitAssessment } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

export default function StudentAssessments() {
  const { notify } = useToast();
  const [assessments, setAssessments] = useState([]);
  const [loading, setLoading] = useState(true);

  // Active quiz state
  const [activeQuiz, setActiveQuiz] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [loadingQuestions, setLoadingQuestions] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);

  // Answers & Coding inputs
  const [selectedAnswers, setSelectedAnswers] = useState({}); // { qId: option }
  const [codeAnswers, setCodeAnswers] = useState({}); // { qId: code }

  // Code runner logs & console
  const [testResults, setTestResults] = useState([]);
  const [consoleOutput, setConsoleOutput] = useState("");

  // Countdown timer
  const [timeLeft, setTimeLeft] = useState(0);
  const timerRef = useRef(null);

  // Result overlay
  const [showResults, setShowResults] = useState(null);

  useEffect(() => {
    load();
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);
  const load = () => {
    setLoading(true);
    getAssessments().then((data) => {
      let foundFirstIncomplete = false;
      const lockedData = data.map((a) => {
        if (a.status === "Completed") {
          return a;
        }
        if (!foundFirstIncomplete) {
          foundFirstIncomplete = true;
          return { ...a, status: "Available" };
        }
        return { ...a, status: "Locked" };
      });
      setAssessments(lockedData);
      setLoading(false);
    });
  };  const startQuiz = async (quiz) => {
    setActiveQuiz(quiz);
    setLoadingQuestions(true);
    try {
      const data = await getAssessmentDetails(quiz.id);
      setQuestions(data);

      // Reset test state
      setCurrentIndex(0);
      setSelectedAnswers({});
      setTestResults([]);
      setConsoleOutput("");

      // Set starter code templates
      const starterCodes = {};
      data.forEach((q) => {
        if (q.type === "Code") {
          starterCodes[q.id] = q.starterCode;
        }
      });
      setCodeAnswers(starterCodes);

      // Start countdown
      const minutes = parseInt(quiz.duration) || 15;
      setTimeLeft(minutes * 60);

      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            notify("Time limit reached. Automatically grading your submissions.", { type: "warning" });
            submitQuiz(data, starterCodes, {}, true);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);

    } catch (e) {
      notify("Failed to retrieve assessment details.", { type: "error" });
      setActiveQuiz(null);
    } finally {
      setLoadingQuestions(false);
    }
  };

  const runCodeTests = (question) => {
    const code = codeAnswers[question.id] || "";
    setConsoleOutput("Initializing sandbox runner...\nCompiling script...\n");

    setTimeout(() => {
      let passedCount = 0;
      const logs = [];
      const results = question.testCases.map((tc) => {
        const passed = tc.testFn(code);
        if (passed) {
          passedCount++;
          logs.push(`✓ [PASS] ${tc.name}`);
        } else {
          logs.push(`✗ [FAIL] ${tc.name}\n  Expected output not matched. Input: ${tc.inputDesc}`);
        }
        return { ...tc, passed };
      });

      setTestResults(results);
      setConsoleOutput(
        `Sandbox Run Summary:\n------------------\n` +
        logs.join("\n") +
        `\n\nScore: ${passedCount}/${question.testCases.length} tests passed.` +
        (passedCount === question.testCases.length ? "\n\nSuccess! All code checks cleared." : "\n\nWarning: Code logic has errors.")
      );
    }, 450);
  };

  const submitQuiz = async (loadedQs = questions, codes = codeAnswers, mcqs = selectedAnswers, isAuto = false) => {
    if (timerRef.current) clearInterval(timerRef.current);

    const actualQs = loadedQs.length ? loadedQs : questions;
    let score = 0;

    if (activeQuiz.type === "MCQ") {
      let correct = 0;
      actualQs.forEach((q) => {
        const ans = mcqs[q.id] || selectedAnswers[q.id];
        if (ans === q.correctAnswer) {
          correct++;
        }
      });
      score = Math.round((correct / actualQs.length) * 100);
    } else {
      let totalTests = 0;
      let passedTests = 0;
      actualQs.forEach((q) => {
        const code = codes[q.id] || codeAnswers[q.id] || "";
        q.testCases.forEach((tc) => {
          totalTests++;
          if (tc.testFn(code)) {
            passedTests++;
          }
        });
      });
      score = totalTests > 0 ? Math.round((passedTests / totalTests) * 100) : 0;
    }

    try {
      await submitAssessment(activeQuiz.id, score);
      setShowResults({
        title: activeQuiz.title,
        score,
        passed: score >= 60,
        type: activeQuiz.type
      });
      setActiveQuiz(null);
      load();
    } catch (e) {
      notify("Failed to record score. Please reload.", { type: "error" });
    }
  };

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  if (showResults) {
    return (
      <div>
        <PageHeader title="Assessment Results" subtitle="Graded automatically based on benchmark keys & tests" breadcrumbs={[{ label: "Assessments", to: "/student/assessments" }]} />
        <Card className="max-w-2xl mx-auto text-center py-8">
          <div className="flex justify-center mb-4">
            <div className={`h-16 w-16 rounded-full flex items-center justify-center ${showResults.passed ? "bg-success-50 text-success-500" : "bg-error-50 text-error-500"}`}>
              {showResults.passed ? <Award size={36} /> : <AlertCircle size={36} />}
            </div>
          </div>
          <h2 className="text-2xl font-bold font-display text-ink-900">{showResults.title}</h2>
          <p className="text-sm text-ink-500 mt-1">{showResults.type === "MCQ" ? "Multiple Choice Quiz" : "Live Coding Challenge"}</p>

          <div className="my-8">
            <p className="text-5xl font-extrabold text-ink-900 font-display">{showResults.score}%</p>
            <p className="text-xs text-ink-500 mt-2">Required score to pass: 60%</p>
            <div className="mt-4 flex justify-center">
              <Badge tone={showResults.passed ? "success" : "error"} className="px-4 py-1 text-sm font-semibold">
                {showResults.passed ? "PASS / CERTIFIED" : "FAIL / PIP ELIGIBLE"}
              </Badge>
            </div>
          </div>

          <div className="border-t border-border pt-6 mt-6 flex justify-center gap-3">
            <Button onClick={() => setShowResults(null)} variant="primary">Return to Assessments</Button>
          </div>
        </Card>
      </div>
    );
  }

  if (activeQuiz) {
    const currentQ = questions[currentIndex];
    const progress = questions.length ? Math.round(((currentIndex + 1) / questions.length) * 100) : 0;
    const isLast = currentIndex === questions.length - 1;

    return (
      <div className="min-h-screen flex flex-col -m-4 lg:-m-6 bg-cream-100">
        <header className="bg-white border-b border-border px-6 py-4 flex items-center justify-between shrink-0">
          <div>
            <h2 className="font-display font-semibold text-ink-900 text-lg">{activeQuiz.title}</h2>
            <p className="text-xs text-ink-500">Question {currentIndex + 1} of {questions.length}</p>
          </div>
          <div className="flex items-center gap-4">
            <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-bold ${timeLeft < 120 ? "bg-error-50 text-error-600 animate-pulse" : "bg-primary-50 text-primary-700"}`}>
              <Clock size={16} />
              <span>{formatTime(timeLeft)}</span>
            </div>
            <Button variant="ghost" size="sm" onClick={() => { if (confirm("Are you sure you want to exit? Your progress will be lost.")) setActiveQuiz(null); }}>Exit Test</Button>
          </div>
        </header>

        <div className="w-full bg-cream-200 h-1 shrink-0">
          <div className="bg-primary-600 h-full transition-all duration-300" style={{ width: `${progress}%` }} />
        </div>

        <main className="flex-1 overflow-y-auto p-6 flex flex-col">
          {loadingQuestions ? (
            <div className="flex-1 flex items-center justify-center"><LoadingSpinner label="Loading assessment task..." /></div>
          ) : currentQ ? (
            <div className="flex-1 flex flex-col gap-6 max-w-5xl w-full mx-auto">
              <Card className="flex-1 flex flex-col">
                <div className="mb-4">
                  <Badge tone="primary" className="mb-2">{currentQ.type === "MCQ" ? "Multiple Choice" : "Coding Arena"}</Badge>
                  <h3 className="font-display text-lg font-bold text-ink-900 whitespace-pre-line">{currentQ.text}</h3>
                </div>

                {currentQ.type === "MCQ" ? (
                  <div className="flex flex-col gap-3 mt-4 flex-1 justify-center max-w-xl mx-auto w-full">
                    {currentQ.options.map((opt) => {
                      const isSelected = selectedAnswers[currentQ.id] === opt;
                      return (
                        <button
                          key={opt}
                          onClick={() => setSelectedAnswers((prev) => ({ ...prev, [currentQ.id]: opt }))}
                          className={`w-full text-left p-4 rounded-xl border transition-all flex items-center justify-between ${
                            isSelected
                              ? "border-primary-600 bg-primary-50 text-primary-900 ring-2 ring-primary-500/20"
                              : "border-border bg-white hover:bg-cream-50 text-ink-700"
                          }`}
                        >
                          <span className="font-medium text-sm">{opt}</span>
                          {isSelected && <CheckCircle2 size={16} className="text-primary-600" />}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-4 flex-1">
                    {/* Left: Challenge Specs */}
                    <div className="flex flex-col gap-4">
                      <div className="bg-cream-100 rounded-lg p-4 border border-border text-sm text-ink-700 leading-relaxed overflow-y-auto max-h-[300px]">
                        <p className="font-semibold text-ink-900 mb-1">Functional Specs:</p>
                        <p className="whitespace-pre-line text-xs font-mono">{currentQ.text}</p>
                      </div>

                      <div className="flex flex-col gap-2">
                        <p className="text-xs font-semibold text-ink-600">Unit Verification Tests</p>
                        <div className="flex flex-col gap-2">
                          {currentQ.testCases.map((tc) => {
                            const runResult = testResults.find((r) => r.id === tc.id);
                            return (
                              <div key={tc.id} className="flex items-center justify-between bg-white border border-border rounded-lg p-2.5 text-xs">
                                <span className="font-medium text-ink-700">{tc.name}</span>
                                {runResult ? (
                                  <Badge tone={runResult.passed ? "success" : "error"}>
                                    {runResult.passed ? "Passed" : "Failed"}
                                  </Badge>
                                ) : (
                                  <Badge tone="neutral">Untested</Badge>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    </div>

                    {/* Right: Code Sandbox Editor */}
                    <div className="flex flex-col gap-3 min-h-[360px]">
                      <div className="flex-1 flex flex-col border border-border rounded-xl overflow-hidden bg-primary-900">
                        <div className="bg-primary-950 px-4 py-2 border-b border-primary-800 flex items-center justify-between">
                          <span className="text-xs text-primary-200 font-mono">sandbox_module.js</span>
                          <Button size="xs" variant="secondary" className="bg-primary-800 border-primary-700 text-white hover:bg-primary-700" icon={Terminal} onClick={() => runCodeTests(currentQ)}>Run Tests</Button>
                        </div>
                        <textarea
                          className="flex-1 w-full bg-transparent text-white font-mono text-xs p-4 focus:outline-none resize-none leading-relaxed"
                          value={codeAnswers[currentQ.id] || ""}
                          onChange={(e) => setCodeAnswers((prev) => ({ ...prev, [currentQ.id]: e.target.value }))}
                          placeholder="// Write JavaScript code here"
                        />
                      </div>

                      {/* Mock Console */}
                      <div className="h-32 bg-primary-950 border border-primary-800 rounded-xl p-3 font-mono text-[11px] text-emerald-400 overflow-y-auto leading-relaxed whitespace-pre-wrap">
                        {consoleOutput || "Terminal logs idle. Press 'Run Tests' above to execute specs."}
                      </div>
                    </div>
                  </div>
                )}
              </Card>

              <div className="flex items-center justify-between shrink-0 pb-4">
                <Button
                  variant="secondary"
                  icon={ArrowLeft}
                  disabled={currentIndex === 0}
                  onClick={() => setCurrentIndex((idx) => idx - 1)}
                >
                  Previous
                </Button>

                {isLast ? (
                  <Button variant="primary" icon={Check} onClick={() => submitQuiz()}>
                    Submit Assessment
                  </Button>
                ) : (
                  <Button
                    variant="primary"
                    icon={ArrowRight}
                    iconPosition="right"
                    disabled={currentQ.type === "MCQ" && !selectedAnswers[currentQ.id]}
                    onClick={() => setCurrentIndex((idx) => idx + 1)}
                  >
                    Next
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center text-ink-500">Invalid question configuration.</div>
          )}
        </main>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Assessments"
        subtitle="Timed quizzes and live coding challenges — 60% minimum to pass"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Assessments" }]}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {loading ? (
          <div className="col-span-2 flex justify-center py-16"><LoadingSpinner label="Loading assessments list…" /></div>
        ) : (
          assessments.map((a) => (
            <Card key={a.id}>
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-medium text-ink-900">{a.title}</h3>
                  <div className="flex items-center gap-3 mt-1.5 text-xs text-ink-500">
                    <span>{a.type}</span>
                    <span className="flex items-center gap-1"><Clock size={12} /> {a.duration}</span>
                  </div>
                </div>
                <Badge tone={a.status === "Completed" ? "success" : a.status === "Available" ? "gold" : "neutral"}>
                  {a.status}
                </Badge>
              </div>

              {a.score !== null ? (
                <div className="mt-4">
                  <ProgressBar value={a.score} tone={a.score >= 60 ? "success" : "error"} label={`Your score: ${a.score}%`} />
                </div>
              ) : (
                <div className="mt-4">
                  <Button
                    fullWidth
                    variant={a.status === "Available" ? "primary" : "secondary"}
                    icon={a.status === "Available" ? PlayCircle : Clock}
                    disabled={a.status === "Locked"}
                    onClick={() => startQuiz(a)}
                  >
                    {a.status === "Available" ? "Start Assessment" : "Locked"}
                  </Button>
                </div>
              )}
            </Card>
          ))
        )}
      </div>
    </div>
  );
}

