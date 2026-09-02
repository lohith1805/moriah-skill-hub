import Button from "./Button";

export default function EmptyState({ icon: Icon, title, description, actionLabel, onAction }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border py-14 text-center px-4">
      {Icon && (
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary-50">
          <Icon size={22} className="text-primary-600" />
        </div>
      )}
      <div>
        <p className="font-medium text-ink-900">{title}</p>
        {description && <p className="text-sm text-ink-500 mt-1 max-w-sm">{description}</p>}
      </div>
      {actionLabel && (
        <Button size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}
