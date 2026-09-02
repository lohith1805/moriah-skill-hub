import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { LogIn, Lock, Eye, EyeOff, User } from "lucide-react";
import Button from "../../components/ui/Button";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { ROLE_HOME } from "../../utils/roleAccess";

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
  const { login, loading } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [values, setValues] = useState({ identifier: "", password: "" });
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);

  // MFA states
  const [mfaStep, setMfaStep] = useState(false);
  const [otp, setOtp] = useState("");
  const [tempUser, setTempUser] = useState(null);
  const [mfaError, setMfaError] = useState("");

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { identifier: [required], password: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    try {
      const user = await login({ identifier: values.identifier, password: values.password });
      
      if (user.mfaEnabled || localStorage.getItem("msh_mfa_enabled") === "true") {
        setTempUser(user);
        setMfaStep(true);
        notify("Verification code required to authenticate your identity.", { type: "info", title: "MFA Required" });
      } else {
        notify(`Welcome back, ${user.name.split(" ")[0]}.`, { type: "success", title: "Signed in" });
        navigate(location.state?.from?.pathname || ROLE_HOME[user.role], { replace: true });
      }
    } catch (err) {
      notify(err.message || "Unable to sign in. Please try again.", { type: "error", title: "Sign in failed" });
    }
  };

  const handleMfaVerify = (e) => {
    e.preventDefault();
    if (otp === "123456" || otp === "000000") {
      notify(`Welcome back, ${tempUser.name.split(" ")[0]}.`, { type: "success", title: "Signed in" });
      navigate(location.state?.from?.pathname || ROLE_HOME[tempUser.role], { replace: true });
    } else {
      setMfaError("Invalid 6-digit code. Try 123456 for testing.");
      notify("Verification failed. Please enter the correct code.", { type: "error" });
    }
  };

  const handleOAuth = async (provider) => {
    notify(`Connecting with ${provider} OAuth...`, { type: "info" });
    await new Promise((r) => setTimeout(r, 900));

    const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
    const list = rawList ? JSON.parse(rawList) : [];
    
    let oauthUser = list.find((u) => u.email === `oauth.${provider.toLowerCase()}@moriah.io`);
    if (!oauthUser) {
      oauthUser = {
        id: `usr_${Date.now()}`,
        name: `OAuth ${provider} Learner`,
        email: `oauth.${provider.toLowerCase()}@moriah.io`,
        phone: "+91 88888 88888",
        role: "student",
        password: "OAuthPassword123",
        track: "Full-Stack Development",
        batch: "FS-Batch-14",
        avatarColor: "primary",
        subscription: {
          planCode: "project_based",
          planName: "Project-Based Learning Plan",
          price: 14999,
          model: "Standard App",
          paidAt: new Date().toISOString(),
          paymentId: `oauth_${provider.toLowerCase()}_pi_${Math.random().toString(36).substring(2, 9)}`,
          gateway: provider,
        }
      };
      list.push(oauthUser);
      localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
    }
    
    localStorage.setItem("msh_user", JSON.stringify(oauthUser));
    notify(`Signed in via ${provider} successfully.`, { type: "success", title: "OAuth Connected" });
    navigate(ROLE_HOME.student, { replace: true });
  };

  if (mfaStep) {
    return (
      <div className="text-left">
        <h2 className="font-display text-2xl font-bold text-ink-900">MFA Verification</h2>
        <span className="block h-0.5 w-10 bg-gold-400 mt-3" />
        <p className="text-sm text-ink-500 mt-3">
          Please enter the 6-digit authenticator code. Use <code className="bg-cream-100 font-mono text-xs px-1 py-0.5 rounded">123456</code> to verify.
        </p>

        <form onSubmit={handleMfaVerify} className="mt-6 flex flex-col gap-4">
          <div>
            <FieldLabel required>MFA Verification Code</FieldLabel>
            <div className="mt-1.5 relative">
              <IconBox icon={Lock} active={!!otp} />
              <input
                type="text"
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

          <Button type="submit" fullWidth>
            Verify Code &amp; Sign In
          </Button>

          <button
            type="button"
            onClick={() => { setMfaStep(false); setOtp(""); setMfaError(""); }}
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

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4 text-left" noValidate>
        <div>
          <FieldLabel required>Email, phone number or username</FieldLabel>
          <div className="mt-1.5">
            <IconInput
              name="identifier"
              icon={User}
              type="text"
              required
              placeholder="Username, email or phone"
              value={values.identifier}
              onChange={set("identifier")}
              error={errors.identifier}
              autoComplete="username"
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