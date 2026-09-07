import { useEffect, useRef, useState } from "react";
import { Menu, Bell, Search, LogOut, Settings, UserCircle, CheckCheck } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import Avatar from "../ui/Avatar";
import Dropdown from "../ui/Dropdown";
import Badge from "../ui/Badge";
import { useAuth } from "../../context/AuthContext";
import { getNotifications, markAsRead } from "../../services/notificationService";
import { timeAgo } from "../../utils/formatters";
import { ROLE_PREFIX } from "../../utils/roleAccess";

export default function Header({ onOpenMobile, title }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const notifRef = useRef(null);

  useEffect(() => {
    getNotifications().then(setNotifications);
  }, []);

  useEffect(() => {
    if (!notifOpen) return;
    const onPointerDown = (e) => {
      if (notifRef.current && !notifRef.current.contains(e.target)) {
        setNotifOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [notifOpen]);

  const unread = notifications.filter((n) => !n.read).length;
  const searchVal = searchParams.get("search") || "";

  const handleSearchChange = (e) => {
    const val = e.target.value;
    if (val) {
      setSearchParams({ ...Object.fromEntries(searchParams.entries()), search: val });
    } else {
      const copy = Object.fromEntries(searchParams.entries());
      delete copy.search;
      setSearchParams(copy);
    }
  };

  const handleOpenNotification = async (n) => {
    if (!n.read) {
      await markAsRead(n.id);
      setNotifications((prev) => prev.map((p) => (p.id === n.id ? { ...p, read: true } : p)));
    }
    setNotifOpen(false);
  };

  const handleMarkAllAsRead = async () => {
    const unreadNotifications = notifications.filter((n) => !n.read);
    if (unreadNotifications.length === 0) return;

    // Trigger API calls for all unread items
    await Promise.all(unreadNotifications.map((n) => markAsRead(n.id)));

    // Optimistically update UI state
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-white/90 backdrop-blur px-4 lg:px-6">
      <button onClick={onOpenMobile} className="lg:hidden text-ink-500 hover:text-ink-900">
        <Menu size={20} />
      </button>

      {title && <h1 className="hidden sm:block text-base font-semibold text-ink-900 font-display">{title}</h1>}

      <div className="ml-auto flex items-center gap-2">
        <div className="hidden md:flex items-center gap-2 rounded-lg border border-border bg-cream-100 px-3 h-9 w-56">
          <Search size={15} className="text-ink-400" />
          <input
            value={searchVal}
            onChange={handleSearchChange}
            placeholder="Search…"
            className="bg-transparent text-sm outline-none placeholder:text-ink-400 w-full"
          />
        </div>

        <div className="relative" ref={notifRef}>
          <button
            onClick={() => setNotifOpen((o) => !o)}
            className="relative flex h-9 w-9 items-center justify-center rounded-lg text-ink-500 hover:bg-cream-100"
          >
            <Bell size={18} />
            {unread > 0 && <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-gold-500 ring-2 ring-white" />}
          </button>

          {notifOpen && (
            <>
              {/* Mobile backdrop so the panel reads as an overlay, not stray content */}
              <div className="fixed inset-0 z-40 bg-ink-900/20 sm:hidden" onClick={() => setNotifOpen(false)} />

              <div
                className="fixed left-4 right-4 top-16 z-50 mt-2 rounded-xl border border-border bg-white shadow-popover
                           sm:absolute sm:left-auto sm:right-0 sm:top-auto sm:w-80 sm:mt-2"
              >
                <div className="flex items-center justify-between gap-2 px-4 py-3 border-b border-border flex-wrap">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold text-ink-900">Notifications</p>
                    {unread > 0 && <Badge tone="gold">{unread} new</Badge>}
                  </div>
                  {unread > 0 && (
                    <button
                      onClick={handleMarkAllAsRead}
                      className="flex items-center gap-1 text-xs font-medium text-primary-600 hover:text-primary-800 transition-colors shrink-0"
                    >
                      <CheckCheck size={14} />
                      Mark all read
                    </button>
                  )}
                </div>

                <div className="max-h-[60vh] sm:max-h-80 overflow-y-auto scrollbar-thin">
                  {notifications.length === 0 ? (
                    <p className="px-4 py-6 text-center text-sm text-ink-400">You're all caught up.</p>
                  ) : (
                    notifications.map((n) => (
                      <button
                        key={n.id}
                        type="button"
                        onClick={() => handleOpenNotification(n)}
                        className={`block w-full text-left px-4 py-3 border-b border-border last:border-0 hover:bg-cream-100 transition-colors ${
                          !n.read ? "bg-primary-50/40" : ""
                        }`}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <p className={`text-sm ${!n.read ? "font-semibold text-ink-900" : "font-medium text-ink-700"}`}>
                            {n.title}
                          </p>
                          {!n.read && <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-gold-500" />}
                        </div>
                        <p className="text-xs text-ink-500 mt-0.5">{n.body}</p>
                        <p className="text-[11px] text-ink-400 mt-1">{timeAgo(n.time)}</p>
                      </button>
                    ))
                  )}
                </div>
              </div>
            </>
          )}
        </div>

        <Dropdown
          align="right"
          trigger={
            <button className="flex items-center gap-2 rounded-lg pl-1 pr-2 py-1 hover:bg-cream-100">
              <Avatar name={user?.name} color={user?.avatarColor} size={32} />
              <span className="hidden sm:block text-sm font-medium text-ink-800 max-w-[120px] truncate">{user?.name}</span>
            </button>
          }
          items={[
            { label: "My Profile", icon: UserCircle, onClick: () => navigate(`${ROLE_PREFIX[user?.role] || "/student"}/profile`) },
            { label: "Account Settings", icon: Settings, onClick: () => navigate(`${ROLE_PREFIX[user?.role] || "/student"}/settings`) },
            { divider: true },
            { label: "Log Out", icon: LogOut, danger: true, onClick: () => { logout(); navigate("/login"); } },
          ]}
        />
      </div>
    </header>
  );
}