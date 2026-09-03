import { useEffect, useState, useRef } from "react";
import { Clock, CheckCircle2, PlayCircle, Award, ArrowLeft, ArrowRight, Check, AlertCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getAssessments, startAssessmentAttempt, submitAssessmentAttempt } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

const formatTime = (seconds) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s < 10 ? "0" : ""}${s}`;
};

export default function StudentAssessments() {
  const { notify } = useToast();
  const [assessments, setAssessments] = useState([]);
  const [loading, setLoading] = useState(true);

  const [attempt, setAttempt] = useState(null); // { attemptId, title, durationMinutes, questions }
  const [starting, setStarting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState({}); // { [questionId]: { selectedOptionIndices:[], codeAnswer:"" } }
  const [submitting, setSubmitting] = useState(false);
  const [timeLeft, setTimeLeft] = useState(0);
  const [showResult, setShowResult] = useState(null);
  const timerRef = useRef(null);

  const load = () => {
    setLoading(true);
    getAssessments()
      .then(setAssessments)
      .catch((e) => notify(e.message || "Could not load assessments.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    return () => timerRef.current && clearInterval(timerRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const buildAnswers = (att, state) =>
    att.questions.map((q) => ({
      questionId: q.id,
      selectedOptionIndices: state[q.id]?.selectedOptionIndices || [],
      codeAnswer: state[q.id]?.codeAnswer || "",
    }));

  const finishAttempt = async (att, state, auto = false) => {
    if (timerRef.current) clearInterval(timerRef.current);
    setSubmitting(true);
    try {
      const graded = await submitAssessmentAttempt(att.attemptId, buildAnswers(att, state));
      setAttempt(null);
      setShowResult(graded);
      if (auto) notify("Time's up — your attempt was submitted.", { type: "warning" });
      load();
    } catch (err) {
      notify(err.message || "Could not submit the attempt.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const start = async (a) => {
    setStarting(true);
    try {
      const att = await startAssessmentAttempt(a.id);
      setAttempt(att);
      setCurrentIndex(0);
      setAnswers({});
      setShowResult(null);
      const secs = (att.durationMinutes || a.durationMinutes || 15) * 60;
      setTimeLeft(secs);
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            clearInterval(timerRef.current);
            setAttempt((cur) => {
              if (cur) finishAttempt(cur, answersRef.current, true);
              return cur;
            });
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch (err) {
      notify(err.message || "Could not start the assessment.", { type: "error" });
    } finally {
      setStarting(false);
    }
  };

  // keep a ref of answers so the timer's stale closure can read the latest
  const answersRef = useRef(answers);
  useEffect(() => {
    answersRef.current = answers;
  }, [answers]);

  const setOption = (q, optIdx) => {
    setAnswers((prev) => {
      const cur = prev[q.id]?.selectedOptionIndices || [];
      let next;
      if (q.type === "MULTI_SELECT") {
        next = cur.includes(optIdx) ? cur.filter((i) => i !== optIdx) : [...cur, optIdx];
      } else {
        next = [optIdx];
      }
      return { ...prev, [q.id]: { ...prev[q.id], selectedOptionIndices: next } };
    });
  };
  const setCode = (q, value) =>
    setAnswers((prev) => ({ ...prev, [q.id]: { ...prev[q.id], codeAnswer: value } }));

  // ---- Result screen ----
  if (showResult) {
    const r = showResult;
    const pct = r.percentage != null ? Math.round(r.percentage) : null;
    const manual = r.status === "PENDING_MANUAL_GRADING";
    return (
      <div>
        <PageHeader
          title="Assessment Result"
          subtitle={r.title}
          breadcrumbs={[{ label: "Assessments", to: "/student/assessments" }]}
        />
        <Card className="max-w-2xl mx-auto text-center py-8">
          <div className="flex justify-center mb-4">
            <div className={`h-16 w-16 rounded-full flex items-center justify-center ${r.passed ? "bg-success-50 text-success-500" : "bg-error-50 text-error-500"}`}>
              {r.passed ? <Award size={36} /> : <AlertCircle size={36} />}
            </div>
          </div>
          <h2 className="text-2xl font-bold font-display text-ink-900">{pct != null ? `${pct}%` : "Submitted"}</h2>
          <p className="text-sm text-ink-500 mt-1">
            {r.autoGradedMarks != null && r.autoGradableMarks != null
              ? `${r.autoGradedMarks} / ${r.autoGradableMarks} auto-graded marks`
              : "Auto-graded portion"}
          </p>
          <div className="mt-4 flex justify-center">
            <Badge tone={r.passed ? "success" : manual ? "gold" : "error"} className="px-4 py-1 text-sm font-semibold">
              {manual ? "CODE ANSWERS PENDING MANUAL GRADING" : r.passed ? "PASS" : "FAIL"}
            </Badge>
          </div>

          {r.questions?.length > 0 && (
            <div className="mt-8 text-left flex flex-col gap-3">
              {r.questions.map((q, i) => (
                <div key={q.id} className="rounded-lg border border-border p-3">
                  <div className="flex items-start gap-2">
                    {q.isCorrect === true ? (
                      <CheckCircle2 size={16} className="text-success-600 shrink-0 mt-0.5" />
                    ) : q.isCorrect === false ? (
                      <AlertCircle size={16} className="text-error-600 shrink-0 mt-0.5" />
                    ) : (
                      <Clock size={16} className="text-ink-400 shrink-0 mt-0.5" />
                    )}
                    <div>
                      <p className="text-sm font-medium text-ink-900">Q{i + 1}. {q.text}</p>
                      {q.explanation && <p className="text-xs text-ink-500 mt-1">{q.explanation}</p>}
                      <p className="text-[11px] text-ink-400 mt-1">
                        {q.marksAwarded != null ? `${q.marksAwarded}` : "—"} / {q.marks ?? "—"} marks
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="border-t border-border pt-6 mt-6">
            <Button onClick={() => setShowResult(null)}>Back to Assessments</Button>
          </div>
        </Card>
      </div>
    );
  }

  // ---- Active attempt ----
  if (attempt) {
    const q = attempt.questions[currentIndex];
    const isLast = currentIndex === attempt.questions.length - 1;
    const chosen = answers[q?.id]?.selectedOptionIndices || [];
    const answered = q?.type === "CODE" ? true : chosen.length > 0;

    return (
      <div className="min-h-screen flex flex-col -m-4 lg:-m-6 bg-cream-100">
        <header className="bg-white border-b border-border px-6 py-4 flex items-center justify-between shrink-0">
          <div>
            <h2 className="font-display font-semibold text-ink-900 text-lg">{attempt.title}</h2>
            <p className="text-xs text-ink-500">Question {currentIndex + 1} of {attempt.questions.length}</p>
          </div>
          <div className="flex items-center gap-4">
            <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-bold ${timeLeft < 120 ? "bg-error-50 text-error-600 animate-pulse" : "bg-primary-50 text-primary-700"}`}>
              <Clock size={16} /><span>{formatTime(timeLeft)}</span>
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          {q ? (
            <div className="max-w-3xl w-full mx-auto flex flex-col gap-6">
              <Card>
                <Badge tone="primary" className="mb-3">
                  {q.type === "CODE" ? "Coding (manually graded)" : q.type === "MULTI_SELECT" ? "Multiple answers" : "Single answer"}
                </Badge>
                <h3 className="font-display text-lg font-bold text-ink-900 whitespace-pre-line">{q.text}</h3>

                {q.type === "CODE" ? (
                  <textarea
                    className="mt-4 w-full min-h-[280px] rounded-xl border border-border bg-primary-950 text-white font-mono text-xs p-4 focus:outline-none resize-y leading-relaxed"
                    value={answers[q.id]?.codeAnswer || ""}
                    onChange={(e) => setCode(q, e.target.value)}
                    placeholder="// Write your solution here — a reviewer grades this after submission."
                  />
                ) : (
                  <div className="flex flex-col gap-3 mt-4">
                    {q.options.map((opt, idx) => {
                      const sel = chosen.includes(idx);
                      return (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => setOption(q, idx)}
                          className={`w-full text-left p-4 rounded-xl border transition-all flex items-center justify-between ${
                            sel ? "border-primary-600 bg-primary-50 text-primary-900 ring-2 ring-primary-500/20" : "border-border bg-white hover:bg-cream-50 text-ink-700"
                          }`}
                        >
                          <span className="font-medium text-sm">{opt}</span>
                          {sel && <CheckCircle2 size={16} className="text-primary-600" />}
                        </button>
                      );
                    })}
                  </div>
                )}
              </Card>

              <div className="flex items-center justify-between pb-4">
                <Button variant="secondary" icon={ArrowLeft} disabled={currentIndex === 0} onClick={() => setCurrentIndex((i) => i - 1)}>
                  Previous
                </Button>
                {isLast ? (
                  <Button icon={Check} loading={submitting} onClick={() => finishAttempt(attempt, answers)}>
                    Submit Assessment
                  </Button>
                ) : (
                  <Button icon={ArrowRight} iconPosition="right" disabled={!answered} onClick={() => setCurrentIndex((i) => i + 1)}>
                    Next
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center text-ink-500 py-16">This assessment has no questions.</div>
          )}
        </main>
      </div>
    );
  }

  // ---- List ----
  return (
    <div>
      <PageHeader
        title="Assessments"
        subtitle="Timed quizzes — auto-graded MCQ + manually reviewed coding questions"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Assessments" }]}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {loading ? (
          <div className="col-span-2 flex justify-center py-16"><LoadingSpinner label="Loading assessments…" /></div>
        ) : assessments.length === 0 ? (
          <div className="col-span-2 text-sm text-ink-400 py-12 text-center">
            No assessments have been published for your batch yet.
          </div>
        ) : (
          assessments.map((a) => (
            <Card key={a.id}>
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-medium text-ink-900">{a.title}</h3>
                  <div className="flex items-center gap-3 mt-1.5 text-xs text-ink-500">
                    {a.duration && <span className="flex items-center gap-1"><Clock size={12} /> {a.duration}</span>}
                    <span>Pass ≥ {a.passPercentage}%</span>
                    {a.maxAttempts != null && <span>{a.maxAttempts} attempt{a.maxAttempts === 1 ? "" : "s"}</span>}
                  </div>
                </div>
                <Badge tone="gold">Available</Badge>
              </div>
              <div className="mt-4">
                <Button fullWidth icon={PlayCircle} loading={starting} onClick={() => start(a)}>
                  Start Assessment
                </Button>
              </div>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
