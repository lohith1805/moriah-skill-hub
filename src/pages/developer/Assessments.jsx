import { useEffect, useState } from "react";
import { Rocket, ClipboardList, Trash2, Users } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import { Input, Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getAssessmentBanks,
  getPublishedAssessments,
  publishAssessment,
  deletePublishedAssessment,
  getBatchesForAssignment,
} from "../../services/developerService";

export default function DeveloperAssessments() {
  const { notify } = useToast();
  const [items, setItems] = useState([]);
  const [banks, setBanks] = useState([]);
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({ bankId: "", title: "", durationMinutes: "20", passingScore: "60", batchId: "" });
  const [errors, setErrors] = useState({});

  const load = () => {
    setLoading(true);
    Promise.all([getPublishedAssessments(), getAssessmentBanks(), getBatchesForAssignment()]).then(
      ([published, allBanks, allBatches]) => {
        setItems(published);
        setBanks(allBanks.filter((b) => b.questions.length > 0));
        setBatches(allBatches);
        setLoading(false);
      }
    );
  };
  useEffect(() => { load(); }, []);

  const openModal = () => {
    if (!banks.length) {
      notify("No question banks with real questions yet. Add questions in Assessment Bank first.", { type: "info" });
      return;
    }
    setErrors({});
    setValues({ bankId: "", title: "", durationMinutes: "20", passingScore: "60", batchId: "" });
    setModalOpen(true);
  };

  const onBankChange = (e) => {
    const bankId = e.target.value;
    const bank = banks.find((b) => b.id === bankId);
    setValues((v) => ({ ...v, bankId, title: v.title || bank?.title || "" }));
  };

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { bankId: [required], durationMinutes: [required], passingScore: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSaving(true);
    try {
      await publishAssessment({
        bankId: values.bankId,
        title: values.title,
        durationMinutes: values.durationMinutes,
        passingScore: values.passingScore,
        batchIds: values.batchId ? [values.batchId] : [],
      });
      notify(
        values.batchId
          ? "Assessment published and assigned to that batch's students."
          : "Assessment published — visible to every student.",
        { type: "success", title: "Published" }
      );
      setModalOpen(false);
      load();
    } catch (err) {
      notify(err.message || "Couldn't publish this assessment.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item) => {
    await deletePublishedAssessment(item.id);
    notify(`"${item.title}" unpublished — students can no longer see or take it.`, { type: "success" });
    load();
  };

  const batchName = (id) => batches.find((b) => b.id === id)?.name;

  return (
    <div>
      <PageHeader
        title="Assessment Engine"
        subtitle="Publish a question bank as a live, auto-graded assessment for students"
        breadcrumbs={[{ label: "Dashboard", to: "/developer/dashboard" }, { label: "Assessments" }]}
        action={<Button icon={Rocket} onClick={openModal}>Publish Assessment</Button>}
      />

      <Card>
        {!loading && items.length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title="Nothing published yet"
            description="Build a question bank with real questions in Assessment Bank, then publish it here so it shows up on students' Assessments page."
            actionLabel="Publish Assessment"
            onAction={openModal}
          />
        ) : (
          <Table
            loading={loading}
            data={items}
            columns={[
              { key: "title", header: "Assessment", className: "text-left" },
              { key: "type", header: "Type", className: "text-left", render: (r) => <Badge tone="primary">{r.type === "MCQ" ? "MCQ" : "Live Code Runner"}</Badge> },
              { key: "questions", header: "Questions", className: "text-left", render: (r) => r.questions.length },
              { key: "duration", header: "Duration", className: "text-left" },
              { key: "passingScore", header: "Pass Mark", className: "text-left", render: (r) => `${r.passingScore}%` },
              { key: "audience", header: "Audience", className: "text-left", render: (r) => (
                <span className="inline-flex items-center gap-1.5 text-sm text-ink-600">
                  <Users size={14} />
                  {r.batchIds?.length ? r.batchIds.map(batchName).filter(Boolean).join(", ") || "1 batch" : "All students"}
                </span>
              ) },
              { key: "action", header: "", className: "text-right", render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r)}>Unpublish</Button>
                </div>
              ) },
            ]}
          />
        )}
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Publish an assessment"
        description="Picks real questions from a bank you've already authored in Assessment Bank."
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button icon={Rocket} loading={saving} onClick={submit}>Publish</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Select
            label="Question bank"
            required
            placeholder="Select a question bank"
            options={banks.map((b) => ({ value: b.id, label: `${b.title} (${b.questions.length} questions)` }))}
            value={values.bankId}
            onChange={onBankChange}
            error={errors.bankId}
          />
          <Input label="Assessment title" value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} placeholder="Defaults to the bank's title" />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Duration (minutes)" type="number" min="1" required value={values.durationMinutes} onChange={(e) => setValues((v) => ({ ...v, durationMinutes: e.target.value }))} error={errors.durationMinutes} />
            <Input label="Passing score (%)" type="number" min="0" max="100" required value={values.passingScore} onChange={(e) => setValues((v) => ({ ...v, passingScore: e.target.value }))} error={errors.passingScore} />
          </div>
          <Select
            label="Assign to batch"
            placeholder="All students (no batch restriction)"
            options={batches.map((b) => ({ value: b.id, label: b.name }))}
            value={values.batchId}
            onChange={(e) => setValues((v) => ({ ...v, batchId: e.target.value }))}
            hint="Leave unset to publish it for every student — this app doesn't yet track which batch each student belongs to, so batch targeting is best-effort."
          />
        </form>
      </Modal>
    </div>
  );
}
