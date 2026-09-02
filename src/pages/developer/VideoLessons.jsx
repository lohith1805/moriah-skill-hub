import { useEffect, useState } from "react";
import { Plus, Rocket, Trash2, PlayCircle, ListChecks, EyeOff, Link2, UploadCloud } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getDevVideoLessons,
  createVideoLesson,
  deleteVideoLesson,
  addQuestionToLesson,
  removeQuestionFromLesson,
  publishVideoLesson,
  unpublishVideoLesson,
  extractYouTubeId,
} from "../../services/developerService";

const emptyLessonForm = { title: "", module: "", description: "", videoUrl: "", duration: "" };
const emptyQForm = { question: "", option1: "", option2: "", option3: "", option4: "", correctIndex: "0" };

export default function DeveloperVideoLessons() {
  const { notify } = useToast();
  const [lessons, setLessons] = useState([]);
  const [loading, setLoading] = useState(true);

  // Create lesson
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState(emptyLessonForm);
  const [errors, setErrors] = useState({});
  const [sourceType, setSourceType] = useState("youtube"); // "youtube" | "upload"
  const [videoFile, setVideoFile] = useState(null);

  // Manage quiz modal
  const [activeLesson, setActiveLesson] = useState(null);
  const [qForm, setQForm] = useState(emptyQForm);
  const [qErrors, setQErrors] = useState({});

  const load = () => {
    setLoading(true);
    getDevVideoLessons().then((data) => { setLessons(data); setLoading(false); });
  };
  useEffect(() => { load(); }, []);

  const openCreate = () => {
    setErrors({});
    setValues(emptyLessonForm);
    setSourceType("youtube");
    setVideoFile(null);
    setOpen(true);
  };

  const onChange = (e) => setValues((v) => ({ ...v, [e.target.name]: e.target.value }));

  const onCreate = async (e) => {
    e.preventDefault();

    const rules = { title: [required], module: [required], duration: [required] };
    if (sourceType === "youtube") rules.videoUrl = [required];
    const nextErrors = validateForm(values, rules);
    if (sourceType === "upload" && !videoFile) nextErrors.videoFile = "Choose a video file to upload.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    setSaving(true);
    try {
      await createVideoLesson({
        title: values.title,
        module: values.module,
        description: values.description,
        videoType: sourceType,
        videoId: sourceType === "youtube" ? extractYouTubeId(values.videoUrl) : "",
        // Session-only preview: no backend to actually store the file, so
        // this blob URL only plays back in this browser tab until reload.
        videoUrl: sourceType === "upload" ? URL.createObjectURL(videoFile) : "",
        duration: values.duration,
      });
      notify("Video lesson created as a Draft. Add quiz questions, then publish it.", { type: "success" });
      setOpen(false);
      load();
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (lesson) => {
    await deleteVideoLesson(lesson.id);
    notify(`"${lesson.title}" deleted.`, { type: "success" });
    load();
  };

  const openManage = (lesson) => {
    setActiveLesson(lesson);
    setQForm(emptyQForm);
    setQErrors({});
  };

  const closeManage = () => {
    setActiveLesson(null);
    load();
  };

  const refreshActiveLesson = async () => {
    const fresh = await getDevVideoLessons();
    setLessons(fresh);
    setActiveLesson(fresh.find((l) => l.id === activeLesson.id) || null);
  };

  const addQuestion = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(qForm, {
      question: [required], option1: [required], option2: [required], option3: [required], option4: [required],
    });
    setQErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    const options = [qForm.option1, qForm.option2, qForm.option3, qForm.option4];
    const correctAnswer = options[Number(qForm.correctIndex)];

    await addQuestionToLesson(activeLesson.id, { question: qForm.question, options, correctAnswer });
    notify("Question added.", { type: "success" });
    setQForm(emptyQForm);
    setQErrors({});
    refreshActiveLesson();
  };

  const handleRemoveQuestion = async (questionId) => {
    await removeQuestionFromLesson(activeLesson.id, questionId);
    notify("Question removed.", { type: "success" });
    refreshActiveLesson();
  };

  const handlePublish = async (lesson) => {
    try {
      await publishVideoLesson(lesson.id);
      notify(`"${lesson.title}" published — visible on every student's Video Lessons screen.`, { type: "success", title: "Published" });
      load();
    } catch (err) {
      notify(err.message || "Couldn't publish this lesson.", { type: "error" });
    }
  };

  const handleUnpublish = async (lesson) => {
    await unpublishVideoLesson(lesson.id);
    notify(`"${lesson.title}" unpublished — students can no longer see it.`, { type: "success" });
    load();
  };

  return (
    <div>
      <PageHeader
        title="Video Lessons"
        subtitle="Upload instructional videos and attach a quiz — publish to make them live on students' dashboards"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Video Lessons" }]}
        action={<Button icon={Plus} onClick={openCreate}>New Video Lesson</Button>}
      />

      <Card>
        {!loading && lessons.length === 0 ? (
          <EmptyState
            icon={PlayCircle}
            title="No video lessons yet"
            description="Create a lesson with a YouTube (unlisted)/Vimeo link, add quiz questions to it, then publish it so students can watch and take the quiz."
            actionLabel="New Video Lesson"
            onAction={openCreate}
          />
        ) : (
          <Table
            loading={loading}
            data={lessons}
            columns={[
              { key: "title", header: "Lesson", className: "text-left" },
              { key: "module", header: "Module", className: "text-left", render: (r) => <Badge tone="neutral">{r.module}</Badge> },
              { key: "duration", header: "Duration", className: "text-left" },
              { key: "quiz", header: "Questions", className: "text-left", render: (r) => r.quiz.length },
              { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={r.status === "Published" ? "success" : "neutral"}>{r.status}</Badge> },
              { key: "action", header: "", className: "text-right", render: (r) => (
                <div className="flex gap-2 justify-end flex-wrap">
                  <Button size="sm" variant="secondary" icon={ListChecks} onClick={() => openManage(r)}>Manage Quiz</Button>
                  {r.status === "Published" ? (
                    <Button size="sm" variant="secondary" icon={EyeOff} onClick={() => handleUnpublish(r)}>Unpublish</Button>
                  ) : (
                    <Button size="sm" icon={Rocket} onClick={() => handlePublish(r)}>Publish</Button>
                  )}
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r)}>Delete</Button>
                </div>
              ) },
            ]}
          />
        )}
      </Card>

      {/* Create lesson modal */}
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Create video lesson"
        description="Paste a YouTube (unlisted), Vimeo, or S3 link — a bare video ID also works."
        footer={<>
          <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
          <Button icon={Plus} loading={saving} onClick={onCreate}>Create</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onCreate}>
          <Input label="Title" name="title" value={values.title} onChange={onChange} error={errors.title} required placeholder="e.g. CSS Layout: Flexbox & Grid" />
          <Input label="Module" name="module" value={values.module} onChange={onChange} error={errors.module} required placeholder="e.g. Web Fundamentals" />
          <Textarea label="Description" name="description" value={values.description} onChange={onChange} rows={2} placeholder="What this lesson covers" />

          <div className="flex flex-col gap-2">
            <div className="flex gap-2 rounded-lg bg-cream-100 p-1 w-fit">
              <button type="button" onClick={() => setSourceType("youtube")}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${sourceType === "youtube" ? "bg-white shadow-sm text-primary-700" : "text-ink-500"}`}>
                <Link2 size={13} /> Paste link
              </button>
              <button type="button" onClick={() => setSourceType("upload")}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${sourceType === "upload" ? "bg-white shadow-sm text-primary-700" : "text-ink-500"}`}>
                <UploadCloud size={13} /> Upload file
              </button>
            </div>

            {sourceType === "youtube" ? (
              <Input label="Video URL" name="videoUrl" value={values.videoUrl} onChange={onChange} error={errors.videoUrl} required placeholder="https://www.youtube.com/watch?v=..." />
            ) : (
              <FileUpload
                label="Video file"
                accept=".mp4,.mov,.webm,video/*"
                hint="MP4, MOV, or WebM — previews in this browser only (no backend storage yet), so paste a hosted link instead if you need it to persist."
                error={errors.videoFile}
                initialFiles={videoFile ? [videoFile] : []}
                onChange={(files) => setVideoFile(files[0] || null)}
              />
            )}
          </div>

          <Input label="Duration" name="duration" value={values.duration} onChange={onChange} error={errors.duration} required placeholder="e.g. 45:00" />
        </form>
      </Modal>

      {/* Manage quiz modal */}
      <Modal
        open={!!activeLesson}
        onClose={closeManage}
        title={activeLesson ? `Manage quiz — ${activeLesson.title}` : ""}
        description={activeLesson ? `${activeLesson.quiz.length} question${activeLesson.quiz.length === 1 ? "" : "s"} · 60% required to pass` : ""}
        size="lg"
      >
        {activeLesson && (
          <div className="flex flex-col gap-6 text-left">
            {activeLesson.quiz.length > 0 && (
              <div className="flex flex-col gap-2">
                {activeLesson.quiz.map((q, i) => (
                  <div key={q.id} className="flex items-start justify-between gap-3 rounded-lg border border-border p-3 bg-cream-50">
                    <div className="text-sm">
                      <p className="font-medium text-ink-900">Q{i + 1}. {q.question}</p>
                      <p className="text-xs text-ink-500 mt-1">Options: {q.options.join(" · ")} — Correct: <span className="font-semibold text-success-600">{q.correctAnswer}</span></p>
                    </div>
                    <Button size="xs" variant="danger" icon={Trash2} onClick={() => handleRemoveQuestion(q.id)} />
                  </div>
                ))}
              </div>
            )}

            <div className="border-t border-border pt-4">
              <p className="text-sm font-semibold text-ink-900 mb-3">Add a question</p>
              <form className="flex flex-col gap-3" onSubmit={addQuestion}>
                <Textarea label="Question text" value={qForm.question} onChange={(e) => setQForm((v) => ({ ...v, question: e.target.value }))} error={qErrors.question} required rows={2} />
                <div className="grid sm:grid-cols-2 gap-3">
                  <Input label="Option A" value={qForm.option1} onChange={(e) => setQForm((v) => ({ ...v, option1: e.target.value }))} error={qErrors.option1} required />
                  <Input label="Option B" value={qForm.option2} onChange={(e) => setQForm((v) => ({ ...v, option2: e.target.value }))} error={qErrors.option2} required />
                  <Input label="Option C" value={qForm.option3} onChange={(e) => setQForm((v) => ({ ...v, option3: e.target.value }))} error={qErrors.option3} required />
                  <Input label="Option D" value={qForm.option4} onChange={(e) => setQForm((v) => ({ ...v, option4: e.target.value }))} error={qErrors.option4} required />
                </div>
                <Select
                  label="Correct answer"
                  value={qForm.correctIndex}
                  onChange={(e) => setQForm((v) => ({ ...v, correctIndex: e.target.value }))}
                  options={[
                    { value: "0", label: qForm.option1 || "Option A" },
                    { value: "1", label: qForm.option2 || "Option B" },
                    { value: "2", label: qForm.option3 || "Option C" },
                    { value: "3", label: qForm.option4 || "Option D" },
                  ]}
                />
                <Button type="submit" icon={Plus} className="self-start">Add question</Button>
              </form>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}