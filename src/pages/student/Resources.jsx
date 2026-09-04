import { useEffect, useMemo, useState } from "react";
import { FileCode2, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import { Select } from "../../components/ui/FormField";
import { getResourceLibrary } from "../../services/developerService";
import { getMyBatch, getMyProjects } from "../../services/studentService";

export default function StudentResources() {
  const [items, setItems] = useState([]);
  const [projects, setProjects] = useState([]);
  const [projectId, setProjectId] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      getMyBatch().catch(() => null).then((batch) => getResourceLibrary(batch?.trackCode)).catch(() => []),
      getMyProjects().catch(() => []),
    ])
      .then(([d, p]) => { setItems(d); setProjects(p); })
      .catch(() => { setItems([]); setProjects([]); })
      .finally(() => setLoading(false));
  }, []);

  const projectTitle = (id) => projects.find((p) => String(p.id) === String(id))?.title || `Project #${id}`;

  const filtered = useMemo(
    () => items.filter((r) => !projectId || !r.projectId || String(r.projectId) === String(projectId)),
    [items, projectId],
  );

  return (
    <div>
      <PageHeader
        title="Resource Library"
        subtitle="Explore shared developer utilities, API sheets, cheat sheets, and starter SDK configurations"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Resources" }]}
      />

      <Card>
        {projects.length > 0 && (
          <div className="mb-4 max-w-xs">
            <Select
              label="Filter by project"
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              options={[{ value: "", label: "All projects" }, ...projects.map((p) => ({ value: String(p.id), label: p.title }))]}
            />
          </div>
        )}
        <Table
          loading={loading}
          data={filtered}
          emptyTitle="No resources published yet"
          emptyHint="Check back later for cheat sheets and boilerplate templates from the engineering team."
          columns={[
            { key: "title", header: "Resource Utility / Documentation", render: (r) => <span className="flex items-center gap-2 text-ink-900 font-semibold"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Classification", render: (r) => <Badge tone="primary">{r.type}</Badge> },
            { key: "project", header: "Project", render: (r) => <span className="text-xs text-ink-500">{r.projectId ? projectTitle(r.projectId) : "All"}</span> },
            { key: "updatedAt", header: "Last Updated" },
            { key: "action", header: "", render: (r) => (
              <a href={r.link} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-primary-700 hover:underline text-xs font-bold">
                <Link2 size={12} /> Open Resource Link
              </a>
            ) },
          ]}
        />
      </Card>
    </div>
  );
}
