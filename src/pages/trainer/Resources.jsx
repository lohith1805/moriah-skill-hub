import { useEffect, useState } from "react";
import { FileCode2, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
// Resource Library is authored by Developers, but is an internal reference
// shelf (cheat sheets, SDK docs, boilerplates) meant for anyone building
// curriculum content — so Trainer reads the same shared library here
// read-only, without needing its own copy of the data.
import { getResourceLibrary } from "../../services/developerService";

export default function TrainerResources() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getResourceLibrary().then((d) => { setItems(d); setLoading(false); });
  }, []);

  return (
    <div>
      <PageHeader title="Resource Library" subtitle="Shared libraries, cheat sheets, SDK documentation, and boilerplates authored by the Developer team" breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Resources" }]} />

      <Card>
        <Table
          loading={loading}
          data={items}
          emptyTitle="No resources published yet"
          emptyHint="Check back later for cheat sheets and boilerplate templates from the Developer team."
          columns={[
            { key: "title", header: "Resource", render: (r) => <span className="flex items-center gap-2"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Type", render: (r) => <Badge tone="primary">{r.type}</Badge> },
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