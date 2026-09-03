import { useState, useEffect } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { LogIn, Lock, Eye, EyeOff, User } from "lucide-react";
import Button from "../../components/ui/Button";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { ROLE_HOME } from "../../utils/roleAccess";
import { beginTwoFactorSetup, oauthAuthorizeUrl } from "../../services/authService";
import QrCode from "../../components/ui/QrCode";

/* --------------------------------- Google icon -------------------------------- */
/* Inline so this page has no extra file dependency. lucide-react has no brand
   icons (its old "Chrome" glyph was generic, not the Google logo, and has
   since been removed entirely) — this is the standard 4-color Google "G". */
function GoogleIcon({ size = 16 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#4285F4" d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z" />
      <path fill="#34A853" d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z" />
      <path fill="#FBBC05" d="M11.69 28.18A13.98 13.98 0 0 1 10.9 24c0-1.45.25-2.86.69-4.18v-5.7H4.34A21.97 21.97 0 0 0 2 24c0 3.55.85 6.91 2.34 9.88l7.35-5.7z" />
      <path fill="#EA4335" d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z" />
    </svg>
  );
}

function GithubIcon({ size = 16 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
    </svg>
  );
}

/* ------------------------------- Field shells ------------------------------- */
/* Local, self-contained field components so this page doesn't depend on the
   shared FormField's internal API — a boxed icon chip + input + optional
   trailing action, matching the reference mock. */

function FieldLabel({ children, required: req }) {
  return (
    <label className="text-sm font-medium text-ink-900">
      {children} {req && <span className="text-error-500">*</span>}
    </label>
  );
}

function FieldError({ children }) {
  if (!children) return null;
  return <p className="text-xs text-error-500 mt-1.5">{children}</p>;
}

function IconBox({ icon: Icon, active }) {
  return (
    <span
      className={
        "absolute left-1.5 top-1/2 -translate-y-1/2 h-8 w-8 rounded-md flex items-center justify-center transition-colors duration-200 " +
        (active ? "bg-primary-800 text-white" : "bg-cream-100 text-ink-500")
      }
    >
      <Icon size={15} />
    </span>
  );
}

function IconInput({ icon, error, trailing, active, ...props }) {
  return (
    <div className="relative">
      <IconBox icon={icon} active={active} />
      <input
        {...props}
        className={
          "w-full rounded-lg border pl-12 pr-4 py-3 text-sm text-ink-900 outline-none transition-all duration-200 " +
          (trailing ? "pr-11 " : "") +
          (error
             ? "border-error-400 focus:ring-2 focus:ring-error-100"
             : "border-border focus:border-primary-500 focus:ring-2 focus:ring-primary-100")
        }
      />
      {trailing}
    </div>
  );
}

/* ---------------------------------- Page ------------------------------------ */

export default function Login() {
  const { login, completeTwoFactor, loading } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [values, setValues] = useState({ identifier: "", password: "" });
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);

  // Real 2FA: after login the backend may return a challenge token instead of a
  // session. `setup` holds { secret, provisioningUri } when the account has 2FA
  // mandated but not yet configured (ADMIN / HR_MANAGER first login).
  const [mfaStep, setMfaStep] = useState(false);
  const [challengeToken, setChallengeToken] = useState(null);
  const [setup, setSetup] = useState(null);
  const [otp, setOtp] = useState("");
  const [mfaError, setMfaError] = useState("");
  const [mfaBusy, setMfaBusy] = useState(false);

  // A failed social sign-in (OAuthCallback) bounces back here with a human-readable
  // reason in router state — show it the same way a failed password login is shown,
  // then clear it so a refresh doesn't repeat it.
  const [authError] = useState(location.state?.authError || "");
  useEffect(() => {
    if (!location.state?.authError) return;
    notify(location.state.authError, { type: "error", title: "Sign in failed" });
    navigate(location.pathname, { replace: true, state: null });
  }, [location.state, location.pathname, navigate, notify]);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }));

  const goHome = (user) =>
    navigate(location.state?.from?.pathname || ROLE_HOME[user.role] || "/", { replace: true });

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { identifier: [required], password: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    try {
      const result = await login({ email: values.identifier, password: values.password });

      if (result.twoFactorRequired) {
        setChallengeToken(result.challengeToken);
        setMfaStep(true);
        setOtp("");
        setMfaError("");
        if (result.twoFactorSetupRequired) {
          try {
            const s = await beginTwoFactorSetup(result.challengeToken);
            setSetup(s);
            notify("Set up two-factor authentication to continue.", { type: "info", title: "2FA setup required" });
          } catch {
            notify("Could not start 2FA setup. Please try again.", { type: "error" });
          }
        } else {
          notify("Enter the 6-digit code from your authenticator app.", { type: "info", title: "2FA required" });
        }
        return;
      }

      notify(`Welcome back, ${result.user.name.split(" ")[0]}.`, { type: "success", title: "Signed in" });
      goHome(result.user);
    } catch (err) {
      notify(err.message || "Unable to sign in. Please try again.", { type: "error", title: "Sign in failed" });
    }
  };

  const handleMfaVerify = async (e) => {
    e.preventDefault();
    if (!/^\d{6}$/.test(otp)) {
      setMfaError("Enter the 6-digit code from your authenticator app.");
      return;
    }
    setMfaBusy(true);
    setMfaError("");
    try {
      const user = await completeTwoFactor({ challengeToken, totpCode: otp });
      notify(`Welcome back, ${user.name.split(" ")[0]}.`, { type: "success", title: "Signed in" });
      goHome(user);
    } catch (err) {
      setMfaError(err.message || "That code was not accepted. Try again.");
    } finally {
      setMfaBusy(false);
    }
  };

  const handleOAuth = (provider) => {
    // Full-page redirect to the backend's OAuth2 authorize endpoint. The
    // backend's success handler completes the sign-in and redirects back.
    window.location.href = oauthAuthorizeUrl(provider.toLowerCase());
  };

  if (mfaStep) {
    return (
      <div className="text-left">
        <h2 className="font-display text-2xl font-bold text-ink-900">
          {setup ? "Set up two-factor authentication" : "Two-factor verification"}
        </h2>
        <span className="block h-0.5 w-10 bg-gold-400 mt-3" />

        {setup ? (
          <div className="mt-3 text-sm text-ink-500">
            <p>
              Your role requires 2FA. Scan this QR code with an authenticator app (Google
              Authenticator, Authy, 1Password…), then enter the current 6-digit code.
            </p>
            {setup.provisioningUri && (
              <div className="mt-3 flex justify-center">
                <QrCode value={setup.provisioningUri} />
              </div>
            )}
            <div className="mt-3 rounded-lg border border-border bg-cream-50 p-3">
              <p className="text-xs uppercase tracking-wide text-ink-400">Setup key (manual entry)</p>
              <code className="block font-mono text-sm text-ink-900 break-all mt-1">{setup.secret}</code>
              {setup.provisioningUri && (
                <a
                  href={setup.provisioningUri}
                  className="mt-2 inline-block text-xs font-medium text-primary-600 hover:underline break-all"
                >
                  Open in authenticator app
                </a>
              )}
            </div>
          </div>
        ) : (
          <p className="text-sm text-ink-500 mt-3">
            Enter the 6-digit code from your authenticator app.
          </p>
        )}

        <form onSubmit={handleMfaVerify} className="mt-6 flex flex-col gap-4">
          <div>
            <FieldLabel required>Authentication code</FieldLabel>
            <div className="mt-1.5 relative">
              <IconBox icon={Lock} active={!!otp} />
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                required
                placeholder="123456"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                className="w-full rounded-lg border pl-12 pr-4 py-3 text-sm text-ink-900 outline-none transition-all duration-200 border-border focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
              />
            </div>
            <FieldError>{mfaError}</FieldError>
          </div>

          <Button type="submit" fullWidth disabled={mfaBusy}>
            {mfaBusy ? "Verifying…" : "Verify & sign in"}
          </Button>

          <button
            type="button"
            onClick={() => {
              setMfaStep(false);
              setSetup(null);
              setChallengeToken(null);
              setOtp("");
              setMfaError("");
            }}
            className="text-sm font-medium text-ink-500 hover:text-ink-700 transition-colors w-full text-center mt-2"
          >
            Go back to credentials
          </button>
        </form>
      </div>
    );
  }

  return (
    <div>
      <h2 className="font-display text-2xl font-bold text-ink-900">Welcome back!</h2>
      <span className="block h-0.5 w-10 bg-gold-400 mt-3" />
      <p className="text-sm text-ink-500 mt-3">Sign in to access your Moriah Skill Hub dashboard.</p>

      {authError && (
        <div className="mt-4 rounded-lg border border-error-200 bg-error-50 px-4 py-3 text-sm text-error-700">
          {authError}
        </div>
      )}

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4 text-left" noValidate>
        <div>
          <FieldLabel required>Email address</FieldLabel>
          <div className="mt-1.5">
            <IconInput
              name="identifier"
              icon={User}
              type="email"
              required
              placeholder="you@example.com"
              value={values.identifier}
              onChange={set("identifier")}
              error={errors.identifier}
              autoComplete="email"
            />
          </div>
          <FieldError>{errors.identifier}</FieldError>
        </div>

        <div>
          <FieldLabel required>Password</FieldLabel>
          <div className="mt-1.5">
            <IconInput
              name="password"
              icon={Lock}
              type={showPassword ? "text" : "password"}
              required
              placeholder="••••••••"
              value={values.password}
              onChange={set("password")}
              error={errors.password}
              autoComplete="current-password"
              trailing={
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
            />
          </div>
          <FieldError>{errors.password}</FieldError>
        </div>

        <div className="flex items-center justify-between -mt-1">
          <label className="flex items-center gap-2 text-sm text-ink-600 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="h-4 w-4 rounded border-border text-primary-700 focus:ring-primary-200"
            />
            Remember me
          </label>
          <Link to="/forgot-password" className="text-sm font-medium text-primary-700 hover:underline">
            Forgot password?
          </Link>
        </div>

        <Button type="submit" fullWidth loading={loading} icon={LogIn}>
          Sign In
        </Button>

        <div className="relative flex items-center py-1">
          <span className="flex-1 h-px bg-border" />
          <span className="px-3 text-xs text-ink-400">or continue with</span>
          <span className="flex-1 h-px bg-border" />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => handleOAuth("Google")}
            className="flex items-center justify-center gap-2 rounded-lg border border-border py-3 text-xs font-semibold text-ink-700 transition-all duration-200 hover:bg-cream-50 hover:-translate-y-0.5"
          >
            <GoogleIcon size={14} />
            Google
          </button>
          <button
            type="button"
            onClick={() => handleOAuth("GitHub")}
            className="flex items-center justify-center gap-2 rounded-lg border border-border py-3 text-xs font-semibold text-ink-700 transition-all duration-200 hover:bg-cream-50 hover:-translate-y-0.5"
          >
            <GithubIcon size={14} />
            GitHub
          </button>
        </div>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        New student?{" "}
        <Link to="/register" className="font-medium text-primary-700 hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}