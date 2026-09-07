import { useState } from "react";
import clsx from "clsx";

// tabs = [{ key, label, icon? }]
export default function Tabs({ tabs, defaultTab, onChange, children }) {
  const [active, setActive] = useState(defaultTab || tabs[0]?.key);

  const select = (key) => {
    setActive(key);
    onChange?.(key);
  };

  return (
    <div>
      <div className="flex items-center gap-1 border-b border-border overflow-x-auto scrollbar-thin">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => select(tab.key)}
            className={clsx(
              "flex items-center gap-1.5 whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              active === tab.key ? "border-primary-700 text-primary-700" : "border-transparent text-ink-500 hover:text-ink-700"
            )}
          >
            {tab.icon && <tab.icon size={15} />}
            {tab.label}
          </button>
        ))}
      </div>
      <div className="pt-4">{typeof children === "function" ? children(active) : children}</div>
    </div>
  );
}
