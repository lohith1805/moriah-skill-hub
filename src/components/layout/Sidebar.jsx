import { NavLink } from "react-router-dom";
import clsx from "clsx";
import { X } from "lucide-react";
import logoMark from "../../assets/logo-mark.png";
import { NAV_CONFIG } from "../../routes/navConfig";
import { ROLE_LABELS } from "../../utils/constants";

export default function Sidebar({ role, mobileOpen, onCloseMobile }) {
  const items = NAV_CONFIG[role] || [];

  return (
    <>
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-primary-900/40 lg:hidden"
          onClick={onCloseMobile}
        />
      )}
      <aside
        className={clsx(
          "fixed z-50 inset-y-0 left-0 w-64 bg-primary-800 text-white flex flex-col transition-transform duration-200 lg:translate-x-0 lg:z-30",
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        {/* Full-width logo header section */}
        <div className="px-5 py-4 border-b border-white/10 shrink-0 flex flex-col gap-2">
          <div className="relative flex items-center w-full">
            <img
              src={logoMark}
              alt="Logo"
              className="w-full h-auto max-h-16 object-contain"
            />
            <button
              onClick={onCloseMobile}
              className="absolute right-0 top-0 lg:hidden text-white/60 hover:text-white"
            >
              <X size={18} />
            </button>
          </div>

          <div>
            <p className="text-[14px] text-gold-300 font-medium capitalize text-center">
              {ROLE_LABELS[role] || role}
            </p>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="flex-1 overflow-y-auto scrollbar-thin px-3 py-4 flex flex-col gap-0.5">

          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={onCloseMobile}
              className={({ isActive }) =>
                clsx(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-gold-500 text-primary-900 font-semibold"
                    : "text-white/75 hover:bg-white/10 hover:text-white"
                )
              }
            >
              <item.icon size={17} className="shrink-0" />
              <span className="truncate">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Footer */}
        <div className="px-5 py-4 border-t border-white/10 text-[11px] text-white/50">
          Learn · Grow · Succeed
        </div>
      </aside>
    </>
  );
}