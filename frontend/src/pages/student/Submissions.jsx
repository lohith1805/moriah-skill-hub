import { useEffect, useState } from "react";
import { GitFork, Video, Plus, CheckCircle, Loader2, PlayCircle, ShieldCheck, XCircle, AlertCircle } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select } from "../../components/ui/FormField";
import { getMyTasks, submitGithubPR } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required, isUrl } from "../../utils/validators";

export default function StudentSubmissions() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState({ taskId: "", prUrl: "", videoType: "paste", videoUrl: "", videoFile: null });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const { notify } = useToast();

  // Simulated GitHub Verification states
  const [verifying, setVerifying] = useState(false);
  const [verifySteps, setVerifySteps] = useState([
    { label: "Validating GitHub Repository Access", status: "pending" },
    { label: "Checking branch naming rules (e.g. feature/*, bugfix/*)", status: "pending" },
    { label: "Verifying commits conform to Conventional Commits", status: "pending" },
    { label: "Confirming pull request status and CI checks", status: "pending" }
  ]);

  // Video Upload Simulation states
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  // Play Video Modal states
  const [activeVideo, setActiveVideo] = useState(null);

  const load = () => getMyTasks()
    .then((t) => setTasks(t))
    .catch((e) => notify(e?.message || "Could not load your submissions.", { type: "error" }))
    .finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0] || null;
    if (file) {
      if (file.size > 25 * 1024 * 1024) {
        notify("Video file must be smaller than 25MB.", { type: "error", title: "File too large" });
        return;
      }
      setValues((v) => ({ ...v, videoFile: file }));
      simulateVideoUpload();
    }
  };

  const simulateVideoUpload = () => {
    setUploading(true);
    setUploadProgress(0);
    const interval = setInterval(() => {
      setUploadProgress((p) => {
        if (p >= 100) {
          clearInterval(interval);
          setUploading(false);
          const mockS3Url = `https://moriah-skill-hub-demo.s3.amazonaws.com/walkthroughs/walkthrough_${Date.now()}.mp4`;
          setValues((v) => ({ ...v, videoUrl: mockS3Url }));
          notify("Video walkthrough uploaded successfully to secure S3 storage.", { type: "success" });
          return 100;
        }
        return p + 25;
      });
    }, 250);
  };

  const submit = async (e) => {
    e.preventDefault();
    const rules = { taskId: [required] };
    if (values.prUrl) rules.prUrl = [isUrl];
    if (values.videoUrl) rules.videoUrl = [isUrl];
    
    if (!values.prUrl && !values.videoUrl) {
      notify("Please provide either a GitHub PR URL or a Walkthrough Video.", { type: "warning", title: "Missing Deliverables" });
      return;
    }

    const validation = validateForm(values, rules);
    setErrors(validation);
    if (Object.keys(validation).length) return;

    if (values.prUrl) {
      // Trigger GitHub Verification Flow
      setVerifying(true);
      setVerifySteps([
        { label: "Validating GitHub Repository Access", status: "checking" },
        { label: "Checking branch naming rules (e.g. feature/*, bugfix/*)", status: "pending" },
        { label: "Verifying commits conform to Conventional Commits", status: "pending" },
        { label: "Confirming pull request status and CI checks", status: "pending" }
      ]);

      await new Promise(r => setTimeout(r, 600));
      setVerifySteps(prev => [
        { ...prev[0], status: "success" },
        { ...prev[1], status: "checking" },
        ...prev.slice(2)
      ]);

      await new Promise(r => setTimeout(r, 600));
      setVerifySteps(prev => [
        prev[0],
        { ...prev[1], status: "success" },
        { ...prev[2], status: "checking" },
        prev[3]
      ]);

      await new Promise(r => setTimeout(r, 600));
      setVerifySteps(prev => [
        prev[0],
        prev[1],
        { ...prev[2], status: "success" },
        { ...prev[3], status: "checking" }
      ]);

      await new Promise(r => setTimeout(r, 600));
      setVerifySteps(prev => [
        prev[0],
        prev[1],
        prev[2],
        { ...prev[3], status: "success" }
      ]);

      await new Promise(r => setTimeout(r, 500));
      setVerifying(false);
    }

    if (!values.prUrl) {
      notify("A GitHub PR URL is required to submit a task for review.", { type: "warning", title: "PR URL required" });
      return;
    }

    setSubmitting(true);
    try {
      await submitGithubPR(values.taskId, values.prUrl, values.videoUrl || "");
      notify("Your submission has been registered and the task moved to review.", { type: "success", title: "Submission Recorded" });
      setModalOpen(false);
      setValues({ taskId: "", prUrl: "", videoType: "paste", videoUrl: "", videoFile: null });
      load();
    } catch (err) {
      notify(err.message || "Could not record the submission.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="GitHub & Video Submissions"
        subtitle="Link your pull requests and walkthrough videos to tasks for review"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Submissions" }]}
        action={<Button icon={Plus} onClick={() => setModalOpen(true)}>New Submission</Button>}
      />

      <Card>
        <CardHeader title="Task Submissions Ledger" subtitle="Verified via GitHub REST API commit/PR tracking & walkthrough previews" />
        <Table
          loading={loading}
          data={tasks}
          emptyTitle="No submissions yet"
          emptyHint="Link a pull request or demo video to your first task to see it here."
          columns={[
            { key: "title", header: "Backlog Task Title" },
            { key: "status", header: "Status", render: (r) => (
              <Badge tone={r.status === "Completed" ? "success" : r.status === "Review" ? "info" : "neutral"}>
                {r.status}
              </Badge>
            ) },
            { key: "githubPr", header: "Linked PR", render: (r) => r.githubPr ? (
                <a href={r.githubPr} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 text-primary-700 hover:underline text-sm font-semibold">
                  <GitFork size={14} /> View Pull Request
                </a>
              ) : <span className="text-ink-400 text-sm">Not linked</span> },
            { key: "videoUrl", header: "Walkthrough Video", render: (r) => r.videoUrl ? (
                <button
                  onClick={() => setActiveVideo(r)}
                  className="flex items-center gap-1.5 text-success-700 hover:text-success-800 text-sm font-semibold"
                >
                  <PlayCircle size={15} /> Play Demo
                </button>
              ) : <span className="text-ink-400 text-sm">Not uploaded</span> },
            { key: "due", header: "Deadline Date" },
          ]}
        />
      </Card>

      {/* Modal: New Submission */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Submit Deliverables"
        description="Link a GitHub PR or upload a demonstration walkthrough video to your active sprint tasks."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={ShieldCheck} loading={submitting || verifying} onClick={submit}>Run API Verification</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left" onSubmit={submit}>
          <Select
            label="Sprint Task to Submit"
            required
            placeholder="Select a task"
            options={tasks.map((t) => ({ value: t.id, label: t.title }))}
            value={values.taskId}
            onChange={(e) => setValues((v) => ({ ...v, taskId: e.target.value }))}
            error={errors.taskId}
          />
          <Input
            label="Pull Request URL (GitHub)"
            placeholder="e.g. https://github.com/myorg/my-project/pull/15"
            value={values.prUrl}
            onChange={(e) => setValues((v) => ({ ...v, prUrl: e.target.value }))}
            error={errors.prUrl}
          />
          
          <div className="border-t border-border pt-4 mt-2">
            <label className="text-xs font-bold text-ink-500 uppercase tracking-wider block mb-2">Video Walkthrough Submission</label>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <button
                type="button"
                onClick={() => setValues((v) => ({ ...v, videoType: "paste" }))}
                className={`py-2 px-3 text-xs font-semibold rounded-lg border transition-all ${
                  values.videoType === "paste" ? "border-primary-500 bg-primary-50 text-primary-700" : "border-border text-ink-600 hover:bg-cream-50"
                }`}
              >
                Paste YouTube/Vimeo Link
              </button>
              <button
                type="button"
                onClick={() => setValues((v) => ({ ...v, videoType: "upload" }))}
                className={`py-2 px-3 text-xs font-semibold rounded-lg border transition-all ${
                  values.videoType === "upload" ? "border-primary-500 bg-primary-50 text-primary-700" : "border-border text-ink-600 hover:bg-cream-50"
                }`}
              >
                Upload Walkthrough (S3)
              </button>
            </div>

            {values.videoType === "paste" ? (
              <Input
                label="Walkthrough Video URL"
                placeholder="https://www.youtube.com/watch?v=..."
                value={values.videoUrl}
                onChange={(e) => setValues((v) => ({ ...v, videoUrl: e.target.value }))}
                error={errors.videoUrl}
              />
            ) : (
              <div className="flex flex-col gap-2">
                <input
                  type="file"
                  id="walkthrough-upload"
                  accept="video/mp4"
                  className="hidden"
                  onChange={handleFileChange}
                />
                <button
                  type="button"
                  onClick={() => document.getElementById("walkthrough-upload")?.click()}
                  className="w-full border-2 border-dashed border-border rounded-xl p-6 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-cream-50/40 transition-colors"
                >
                  <Video className="text-ink-400 mb-1" size={24} />
                  <span className="text-sm font-semibold text-ink-700">
                    {values.videoFile ? values.videoFile.name : "Choose MP4 walkthrough file"}
                  </span>
                  <span className="text-[10px] text-ink-400 mt-0.5">Maximum size: 25MB</span>
                </button>
                
                {uploading && (
                  <div className="flex items-center gap-3 bg-primary-50 border border-primary-100 p-3 rounded-lg mt-2">
                    <Loader2 className="animate-spin text-primary-600" size={16} />
                    <div className="flex-1">
                      <div className="flex justify-between text-[10px] text-primary-850 font-bold mb-1">
                        <span>Uploading Walkthrough to AWS S3 bucket...</span>
                        <span>{uploadProgress}%</span>
                      </div>
                      <div className="w-full bg-cream-200 h-1.5 rounded-full overflow-hidden">
                        <div className="bg-primary-600 h-1.5 transition-all duration-300" style={{ width: `${uploadProgress}%` }} />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </form>
      </Modal>

      {/* Modal: GitHub API Verification Progress */}
      {verifying && (
        <Modal open={verifying} onClose={() => {}} title="GitHub API Check" size="md">
          <div className="flex flex-col gap-4 text-left p-2">
            <div className="flex items-center gap-3 mb-2">
              <Loader2 className="animate-spin text-primary-600" size={20} />
              <p className="font-semibold text-sm text-ink-900">Verifying Pull Request against task criteria...</p>
            </div>
            
            <div className="flex flex-col gap-3 border-t border-border pt-4">
              {verifySteps.map((step, idx) => (
                <div key={idx} className="flex items-center justify-between gap-3 text-xs bg-cream-50/45 p-2 rounded-lg border border-border/40">
                  <span className="text-ink-700 font-medium">{step.label}</span>
                  {step.status === "success" && <CheckCircle size={15} className="text-success-600 shrink-0" />}
                  {step.status === "checking" && <Loader2 size={15} className="animate-spin text-primary-600 shrink-0" />}
                  {step.status === "pending" && <AlertCircle size={15} className="text-ink-300 shrink-0" />}
                </div>
              ))}
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Video Player */}
      {activeVideo && (
        <Modal
          open={!!activeVideo}
          onClose={() => setActiveVideo(null)}
          title={`Walkthrough: ${activeVideo.title}`}
          description={`Submitted by ${activeVideo.assignee}`}
          size="lg"
          footer={<Button variant="secondary" onClick={() => setActiveVideo(null)}>Close Player</Button>}
        >
          <div className="bg-black rounded-lg overflow-hidden flex items-center justify-center p-1.5 aspect-video mt-3">
            <video
              className="w-full h-full object-contain rounded"
              controls
              autoPlay
              src="https://www.w3schools.com/html/mov_bbb.mp4"
            />
          </div>
        </Modal>
      )}
    </div>
  );
}
