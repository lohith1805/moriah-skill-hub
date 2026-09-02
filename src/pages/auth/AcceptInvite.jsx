import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Lock, Eye, EyeOff, ShieldCheck, CheckCircle2, XCircle } from "lucide-react";
import Button from "../../components/ui/Button";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required, passwordStrength, matches } from "../../utils/validators";
import { ROLE_HOME } from "../../utils/roleAccess";

// This is the page that actually resolves when Admin's generated invite
// link (/accept-invite?token=...) is opened — the "set your password and
// activate your account" step for internally-invited staff (Trainer,
// Developer, Lead Generator, HR, Business Analyst, Admin).
export default function AcceptInvite() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const { acceptInvite, loading } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();

  const [values, setValues] = useState({ password: "", confirmPassword: "" });
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [failed, setFailed] = useState(false);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }));

  if (!token) {
    return (
      <div className="text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-error-50">
          <XCircle className="text-error-500" size={24} />
        </div>
        <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Missing invite token</h2>
        <p className="text-sm text-ink-500 mt-2">
          This link is missing its invite token. Ask your Admin to resend it from User Management.
        </p>
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, {
      password: [required, passwordStrength],
      confirmPassword: [required, matches(values.password, "Passwords do not match")],
    });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    try {
      const result = await acceptInvite(token, values.password);
      if (result.twoFactorRequired) {
        // Invited ADMIN / HR_MANAGER: password is set and the account is ACTIVE,
        // but 2FA is mandatory. Send them to sign in, where the mandatory-2FA
        // setup flow runs.
        notify("Account activated. Sign in to set up two-factor authentication.", {
          type: "success",
          title: "Account activated",
        });
        navigate("/login", { replace: true });
        return;
      }
      notify(`Welcome to Moriah Skill Hub, ${result.user.name.split(" ")[0]}.`, {
        type: "success",
        title: "Account activated",
      });
      navigate(ROLE_HOME[result.user.role] || "/login", { replace: true });
    } catch (err) {
      setFailed(true);
      notify(err.message || "This invite link is no longer valid.", { type: "error", title: "Could not activate account" });
    }
  };

  if (failed) {
    return (
      <div className="text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-error-50">
          <XCircle className="text-error-500" size={24} />
        </div>
        <h2 className="font-display text-xl font-bold text-ink-900 mt-4">This invite isn't valid</h2>
        <p className="text-sm text-ink-500 mt-2">
          It may have already been used, or your Admin will need to send a fresh one from User Management.
        </p>
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary-50">
        <ShieldCheck className="text-primary-700" size={22} />
      </div>
      <h2 className="font-display text-2xl font-bold text-ink-900 mt-4 text-center">Activate your account</h2>
      <p className="text-sm text-ink-500 mt-2 text-center">
        You've been invited to join Moriah Skill Hub as internal staff. Set a password to finish activating your account.
      </p>

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4" noValidate>
        <div>
          <label className="text-sm font-medium text-ink-900">Choose a password</label>
          <div className="relative mt-1.5">
            <input
              type={showPassword ? "text" : "password"}
              value={values.password}
              onChange={set("password")}
              placeholder="••••••••"
              className={
                "w-full rounded-lg border pl-4 pr-11 py-3 text-sm text-ink-900 outline-none transition-all duration-200 " +
                (errors.password ? "border-error-400 focus:ring-2 focus:ring-error-100" : "border-border focus:border-primary-500 focus:ring-2 focus:ring-primary-100")
              }
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600"
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.password && <p className="text-xs text-error-500 mt-1.5">{errors.password}</p>}
        </div>

        <div>
          <label className="text-sm font-medium text-ink-900">Confirm password</label>
          <div className="relative mt-1.5">
            <input
              type={showConfirm ? "text" : "password"}
              value={values.confirmPassword}
              onChange={set("confirmPassword")}
              placeholder="••••••••"
              className={
                "w-full rounded-lg border pl-4 pr-11 py-3 text-sm text-ink-900 outline-none transition-all duration-200 " +
                (errors.confirmPassword ? "border-error-400 focus:ring-2 focus:ring-error-100" : "border-border focus:border-primary-500 focus:ring-2 focus:ring-primary-100")
              }
            />
            <button
              type="button"
              onClick={() => setShowConfirm((v) => !v)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600"
            >
              {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {errors.confirmPassword && <p className="text-xs text-error-500 mt-1.5">{errors.confirmPassword}</p>}
        </div>

        <Button type="submit" fullWidth loading={loading} icon={CheckCircle2}>
          Activate Account
        </Button>
      </form>
    </div>
  );
}
