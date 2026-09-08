import { useCallback, useEffect, useRef, useState } from "react";
import { Bell, CheckCircle2, Info, AlertTriangle } from "lucide-react";
import { getNotifications, markAsRead, markAllAsRead } from "../../services/notificationService";
import { timeAgo } from "../../utils/formatters";
import LoadingSpinner from "../ui/LoadingSpinner";
import EmptyState from "../ui/EmptyState";
import clsx from "clsx";

const ICONS = { success: CheckCircle2, warning: AlertTriangle, info: Info };
const TONE_COLOR = { success: "text-success-600", warning: "text-warning-600", info: "text-info-600" };

// The feed has no websocket, so a short poll is how a notification raised mid-session
// (PIP triggered, doc rejected, batch allocated, …) actually reaches the user without a reload.
const POLL_MS = 45000;

export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const ref = useRef(null);

  const refresh = useCallback(async () => {
    try {
      setNotifications(await getNotifications());
    } catch {
      /* transient — keep what's on screen, next poll retries */
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, POLL_MS);
    const onVisible = () => document.visibilityState === "visible" && refresh();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [refresh]);

  // Re-pull the moment the dropdown opens, so it never shows a stale list.
  useEffect(() => {
    if (open) refresh();
  }, [open, refresh]);

  useEffect(() => {
    const onClick = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  const unread = notifications.filter((n) => !n.read).length;

  const handleOpenItem = async (n) => {
    if (n.read) return;
    setNotifications((prev) => prev.map((p) => (p.id === n.id ? { ...p, read: true } : p)));
    try {
      await markAsRead(n.id);
    } catch {
      setNotifications((prev) => prev.map((p) => (p.id === n.id ? { ...p, read: false } : p)));
    }
  };

  const handleMarkAll = async () => {
    setNotifications((prev) => prev.map((p) => ({ ...p, read: true })));
    try {
      await markAllAsRead();
    } catch {
      refresh();
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen((o) => !o)} className="relative flex h-9 w-9 items-center justify-center rounded-lg text-ink-500 hover:bg-cream-100 hover:text-ink-700">
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-error-500 text-[10px] font-semibold text-white">
            {unread}
          </span>
        )}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30 bg-ink-900/20 sm:hidden" onClick={() => setOpen(false)} />
          <div
            className="fixed left-4 right-4 top-16 z-40 mt-2 rounded-xl border border-border bg-white shadow-popover animate-[fadeIn_0.12s_ease-out]
                       sm:absolute sm:left-auto sm:right-0 sm:top-auto sm:w-80 sm:mt-2"
          >
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <p className="text-sm font-semibold text-ink-900">Notifications</p>
              {unread > 0 && (
                <button onClick={handleMarkAll} className="text-xs text-primary-700 font-medium hover:text-primary-800">
                  Mark all read ({unread})
                </button>
              )}
            </div>
            <div className="max-h-[60vh] sm:max-h-80 overflow-y-auto scrollbar-thin">
              {loading ? (
                <LoadingSpinner label="Loading…" />
              ) : notifications.length === 0 ? (
                <EmptyState icon={Bell} title="No notifications" description="You're all caught up." compact />
              ) : (
                notifications.map((n) => {
                  const Icon = ICONS[n.tone] || Info;
                  return (
                    <button
                      key={n.id}
                      onClick={() => handleOpenItem(n)}
                      className={clsx("flex w-full items-start gap-2.5 px-4 py-3 text-left border-b border-border last:border-0 hover:bg-cream-100", !n.read && "bg-primary-50/40")}
                    >
                      <Icon size={16} className={clsx("mt-0.5 shrink-0", TONE_COLOR[n.tone])} />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-ink-900">{n.title}</p>
                        <p className="text-xs text-ink-500 mt-0.5 line-clamp-2">{n.body}</p>
                        <p className="text-[11px] text-ink-400 mt-1">{timeAgo(n.time)}</p>
                      </div>
                      {!n.read && <span className="mt-1.5 h-2 w-2 rounded-full bg-primary-600 shrink-0" />}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}