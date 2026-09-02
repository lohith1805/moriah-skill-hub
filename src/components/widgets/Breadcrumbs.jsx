import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";

export default function Breadcrumbs({ items }) {
  return (
    <nav className="flex items-center gap-1.5 text-sm text-ink-400 mb-1">
      {items.map((item, idx) => (
        <span key={idx} className="flex items-center gap-1.5">
          {idx > 0 && <ChevronRight size={13} />}
          {item.href ? (
            <Link to={item.href} className="hover:text-primary-700">{item.label}</Link>
          ) : (
            <span className="text-ink-700 font-medium">{item.label}</span>
          )}
        </span>
      ))}
    </nav>
  );
}
