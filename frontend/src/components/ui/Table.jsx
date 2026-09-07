import { Inbox } from "lucide-react";
import clsx from "clsx";
import LoadingSpinner from "./LoadingSpinner";

// Generic data table: columns = [{ key, header, render?(row), width? }]
export default function Table({ columns, data, loading, emptyTitle = "No records found", emptyHint = "Try adjusting your filters.", rowKey = "id", onRowClick }) {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <LoadingSpinner label="Loading data…" />
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
        <Inbox className="text-ink-400" size={30} />
        <p className="font-medium text-ink-700">{emptyTitle}</p>
        <p className="text-sm text-ink-400">{emptyHint}</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto -mx-5 px-5">
      <table className="w-full min-w-[640px] border-collapse text-sm">
        <thead>
          <tr className="border-b border-border text-left">
            {columns.map((col) => (
              <th key={col.key} style={{ width: col.width }} className="whitespace-nowrap px-3 py-2.5 font-medium text-ink-500">
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr
              key={row[rowKey]}
              onClick={() => onRowClick?.(row)}
              className={clsx("border-b border-border last:border-0 hover:bg-cream-100/70 transition-colors", onRowClick && "cursor-pointer")}
            >
              {columns.map((col) => (
                <td key={col.key} className="px-3 py-3 align-middle text-ink-700">
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
