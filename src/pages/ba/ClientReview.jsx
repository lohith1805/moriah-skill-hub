import { useEffect, useState } from "react";
import { Inbox, Activity, Building2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import ProgressBar from "../../components/ui/ProgressBar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { useToast } from "../../context/ToastContext";
import { getClientProjects, getClientProjectProgress } from "../../services/clientService";

export default function BaClientReview() {
  const { notify } = useToast();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null); // the selected project row
  const [progress, setProgress] = useState(null);
  const [progressLoading, setProgressLoading] = useState(false);

  useEffect(() => {
    getClientProjects()
      .then(setProjects)
      .catch((e) => notify(e?.message || "Couldn't load client projects.", { type: "error" }))
      .finally(() => setLoading(false));
  }, [notify]);

  const open = async (row) => {
    setDetail(row);
    setProgress(null);
    if (row.allocated) {
      setProgressLoading(true);
      try {
        setProgress(await getClientProjectProgress(row.id));
      } catch {
        setProgress(null);
      } finally {
        setProgressLoading(false);
      }
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Project Review"
        subtitle="Every project scope submitted by a client — read the brief, then author the BRD / SRS / FRS on the Requirements Authoring screen (MSH-FR-BA-03)"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Client Review" }]}
      />

      <Card>
        <Table
          loading={loading}
          data={projects}
          emptyTitle="No client submissions yet"
          columns={[
            { key: "title", header: "Project", className: "text-left font-medium text-ink-900" },
            { key: "clientName", header: "Client", className: "text-left text-xs", render: (r) => (
              <span className="inline-flex items-center gap-1.5"><Building2 size={12} className="text-ink-400" /> {r.clientName || "—"}</span>
            ) },
            { key: "scope", header: "Scope", className: "text-left max-w-md truncate", render: (r) => r.scope },
            { key: "budgetRange", header: "Budget", className: "text-left text-xs", render: (r) => r.budgetRange || "—" },
            { key: "submittedAt", header: "Submitted", className: "text-left text-xs" },
            { key: "status", header: "Status", className: "text-left", render: (r) => (
              <Badge tone={r.status === "Completed" ? "success" : r.status === "In Progress" ? "primary" : "warning"}>
                {r.allocated ? r.status : "Awaiting batch"}
              </Badge>
            ) },
            { key: "action", header: "", className: "text-right", render: (r) => (
              <Button size="sm" variant="secondary" icon={r.allocated ? Activity : Inbox} onClick={() => open(r)}>
                {r.allocated ? "Progress" : "Open brief"}
              </Button>
            ) },
          ]}
        />
      </Card>

      <Modal open={!!detail} onClose={() => setDetail(null)} title={detail?.title || "Project"} size="lg"
        footer={<Button variant="secondary" onClick={() => setDetail(null)}>Close</Button>}>
        {detail && (
          <div className="flex flex-col gap-4 text-left">
            <div className="text-xs text-ink-500">
              <span className="font-semibold text-ink-700">{detail.clientName}</span> · submitted {detail.submittedAt}
              {detail.budgetRange ? ` · budget ${detail.budgetRange}` : ""}
            </div>
            <div>
              <p className="text-xs font-semibold text-ink-700 mb-1">Business challenge & scope</p>
              <p className="text-sm text-ink-700 whitespace-pre-line bg-cream-50 border border-border/50 rounded-lg p-3">{detail.scope || "—"}</p>
            </div>

            {detail.allocated && (
              <div>
                <p className="text-xs font-semibold text-ink-700 mb-2">Delivery progress</p>
                {progressLoading ? (
                  <p className="text-sm text-ink-400">Loading…</p>
                ) : progress ? (
                  <>
                    <ProgressBar value={progress.milestoneCompletion} tone="primary" showValue label="Milestone completion" />
                    {progress.burndown.length > 0 && (
                      <div className="mt-3 overflow-x-auto rounded-lg border border-border">
                        <table className="w-full text-xs text-left">
                          <thead className="bg-cream-100/90 border-b border-border">
                            <tr><th className="px-3 py-2">Sprint</th><th className="px-3 py-2">Status</th><th className="px-3 py-2">Planned</th><th className="px-3 py-2">Completed</th></tr>
                          </thead>
                          <tbody className="divide-y divide-border">
                            {progress.burndown.map((s) => (
                              <tr key={s.sprintId}><td className="px-3 py-2">#{s.sprintNumber}</td><td className="px-3 py-2">{s.status}</td><td className="px-3 py-2">{s.plannedPoints}</td><td className="px-3 py-2">{s.completedPoints}</td></tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="text-sm text-ink-400">No progress data.</p>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
