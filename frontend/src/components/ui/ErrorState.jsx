import { AlertTriangle } from "lucide-react";
import Button from "./Button";

export default function ErrorState({ title = "Couldn't load this data", description = "Something went wrong on our end. Please try again.", onRetry }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-14">
      <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-error-50 text-error-500">
        <AlertTriangle size={22} />
      </div>
      <p className="text-sm font-semibold text-ink-700">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-ink-400">{description}</p>
      {onRetry && (
        <div className="mt-4">
          <Button variant="secondary" onClick={onRetry}>Try again</Button>
        </div>
      )}
    </div>
  );
}
