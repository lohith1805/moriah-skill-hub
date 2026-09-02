import { Loader2 } from "lucide-react";

export default function LoadingSpinner({ label, size = 20 }) {
  return (
    <div className="flex items-center gap-2 text-ink-500">
      <Loader2 size={size} className="animate-spin text-primary-600" />
      {label && <span className="text-sm">{label}</span>}
    </div>
  );
}
