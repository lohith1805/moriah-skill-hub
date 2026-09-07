import { useEffect, useState } from "react";
import { Plus, Send, Activity } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { getMyRequirements, submitProjectRequirement, getClientProjectProgress } from "../../services/clientService";

const BUDGETS = ["< ₹5L", "₹5L – ₹15L", "₹15L – ₹40L", "₹40L+", "Not sure yet"].map((b) => ({ value: b, label: b }));

export default function ClientProjects() {
  const { notify } = useToast();
  const [reqs, setReqs] = useState([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ title: "", scope: "", budgetRange: "", additionalNotes: "" });
  const [errors, setErrors] = useState({});

  const [progress, setProgress] = useState(null); // { title, milestoneCompletion, burndown }
  const [progressLoading, setProgressLoading] = useState(false);

  const load = () => {
    setLoading(true);
    getMyRequirements()
      .then(setReqs)
      .catch(() => setReqs([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const submit = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { title: [required], scope: [required] });
    setErrors(v);
    if (Object.keys(v).length) return;
    setSubmitting(true);
    try {
      await submitProjectRequirement(values);
      notify("Project scope submitted — our BA team will review it.", { type: "success", title: "Submitted" });
      setModalOpen(false);
      setValues({ title: "", scope: "", budgetRange: "", additionalNotes: "" });
      load();
    } catch (err) {
      notify(err?.message || "Couldn't submit. Please try again.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const viewProgress = async (row) => {
    setProgressLoading(true);
    setProgress({ title: row.title, milestoneCompletion: 0, burndown: [] });
    try {
      setProgress(await getClientProjectProgress(row.id));
    } catch (err) {
      notify(err?.message || "Couldn't load progress.", { type: "error" });
      setProgress(null);
    } finally {
      setProgressLoading(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="My Project Requirements"
        subtitle="Submit a real-world business challenge for our BA team to scope and staff against a training batch"
        breadcrumbs={[{ label: "Dashboard", to: "/client/dashboard" }, { label: "Project Requirements" }]}
        action={<Button icon={Plus} onClick={() => { setErrors({}); setValues({ title: "", scope: "", budgetRange: "", additionalNotes: "" }); setModalOpen(true); }}>Submit New</Button>}
      />

      <Card className="mb-4">
        <p className="text-sm text-ink-500">
          Your submission goes to our Business Analyst team. A BA reads your brief and authors the formal BRD / SRS / FRS
          from it — you don't prepare those. Once a training batch is allocated, you can track its sprint burndown here.
        </p>
      </Card>

      <Card>
        <h3 className="text-base font-semibold text-ink-900 mb-4 text-left">Your Submissions</h3>
        <Table
          loading={loading}
          data={reqs}
          emptyTitle="Nothing submitted yet"
          columns={[
            { key: "title", header: "Project", className: "text-left font-medium text-ink-900" },
            { key: "scope", header: "Business Scope", className: "text-left max-w-md truncate", render: (r) => r.scope },
            { key: "budgetRange", header: "Budget", className: "text-left text-xs", render: (r) => r.budgetRange || "—" },
            { key: "assignedBa", header: "Your BA", className: "text-left text-xs", render: (r) => r.assignedBaName || <span className="text-ink-400">Assigning…</span> },
            { key: "submittedAt", header: "Submitted", className: "text-left text-xs" },
            { key: "status", header: "Status", className: "text-left", render: (r) => (
              <Badge tone={r.status === "Completed" ? "success" : r.status === "In Progress" ? "primary" : "warning"}>
                {r.allocated ? r.status : "Awaiting batch"}
              </Badge>
            ) },
            { key: "action", header: "", className: "text-right", render: (r) => (
              r.allocated
                ? <Button size="sm" variant="secondary" icon={Activity} onClick={() => viewProgress(r)}>Progress</Button>
                : <span className="text-xs text-ink-400">—</span>
            ) },
          ]}
        />
      </Card>

      {/* Submit modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Submit a project scope"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button icon={Send} loading={submitting} onClick={submit}>Submit</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={submit}>
          <Input label="Project title" required placeholder="e.g. Inventory Management Portal" value={values.title} onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))} error={errors.title} />
          <Textarea label="Business challenge & scope" required rows={5} value={values.scope} onChange={(e) => setValues((v) => ({ ...v, scope: e.target.value }))} error={errors.scope} placeholder="What problem should this solve? Who are the users? Any must-have features or constraints?" />
          <Select label="Budget range" placeholder="Optional" options={BUDGETS} value={values.budgetRange} onChange={(e) => setValues((v) => ({ ...v, budgetRange: e.target.value }))} />
          <Textarea
            label="Anything else our BA should know?"
            rows={3}
            value={values.additionalNotes}
            onChange={(e) => setValues((v) => ({ ...v, additionalNotes: e.target.value }))}
            placeholder="Optional — company background, links, who to loop in, anything beyond the scope above"
          />
        </form>
      </Modal>

      {/* Progress modal */}
      <Modal open={!!progress} onClose={() => setProgress(null)} title={progress?.title || "Progress"} size="lg"
        footer={<Button variant="secondary" onClick={() => setProgress(null)}>Close</Button>}>
        {progressLoading ? (
          <p className="text-sm text-ink-400 py-8 text-center">Loading…</p>
        ) : progress ? (
          <div className="flex flex-col gap-4 text-left">
            <div className="w-full">
              <ProgressBar value={progress.milestoneCompletion} tone="primary" showValue label="Milestone completion" />
            </div>
            {progress.burndown.length === 0 ? (
              <p className="text-sm text-ink-400">No sprints planned for this project's batch yet.</p>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-border">
                <table className="w-full text-xs text-left">
                  <thead className="bg-cream-100/90 border-b border-border">
                    <tr>
                      <th className="px-3 py-2 font-semibold">Sprint</th>
                      <th className="px-3 py-2 font-semibold">Status</th>
                      <th className="px-3 py-2 font-semibold">Planned pts</th>
                      <th className="px-3 py-2 font-semibold">Completed pts</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {progress.burndown.map((s) => (
                      <tr key={s.sprintId}>
                        <td className="px-3 py-2">#{s.sprintNumber}</td>
                        <td className="px-3 py-2">{s.status}</td>
                        <td className="px-3 py-2">{s.plannedPoints}</td>
                        <td className="px-3 py-2">{s.completedPoints}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
