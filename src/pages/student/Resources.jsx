import { useEffect, useState } from "react";
import { Library, FileCode2, Link2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import { getResourceLibrary } from "../../services/developerService";
import { getMyBatch } from "../../services/studentService";

export default function StudentResources() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyBatch()
      .catch(() => null)
      .then((batch) => getResourceLibrary(batch?.trackCode))
      .then((d) => setItems(d))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <PageHeader 
        title="Resource Library" 
        subtitle="Explore shared developer utilities, API sheets, cheat sheets, and starter SDK configurations" 
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Resources" }]} 
      />

      <Card>
        <Table
          loading={loading}
          data={items}
          emptyTitle="No resources published yet"
          emptyHint="Check back later for cheat sheets and boilerplate templates from the engineering team."
          columns={[
            { key: "title", header: "Resource Utility / Documentation", render: (r) => <span className="flex items-center gap-2 text-ink-900 font-semibold"><FileCode2 size={14} className="text-primary-500" /> {r.title}</span> },
            { key: "type", header: "Classification", render: (r) => <Badge tone="primary">{r.type}</Badge> },
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
