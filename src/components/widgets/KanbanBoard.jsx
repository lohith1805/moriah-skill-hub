import { useState } from "react";
import clsx from "clsx";
import Badge from "../ui/Badge";
import EmptyState from "../ui/EmptyState";
import { LayoutGrid } from "lucide-react";

// columns: array of column names in order. items: array with a `status` field matching a column.
// renderCard(item) -> JSX. onMove(item, newStatus) called on drop.
export default function KanbanBoard({ columns, items, renderCard, onMove, cardKey = "id" }) {
  const [dragId, setDragId] = useState(null);

  const displayItems = items || [];

  return (
    <div className="grid grid-flow-col auto-cols-[minmax(260px,1fr)] gap-4 overflow-x-auto pb-2 scrollbar-thin">
      {columns.map((col) => {
        const colItems = displayItems.filter((i) => i.status === col);
        return (
          <div
            key={col}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => {
              if (dragId) onMove?.(dragId, col);
              setDragId(null);
            }}
            className="flex flex-col rounded-xl bg-cream-200/70 border border-border p-3 min-h-[240px]"
          >
            <div className="flex items-center justify-between mb-3 px-1">
              <p className="text-sm font-semibold text-ink-700">{col}</p>
              <Badge tone="neutral">{colItems.length}</Badge>
            </div>
            <div className="flex flex-col gap-2.5">
              {colItems.map((item) => (
                <div
                  key={item[cardKey]}
                  draggable={!!onMove}
                  onDragStart={() => setDragId(item[cardKey])}
                  className={clsx(
                    "rounded-lg border border-border bg-white p-3 shadow-card",
                    onMove && "cursor-grab active:cursor-grabbing"
                  )}
                >
                  {renderCard(item)}
                </div>
              ))}
              {colItems.length === 0 && <p className="text-xs text-ink-400 text-center py-4">No items</p>}
            </div>
          </div>
        );
      })}
    </div>
  );
}
