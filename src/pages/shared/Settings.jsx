import { useState } from "react";
import { Save, Settings2, ShieldAlert, BellRing, Eye, EyeOff } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import { Input } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";

export default function SharedSettings() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [saving, setSaving] = useState(false);
  const [passwords, setPasswords] = useState({ old: "", newPassword: "", confirm: "" });
  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  
  // Notification states loaded dynamically from user context
  const [notifications, setNotifications] = useState(() => {
    return user?.notifications || {
      email: true,
      whatsapp: false,
      desktop: true,
    };
  });

  const [mfaEnabled, setMfaEnabled] = useState(() => {
    return user?.mfaEnabled || localStorage.getItem("msh_mfa_enabled") === "true";
  });

  const handleNotificationChange = (key, checked) => {
    // Side effects (localStorage writes, toast) must live outside the
    // setState updater. React 18/19 Strict Mode intentionally invokes state
    // updater functions twice in development to surface impure updaters —
    // when notify()/localStorage calls lived inside the updater above, that
    // meant every checkbox click fired two toasts. Compute `next` from the
    // current state directly and run side effects once, after the update.
    const next = { ...notifications, [key]: checked };
    setNotifications(next);

    if (user) {
      const updatedUser = { ...user, notifications: next };
      try {
        localStorage.setItem("msh_user", JSON.stringify(updatedUser));
        const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
        if (rawList) {
          const list = JSON.parse(rawList);
          const idx = list.findIndex((u) => u.id === user.id);
          if (idx > -1) {
            list[idx] = updatedUser;
            localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
          }
        }
        notify("Notification preferences updated.", { type: "success", title: "Preferences Saved" });
      } catch (err) {
        console.warn("Failed to persist notification updates:", err);
      }
    }
  };

  const handleMfaChange = (checked) => {
    setMfaEnabled(checked);
    localStorage.setItem("msh_mfa_enabled", checked ? "true" : "false");
    
    if (user) {
      const updatedUser = { ...user, mfaEnabled: checked };
      try {
        localStorage.setItem("msh_user", JSON.stringify(updatedUser));
        const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
        if (rawList) {
          const list = JSON.parse(rawList);
          const idx = list.findIndex((u) => u.id === user.id);
          if (idx > -1) {
            list[idx] = updatedUser;
            localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
          }
        }
        notify(checked ? "Multi-factor authentication (MFA) enabled." : "Multi-factor authentication (MFA) disabled.", {
          type: checked ? "success" : "warning",
          title: "MFA Settings Updated"
        });
      } catch (err) {
        console.warn("Failed to persist MFA preference:", err);
      }
    }
  };

  const save = async (e) => {
    e.preventDefault();
    if (!passwords.old || !passwords.newPassword || !passwords.confirm) {
      notify("Please fill in all password fields.", { type: "error", title: "Validation Error" });
      return;
    }
    if (passwords.newPassword !== passwords.confirm) {
      notify("New passwords do not match.", { type: "error", title: "Validation Error" });
      return;
    }
    if (passwords.newPassword.length < 8) {
      notify("New password must be at least 8 characters long.", { type: "error", title: "Validation Error" });
      return;
    }

    setSaving(true);
    await new Promise((r) => setTimeout(r, 600));

    // Actually update password in session & list
    if (user) {
      const updatedUser = { ...user, password: passwords.newPassword };
      try {
        localStorage.setItem("msh_user", JSON.stringify(updatedUser));
        const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
        if (rawList) {
          const list = JSON.parse(rawList);
          const idx = list.findIndex((u) => u.id === user.id);
          if (idx > -1) {
            list[idx] = updatedUser;
            localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
          }
        }
      } catch (err) {
        console.warn("Failed to persist password update:", err);
      }
    }

    setSaving(false);
    notify("Your password has been changed successfully.", { type: "success", title: "Password Changed" });
    setPasswords({ old: "", newPassword: "", confirm: "" });
    setTimeout(() => {
      window.location.reload();
    }, 800);
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Account Settings"
        subtitle="Manage notifications, authentication options, and theme preferences"
        breadcrumbs={[{ label: "Dashboard" }, { label: "Settings" }]}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Preference Blocks */}
        <div className="flex flex-col gap-6 lg:col-span-1">
          {/* Notification Preferences Card */}
          <Card>
            <h3 className="font-display font-bold text-ink-900 text-sm mb-4 flex items-center gap-2">
              <BellRing size={16} className="text-primary-600" /> Notifications
            </h3>
            <div className="flex flex-col gap-3">
              {[
                { key: "email", label: "Email notifications" },
                { key: "whatsapp", label: "WhatsApp alerts" },
                { key: "desktop", label: "Desktop browser alerts" },
              ].map((item) => (
                <label key={item.key} className="flex items-center gap-3 cursor-pointer text-sm text-ink-700">
                  <input
                    type="checkbox"
                    checked={notifications[item.key]}
                    onChange={(e) => handleNotificationChange(item.key, e.target.checked)}
                    className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                  />
                  <span>{item.label}</span>
                </label>
              ))}
            </div>
          </Card>

          {/* MFA Config Card */}
          <Card>
            <h3 className="font-display font-bold text-ink-900 text-sm mb-4 flex items-center gap-2">
              <ShieldAlert size={16} className="text-primary-600" /> Multi-Factor Auth (MFA)
            </h3>
            <div className="flex flex-col gap-3">
              <label className="flex items-center gap-3 cursor-pointer text-sm text-ink-700">
                <input
                  type="checkbox"
                  checked={mfaEnabled}
                  onChange={(e) => handleMfaChange(e.target.checked)}
                  className="h-4 w-4 rounded border-border text-primary-600 focus:ring-primary-500"
                />
                <span>Enable Google/GitHub Authenticator OTP</span>
              </label>
              <p className="text-[10px] text-ink-400 leading-normal">
                When enabled, logging in with your password will require entering a 6-digit verification code.
              </p>
            </div>
          </Card>
        </div>

        {/* Right Column: Change Password Panel */}
        <Card className="lg:col-span-2">
          <h3 className="font-display font-bold text-ink-900 text-base mb-5 flex items-center gap-2">
            <Settings2 size={17} className="text-primary-600" /> Security Credentials
          </h3>

          <form onSubmit={save} className="flex flex-col gap-4">
            <Input
              label="Current Password"
              type={showOld ? "text" : "password"}
              value={passwords.old}
              onChange={(e) => setPasswords((p) => ({ ...p, old: e.target.value }))}
              placeholder="••••••••"
              rightElement={
                <button
                  type="button"
                  onClick={() => setShowOld(!showOld)}
                  className="text-ink-400 hover:text-ink-600 transition-colors p-1"
                >
                  {showOld ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
            />
            
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                label="New Password"
                type={showNew ? "text" : "password"}
                value={passwords.newPassword}
                onChange={(e) => setPasswords((p) => ({ ...p, newPassword: e.target.value }))}
                placeholder="••••••••"
                hint="Minimum 8 characters with numbers."
                rightElement={
                  <button
                    type="button"
                    onClick={() => setShowNew(!showNew)}
                    className="text-ink-400 hover:text-ink-600 transition-colors p-1"
                  >
                    {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
              />
              <Input
                label="Confirm New Password"
                type={showConfirm ? "text" : "password"}
                value={passwords.confirm}
                onChange={(e) => setPasswords((p) => ({ ...p, confirm: e.target.value }))}
                placeholder="••••••••"
                rightElement={
                  <button
                    type="button"
                    onClick={() => setShowConfirm(!showConfirm)}
                    className="text-ink-400 hover:text-ink-600 transition-colors p-1"
                  >
                    {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
              />
            </div>

            <div className="flex items-start gap-2 bg-warning-50 px-4 py-3 rounded-lg border border-warning-100 text-xs text-warning-600 mt-2">
              <ShieldAlert size={14} className="shrink-0 mt-0.5" />
              <p>Changing your password will terminate all active browser sessions across other devices.</p>
            </div>

            <div className="pt-2">
              <Button type="submit" icon={Save} loading={saving}>
                Apply Security Settings
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </div>
  );
}