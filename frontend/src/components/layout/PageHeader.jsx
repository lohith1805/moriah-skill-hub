import Breadcrumbs from "../ui/Breadcrumbs";

export default function PageHeader({ title, subtitle, breadcrumbs, action }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3 mb-6">
      <div>
        {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
        <h1 className="font-display text-2xl font-bold text-ink-900">{title}</h1>
        {subtitle && <p className="text-sm text-ink-500 mt-1">{subtitle}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
