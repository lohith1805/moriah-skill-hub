import { useEffect, useRef, useState } from "react";
import clsx from "clsx";

export default function Dropdown({ trigger, items = [], align = "right" }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const onClick = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <div onClick={() => setOpen((o) => !o)}>{trigger}</div>
      {open && (
        <div
          className={clsx(
            "absolute z-40 mt-2 w-56 rounded-xl border border-border bg-white py-1.5 shadow-popover animate-[fadeIn_0.12s_ease-out]",
            align === "right" ? "right-0" : "left-0"
          )}
        >
          {items.map((item, idx) =>
            item.divider ? (
              <div key={idx} className="my-1 border-t border-border" />
            ) : (
              <button
                key={item.label}
                onClick={() => {
                  item.onClick?.();
                  setOpen(false);
                }}
                className={clsx(
                  "flex w-full items-center gap-2 px-3.5 py-2 text-left text-sm hover:bg-cream-100",
                  item.danger ? "text-error-500" : "text-ink-700"
                )}
              >
                {item.icon && <item.icon size={15} />}
                {item.label}
              </button>
            )
          )}
        </div>
      )}
    </div>
  );
}
