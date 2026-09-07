import { useState, useEffect } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  UserPlus, User, Mail, Phone, Lock, Eye, EyeOff,
  GraduationCap, Building2, ShieldCheck, Clock,
} from "lucide-react";
import Button from "../../components/ui/Button";
import { Select } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required, isEmail, isPhone, passwordStrength, matches } from "../../utils/validators";
import { ROLES, ROLE_LABELS } from "../../utils/constants";
import { oauthAuthorizeUrl, resendVerificationEmail } from "../../services/authService";
import LegalModal from "../../components/legal/LegalModal";

/* Only Student and Corporate Client are self-registerable. Every other role
   (Trainer, Developer, Lead Generator, HR, Business Analyst, Admin) is
   internal staff — those accounts are created by an Admin via an invite link
   from /admin/users, never through this public form. */
const ROLE_OPTIONS = [
  { value: ROLES.STUDENT, label: ROLE_LABELS[ROLES.STUDENT], icon: GraduationCap, blurb: "Enroll in a track, pick a plan, start training." },
  { value: ROLES.CLIENT, label: ROLE_LABELS[ROLES.CLIENT], icon: Building2, blurb: "Post project requirements, review talent & sprint demos." },
];

/* --------------------------------- Google icon -------------------------------- */
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

function WhatsAppIcon({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className="text-[#25D366] shrink-0">
      <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946C.003 5.37 5.37.003 12 .003a11.948 11.948 0 0 1 8.484 3.515 11.961 11.961 0 0 1 3.515 8.484c-.004 6.63-5.37 11.997-12 11.997-2.006-.002-3.973-.5-5.748-1.45L0 24zm12-21.884c-5.443 0-9.874 4.43-9.874 9.874 0 2.015.612 3.93 1.758 5.545l-.97 3.54 3.655-.956c1.554.85 3.3 1.298 5.43 1.298 5.443 0 9.874-4.43 9.874-9.874 0-5.444-4.43-9.874-9.874-9.874z" />
    </svg>
  );
}

/* ------------------------------- Field shells ------------------------------- */

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

function FieldHint({ children }) {
  if (!children) return null;
  return <p className="text-xs text-ink-400 mt-1.5">{children}</p>;
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

function IconInput({ icon, error, trailing, ...props }) {
  return (
    <div className="relative">
      <IconBox icon={icon} />
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

export default function Register() {
  const { notify } = useToast();
  const { register, registerClientAccount } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [searchParams] = useSearchParams();
  const leadId = searchParams.get("leadId");

  const [role, setRole] = useState(ROLES.STUDENT);
  const [clientSubmitted, setClientSubmitted] = useState(false);
  const [studentSubmitted, setStudentSubmitted] = useState(false);
  const [resendState, setResendState] = useState("idle"); // idle | sending | sent

  const [values, setValues] = useState({
    name: searchParams.get("name") || "",
    email: searchParams.get("email") || "",
    phone: searchParams.get("phone") || "",
    company: "",
    password: "",
    confirmPassword: "",
    track: "Full-Stack Development",
    whatsappNotifications: false,
    agree: false,
  });

  // Came here from a Lead Generator's "Send Enrollment Link" action — let them
  // know their details were carried over. Plan selection and payment now happen
  // after first login on /student/subscription (D2 flow), so this form only
  // collects account details.
  useEffect(() => {
    if (!leadId) return;
    notify("Your details were pre-filled from your enquiry — just set a password and confirm to finish.", {
      type: "info",
      title: "Welcome back",
    });
  }, [leadId]); // eslint-disable-line react-hooks/exhaustive-deps

  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [legalDoc, setLegalDoc] = useState(null);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));

  const isStudent = role === ROLES.STUDENT;

  /* ---- Account details ---- */
  const submitDetails = (e) => {
    e.preventDefault();
    const rules = {
      name: [required],
      email: [required, isEmail],
      phone: [required, isPhone],
      password: [required, passwordStrength],
      confirmPassword: [required, matches(values.password, "Passwords do not match")],
    };
    if (isStudent) rules.track = [required];
    if (!isStudent) rules.company = [required];
    const validation = validateForm(values, rules);
    if (!values.agree) validation.agree = "You must accept the Terms to continue";
    setErrors(validation);
    if (Object.keys(validation).length) return;

    if (isStudent) {
      submitStudent();
      return;
    }
    submitClient();
  };

  /* ---- Student: create the account, then send them to verify + sign in.
     Plan selection and payment now happen after first login, on
     /student/subscription (D2 flow) — the backend never auto-logs-in a
     freshly-registered student and the account is PENDING_VERIFICATION. ---- */
  const submitStudent = async () => {
    setSubmitting(true);
    try {
      const res = await register({
        name: values.name,
        email: values.email,
        phone: values.phone,
        password: values.password,
        track: values.track,
        githubUsername: values.githubUsername || undefined,
        notifications: { email: true, whatsapp: values.whatsappNotifications, desktop: true },
        agree: values.agree,
      });
      setStudentSubmitted(res?.needsEmailVerification !== false);
    } catch (err) {
      notify(err.message || "Registration failed. Please try again.", { type: "error", title: "Something went wrong" });
    } finally {
      setSubmitting(false);
    }
  };

  /* ---- Client: no plan/payment step — submit straight to the approval queue ---- */
  const submitClient = async () => {
    setSubmitting(true);
    try {
      await registerClientAccount({
        name: values.name,
        email: values.email,
        phone: values.phone,
        company: values.company,
        password: values.password,
        notifications: { email: true, whatsapp: values.whatsappNotifications, desktop: true },
        agree: values.agree,
      });
      setClientSubmitted(true);
    } catch (err) {
      notify(err.message || "Registration failed. Please try again.", { type: "error", title: "Something went wrong" });
    } finally {
      setSubmitting(false);
    }
  };

  /* Full-page redirect to the backend's OAuth2 authorize endpoint — identical to the
     Login page. The backend completes the sign-in (first OAuth login auto-creates an
     ACTIVE student with a verified email) and redirects back to /auth/oauth/callback. */
  const handleOAuth = (provider) => {
    window.location.href = oauthAuthorizeUrl(provider.toLowerCase());
  };

  const handleResendVerification = async () => {
    if (resendState !== "idle") return;
    setResendState("sending");
    try {
      await resendVerificationEmail(values.email);
      setResendState("sent");
      notify(`We've sent another verification link to ${values.email}.`, {
        type: "success",
        title: "Link on its way",
      });
    } catch (err) {
      setResendState("idle");
      notify(err.message || "Could not resend the link. Try again in a moment.", { type: "error" });
    }
  };

  /* ------------------------------ Student: success screen ------------------------------ */
  if (studentSubmitted) {
    return (
      <div className="text-center py-4">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-primary-100 text-primary-800">
          <ShieldCheck size={26} />
        </div>
        <h2 className="font-display text-2xl font-bold text-ink-900 mt-5">Verify your email</h2>
        <span className="block h-0.5 w-10 bg-gold-400 mt-3 mx-auto" />
        <p className="text-sm text-ink-500 mt-4 max-w-sm mx-auto">
          Thanks, <strong className="text-ink-700">{values.name.split(" ")[0]}</strong> — we've sent a verification
          link to <strong className="text-ink-700">{values.email}</strong>. Confirm it, then sign in.
        </p>
        <div className="mt-5 rounded-lg border border-border bg-cream-50 p-4 text-left text-sm text-ink-600 max-w-sm mx-auto">
          <p className="font-medium text-ink-900 mb-1.5">What happens next</p>
          <ol className="list-decimal list-inside space-y-1">
            <li>Open the verification link in your inbox.</li>
            <li>Sign in with your email and password.</li>
            <li>Choose a plan and pay on the Subscription page — that unlocks your dashboard.</li>
          </ol>
        </div>
        <p className="text-xs text-ink-400 mt-5">
          Didn't get the email? Check your spam folder, or{" "}
          <button
            type="button"
            onClick={handleResendVerification}
            disabled={resendState !== "idle"}
            className="font-medium text-primary-700 hover:underline disabled:no-underline disabled:text-ink-400"
          >
            {resendState === "sending"
              ? "sending…"
              : resendState === "sent"
              ? "link sent"
              : "resend the verification link"}
          </button>
          .
        </p>

        <Link to="/login" className="inline-block mt-4 text-sm font-medium text-primary-700 hover:underline">
          Go to sign in
        </Link>
      </div>
    );
  }

  if (clientSubmitted) {
    return (
      <div className="text-center py-4">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-gold-100 text-primary-800">
          <Clock size={26} />
        </div>
        <h2 className="font-display text-2xl font-bold text-ink-900 mt-5">Registration submitted</h2>
        <span className="block h-0.5 w-10 bg-gold-400 mt-3 mx-auto" />
        <p className="text-sm text-ink-500 mt-4 max-w-sm mx-auto">
          Thanks, <strong className="text-ink-700">{values.name.split(" ")[0]}</strong> — your Corporate Client account
          for <strong className="text-ink-700">{values.company}</strong> is now waiting on Admin review.
        </p>
        <div className="mt-5 rounded-lg border border-border bg-cream-50 p-4 text-left text-sm text-ink-600 max-w-sm mx-auto">
          <p className="font-medium text-ink-900 mb-1.5 flex items-center gap-1.5"><ShieldCheck size={15} className="text-primary-700" /> What happens next</p>
          <p>Our team verifies your company details before granting access. You'll get an email once your account is
          approved — this usually takes 1–2 business days. You won't be able to sign in until then.</p>
        </div>
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h2 className="font-display text-2xl font-bold text-ink-900">Create your account</h2>
      <span className="block h-0.5 w-10 bg-gold-400 mt-3" />
      <p className="text-sm text-ink-500 mt-3">
        {isStudent ? "Register and verify your email — you'll pick a plan after signing in." : "Register your company to review talent and project demos."}
      </p>

      {/* ---------------- account details (both roles) ---------------- */}
      <form onSubmit={submitDetails} className="mt-1 flex flex-col gap-4" noValidate>
        <div>
          <FieldLabel required>Register as</FieldLabel>
          <div className="mt-1.5 grid grid-cols-2 gap-3">
            {ROLE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setRole(opt.value)}
                className={
                  "flex flex-col items-start gap-1.5 rounded-lg border p-3 text-left transition-all duration-200 " +
                  (role === opt.value
                    ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100"
                    : "border-border hover:border-primary-300")
                }
              >
                <opt.icon size={18} className={role === opt.value ? "text-primary-700" : "text-ink-400"} />
                <span className="text-sm font-semibold text-ink-900">{opt.label}</span>
                <span className="text-xs text-ink-400 leading-snug">{opt.blurb}</span>
              </button>
            ))}
          </div>

        </div>

        <div>
          <FieldLabel required>{isStudent ? "Full name" : "Contact person name"}</FieldLabel>
          <div className="mt-1.5">
            <IconInput name="name" autoComplete="name" icon={User} required placeholder="Full name" value={values.name} onChange={set("name")} error={errors.name} />
          </div>
          <FieldError>{errors.name}</FieldError>
        </div>

        {isStudent && (
          <div>
            <Select
              label="Learning Track"
              required
              placeholder="Select track"
              options={[
                { value: "Full-Stack Development", label: "Full-Stack Development" },
                { value: "Data Analytics", label: "Data Analytics" },
                { value: "Product Design", label: "Product Design" },
                { value: "Backend Engineering", label: "Backend Engineering" },
              ]}
              value={values.track}
              onChange={set("track")}
              error={errors.track}
            />
          </div>
        )}

        {!isStudent && (
          <div>
            <FieldLabel required>Company name</FieldLabel>
            <div className="mt-1.5">
              <IconInput name="company" autoComplete="organization" icon={Building2} required placeholder="Company name" value={values.company} onChange={set("company")} error={errors.company} />
            </div>
            <FieldError>{errors.company}</FieldError>
          </div>
        )}

        <div className="grid sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel required>Email address</FieldLabel>
            <div className="mt-1.5">
              <IconInput name="email" autoComplete="email" icon={Mail} type="email" required placeholder="you@example.com" value={values.email} onChange={set("email")} error={errors.email} />
            </div>
            <FieldError>{errors.email}</FieldError>
          </div>
          <div>
            <FieldLabel required>Phone number</FieldLabel>
            <div className="mt-1.5">
              <IconInput name="phone" autoComplete="tel" icon={Phone} required placeholder="+91 98765 43210" value={values.phone} onChange={set("phone")} error={errors.phone} />
            </div>
            <FieldError>{errors.phone}</FieldError>
          </div>
        </div>

        <div className="grid sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel required>Password</FieldLabel>
            <div className="mt-1.5">
              <IconInput
                name="password" autoComplete="new-password" icon={Lock}
                type={showPassword ? "text" : "password"} required
                value={values.password} onChange={set("password")} error={errors.password}
                trailing={
                  <button type="button" onClick={() => setShowPassword((v) => !v)} aria-label={showPassword ? "Hide password" : "Show password"} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors">
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
              />
            </div>
            {errors.password ? <FieldError>{errors.password}</FieldError> : <FieldHint>At least 8 characters, one uppercase, one number.</FieldHint>}
          </div>
          <div>
            <FieldLabel required>Confirm password</FieldLabel>
            <div className="mt-1.5">
              <IconInput
                name="confirmPassword" autoComplete="new-password" icon={Lock}
                type={showConfirm ? "text" : "password"} required
                value={values.confirmPassword} onChange={set("confirmPassword")} error={errors.confirmPassword}
                trailing={
                  <button type="button" onClick={() => setShowConfirm((v) => !v)} aria-label={showConfirm ? "Hide password" : "Show password"} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600 transition-colors">
                    {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                }
              />
            </div>
            <FieldError>{errors.confirmPassword}</FieldError>
          </div>
        </div>

        <label className="flex items-center gap-2.5 text-sm text-ink-600 cursor-pointer select-none w-fit">
          <input type="checkbox" name="whatsappNotifications" checked={values.whatsappNotifications} onChange={set("whatsappNotifications")} className="h-4 w-4 rounded border-border text-primary-700 focus:ring-primary-200" />
          <WhatsAppIcon size={18} />
          <span className="font-medium text-ink-700">WhatsApp notifications</span>
        </label>

        <label className="flex items-start gap-2.5 text-sm text-ink-600 cursor-pointer select-none">
          <input type="checkbox" checked={values.agree} onChange={set("agree")} className="mt-0.5 h-4 w-4 rounded border-border text-primary-700 focus:ring-primary-200" />
          <span>
            I agree to the{" "}
            <button type="button" onClick={() => setLegalDoc("terms")} className="text-primary-700 hover:underline">Terms of Service</button>{" "}
            and{" "}
            <button type="button" onClick={() => setLegalDoc("privacy")} className="text-primary-700 hover:underline">Privacy Policy</button>
          </span>
        </label>
        <FieldError>{errors.agree}</FieldError>

        <Button type="submit" fullWidth loading={submitting} icon={UserPlus}>
          {isStudent ? "Create account" : "Submit for Admin approval"}
        </Button>

        {isStudent && (
          <>
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
          </>
        )}
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-primary-700 hover:underline">Sign in</Link>
      </p>

      <LegalModal doc={legalDoc} onClose={() => setLegalDoc(null)} />
    </div>
  );
}
