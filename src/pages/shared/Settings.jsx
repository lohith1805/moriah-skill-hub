import { useState } from "react";
import { Settings2, ShieldAlert, BellRing, Mail, KeyRound } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import QrCode from "../../components/ui/QrCode";
import { Input } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import {
  requestPasswordReset,
  startTwoFactorSetup,
  confirmTwoFactorSetup,
  disableTwoFactor,
} from "../../services/authService";

const NOTIF_KEY = "msh_notif_prefs";
const readNotifPrefs = () => {
  try {
    return JSON.parse(localStorage.getItem(NOTIF_KEY) || "null") || { email: true, whatsapp: false, desktop: true };
  } catch {
    return { email: true, whatsapp: false, desktop: true };
  }
};

export default function SharedSettings() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();

  const [notifications, setNotifications] = useState(readNotifPrefs);
  const [sendingReset, setSendingReset] = useState(false);

  // 2FA
  const twoFaOn = !!user?.twoFactorEnabled;
  const [setup, setSetup] = useState(null); // { secret, provisioningUri }
  const [code, setCode] = useState("");
  const [twoFaBusy, setTwoFaBusy] = useState(false);
  const [disableOpen, setDisableOpen] = useState(false);

  const handleNotificationChange = (key, checked) => {
    const next = { ...notifications, [key]: checked };
    setNotifications(next);
    try {
      localStorage.setItem(NOTIF_KEY, JSON.stringify(next));
    } catch {
      /* non-fatal */
    }
    notify("Notification preference saved to this browser.", { type: "success" });
  };

  const sendReset = async () => {
    if (!user?.email) return;
    setSendingReset(true);
    try {
      await requestPasswordReset(user.email);
      notify(`We've emailed a password-reset link to ${user.email}.`, { type: "success", title: "Check your inbox" });
    } catch (err) {
      notify(err.message || "Could not send the reset link.", { type: "error" });
    } finally {
      setSendingReset(false);
    }
  };

  const beginEnable = async () => {
    setTwoFaBusy(true);
    try {
      const res = await startTwoFactorSetup();
      setSetup(res);
      setCode("");
    } catch (err) {
      // The account already has 2FA on (e.g. admin/HR mandatory setup done at first
      // login) but this view's cached user said otherwise — re-sync and correct the UI.
      if (/already.*enabled/i.test(err.message || "") || err.status === 409) {
        await refreshUser();
        notify("Two-factor authentication is already enabled on this account.", { type: "info" });
      } else {
        notify(err.message || "Could not start 2FA setup.", { type: "error" });
      }
    } finally {
      setTwoFaBusy(false);
    }
  };

  const confirmEnable = async () => {
    setTwoFaBusy(true);
    try {
      await confirmTwoFactorSetup(code.trim());
      await refreshUser();
      setSetup(null);
      setCode("");
      notify("Two-factor authentication is on.", { type: "success", title: "2FA enabled" });
    } catch (err) {
      notify(err.message || "That code didn't verify. Try the current one.", { type: "error" });
    } finally {
      setTwoFaBusy(false);
    }
  };

  const confirmDisable = async () => {
    setTwoFaBusy(true);
    try {
      await disableTwoFactor(code.trim());
      await refreshUser();
      setDisableOpen(false);
      setCode("");
      notify("Two-factor authentication is off.", { type: "warning", title: "2FA disabled" });
    } catch (err) {
      notify(err.message || "That code didn't verify.", { type: "error" });
    } finally {
      setTwoFaBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Account Settings"
        subtitle="Notifications, two-factor authentication, and password"
        breadcrumbs={[{ label: "Dashboard" }, { label: "Settings" }]}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="flex flex-col gap-6 lg:col-span-1">
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
            <p className="text-[10px] text-ink-400 leading-normal mt-3">
              Stored in this browser — a server-side preference API is not available yet.
            </p>
          </Card>

          <Card>
            <h3 className="font-display font-bold text-ink-900 text-sm mb-4 flex items-center gap-2">
              <ShieldAlert size={16} className="text-primary-600" /> Two-Factor Auth
            </h3>
            <p className="text-sm text-ink-700">
              Status:{" "}
              <span className={twoFaOn ? "font-semibold text-success-700" : "font-semibold text-ink-500"}>
                {twoFaOn ? "Enabled" : "Disabled"}
              </span>
            </p>
            <p className="text-[11px] text-ink-400 leading-normal mt-1 mb-3">
              An authenticator app (Google Authenticator, 1Password, …) generates a 6-digit code you enter after your password.
            </p>
            {twoFaOn ? (
              <Button size="sm" variant="secondary" onClick={() => { setCode(""); setDisableOpen(true); }}>
                Disable 2FA
              </Button>
            ) : (
              <Button size="sm" loading={twoFaBusy && !setup} onClick={beginEnable}>
                Enable 2FA
              </Button>
            )}
          </Card>
        </div>

        <Card className="lg:col-span-2">
          <h3 className="font-display font-bold text-ink-900 text-base mb-5 flex items-center gap-2">
            <Settings2 size={17} className="text-primary-600" /> Password
          </h3>
          <p className="text-sm text-ink-600 max-w-md">
            For your security, password changes go through an emailed reset link rather than an in-page form. We'll send
            a one-time link to <strong className="text-ink-900">{user?.email}</strong>; it expires shortly after.
          </p>
          <div className="flex items-start gap-2 bg-warning-50 px-4 py-3 rounded-lg border border-warning-100 text-xs text-warning-600 mt-4 max-w-md">
            <ShieldAlert size={14} className="shrink-0 mt-0.5" />
            <p>Completing a reset signs you out of every other device.</p>
          </div>
          <div className="pt-4">
            <Button icon={Mail} loading={sendingReset} onClick={sendReset}>
              Email me a reset link
            </Button>
          </div>
        </Card>
      </div>

      {/* Enable 2FA modal */}
      <Modal
        open={!!setup}
        onClose={() => setSetup(null)}
        title="Enable two-factor authentication"
        footer={
          <>
            <Button variant="secondary" onClick={() => setSetup(null)} disabled={twoFaBusy}>Cancel</Button>
            <Button icon={KeyRound} onClick={confirmEnable} disabled={twoFaBusy || code.trim().length !== 6}>
              {twoFaBusy ? "Verifying…" : "Verify & enable"}
            </Button>
          </>
        }
      >
        {setup && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <p className="text-sm text-ink-600">
              Scan this QR code with your authenticator app (Google Authenticator, Authy, 1Password…),
              then enter the current 6-digit code. Can’t scan? Use the setup key below instead.
            </p>
            {setup.provisioningUri && (
              <div className="flex justify-center">
                <QrCode value={setup.provisioningUri} />
              </div>
            )}
            <div className="rounded-lg border border-border bg-cream-50 p-3">
              <p className="text-[10px] font-bold uppercase text-ink-400">Setup key (manual entry)</p>
              <p className="font-mono text-sm break-all text-ink-900">{setup.secret}</p>
            </div>
            <Input
              label="6-digit code"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              placeholder="123456"
            />
          </div>
        )}
      </Modal>

      {/* Disable 2FA modal */}
      <Modal
        open={disableOpen}
        onClose={() => setDisableOpen(false)}
        title="Disable two-factor authentication"
        footer={
          <>
            <Button variant="secondary" onClick={() => setDisableOpen(false)} disabled={twoFaBusy}>Cancel</Button>
            <Button variant="danger" onClick={confirmDisable} disabled={twoFaBusy || code.trim().length !== 6}>
              {twoFaBusy ? "Verifying…" : "Disable 2FA"}
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4 text-left font-sans">
          <p className="text-sm text-ink-600">Enter a current code from your authenticator app to confirm.</p>
          <Input
            label="6-digit code"
            inputMode="numeric"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            placeholder="123456"
          />
        </div>
      </Modal>
    </div>
  );
}
