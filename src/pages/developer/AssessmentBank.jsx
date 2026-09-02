import { useEffect, useState } from "react";
import { Plus, ClipboardCheck, Trash2, ListChecks, Code2, UploadCloud, AlertTriangle } from "lucide-react";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import EmptyState from "../../components/ui/EmptyState";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import Breadcrumbs from "../../components/widgets/Breadcrumbs";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { parseQuestionFile } from "../../utils/questionFileParser";
import {
  getAssessmentBanks,
  createAssessmentBank,
  deleteAssessmentBank,
  addQuestionToBank,
  removeQuestionFromBank,
  bulkAddQuestionsToBank,
} from "../../services/developerService";

const emptyMcqForm = { text: "", option1: "", option2: "", option3: "", option4: "", correctIndex: "0" };
const emptyCodeForm = {
  text: "",
  functionName: "",
  starterCode: "",
  tc1Name: "", tc1Args: "", tc1Expected: "",
  tc2Name: "", tc2Args: "", tc2Expected: "",
};

export default function AssessmentBank() {
  const { notify } = useToast();
  const [banks, setBanks] = useState([]);
  const [loading, setLoading] = useState(true);

  // Create bank
  const [open, setOpen] = useState(false);
  const [values, setValues] = useState({ title: "", type: "" });
  const [errors, setErrors] = useState({});

  // Manage questions modal
  const [activeBank, setActiveBank] = useState(null);
  const [mcqForm, setMcqForm] = useState(emptyMcqForm);
  const [codeForm, setCodeForm] = useState(emptyCodeForm);
  const [qErrors, setQErrors] = useState({});

  // Bulk upload modal (MCQ banks only)
  const [bulkFile, setBulkFile] = useState(null);
  const [bulkParsing, setBulkParsing] = useState(false);
  const [bulkPreview, setBulkPreview] = useState(null); // { questions, problems }
  const [bulkImporting, setBulkImporting] = useState(false);

  const load = () => getAssessmentBanks().then((b) => { setBanks(b); setLoading(false); });
  useEffect(() => { load(); }, []);

  const onChange = (e) => setValues((v) => ({ ...v, [e.target.name]: e.target.value }));

  const onCreate = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(values, { title: [required], type: [required] });
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    await createAssessmentBank(values);
    notify("Question bank created. Add real questions to it before publishing.", { type: "success" });
    setOpen(false);
    setValues({ title: "", type: "" });
    load();
  };

  const handleDeleteBank = async (bank) => {
    await deleteAssessmentBank(bank.id);
    notify(`"${bank.title}" deleted.`, { type: "success" });
    load();
  };

  const openManage = (bank) => {
    setActiveBank(bank);
    setMcqForm(emptyMcqForm);
    setCodeForm(emptyCodeForm);
    setQErrors({});
    setBulkFile(null);
    setBulkPreview(null);
  };

  const closeManage = () => {
    setActiveBank(null);
    load();
  };

  const refreshActiveBank = async () => {
    const fresh = await getAssessmentBanks();
    setBanks(fresh);
    setActiveBank(fresh.find((b) => b.id === activeBank.id) || null);
  };

  const addMcqQuestion = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(mcqForm, {
      text: [required], option1: [required], option2: [required], option3: [required], option4: [required],
    });
    setQErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    const options = [mcqForm.option1, mcqForm.option2, mcqForm.option3, mcqForm.option4];
    const correctAnswer = options[Number(mcqForm.correctIndex)];

    await addQuestionToBank(activeBank.id, { type: "MCQ", text: mcqForm.text, options, correctAnswer });
    notify("Question added.", { type: "success" });
    setMcqForm(emptyMcqForm);
    setQErrors({});
    refreshActiveBank();
  };

  const parseJsonField = (raw, fallback) => {
    if (!raw || !raw.trim()) return fallback;
    try { return JSON.parse(raw); } catch (e) { return undefined; }
  };

  const addCodeQuestion = async (e) => {
    e.preventDefault();
    const nextErrors = validateForm(codeForm, {
      text: [required], functionName: [required], starterCode: [required],
      tc1Name: [required], tc1Args: [required], tc1Expected: [required],
    });
    setQErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    const testCases = [];
    const tc1Args = parseJsonField(codeForm.tc1Args, undefined);
    const tc1Expected = parseJsonField(codeForm.tc1Expected, undefined);
    if (tc1Args === undefined || tc1Expected === undefined) {
      setQErrors({ tc1Args: "Must be valid JSON, e.g. [5, 3] or [\"Save\", true]" });
      return;
    }
    testCases.push({ id: 1, name: codeForm.tc1Name, args: tc1Args, expected: tc1Expected });

    if (codeForm.tc2Name.trim()) {
      const tc2Args = parseJsonField(codeForm.tc2Args, undefined);
      const tc2Expected = parseJsonField(codeForm.tc2Expected, undefined);
      if (tc2Args === undefined || tc2Expected === undefined) {
        setQErrors({ tc2Args: "Must be valid JSON, e.g. [2, 0] or [\"Submit\", false]" });
        return;
      }
      testCases.push({ id: 2, name: codeForm.tc2Name, args: tc2Args, expected: tc2Expected });
    }

    await addQuestionToBank(activeBank.id, {
      type: "Code",
      text: codeForm.text,
      functionName: codeForm.functionName,
      starterCode: codeForm.starterCode,
      testCases,
    });
    notify("Coding question added, with auto-graded test cases.", { type: "success" });
    setCodeForm(emptyCodeForm);
    setQErrors({});
    refreshActiveBank();
  };

  const handleRemoveQuestion = async (questionId) => {
    await removeQuestionFromBank(activeBank.id, questionId);
    notify("Question removed.", { type: "success" });
    refreshActiveBank();
  };

  const handleBulkFileSelected = async (files) => {
    const file = files[0];
    setBulkFile(file);
    setBulkPreview(null);
    if (!file) return;
    setBulkParsing(true);
    try {
      const { questions, problems } = await parseQuestionFile(file);
      setBulkPreview({ questions, problems });
    } catch (err) {
      notify(err.message || "Couldn't read that file.", { type: "error" });
    } finally {
      setBulkParsing(false);
    }
  };

  const confirmBulkImport = async () => {
    if (!bulkPreview?.questions?.length) return;
    setBulkImporting(true);
    try {
      const toImport = bulkPreview.questions.map(({ question, options, correctAnswer }) => ({ text: question, options, correctAnswer }));
      await bulkAddQuestionsToBank(activeBank.id, toImport);
      notify(`Imported ${toImport.length} question${toImport.length === 1 ? "" : "s"} from "${bulkFile.name}".`, { type: "success", title: "Bulk import complete" });
      setBulkFile(null);
      setBulkPreview(null);
      refreshActiveBank();
    } catch (err) {
      notify(err.message || "Import failed.", { type: "error" });
    } finally {
      setBulkImporting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumbs items={[{ label: "Assessment Bank" }]} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="text-left">
          <h2 className="font-display text-2xl font-bold text-ink-900">Assessment engine design</h2>
          <p className="text-sm text-ink-500 mt-1">
            Author real question banks here — actual MCQ options and correct answers, or actual coding
            challenges with auto-graded test cases. Publish a bank from Developer → Assessments to make it
            live for students.
          </p>
        </div>
        <Button icon={Plus} onClick={() => { setErrors({}); setOpen(true); }}>New question bank</Button>
      </div>

      <Card>
        {loading ? null : banks.length === 0 ? (
          <EmptyState
            icon={ListChecks}
            title="No question banks yet"
            description="Create a bank, add real questions to it, then publish it as an assessment for students."
            actionLabel="New question bank"
            onAction={() => { setErrors({}); setOpen(true); }}
          />
        ) : (
          <Table
            loading={loading}
            data={banks}
            columns={[
              { key: "title", header: "Title", className: "text-left" },
              { key: "type", header: "Type", className: "text-left", render: (r) => <Badge tone="primary">{r.type === "MCQ" ? "Multiple Choice" : "Live Code Runner"}</Badge> },
              { key: "questions", header: "Questions", className: "text-left", render: (r) => r.questions.length },
              { key: "action", header: "", className: "text-right", render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={r.type === "MCQ" ? ListChecks : Code2} onClick={() => openManage(r)}>Manage Questions</Button>
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDeleteBank(r)}>Delete</Button>
                </div>
              ) },
            ]}
          />
        )}
      </Card>

      {/* Create bank modal */}
      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Create question bank"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button icon={ClipboardCheck} onClick={onCreate}>Create</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onCreate}>
          <Input label="Title" name="title" value={values.title} onChange={onChange} error={errors.title} required placeholder="e.g. JavaScript Fundamentals" />
          <Select label="Type" name="type" placeholder="Select type" value={values.type} onChange={onChange} error={errors.type} required
            options={[{ value: "MCQ", label: "Multiple Choice" }, { value: "Code", label: "Live Code Runner" }]} />
        </form>
      </Modal>

      {/* Manage questions modal */}
      <Modal
        open={!!activeBank}
        onClose={closeManage}
        title={activeBank ? `Manage questions — ${activeBank.title}` : ""}
        description={activeBank ? `${activeBank.questions.length} question${activeBank.questions.length === 1 ? "" : "s"} in this bank` : ""}
        size="xl"
      >
        {activeBank && (
          <div className="flex flex-col gap-6 text-left">
            {activeBank.questions.length > 0 && (
              <div className="flex flex-col gap-2">
                {activeBank.questions.map((q, i) => (
                  <div key={q.id} className="flex items-start justify-between gap-3 rounded-lg border border-border p-3 bg-cream-50">
                    <div className="text-sm">
                      <p className="font-medium text-ink-900">Q{i + 1}. {q.text}</p>
                      {q.type === "MCQ" ? (
                        <p className="text-xs text-ink-500 mt-1">Options: {q.options.join(" · ")} — Correct: <span className="font-semibold text-success-600">{q.correctAnswer}</span></p>
                      ) : (
                        <p className="text-xs text-ink-500 mt-1">Function: <code>{q.functionName}</code> — {q.testCases.length} test case{q.testCases.length === 1 ? "" : "s"}</p>
                      )}
                    </div>
                    <Button size="xs" variant="danger" icon={Trash2} onClick={() => handleRemoveQuestion(q.id)} />
                  </div>
                ))}
              </div>
            )}

            <div className="border-t border-border pt-4">
              <p className="text-sm font-semibold text-ink-900 mb-3">Add a question</p>

              {activeBank.type === "MCQ" ? (
                <>
                  <div className="rounded-lg border border-dashed border-primary-300 bg-primary-50/40 p-3 mb-4">
                    <p className="text-xs font-semibold text-ink-700 mb-2 flex items-center gap-1.5"><UploadCloud size={14} className="text-primary-600" /> Bulk upload from a file</p>
                    <p className="text-xs text-ink-500 mb-2">
                      CSV columns: <code>question, optionA, optionB, optionC, optionD, correct</code> (correct = A/B/C/D).
                      Word (.docx) or .txt: one question per block, blank line between blocks, using <code>Q:</code>, <code>A)</code>–<code>D)</code>, and <code>Correct: A</code> lines.
                    </p>
                    <FileUpload
                      hint=".csv, .txt, or .docx"
                      accept=".csv,.txt,.docx"
                      initialFiles={bulkFile ? [bulkFile] : []}
                      onChange={handleBulkFileSelected}
                    />
                    {bulkParsing && <p className="text-xs text-ink-500 mt-2">Reading file…</p>}
                    {bulkPreview && (
                      <div className="mt-3 flex flex-col gap-2">
                        {bulkPreview.questions.length > 0 && (
                          <p className="text-xs font-medium text-success-600">{bulkPreview.questions.length} question{bulkPreview.questions.length === 1 ? "" : "s"} ready to import.</p>
                        )}
                        {bulkPreview.problems.length > 0 && (
                          <div className="rounded-lg bg-warning-50 border border-warning-500/30 p-2.5 flex flex-col gap-1">
                            <p className="text-xs font-semibold text-warning-600 flex items-center gap-1.5"><AlertTriangle size={13} /> {bulkPreview.problems.length} row{bulkPreview.problems.length === 1 ? "" : "s"} skipped</p>
                            {bulkPreview.problems.slice(0, 5).map((p, i) => <p key={i} className="text-xs text-warning-600">{p}</p>)}
                          </div>
                        )}
                        {bulkPreview.questions.length > 0 && (
                          <Button size="sm" icon={UploadCloud} loading={bulkImporting} className="self-start" onClick={confirmBulkImport}>
                            Import {bulkPreview.questions.length} question{bulkPreview.questions.length === 1 ? "" : "s"}
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                  <p className="text-xs font-semibold text-ink-500 mb-2">…or add one manually</p>
                  <form className="flex flex-col gap-3" onSubmit={addMcqQuestion}>
                  <Textarea label="Question text" value={mcqForm.text} onChange={(e) => setMcqForm((v) => ({ ...v, text: e.target.value }))} error={qErrors.text} required rows={2} />
                  <div className="grid sm:grid-cols-2 gap-3">
                    <Input label="Option A" value={mcqForm.option1} onChange={(e) => setMcqForm((v) => ({ ...v, option1: e.target.value }))} error={qErrors.option1} required />
                    <Input label="Option B" value={mcqForm.option2} onChange={(e) => setMcqForm((v) => ({ ...v, option2: e.target.value }))} error={qErrors.option2} required />
                    <Input label="Option C" value={mcqForm.option3} onChange={(e) => setMcqForm((v) => ({ ...v, option3: e.target.value }))} error={qErrors.option3} required />
                    <Input label="Option D" value={mcqForm.option4} onChange={(e) => setMcqForm((v) => ({ ...v, option4: e.target.value }))} error={qErrors.option4} required />
                  </div>
                  <Select
                    label="Correct answer"
                    value={mcqForm.correctIndex}
                    onChange={(e) => setMcqForm((v) => ({ ...v, correctIndex: e.target.value }))}
                    options={[
                      { value: "0", label: mcqForm.option1 || "Option A" },
                      { value: "1", label: mcqForm.option2 || "Option B" },
                      { value: "2", label: mcqForm.option3 || "Option C" },
                      { value: "3", label: mcqForm.option4 || "Option D" },
                    ]}
                  />
                  <Button type="submit" icon={Plus} className="self-start">Add question</Button>
                </form>
                </>
              ) : (
                <form className="flex flex-col gap-3" onSubmit={addCodeQuestion}>
                  <Textarea label="Problem spec (shown to student)" value={codeForm.text} onChange={(e) => setCodeForm((v) => ({ ...v, text: e.target.value }))} error={qErrors.text} required rows={3} />
                  <Input label="Function name" value={codeForm.functionName} onChange={(e) => setCodeForm((v) => ({ ...v, functionName: e.target.value }))} error={qErrors.functionName} required placeholder="e.g. formatButton" />
                  <Textarea label="Starter code" value={codeForm.starterCode} onChange={(e) => setCodeForm((v) => ({ ...v, starterCode: e.target.value }))} error={qErrors.starterCode} required rows={4}
                    placeholder={"function formatButton(label, disabled) {\n  // Write your code here\n}"} />

                  <div className="rounded-lg border border-border p-3 bg-cream-50 flex flex-col gap-3">
                    <p className="text-xs font-semibold text-ink-700">Test case 1 (required)</p>
                    <Input label="Name" value={codeForm.tc1Name} onChange={(e) => setCodeForm((v) => ({ ...v, tc1Name: e.target.value }))} error={qErrors.tc1Name} required placeholder="e.g. Standard Button test" />
                    <div className="grid sm:grid-cols-2 gap-3">
                      <Input label="Arguments (JSON array)" value={codeForm.tc1Args} onChange={(e) => setCodeForm((v) => ({ ...v, tc1Args: e.target.value }))} error={qErrors.tc1Args} required placeholder='["Submit", false]' />
                      <Input label="Expected result (JSON)" value={codeForm.tc1Expected} onChange={(e) => setCodeForm((v) => ({ ...v, tc1Expected: e.target.value }))} error={qErrors.tc1Expected} required placeholder='"<button>Submit</button>"' />
                    </div>
                  </div>

                  <div className="rounded-lg border border-border p-3 bg-cream-50 flex flex-col gap-3">
                    <p className="text-xs font-semibold text-ink-700">Test case 2 (optional)</p>
                    <Input label="Name" value={codeForm.tc2Name} onChange={(e) => setCodeForm((v) => ({ ...v, tc2Name: e.target.value }))} placeholder="e.g. Disabled Button test" />
                    <div className="grid sm:grid-cols-2 gap-3">
                      <Input label="Arguments (JSON array)" value={codeForm.tc2Args} onChange={(e) => setCodeForm((v) => ({ ...v, tc2Args: e.target.value }))} error={qErrors.tc2Args} placeholder='["Save", true]' />
                      <Input label="Expected result (JSON)" value={codeForm.tc2Expected} onChange={(e) => setCodeForm((v) => ({ ...v, tc2Expected: e.target.value }))} placeholder='"<button disabled>Save</button>"' />
                    </div>
                  </div>

                  <Button type="submit" icon={Plus} className="self-start">Add question</Button>
                </form>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
