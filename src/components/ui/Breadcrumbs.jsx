import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";

export default function Breadcrumbs({ items }) {
  return (
    <nav className="flex items-center gap-1.5 text-sm text-ink-500 mb-1 flex-wrap">
      {items.map((item, idx) => (
        <span key={idx} className="flex items-center gap-1.5">
          {idx > 0 && <ChevronRight size={13} className="text-ink-400" />}
          {item.to ? (
            <Link to={item.to} className="hover:text-primary-700">
              {item.label}
            </Link>
          ) : (
            <span className="text-ink-900 font-medium">{item.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
