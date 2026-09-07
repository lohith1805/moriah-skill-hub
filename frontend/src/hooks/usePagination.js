import { useMemo, useState } from "react";

export function usePagination(items = [], pageSize = 8) {
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const pageItems = useMemo(() => {
    const start = (page - 1) * pageSize;
    return items.slice(start, start + pageSize);
  }, [items, page, pageSize]);

  const goTo = (p) => setPage(Math.min(Math.max(1, p), totalPages));

  return { page, totalPages, pageItems, goTo, totalItems: items.length, pageSize };
}
