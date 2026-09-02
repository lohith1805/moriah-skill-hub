import { ChevronLeft, ChevronRight } from "lucide-react";
import clsx from "clsx";

export default function Pagination({ page, totalPages, onChange, totalItems, pageSize }) {
  if (totalPages <= 1) return null;
  const start = (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, totalItems);

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1).filter(
    (p) => p === 1 || p === totalPages || Math.abs(p - page) <= 1
  );

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-4 mt-2 border-t border-border">
      <p className="text-xs text-ink-500">
        Showing <span className="font-medium text-ink-700">{start}–{end}</span> of <span className="font-medium text-ink-700">{totalItems}</span>
      </p>
      <div className="flex items-center gap-1">
        <button
          disabled={page === 1}
          onClick={() => onChange(page - 1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-border text-ink-500 disabled:opacity-40 hover:bg-cream-100"
        >
          <ChevronLeft size={15} />
        </button>
        {pages.map((p, idx) => (
          <span key={p} className="flex items-center">
            {idx > 0 && pages[idx - 1] !== p - 1 && <span className="px-1 text-ink-400">…</span>}
            <button
              onClick={() => onChange(p)}
              className={clsx(
                "flex h-8 w-8 items-center justify-center rounded-lg text-sm",
                p === page ? "bg-primary-700 text-white" : "text-ink-600 hover:bg-cream-100"
              )}
            >
              {p}
            </button>
          </span>
        ))}
        <button
          disabled={page === totalPages}
          onClick={() => onChange(page + 1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-border text-ink-500 disabled:opacity-40 hover:bg-cream-100"
        >
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  );
}
