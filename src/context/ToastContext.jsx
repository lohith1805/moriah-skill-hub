import { createContext, useCallback, useContext, useState } from "react";
import { CheckCircle2, XCircle, Info, AlertTriangle, X } from "lucide-react";

const ToastContext = createContext(null);

const ICONS = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
  warning: AlertTriangle,
};

const STYLES = {
  success: "bg-success-50 border-success-500/30 text-success-600",
  error: "bg-error-50 border-error-500/30 text-error-600",
  info: "bg-info-50 border-info-500/30 text-info-600",
  warning: "bg-warning-50 border-warning-500/30 text-warning-600",
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback(
    (message, { type = "info", title, duration = 4500 } = {}) => {
      const id = Date.now() + Math.random();
      setToasts((prev) => [...prev, { id, message, type, title }]);
      if (duration) setTimeout(() => dismiss(id), duration);
      return id;
    },
    [dismiss]
  );

  return (
    <ToastContext.Provider value={{ notify, dismiss }}>
      {children}
      <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 w-[min(92vw,380px)]">
        {toasts.map((t) => {
          const Icon = ICONS[t.type] || Info;
          return (
            <div
              key={t.id}
              role="status"
              className={`flex items-start gap-3 rounded-xl border px-4 py-3 shadow-popover bg-white animate-[fadeIn_0.2s_ease-out] ${STYLES[t.type]}`}
            >
              <Icon size={18} className="mt-0.5 shrink-0" />
              <div className="flex-1 min-w-0">
                {t.title && <p className="text-sm font-semibold text-ink-900">{t.title}</p>}
                <p className="text-sm text-ink-700">{t.message}</p>
              </div>
              <button onClick={() => dismiss(t.id)} className="text-ink-400 hover:text-ink-700 shrink-0">
                <X size={16} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}
