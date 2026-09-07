import { useEffect, useState } from "react";
import { ListChecks, Percent, AlertTriangle, Trophy, GitFork, ArrowRight, Search, PlayCircle, CalendarDays, CheckCircle2, Clock, FileText, UploadCloud, RefreshCw, Eye, Briefcase, Video } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import StatCard from "../../components/widgets/StatCard";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { getPerformanceSummary, getMyTasks, getMyPipStatus, getVideoLessons, saveResumeFile, getMyResumeUrl, getMySubscription, getMyBatch, getMyStandups, getMyAttendance } from "../../services/studentService";
import { loadRecruitments, stageTone, stageMessage, REJECTED } from "../../utils/placementPipeline";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { Input } from "../../components/ui/FormField";

export default function StudentDashboard() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [subscription, setSubscription] = useState(null);

  // D2 flow: a freshly-verified student has no subscription yet — send them to
  // pick a plan before the dashboard is useful.
  useEffect(() => {
    let cancelled = false;
    getMySubscription()
      .then((sub) => {
        if (cancelled) return;
        setSubscription(sub || null);
        if (!sub) {
          notify("Choose a plan to unlock your training dashboard.", { type: "info" });
          navigate("/student/subscription", { replace: true });
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [navigate, notify]);
  const [summary, setSummary] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [pip, setPip] = useState(null);
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [myBatch, setMyBatch] = useState(null);

  // Batch enrolment isn't on /users/me — read it here so the header reflects an
  // auto-assignment without needing a re-login.
  useEffect(() => {
    getMyBatch().then(setMyBatch).catch(() => setMyBatch(null));
  }, []);

  // Today's standup + the caller's attendance for it — real, from
  // /api/v1/standups and /api/v1/attendance/me. Students only join the meeting
  // link; the trainer records attendance at the start of the day.
  const [todayStandup, setTodayStandup] = useState(null);
  const [standupMark, setStandupMark] = useState(null); // { status, markedByPm, autoMarked } | null

  const loadStandup = () => {
    const today = new Date().toISOString().slice(0, 10);
    Promise.all([getMyStandups(today), getMyAttendance()])
      .then(([standups, att]) => {
        const s = standups.find((x) => x.status !== "CANCELLED") || null;
        setTodayStandup(s);
        setStandupMark(s ? att.find((a) => String(a.standupId) === String(s.id)) || null : null);
      })
      .catch(() => {
        setTodayStandup(null);
        setStandupMark(null);
      });
  };
  useEffect(() => {
    loadStandup();
  }, []);

  // Resume upload — required before the student becomes eligible for client
  // job opportunities. The real state is GET /api/v1/users/me/resume: a
  // presigned URL means one is on file, null means it isn't. (There is no
  // user.profileDetails.resume anywhere — that old reference was always
  // undefined, so the banner used to be stuck on "Not Uploaded".)
  const [resumeUrl, setResumeUrl] = useState(null);
  const [resumeModalOpen, setResumeModalOpen] = useState(false);
  const [pendingResumeFile, setPendingResumeFile] = useState([]);
  const [savingResume, setSavingResume] = useState(false);
  const [recruitment, setRecruitment] = useState(null);

  useEffect(() => {
    Promise.all([
      getPerformanceSummary().catch(() => null),
      getMyTasks().catch(() => []),
      getMyPipStatus().catch(() => null),
      getVideoLessons().catch(() => []),
      getMyResumeUrl().catch(() => null),
    ])
      .then(([s, t, p, vl, ru]) => {
        setSummary(s);
        setTasks(t);
        setPip(p);
        setLessons(vl);
        setResumeUrl(ru);

        // Surface the most advanced non-rejected client recruitment record so
        // the student can see e.g. "Shortlisted" status right on their
        // dashboard, not just on the dedicated Interviews page.
        if (user) {
          try {
            const mine = loadRecruitments().filter((r) => r.candidateName === user.name && r.stage !== REJECTED);
            setRecruitment(mine[0] || null);
          } catch (e) {
            setRecruitment(null);
          }
        }
      })
      .finally(() => setLoading(false));
  }, [user]);

  const openResumeModal = () => {
    setPendingResumeFile([]);
    setResumeModalOpen(true);
  };

  const handleSaveResume = async () => {
    const file = pendingResumeFile[0];
    if (!file || !(file instanceof File) || file.size === 0) {
      notify("Please choose a resume file to upload.", { type: "error" });
      return;
    }
    setSavingResume(true);
    try {
      await saveResumeFile(user, file);
      // Re-read the real status so the banner reflects what's actually stored.
      const url = await getMyResumeUrl().catch(() => null);
      setResumeUrl(url);
      notify("Resume uploaded successfully.", { type: "success", title: "Resume saved" });
      setResumeModalOpen(false);
    } catch (e) {
      notify("Could not save your resume. Please try again.", { type: "error" });
    } finally {
      setSavingResume(false);
    }
  };

  const handleViewResume = () => {
    if (resumeUrl) window.open(resumeUrl, "_blank", "noopener,noreferrer");
  };

  const hasResume = !!resumeUrl;
  const resumeStatus = hasResume ? "Uploaded" : "Not Uploaded";

  const [searchParams, setSearchParams] = useSearchParams();

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <LoadingSpinner label="Loading your dashboard…" />
      </div>
    );
  }

  const query = searchParams.get("search")?.toLowerCase() || "";
  const upcoming = tasks
    .filter((t) => t.status !== "Completed")
    .filter((t) => !query || t.title.toLowerCase().includes(query))
    .slice(0, 4);

  const gitActivity = tasks
    .filter((t) => t.githubPr)
    .filter((t) => !query || t.title.toLowerCase().includes(query));

  return (
    <div>
      <PageHeader
        title={`Welcome back, ${user?.name?.split(" ")[0]}`}
        subtitle={
          myBatch
            ? [subscription?.planName, myBatch.trackCode, myBatch.name].filter(Boolean).join(" · ")
            : subscription?.planName
              ? `${subscription.planName} — we're placing you into a batch. You'll be notified the moment you're in.`
              : "You're on a plan — we're placing you into a batch. You'll be notified the moment you're in."
        }
      />

      <div className="mb-6 max-w-md">
        <Input
          placeholder="Search tasks and GitHub activities..."
          value={searchParams.get("search") || ""}
          onChange={(e) => {
            const val = e.target.value;
            if (val) {
              setSearchParams({ ...Object.fromEntries(searchParams.entries()), search: val });
            } else {
              const copy = Object.fromEntries(searchParams.entries());
              delete copy.search;
              setSearchParams(copy);
            }
          }}
          icon={Search}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard label="Attendance" value={`${summary.attendance}%`} icon={Percent} tone="primary" trend={2} trendLabel="vs last week" />
        <StatCard label="Task Completion" value={`${summary.taskCompletion}%`} icon={ListChecks} tone="gold" trend={5} trendLabel="vs last sprint" />
        <StatCard label="Quiz Average" value={`${summary.quizAverage}%`} icon={Trophy} tone="success" trend={-1} trendLabel="vs last week" />
        <StatCard label="Sprint Velocity" value={`${summary.sprintVelocity} pts`} icon={ArrowRight} tone="primary" />
      </div>

      {/* Resume Upload Reminder Banner */}
      <Card className="mt-4 border-l-4 border-l-gold-500 bg-gradient-to-br from-gold-50/60 to-white">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div className="flex items-start gap-3">
            <div className="h-10 w-10 rounded-lg bg-gold-100 flex items-center justify-center shrink-0">
              <FileText className="text-gold-700" size={20} />
            </div>
            <div>
              <h3 className="font-display font-bold text-ink-900 text-base">
                For Future Job Placements, Upload Your Resume
              </h3>
              <p className="text-xs text-ink-500 mt-1 max-w-md">
                {resumeStatus === "Not Uploaded"
                  ? "You won't be eligible for client job opportunities until your resume is on file. Upload it to get started."
                  : "Keep your resume current — clients review it before shortlisting you for job opportunities."}
              </p>
            </div>
          </div>

          <div className="flex flex-col items-end gap-2 shrink-0">
            <Badge tone={resumeStatus === "Not Uploaded" ? "warning" : resumeStatus === "Updated" ? "primary" : "success"}>
              {resumeStatus}
            </Badge>
            <div className="flex gap-2">
              {hasResume && (
                <Button size="sm" variant="secondary" icon={Eye} onClick={handleViewResume}>View</Button>
              )}
              <Button
                size="sm"
                icon={resumeStatus === "Not Uploaded" ? UploadCloud : RefreshCw}
                onClick={openResumeModal}
              >
                {resumeStatus === "Not Uploaded" ? "Upload Resume" : "Replace Resume"}
              </Button>
            </div>
          </div>
        </div>

        {recruitment && (
          <div className="mt-4 pt-4 border-t border-border/60 flex items-center gap-2 text-left">
            <Briefcase size={14} className="text-primary-600 shrink-0" />
            <p className="text-xs text-ink-600">
              <strong className="text-ink-800">Client interest:</strong>{" "}
              <Badge tone={stageTone(recruitment.stage)} className="mx-1">{recruitment.stage}</Badge>
              {stageMessage(recruitment.stage, "student")}
            </p>
          </div>
        )}
      </Card>

      {/* Today's Standup — real, from /api/v1/standups + /api/v1/attendance/me */}
      <Card className="mt-4 border-l-4 border-l-primary-600 bg-gradient-to-br from-cream-50/40 to-white">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <h3 className="font-display font-bold text-ink-900 text-base flex items-center gap-2">
              <CalendarDays className="text-primary-600" size={18} /> Today's Standup
            </h3>
            <p className="text-xs text-ink-500 mt-1">
              {todayStandup
                ? `Starts ${new Date(todayStandup.scheduledAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })} · late after ${todayStandup.lateCutoffMinutes} min`
                : "No standup scheduled for today."}
            </p>
          </div>

          {standupMark ? (
            <Badge tone={standupMark.status === "Present" ? "success" : standupMark.status === "Late" ? "warning" : "error"} className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
              <CheckCircle2 size={14} /> Marked by trainer — {standupMark.status}
            </Badge>
          ) : todayStandup ? (
            <span className="text-xs font-semibold text-ink-500 bg-cream-50 px-2.5 py-1 rounded-md border border-border/60 flex items-center gap-1">
              <Clock size={12} /> Attendance not marked yet
            </span>
          ) : null}
        </div>

        {todayStandup && (
          <div className="mt-4 pt-4 border-t border-border/60 flex flex-col gap-3">
            {todayStandup.notes && (
              <p className="text-xs text-ink-600 bg-cream-50 p-3 rounded-lg border border-border/40">{todayStandup.notes}</p>
            )}
            <div className="flex justify-end gap-2">
              {todayStandup.meetingLink && (
                <a href={todayStandup.meetingLink} target="_blank" rel="noreferrer">
                  <Button icon={Video}>Join meet</Button>
                </a>
              )}
              <Link to="/student/attendance"><Button variant="ghost">View history</Button></Link>
            </div>
          </div>
        )}
      </Card>

      <Card className="mt-4">
        <CardHeader
          title="Continue Learning"
          subtitle="Video lessons with a quiz at the end of each one"
          icon={PlayCircle}
          action={<Link to="/student/learning"><Button variant="ghost" size="sm" icon={ArrowRight} iconPosition="right">View all lessons</Button></Link>}
        />
        {lessons.length > 0 ? (
          <div className="flex flex-col divide-y divide-border">
            {(lessons.find((l) => !l.quizPassed) ? [lessons.find((l) => !l.quizPassed)] : [lessons[0]]).map((l) => (
              <Link key={l.id} to="/student/learning" className="flex items-center gap-3 py-3 hover:bg-cream-100/60 -mx-2 px-2 rounded-lg">
                <div className="h-10 w-10 rounded-lg bg-primary-50 flex items-center justify-center shrink-0">
                  <PlayCircle size={20} className="text-primary-600" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-ink-900 truncate">{l.title}</p>
                  <p className="text-xs text-ink-500 mt-0.5">{l.module} · {l.duration} · {l.questionCount} question quiz</p>
                </div>
                <Badge tone={l.quizPassed ? "success" : "primary"}>{l.quizPassed ? "Completed" : l.watched ? "Quiz pending" : "Start"}</Badge>
              </Link>
            ))}
          </div>
        ) : (
          <p className="text-sm text-ink-400 py-4 text-center">No video lessons published yet.</p>
        )}
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mt-4">
        <Card className="lg:col-span-2">
          <CardHeader title="Upcoming & Active Tasks" subtitle="From your current sprint board" action={
            <Link to="/student/tasks"><Button variant="ghost" size="sm" icon={ArrowRight} iconPosition="right">View board</Button></Link>
          } />
          <div className="flex flex-col divide-y divide-border">
            {upcoming.length > 0 ? (
              upcoming.map((t) => (
                <div key={t.id} className="flex items-center justify-between py-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-ink-900 truncate">{t.title}</p>
                    <p className="text-xs text-ink-500 mt-0.5">Due {t.due} · {t.points} pts</p>
                  </div>
                  <Badge tone={t.status === "Review" ? "info" : t.status === "In Progress" ? "gold" : "neutral"}>{t.status}</Badge>
                </div>
              ))
            ) : (
              <p className="text-sm text-ink-400 py-6 text-center">
                {query ? "No tasks match your search query." : "No active or upcoming tasks assigned to your batch yet."}
              </p>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader title="PIP & Performance" subtitle="Real-time recovery status" />
          {pip && pip.status !== "Terminated" ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2 rounded-lg bg-warning-50 px-3 py-2.5">
                <AlertTriangle size={16} className="text-warning-600 shrink-0" />
                <p className="text-sm text-warning-600">Active PIP: {pip.reason}</p>
              </div>
              <ProgressBar value={pip.daysRemaining > 0 ? ((15 - pip.daysRemaining) / 15) * 100 : 100} tone="warning" label={`${Math.max(pip.daysRemaining, 0)} days remaining`} />
              <Link to="/student/pip">
                <Button variant="secondary" size="sm" fullWidth>View recovery plan</Button>
              </Link>
            </div>
          ) : (
            <div className="flex flex-col items-center text-center py-6">
              <Trophy className="text-success-600" size={28} />
              <p className="text-sm font-medium text-ink-800 mt-2">You're in good standing</p>
              <p className="text-xs text-ink-400 mt-1">No active performance flags.</p>
            </div>
          )}
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader title="Recent GitHub Activity" icon={GitFork} action={<Link to="/student/submissions"><Button variant="ghost" size="sm" icon={ArrowRight} iconPosition="right">All submissions</Button></Link>} />
        <div className="flex flex-col divide-y divide-border">
          {gitActivity.length > 0 ? (
            gitActivity.map((t) => (
              <a key={t.id} href={t.githubPr} target="_blank" rel="noreferrer" className="flex items-center gap-3 py-3 hover:bg-cream-100/60 -mx-2 px-2 rounded-lg">
                <GitFork size={16} className="text-ink-400 shrink-0" />
                <p className="text-sm text-ink-700 truncate flex-1">{t.title}</p>
                <Badge tone="primary">{t.status}</Badge>
              </a>
            ))
          ) : (
            <p className="text-sm text-ink-400 py-4 text-center">
              {query ? "No submissions match your search query." : "No recent pull requests submitted."}
            </p>
          )}
        </div>
      </Card>

      {/* Upload / Replace Resume Modal */}
      <Modal
        open={resumeModalOpen}
        onClose={() => setResumeModalOpen(false)}
        title={resumeStatus === "Not Uploaded" ? "Upload Your Resume" : "Replace Your Resume"}
        description="Clients review this resume before shortlisting you for job opportunities."
        footer={
          <>
            <Button variant="secondary" onClick={() => setResumeModalOpen(false)}>Cancel</Button>
            <Button icon={UploadCloud} loading={savingResume} onClick={handleSaveResume}>Save Resume</Button>
          </>
        }
      >
        <FileUpload
          label="Resume"
          hint="Upload PDF (max 10MB)"
          accept=".pdf"
          initialFiles={pendingResumeFile}
          onChange={setPendingResumeFile}
        />
      </Modal>
    </div>
  );
}