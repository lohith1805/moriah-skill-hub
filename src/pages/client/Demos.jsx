import { useEffect, useState } from "react";
import { Video, Calendar, Star, MessageSquare } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Select, Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getClientProjects } from "../../services/clientService";
import { useToast } from "../../context/ToastContext";

export default function ClientDemos() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Feedback states
  const [feedbacks, setFeedbacks] = useState({});
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [targetProjectId, setTargetProjectId] = useState(null);
  const [rating, setRating] = useState("5");
  const [comments, setComments] = useState("");

  const { notify } = useToast();

  useEffect(() => {
    // Load projects
    getClientProjects().then((p) => {
      setProjects(p);
      setLoading(false);
    });

    // Load feedbacks
    const saved = localStorage.getItem("msh_client_projects_feedback");
    if (saved) {
      setFeedbacks(JSON.parse(saved));
    }
  }, []);

  const joinDemo = (p) => {
    window.open("https://meet.google.com/abc-defg-hij", "_blank");
    notify(`Joining sprint demo call for: ${p.title}`, { type: "info" });
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
      <PageHeader title="Sprint Demo Reviews" subtitle="Join live sprint ceremonies and review delivered milestones" breadcrumbs={[{ label: "Dashboard", to: "/client/dashboard" }, { label: "Sprint Demos" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading demos…" /></div>
      ) : (
        <div className="flex flex-col gap-4">
          {projects.map((p) => {
            const fb = feedbacks[p.id];
            return (
              <Card key={p.id}>
                <CardHeader
                  title={p.title}
                  subtitle={p.batch}
                  action={<Badge tone="gold" dot>{p.milestone}</Badge>}
                />
                <ProgressBar value={p.progress} tone="primary" label="Sprint completion" />
                
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

                <div className="flex items-center justify-between mt-4 pt-4 border-t border-border flex-wrap gap-2">
                  <p className="text-sm text-ink-600 flex items-center gap-1.5"><Calendar size={14} /> Next demo: {p.demoDate}</p>
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" icon={MessageSquare} onClick={() => openFeedbackModal(p.id)}>
                      {fb ? "Edit Feedback" : "Leave Feedback"}
                    </Button>
                    <Button size="sm" icon={Video} onClick={() => joinDemo(p)}>Join Demo Call</Button>
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
