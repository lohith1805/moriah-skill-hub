import { useEffect, useMemo, useState } from "react";
import { FileCode2, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import { Select } from "../../components/ui/FormField";
import { TRACKS, TRACK_LABELS } from "../../utils/constants";
// Resource Library is authored by Developers, but is an internal reference
// shelf (cheat sheets, SDK docs, boilerplates) meant for anyone building
// curriculum content — so Trainer reads the same shared library here
// read-only, without needing its own copy of the data.
import { getResourceLibrary, getProjects } from "../../services/developerService";

export default function TrainerResources() {
  const [items, setItems] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [track, setTrack] = useState("");
  const [projectId, setProjectId] = useState("");

  useEffect(() => {
    Promise.all([getResourceLibrary(), getProjects().catch(() => [])])
      .then(([d, p]) => { setItems(d); setProjects(p); })
      .finally(() => setLoading(false));
  }, []);

  const projectTitle = (id) => projects.find((p) => String(p.id) === String(id))?.title || `Project #${id}`;

  // Client-side: a blank track / project on a row means "everyone" and always shows.
  const filtered = useMemo(
    () => items.filter((r) =>
      (!track || !r.track || r.track === track) &&
      (!projectId || !r.projectId || String(r.projectId) === String(projectId))),
    [items, track, projectId],
  );

  return (
    <div>
      <PageHeader title="Resource Library" subtitle="Shared libraries, cheat sheets, SDK documentation, and boilerplates authored by the Developer team" breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Resources" }]} />

      <Card>
        <div className="mb-4 flex flex-wrap gap-3">
          <Select
            className="w-56"
            label="Filter by track"
            value={track}
            onChange={(e) => setTrack(e.target.value)}
            options={[{ value: "", label: "All tracks" }, ...TRACKS]}
          />
          <Select
            className="w-64"
            label="Filter by project"
            value={projectId}
            onChange={(e) => setProjectId(e.target.value)}
            options={[{ value: "", label: "All projects" }, ...projects.map((p) => ({ value: String(p.id), label: p.title }))]}
          />
        </div>
        <Table
          loading={loading}
          data={filtered}
          emptyTitle="No resources published yet"
          emptyHint="Check back later for cheat sheets and boilerplate templates from the Developer team."
          columns={[
            { key: "title", header: "Resource", render: (r) => <span className="flex items-center gap-2"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Type", render: (r) => <Badge tone="primary">{r.type}</Badge> },
            { key: "track", header: "Track", render: (r) => <span className="text-xs text-ink-500">{r.track ? TRACK_LABELS[r.track] || r.track : "All tracks"}</span> },
            { key: "project", header: "Project", render: (r) => <span className="text-xs text-ink-500">{r.projectId ? projectTitle(r.projectId) : "All projects"}</span> },
            { key: "updatedAt", header: "Updated" },
            { key: "action", header: "", render: (r) => (
              <a href={r.link} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1 text-primary-700 hover:underline text-xs font-semibold">
                <Link2 size={12} /> Open
              </a>
            ) },
          ]}
        />
      </Card>
    </div>
  );
}
