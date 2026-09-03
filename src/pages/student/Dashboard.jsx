import { useEffect, useState } from "react";
import { ListChecks, Percent, AlertTriangle, Trophy, GitFork, ArrowRight, Search, PlayCircle, CalendarDays, CheckCircle2, Clock, Check, FileText, UploadCloud, RefreshCw, Eye, Briefcase } from "lucide-react";
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
import { getPerformanceSummary, getMyTasks, getMyPipStatus, getVideoLessons, getResumeStatus, saveResumeFile, getMySubscription, getMyBatch } from "../../services/studentService";
import { loadRecruitments, stageTone, stageMessage, REJECTED } from "../../utils/placementPipeline";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { Input } from "../../components/ui/FormField";

export default function StudentDashboard() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  // D2 flow: a freshly-verified student has no subscription yet — send them to
  // pick a plan before the dashboard is useful.
  useEffect(() => {
    let cancelled = false;
    getMySubscription()
      .then((sub) => {
        if (!cancelled && !sub) {
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

  const [hasCheckedIn, setHasCheckedIn] = useState(false);
  const [checkinTime, setCheckinTime] = useState("");
  const [hasCheckedOut, setHasCheckedOut] = useState(false);
  const [checkoutTime, setCheckoutTime] = useState("");
  const [blockerNote, setBlockerNote] = useState("");
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);
  // True when today's row was created/marked by the trainer (e.g. from the
  // Standups & Attendance roster) rather than by the student themselves —
  // in that case we still show the student their own "Clock In" button so
  // they can submit today's standup note instead of being locked out.
  const [trainerMarked, setTrainerMarked] = useState(false);
  const [trainerMarkedTime, setTrainerMarkedTime] = useState("");
  const [trainerMarkedStatus, setTrainerMarkedStatus] = useState("");

  // Resume upload — required before the student becomes eligible for
  // client job opportunities (see HR Exit → Client Talent Pool handoff).
  const [resume, setResume] = useState(user?.profileDetails?.resume || null);
  const [resumeModalOpen, setResumeModalOpen] = useState(false);
  const [pendingResumeFile, setPendingResumeFile] = useState([]);
  const [savingResume, setSavingResume] = useState(false);
  const [recruitment, setRecruitment] = useState(null);

  useEffect(() => {
    Promise.all([getPerformanceSummary(), getMyTasks(), getMyPipStatus(), getVideoLessons()]).then(([s, t, p, vl]) => {
      setSummary(s);
      setTasks(t);
      setPip(p);
      setLessons(vl);

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

      if (user) {
        try {
          const rawLogs = localStorage.getItem("msh_attendance_logs");
          const logs = rawLogs ? JSON.parse(rawLogs) : [];
          const todayStr = new Date().toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
          const myLog = logs.find((l) => l.name === user.name && l.date === todayStr);
          if (myLog) {
            const inTime = myLog.checkIn || myLog.time; // `time` kept for older saved logs
            // Older/legacy self check-ins never wrote a `loggedBy` field, so
            // fall back to the WEB-AUTH-PORTAL device id (only ever used by
            // the student's own Clock In button) to recognize them as self.
            const isSelfCheckin = myLog.loggedBy === "student" || (!myLog.loggedBy && myLog.deviceId === "WEB-AUTH-PORTAL");
            if (inTime && inTime !== "--" && isSelfCheckin) {
              setHasCheckedIn(true);
              setCheckinTime(inTime);
              setBlockerNote(myLog.notes);
            } else if (inTime && inTime !== "--") {
              // Trainer (or HR) logged this row on the student's behalf —
              // don't treat it as the student's own check-in, but let them
              // know and still offer the Clock In button below.
              setTrainerMarked(true);
              setTrainerMarkedTime(inTime);
              setTrainerMarkedStatus(myLog.status);
            }
            if (myLog.checkOut && myLog.checkOut !== "--") {
              setHasCheckedOut(true);
              setCheckoutTime(myLog.checkOut);
            }
          }
        } catch (e) {
          console.warn("Failed to load today check-in log:", e);
        }
      }

      setLoading(false);
    });
  }, [user]);

  const handleCheckIn = async () => {
    setCheckingIn(true);
    await new Promise((r) => setTimeout(r, 800));
    setCheckingIn(false);

    const now = new Date();
    const timeStr = now.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
    const todayStr = now.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });

    let status = "Present";
    const hours = now.getHours();
    const minutes = now.getMinutes();
    if (hours > 9 || (hours === 9 && minutes > 15)) {
      status = "Late";
    }

    // Written in the same shape HR's Attendance & Leave "Live Biometric /
    // Web Check-ins" table reads (checkIn/checkOut/deviceId) — see
    // hrService.getClockinLogs — so a student's self check-in actually
    // shows up there with a real check-in time instead of being invisible.
    // `loggedBy: "student"` marks this as the student's own submission so
    // it isn't mistaken for a trainer-side manual mark on the next load.
    const noteText = blockerNote.trim() || "None (On Track)";
    const newLog = {
      id: `log-${Date.now()}`,
      name: user?.name || "Student",
      role: "Student",
      date: todayStr,
      checkIn: timeStr,
      checkOut: "--",
      hours: 0,
      deviceId: "WEB-AUTH-PORTAL",
      status,
      notes: noteText,
      loggedBy: "student"
    };

    try {
      const rawLogs = localStorage.getItem("msh_attendance_logs") || "[]";
      const logs = JSON.parse(rawLogs);
      // If the trainer already created today's row (e.g. via Standups &
      // Attendance), fill in the student's own check-in on that same row
      // instead of adding a duplicate entry for the day.
      const todayIdx = logs.findIndex((l) => l.name === (user?.name || "Student") && l.date === todayStr);
      if (todayIdx > -1) {
        logs[todayIdx] = { ...logs[todayIdx], ...newLog, id: logs[todayIdx].id };
      } else {
        logs.unshift(newLog);
      }
      localStorage.setItem("msh_attendance_logs", JSON.stringify(logs));

      const rawUsers = localStorage.getItem("mORIAH_REGISTERED_USERS");
      if (rawUsers && user) {
        const users = JSON.parse(rawUsers);
        const idx = users.findIndex((u) => u.id === user.id);
        if (idx > -1) {
          const u = users[idx];
          const newAttendance = Math.min((u.attendance || 90) + 1, 100);
          users[idx] = { ...u, attendance: newAttendance };
          localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(users));
          localStorage.setItem("msh_user", JSON.stringify(users[idx]));
          
          setSummary((s) => s ? { ...s, attendance: newAttendance } : null);
        }
      }

      setHasCheckedIn(true);
      setCheckinTime(timeStr);
      setBlockerNote(newLog.notes);
      setTrainerMarked(false);
      notify(`Successfully checked in! Clock-in logged as ${status}.`, { type: "success" });
    } catch (e) {
      notify("Failed to save check-in.", { type: "error" });
    }
  };

  // Records the student's check-out time against today's existing log entry
  // so HR sees both the check-in and check-out timing side by side before
  // finalizing attendance for the day.
  const handleCheckOut = async () => {
    setCheckingOut(true);
    await new Promise((r) => setTimeout(r, 500));
    setCheckingOut(false);

    const now = new Date();
    const timeStr = now.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
    const todayStr = now.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });

    try {
      const rawLogs = localStorage.getItem("msh_attendance_logs") || "[]";
      const logs = JSON.parse(rawLogs);
      const idx = logs.findIndex((l) => l.name === (user?.name || "Student") && l.date === todayStr);
      if (idx === -1) {
        notify("Please check in before checking out.", { type: "error" });
        return;
      }

      const entry = logs[idx];
      const inTime = entry.checkIn || entry.time;
      let hoursWorked = entry.hours || 0;
      if (inTime) {
        const parseTime = (t) => {
          const match = /(\d+):(\d+)\s*(am|pm)/i.exec(t);
          if (!match) return null;
          let [, h, m, period] = match;
          h = parseInt(h, 10);
          m = parseInt(m, 10);
          if (/pm/i.test(period) && h !== 12) h += 12;
          if (/am/i.test(period) && h === 12) h = 0;
          return h * 60 + m;
        };
        const inMins = parseTime(inTime);
        const outMins = parseTime(timeStr);
        if (inMins !== null && outMins !== null && outMins > inMins) {
          hoursWorked = Math.round(((outMins - inMins) / 60) * 10) / 10;
        }
      }

      logs[idx] = { ...entry, checkIn: inTime, checkOut: timeStr, hours: hoursWorked };
      localStorage.setItem("msh_attendance_logs", JSON.stringify(logs));

      setHasCheckedOut(true);
      setCheckoutTime(timeStr);
      notify("Successfully checked out!", { type: "success" });
    } catch (e) {
      notify("Failed to save check-out.", { type: "error" });
    }
  };

  const openResumeModal = () => {
    setPendingResumeFile(resume?.name ? [new File([], resume.name, { type: "application/pdf" })] : []);
    setResumeModalOpen(true);
  };

  const handleSaveResume = async () => {
    const file = pendingResumeFile[0];
    // The placeholder reconstructed from a previously-saved resume (see
    // openResumeModal) is a real but empty (0-byte) File used only so
    // FileUpload has something to display — it isn't a fresh selection, so
    // don't try to persist it as new content.
    if (!file || !(file instanceof File) || file.size === 0) {
      notify("Please choose a resume file to upload.", { type: "error" });
      return;
    }
    setSavingResume(true);
    try {
      const { resumeMeta } = await saveResumeFile(user, file);
      setResume(resumeMeta);
      notify("Resume uploaded successfully.", { type: "success", title: "Resume saved" });
      setResumeModalOpen(false);
    } catch (e) {
      notify("Could not save your resume. Please try again.", { type: "error" });
    } finally {
      setSavingResume(false);
    }
  };

  const handleViewResume = () => {
    if (!resume?.fileData) return;
    try {
      const [header, base64] = resume.fileData.split(",");
      const mimeMatch = header.match(/data:(.*?);base64/);
      const mime = mimeMatch ? mimeMatch[1] : "application/octet-stream";
      const binary = atob(base64);
      const array = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
      const blob = new Blob([array], { type: mime });
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      notify("Could not open your resume.", { type: "error" });
    }
  };

  const resumeStatus = getResumeStatus(resume);

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
            ? [myBatch.trackCode, myBatch.name].filter(Boolean).join(" · ")
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
              {resume?.fileData && (
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

      {/* Daily Standup Check-in Card */}
      <Card className="mt-4 border-l-4 border-l-primary-600 bg-gradient-to-br from-cream-50/40 to-white">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <h3 className="font-display font-bold text-ink-900 text-base flex items-center gap-2">
              <CalendarDays className="text-primary-600" size={18} /> Daily Standup Check-in
            </h3>
            <p className="text-xs text-ink-500 mt-1">
              Active check-in window: 09:00 AM – 10:00 AM. Log your daily blocker updates.
            </p>
          </div>
          
          {hasCheckedIn ? (
            <div className="flex items-center gap-2 flex-wrap">
              <Badge tone="success" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
                <CheckCircle2 size={14} /> Checked In at {checkinTime}
              </Badge>
              {hasCheckedOut && (
                <Badge tone="primary" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
                  <CheckCircle2 size={14} /> Checked Out at {checkoutTime}
                </Badge>
              )}
            </div>
          ) : trainerMarked ? (
            <div className="flex items-center gap-2 flex-wrap">
              <Badge tone="warning" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
                <Clock size={14} /> Marked {trainerMarkedStatus || "Present"} by Trainer at {trainerMarkedTime}
              </Badge>
            </div>
          ) : (
            <span className="text-xs font-semibold text-warning-600 bg-warning-50 px-2.5 py-1 rounded-md border border-warning-100 flex items-center gap-1">
              <Clock size={12} /> Pending Check-in
            </span>
          )}
        </div>

        {!hasCheckedIn ? (
          <div className="mt-4 pt-4 border-t border-border/60 flex flex-col gap-3">
            {trainerMarked && (
              <p className="text-xs text-ink-600 bg-cream-50 p-3 rounded-lg border border-border/40">
                Your trainer already logged an attendance entry for you today, but you haven't checked in yourself yet — clock in below to submit your own standup note.
              </p>
            )}
            <div className="flex flex-col gap-1.5 text-left">
              <label className="text-xs font-semibold text-ink-700">What are you working on today? Any blockers?</label>
              <textarea
                value={blockerNote}
                onChange={(e) => setBlockerNote(e.target.value)}
                placeholder="e.g. Working on checkout flow. Blocked on Stripe API test tokens."
                className="w-full text-sm rounded-lg border border-border p-3 focus:ring-2 focus:ring-primary-500/20 focus:border-primary-500 outline-none placeholder:text-ink-300"
                rows={2}
              />
            </div>
            <div className="flex justify-end">
              <Button onClick={handleCheckIn} loading={checkingIn} icon={Check}>
                Clock In & Submit Standup
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-4 pt-4 border-t border-border/60 flex flex-col gap-3 text-left">
            <p className="text-xs text-ink-600 font-medium bg-cream-50 p-3 rounded-lg border border-border/40">
              <strong className="text-ink-800">Your Blocker Note:</strong> {blockerNote || "None (On Track)"}
            </p>
            {!hasCheckedOut && (
              <div className="flex justify-end">
                <Button onClick={handleCheckOut} loading={checkingOut} icon={Check} variant="secondary">
                  Clock Out
                </Button>
              </div>
            )}
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