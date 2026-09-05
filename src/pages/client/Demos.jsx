import { useEffect, useState } from "react";
import { Video, Calendar, Star, MessageSquare, TrendingUp } from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getClientProjects, getClientProjectProgress } from "../../services/clientService";
import { getMyMeetings } from "../../services/meetingService";
import { useToast } from "../../context/ToastContext";

const STATUS_TONE = { SCHEDULED: "primary", COMPLETED: "success", CANCELLED: "error" };

/**
 * FRS MSH-FR-BA-03 ("Client Project Review Portal") + MSH-FR-BA-04 ("Client Meeting
 * Coordination"): a client should be able to inspect batch progress / sprint burn-down here, and
 * join the live Sprint Demo their BA scheduled — both of those already exist elsewhere
 * (ClientProjectProgressResponse / the BaMeeting "Sprint Demo" ceremony type) and are wired in
 * here rather than reimplemented. This page used to render one fake card per project with
 * placeholder fields (progress/milestone/demoDate) the real project API never returned — hence
 * the NaN% and blank "Next demo" a client would have seen.
 */
export default function ClientDemos() {
  const [projects, setProjects] = useState([]);
  const [progressByProject, setProgressByProject] = useState({}); // { [projectId]: ClientProjectProgressResponse }
  const [demosByProject, setDemosByProject] = useState({}); // { [projectId]: { next, lastCompleted } }
  const [loading, setLoading] = useState(true);

  // Feedback states — still a client-local note for now (no backend "demo feedback" concept yet).
  const [feedbacks, setFeedbacks] = useState({});
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [targetProjectId, setTargetProjectId] = useState(null);
  const [rating, setRating] = useState("5");
  const [comments, setComments] = useState("");

  const { notify } = useToast();

  useEffect(() => {
    const saved = localStorage.getItem("msh_client_projects_feedback");
    if (saved) setFeedbacks(JSON.parse(saved));

    Promise.all([getClientProjects(), getMyMeetings().catch(() => [])])
      .then(async ([projectRows, meetings]) => {
        setProjects(projectRows);

        // Real "Sprint Demo" meetings this client is invited to, grouped by project.
        const demoMap = {};
        meetings
          .filter((m) => m.type === "Sprint Demo" && m.clientProjectId != null)
          .forEach((m) => {
            const key = String(m.clientProjectId);
            if (!demoMap[key]) demoMap[key] = [];
            demoMap[key].push(m);
          });
        const byProject = {};
        Object.entries(demoMap).forEach(([key, list]) => {
          const scheduled = list
            .filter((m) => m.status === "SCHEDULED")
            .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));
          const completed = list
            .filter((m) => m.status === "COMPLETED")
            .sort((a, b) => new Date(b.scheduledAt) - new Date(a.scheduledAt));
          byProject[key] = { next: scheduled[0] || null, lastCompleted: completed[0] || null };
        });
        setDemosByProject(byProject);

        // Real burndown/milestone completion — only meaningful once a batch is allocated;
        // getClientProjectProgress already returns a safe zeroed shape otherwise, but there's no
        // point in the extra round-trip for a project nobody's picked up yet.
        const allocated = projectRows.filter((p) => p.allocated);
        const progressEntries = await Promise.all(
          allocated.map((p) => getClientProjectProgress(p.id).catch(() => null))
        );
        const progressMap = {};
        allocated.forEach((p, i) => {
          if (progressEntries[i]) progressMap[p.id] = progressEntries[i];
        });
        setProgressByProject(progressMap);
      })
      .catch(() => setProjects([]))
      .finally(() => setLoading(false));
  }, []);

  const joinDemo = (meeting) => {
    window.open(meeting.meetLink, "_blank");
    notify(`Joining sprint demo call: ${meeting.title}`, { type: "info" });
  };

  const openFeedbackModal = (projectId) => {
    const existing = feedbacks[projectId] || { rating: "5", comments: "" };
    setTargetProjectId(projectId);
    setRating(existing.rating);
    setComments(existing.comments);
    setFeedbackOpen(true);
  };

  const saveFeedback = (e) => {
    e.preventDefault();
    const updated = {
      ...feedbacks,
      [targetProjectId]: { rating, comments }
    };
    setFeedbacks(updated);
    localStorage.setItem("msh_client_projects_feedback", JSON.stringify(updated));
    notify("Thank you! Your sprint feedback has been registered.", { type: "success" });
    setFeedbackOpen(false);
  };

  return (
    <div>
      <PageHeader title="Sprint Demo Reviews" subtitle="Track real sprint progress and join the Sprint Demo calls your Business Analyst schedules" breadcrumbs={[{ label: "Dashboard", to: "/client/dashboard" }, { label: "Sprint Demos" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading demos…" /></div>
      ) : projects.length === 0 ? (
        <Card>
          <EmptyState icon={Calendar} title="No projects yet" description="Once you submit a project requirement, its sprint progress and demo schedule will show up here." />
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          {projects.map((p) => {
            const fb = feedbacks[p.id];
            const progress = progressByProject[p.id];
            const demos = demosByProject[p.id] || { next: null, lastCompleted: null };
            const burndown = (progress?.burndown || []).map((s) => ({
              sprint: `Sprint ${s.sprintNumber}`,
              planned: s.plannedPoints,
              completed: s.completedPoints,
            }));
            return (
              <Card key={p.id}>
                <CardHeader
                  title={p.title}
                  subtitle={p.assignedDeveloperName ? `Developer: ${p.assignedDeveloperName}` : "Developer not yet assigned"}
                  action={<Badge tone="gold" dot>{p.status}</Badge>}
                />

                {!p.allocated ? (
                  <p className="text-sm text-ink-400 py-4 text-center bg-cream-50/50 rounded-lg border border-border">
                    Sprint tracking starts once staff allocate a batch to this project.
                  </p>
                ) : (
                  <>
                    <ProgressBar value={progress?.milestoneCompletion ?? 0} tone="primary" label="Sprint completion (milestones)" />
                    {burndown.length > 0 ? (
                      <div className="mt-4">
                        <p className="text-xs font-semibold text-ink-700 flex items-center gap-1 mb-1.5">
                          <TrendingUp size={13} /> Sprint burn-down — planned vs. completed story points
                        </p>
                        <ResponsiveContainer width="100%" height={160}>
                          <BarChart data={burndown}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#E4E1D8" vertical={false} />
                            <XAxis dataKey="sprint" tick={{ fontSize: 11, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                            <YAxis tick={{ fontSize: 11, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                            <Tooltip contentStyle={{ borderRadius: 10, border: "1px solid #E4E1D8" }} />
                            <Bar dataKey="planned" fill="#E4E1D8" radius={[6, 6, 0, 0]} name="Planned points" />
                            <Bar dataKey="completed" fill="#0D2845" radius={[6, 6, 0, 0]} name="Completed points" />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <p className="text-xs text-ink-400 mt-3 text-center">No sprints logged yet.</p>
                    )}
                  </>
                )}

                {/* Submitted feedback highlight */}
                {fb && (
                  <div className="mt-4 p-3 bg-neutral-50 rounded-lg border border-border text-left">
                    <p className="text-xs font-semibold text-ink-900 flex items-center gap-1">
                      <Star size={14} className="fill-amber-400 text-amber-400" />
                      Client Rating: {fb.rating}/5
                    </p>
                    <p className="text-xs text-ink-600 mt-1 italic">"{fb.comments || "No comments written."}"</p>
                  </div>
                )}

                {demos.lastCompleted?.momNotes && (
                  <div className="mt-4 p-3 bg-white rounded-lg border border-border text-xs">
                    <p className="font-semibold text-ink-700">Notes from the last demo ({demos.lastCompleted.date}):</p>
                    <p className="text-ink-600 mt-1 italic">{demos.lastCompleted.momNotes}</p>
                  </div>
                )}

                <div className="flex items-center justify-between mt-4 pt-4 border-t border-border flex-wrap gap-2">
                  {demos.next ? (
                    <p className="text-sm text-ink-600 flex items-center gap-1.5">
                      <Calendar size={14} /> Next demo: {demos.next.date} at {demos.next.timeDisplay || demos.next.time}
                      <Badge tone={STATUS_TONE[demos.next.status] || "neutral"}>{demos.next.status}</Badge>
                    </p>
                  ) : (
                    <p className="text-sm text-ink-400 flex items-center gap-1.5">
                      <Calendar size={14} /> No demo scheduled yet — your Business Analyst will invite you here once one's booked.
                    </p>
                  )}
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" icon={MessageSquare} onClick={() => openFeedbackModal(p.id)}>
                      {fb ? "Edit Feedback" : "Leave Feedback"}
                    </Button>
                    <Button size="sm" icon={Video} disabled={!demos.next} onClick={() => demos.next && joinDemo(demos.next)}>
                      Join Demo Call
                    </Button>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Feedback Modal */}
      <Modal
        open={feedbackOpen}
        onClose={() => setFeedbackOpen(false)}
        title="Submit Sprint Feedback"
        footer={<>
          <Button variant="secondary" onClick={() => setFeedbackOpen(false)}>Cancel</Button>
          <Button onClick={saveFeedback}>Submit Feedback</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={saveFeedback}>
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-ink-900">Sprint Rating</span>
            <select
              value={rating}
              onChange={(e) => setRating(e.target.value)}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
            >
              <option value="5">5 Stars (Excellent)</option>
              <option value="4">4 Stars (Good)</option>
              <option value="3">3 Stars (Average)</option>
              <option value="2">2 Stars (Fair)</option>
              <option value="1">1 Star (Poor)</option>
            </select>
          </div>
          <Textarea label="Feedback & Review Comments" rows={4} placeholder="Type your review comments here (e.g. key highlights, features you loved, or points of concern)..." value={comments} onChange={(e) => setComments(e.target.value)} />
        </form>
      </Modal>
    </div>
  );
}
