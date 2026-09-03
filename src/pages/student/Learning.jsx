import { useEffect, useState, useRef } from "react";
import { PlayCircle, CheckCircle2, Clock, Award, AlertCircle, ArrowLeft, ArrowRight, ListChecks, Check, X, Lock } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ProgressBar from "../../components/ui/ProgressBar";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getVideoLessons, getVideoLessonDetail, markLessonWatched, submitLessonQuiz } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

// NOTE: videoId values in utils/constants.js#VIDEO_LESSONS are sample public
// YouTube tutorials standing in for Moriah's own recorded content. Swap them
// for real unlisted YouTube/Vimeo/S3 links once MSH-FR-DEV-04 (Video Tutorial
// Authoring) and the backend streaming API are wired up.

export default function StudentLearning() {
  const { notify } = useToast();
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);

  const [activeLesson, setActiveLesson] = useState(null);
  const [loadingLesson, setLoadingLesson] = useState(false);
  const [hasWatched, setHasWatched] = useState(false);

  // Quiz state
  const [quizStarted, setQuizStarted] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const [showResults, setShowResults] = useState(null);
  const watchTimerRef = useRef(null);

  useEffect(() => {
    load();
    return () => clearTimeout(watchTimerRef.current);
  }, []);

  const load = () => {
    setLoading(true);
    getVideoLessons().then((data) => {
      setLessons(data);
      setLoading(false);
    });
  };

  const openLesson = async (lessonSummary) => {
    setLoadingLesson(true);
    setQuizStarted(false);
    setShowResults(null);
    setCurrentIndex(0);
    setAnswers({});
    try {
      const detail = await getVideoLessonDetail(lessonSummary.id);
      setActiveLesson({ ...detail, ...lessonSummary });
      setHasWatched(lessonSummary.watched);

      // Simulate "watched" once the student has had the player open a
      // few seconds, in place of a real onended/progress event from the
      // video streaming API (MSH-FR-DEV-04, not live yet).
      if (!lessonSummary.watched) {
        clearTimeout(watchTimerRef.current);
        watchTimerRef.current = setTimeout(async () => {
          await markLessonWatched(lessonSummary.id);
          setHasWatched(true);
        }, 4000);
      }
    } catch (e) {
      notify("Failed to load this lesson.", { type: "error" });
    } finally {
      setLoadingLesson(false);
    }
  };

  const backToList = () => {
    clearTimeout(watchTimerRef.current);
    setActiveLesson(null);
    setQuizStarted(false);
    setShowResults(null);
    load();
  };

  const startQuiz = () => {
    setCurrentIndex(0);
    setAnswers({});
    setQuizStarted(true);
  };

  const selectAnswer = (questionId, option) => {
    setAnswers((prev) => ({ ...prev, [questionId]: option }));
  };

  const goNext = () => {
    if (currentIndex < activeLesson.quiz.length - 1) {
      setCurrentIndex((i) => i + 1);
    } else {
      finishQuiz();
    }
  };

  const goPrev = () => {
    if (currentIndex > 0) setCurrentIndex((i) => i - 1);
  };

  const finishQuiz = async () => {
    const questions = activeLesson.quiz;
    // Positional array of chosen option indices, in quiz order — the backend
    // grades this server-side (the answer key is never sent to the client).
    const answersArray = questions.map((q) => q.options.indexOf(answers[q.id]));

    try {
      const result = await submitLessonQuiz(activeLesson.id, answersArray);
      setQuizStarted(false);
      setShowResults({
        score: result.quizScore,
        passed: result.quizPassed,
        correct: result.correct,
        total: result.total,
      });
    } catch (e) {
      notify("Failed to record your quiz score. Please try again.", { type: "error" });
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <LoadingSpinner label="Loading your video lessons…" />
      </div>
    );
  }

  // --- Results overlay ----------------------------------------------------
  if (showResults) {
    return (
      <div>
        <PageHeader
          title="Quiz Results"
          subtitle={activeLesson.title}
          breadcrumbs={[{ label: "Video Lessons", to: "/student/learning" }]}
        />
        <Card className="max-w-xl mx-auto text-center py-8">
          <div className="flex justify-center mb-4">
            <div className={`h-16 w-16 rounded-full flex items-center justify-center ${showResults.passed ? "bg-success-50 text-success-500" : "bg-error-50 text-error-500"}`}>
              {showResults.passed ? <Award size={36} /> : <AlertCircle size={36} />}
            </div>
          </div>
          <h2 className="text-2xl font-bold font-display text-ink-900">{showResults.passed ? "Quiz Passed" : "Below Passing Score"}</h2>
          <p className="text-sm text-ink-500 mt-1">
            {showResults.correct} of {showResults.total} answered correctly
          </p>

          <div className="my-8">
            <p className="text-5xl font-bold font-display text-ink-900">{showResults.score}%</p>
            <p className="text-xs text-ink-400 mt-1">Minimum passing score: {activeLesson.passingScore ?? 60}%</p>
          </div>

          <div className="flex items-center justify-center gap-3">
            {!showResults.passed && (
              <Button variant="secondary" onClick={startQuiz}>Retake Quiz</Button>
            )}
            <Button onClick={backToList}>Back to Video Lessons</Button>
          </div>
        </Card>
      </div>
    );
  }

  // --- Active quiz ---------------------------------------------------------
  if (activeLesson && quizStarted) {
    const question = activeLesson.quiz[currentIndex];
    const answered = answers[question.id] !== undefined;

    return (
      <div>
        <PageHeader
          title={activeLesson.title}
          subtitle={`Question ${currentIndex + 1} of ${activeLesson.quiz.length}`}
          breadcrumbs={[{ label: "Video Lessons", to: "/student/learning" }]}
        />
        <Card className="max-w-2xl mx-auto">
          <ProgressBar value={((currentIndex + 1) / activeLesson.quiz.length) * 100} showValue={false} />
          <p className="text-base font-medium text-ink-900 mt-6 mb-5">{question.question}</p>

          <div className="flex flex-col gap-2.5">
            {question.options.map((opt) => {
              const selected = answers[question.id] === opt;
              return (
                <button
                  key={opt}
                  onClick={() => selectAnswer(question.id, opt)}
                  className={`text-left rounded-lg border px-4 py-3 text-sm transition-colors ${
                    selected ? "border-primary-600 bg-primary-50 text-primary-700 font-medium" : "border-border hover:bg-cream-100/60 text-ink-700"
                  }`}
                >
                  {opt}
                </button>
              );
            })}
          </div>

          <div className="flex items-center justify-between mt-8">
            <Button variant="ghost" size="sm" icon={ArrowLeft} onClick={goPrev} disabled={currentIndex === 0}>Previous</Button>
            <Button size="sm" icon={ArrowRight} iconPosition="right" onClick={goNext} disabled={!answered}>
              {currentIndex === activeLesson.quiz.length - 1 ? "Submit Quiz" : "Next"}
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  // --- Video player + quiz launch screen -----------------------------------
  if (activeLesson) {
    return (
      <div>
        <PageHeader
          title={activeLesson.title}
          subtitle={activeLesson.module}
          breadcrumbs={[{ label: "Video Lessons", to: "/student/learning" }]}
          action={<Button variant="ghost" size="sm" icon={ArrowLeft} onClick={backToList}>Back to lessons</Button>}
        />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <Card className="lg:col-span-2" padding={false}>
            {loadingLesson ? (
              <div className="flex items-center justify-center aspect-video">
                <LoadingSpinner label="Loading player…" />
              </div>
            ) : (
              <div className="aspect-video w-full overflow-hidden rounded-t-xl bg-black">
                {activeLesson.videoType === "upload" && activeLesson.videoUrl ? (
                  <video
                    className="w-full h-full"
                    src={activeLesson.videoUrl}
                    controls
                    autoPlay
                  />
                ) : (
                  <iframe
                    className="w-full h-full"
                    src={`https://www.youtube.com/embed/${activeLesson.videoId}`}
                    title={activeLesson.title}
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowFullScreen
                  />
                )}
              </div>
            )}
            <div className="p-5">
              <div className="flex items-center gap-2 flex-wrap mb-2">
                <Badge tone="primary">{activeLesson.module}</Badge>
                <Badge tone="neutral"><Clock size={12} /> {activeLesson.duration}</Badge>
                {hasWatched && <Badge tone="success"><CheckCircle2 size={12} /> Watched</Badge>}
              </div>
              <p className="text-sm text-ink-600">{activeLesson.description}</p>
            </div>
          </Card>

          <Card>
            <CardHeader title="Lesson Quiz" subtitle={`${activeLesson.quiz.length} questions · ${activeLesson.passingScore ?? 60}% to pass`} />
            {activeLesson.quizScore !== null && activeLesson.quizScore !== undefined ? (
              <div className="flex flex-col gap-3">
                <div className="flex items-center gap-2 rounded-lg bg-cream-100 px-3 py-2.5">
                  {activeLesson.quizPassed ? <CheckCircle2 size={16} className="text-success-600 shrink-0" /> : <AlertCircle size={16} className="text-warning-600 shrink-0" />}
                  <p className="text-sm text-ink-700">Last score: <span className="font-semibold">{activeLesson.quizScore}%</span></p>
                </div>
                <Button variant="secondary" fullWidth onClick={startQuiz}>Retake Quiz</Button>
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                <p className="text-sm text-ink-500">
                  {hasWatched ? "You're ready to take the quiz for this lesson." : "Finish watching the video, then take the quiz to mark this lesson complete."}
                </p>
                <Button fullWidth icon={ListChecks} onClick={startQuiz}>Take Quiz</Button>
              </div>
            )}
          </Card>
        </div>
      </div>
    );
  }

  // --- Lesson catalog list ---------------------------------------------------
  const totalLessons = lessons.length;
  const completedLessons = lessons.filter((l) => l.quizPassed).length;

  return (
    <div>
      <PageHeader title="Video Lessons" subtitle="Self-paced video modules with an auto-graded quiz at the end of each one" />

      <Card className="mb-4">
        <ProgressBar
          value={totalLessons ? (completedLessons / totalLessons) * 100 : 0}
          tone="success"
          label={`${completedLessons} of ${totalLessons} lessons completed`}
        />
      </Card>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {lessons.map((lesson) => (
          <Card key={lesson.id} className="flex flex-col cursor-pointer hover:shadow-lg transition-shadow" onClick={() => openLesson(lesson)}>
            <div className="flex items-start justify-between gap-2 mb-2">
              <Badge tone="neutral">{lesson.module}</Badge>
              {lesson.quizPassed ? (
                <Badge tone="success"><CheckCircle2 size={12} /> Passed</Badge>
              ) : lesson.quizScore !== null ? (
                <Badge tone="warning"><AlertCircle size={12} /> Retake</Badge>
              ) : null}
            </div>
            <div className="flex items-center justify-center h-28 rounded-lg bg-primary-50 mb-3">
              <PlayCircle size={40} className="text-primary-600" />
            </div>
            <h3 className="text-sm font-semibold text-ink-900">{lesson.title}</h3>
            <p className="text-xs text-ink-500 mt-1 line-clamp-2 flex-1">{lesson.description}</p>
            <div className="flex items-center justify-between mt-4 text-xs text-ink-400">
              <span className="flex items-center gap-1"><Clock size={12} /> {lesson.duration}</span>
              {lesson.questionCount != null && (
                <span className="flex items-center gap-1"><ListChecks size={12} /> {lesson.questionCount} questions</span>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}