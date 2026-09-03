import { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  UserPlus, User, Mail, Phone, Lock, Eye, EyeOff,
  GraduationCap, Building2, Check, CreditCard, ShieldCheck, Clock, ArrowLeft, ArrowRight,
} from "lucide-react";
import Button from "../../components/ui/Button";
import { Input, Select } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { validateForm, required, isEmail, isPhone, passwordStrength, matches } from "../../utils/validators";
import { ROLES, ROLE_LABELS, SUBSCRIPTION_PLANS, CURRENCY } from "../../utils/constants";
import LegalModal from "../../components/legal/LegalModal";
import RazorpayMockModal from "../../components/ui/RazorpayMockModal";

const loadRazorpayScript = () => {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
};

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

/* --------------------------------- Stepper --------------------------------- */

function Stepper({ steps, current }) {
  return (
    <div className="flex items-center gap-2 mb-6">
      {steps.map((label, i) => {
        const stepNum = i + 1;
        const isDone = stepNum < current;
        const isActive = stepNum === current;
        return (
          <div key={label} className="flex items-center gap-2 flex-1">
            <div className="flex items-center gap-2 flex-1">
              <div
                className={
                  "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors " +
                  (isDone
                    ? "bg-primary-700 text-white"
                    : isActive
                    ? "bg-gold-400 text-primary-900"
                    : "bg-cream-200 text-ink-400")
                }
              >
                {isDone ? <Check size={13} /> : stepNum}
              </div>
              <span className={"text-xs font-medium hidden sm:block " + (isActive ? "text-ink-900" : "text-ink-400")}>
                {label}
              </span>
            </div>
            {stepNum < steps.length && <div className={"h-0.5 flex-1 rounded " + (isDone ? "bg-primary-700" : "bg-cream-200")} />}
          </div>
        );
      })}
    </div>
  );
}

/* ---------------------------------- Page ------------------------------------ */

export default function Register() {
  const { notify } = useToast();
  const navigate = useNavigate();
  const { register, registerClientAccount } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [searchParams] = useSearchParams();
  const leadId = searchParams.get("leadId");

  const [role, setRole] = useState(ROLES.STUDENT);
  const [step, setStep] = useState(1); // 1 = details (plan/payment now happen post-login on /student/subscription)
  const [clientSubmitted, setClientSubmitted] = useState(false);
  const [studentSubmitted, setStudentSubmitted] = useState(false);
  const [gateway, setGateway] = useState("Razorpay");

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
  // know their details were carried over, and mark the originating lead as
  // reached-the-form so LeadGen can see the funnel is progressing.
  useEffect(() => {
    if (!leadId) return;
    notify("Your details were pre-filled from your enquiry — just finish the plan & payment steps.", {
      type: "info",
      title: "Welcome back",
    });
  }, [leadId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Marks the originating CRM lead as "Enrolled" and records the plan &
  // price the customer actually selected and paid for — this is the single
  // source of truth for deal value / pipeline revenue. Lead Gen never sets
  // this manually; it's written automatically the moment payment succeeds.
  function markLeadEnrolled(newUser) {
    if (!leadId) return;
    try {
      const raw = localStorage.getItem("msh_crm_leads");
      if (!raw) return;
      const leads = JSON.parse(raw);
      const updated = leads.map((l) =>
        l.id === leadId
          ? {
              ...l,
              // Fill in whatever contact details the lead was still missing —
              // by the time they've paid we have a verified phone/email from
              // the form itself, so backfill rather than leaving these blank.
              phone: l.phone || values.phone || newUser.phone || l.phone,
              email: l.email || values.email || newUser.email || l.email,
              stage: "Enrolled",
              convertedUserId: newUser.id,
              selectedPlanCode: selectedPlan?.code || l.selectedPlanCode,
              selectedPlanName: selectedPlan?.name || l.selectedPlanName,
              dealValue: finalPrice || l.dealValue,
              couponCode: appliedCoupon?.code || null,
              paidAt: new Date().toISOString(),
              updatedAt: new Date().toISOString().slice(0, 10)
            }
          : l
      );
      localStorage.setItem("msh_crm_leads", JSON.stringify(updated));
    } catch (err) {
      console.warn("[Register] Could not link lead to new account:", err.message);
    }
  }
  const [planCode, setPlanCode] = useState("");

  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [legalDoc, setLegalDoc] = useState(null);

  // Coupon (Step 3) — reads the same "msh_coupons" list Admin manages under
  // Plans & Pricing. Shown as a dropdown of currently valid coupons (active
  // status AND not past their expiry date) rather than free-text entry, so
  // a student picks from what's actually available instead of guessing codes.
  const [availableCoupons, setAvailableCoupons] = useState([]);
  const [selectedCouponCode, setSelectedCouponCode] = useState("");
  const [appliedCoupon, setAppliedCoupon] = useState(null);
  const [couponError, setCouponError] = useState("");

  useEffect(() => {
    let coupons = [];
    try {
      coupons = JSON.parse(localStorage.getItem("msh_coupons") || "[]");
    } catch (e) {
      coupons = [];
    }
    const today = new Date().toISOString().slice(0, 10);
    const valid = coupons.filter((c) => c.status === "active" && (!c.expiryDate || c.expiryDate >= today));
    setAvailableCoupons(valid);
  }, []);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));

  const selectedPlan = SUBSCRIPTION_PLANS.find((p) => p.code === planCode);
  const isStudent = role === ROLES.STUDENT;

  // A coupon's discount is stored by Admin as free text like "25% off" or
  // "10% off (B2B)" — pull the leading percentage out of that string.
  const parseDiscountPercent = (discountText) => {
    const match = /(\d+(?:\.\d+)?)\s*%/.exec(discountText || "");
    return match ? Number(match[1]) : 0;
  };

  const applySelectedCoupon = (code) => {
    setSelectedCouponCode(code);
    setCouponError("");
    if (!code) {
      setAppliedCoupon(null);
      return;
    }
    const match = availableCoupons.find((c) => c.code === code);
    if (!match) {
      setAppliedCoupon(null);
      setCouponError("This coupon is no longer available.");
      return;
    }
    const percent = parseDiscountPercent(match.discount);
    if (!percent) {
      setAppliedCoupon(null);
      setCouponError("This coupon can't be applied automatically — contact support.");
      return;
    }
    setAppliedCoupon({ code: match.code, discount: match.discount, percent, expiryDate: match.expiryDate });
    notify(`Coupon "${match.code}" applied — ${match.discount}.`, { type: "success" });
  };

  const removeCoupon = () => {
    setAppliedCoupon(null);
    setSelectedCouponCode("");
    setCouponError("");
  };

  const discountedPrice = (price) => {
    if (!appliedCoupon || !price) return price;
    return Math.max(0, Math.round(price * (1 - appliedCoupon.percent / 100)));
  };

  const finalPrice = selectedPlan ? discountedPrice(selectedPlan.price) : 0;

  /* ---- Step 1: account details ---- */
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
      });
      setClientSubmitted(true);
    } catch (err) {
      notify(err.message || "Registration failed. Please try again.", { type: "error", title: "Something went wrong" });
    } finally {
      setSubmitting(false);
    }
  };

  /* ---- Step 2: plan selection (student) ---- */
  const confirmPlan = () => {
    if (!planCode) {
      notify("Please select a subscription plan to continue.", { type: "warning" });
      return;
    }
    setStep(3);
  };

  /* ---- Step 3: Razorpay payment + final account creation (student) ---- */
  const handleRazorpayPayment = (e) => {
    e.preventDefault();
    setSubmitting(true);
    setShowPaymentModal(true);
  };

  const handlePaymentSuccess = async (response) => {
    setShowPaymentModal(false);
    setSubmitting(true);
    try {
      const newUser = await register({
        name: values.name,
        email: values.email,
        phone: values.phone,
        password: values.password,
        track: values.track,
        notifications: { email: true, whatsapp: values.whatsappNotifications, desktop: true },
        subscription: {
          planCode: selectedPlan.code,
          planName: selectedPlan.name,
          price: finalPrice,
          originalPrice: selectedPlan.price,
          couponCode: appliedCoupon?.code || null,
          model: selectedPlan.model,
          paidAt: new Date().toISOString(),
          paymentId: response.razorpay_payment_id || `rzp_${Date.now()}`,
          gateway: gateway,
        },
      });
      markLeadEnrolled(newUser);
      notify(`Payment successful. Welcome to Moriah Skill Hub, ${newUser.name.split(" ")[0]}!`, { type: "success", title: "Account created" });
      navigate("/student/dashboard", { replace: true });
    } catch (err) {
      notify(err.message || "Registration failed. Please try again.", { type: "error", title: "Something went wrong" });
    } finally {
      setSubmitting(false);
    }
  };

  const handlePaymentClose = () => {
    setShowPaymentModal(false);
    setSubmitting(false);
  };

  const handleGoogle = () => {
    notify("Google sign-up isn't connected yet.", { type: "info", title: "Coming soon" });
  };

  /* ------------------------------ Client: success screen ------------------------------ */
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
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
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

      {/* ---------------- STEP 1: account details (both roles) ---------------- */}
      {step === 1 && (
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
              <button type="button" onClick={handleGoogle} className="flex items-center justify-center gap-2 rounded-lg border border-border py-3 text-sm font-medium text-ink-700 transition-all duration-200 hover:bg-cream-50 hover:-translate-y-0.5">
                <GoogleIcon size={16} />
                Sign up with Google
              </button>
            </>
          )}
        </form>
      )}

      {/* ---------------- STEP 2: choose plan (student only) ---------------- */}
      {step === 2 && isStudent && (
        <div className="mt-1 flex flex-col gap-4">
          <div className="flex flex-col gap-3">
            {SUBSCRIPTION_PLANS.map((plan) => (
              <button
                key={plan.code}
                type="button"
                onClick={() => setPlanCode(plan.code)}
                className={
                  "flex items-center justify-between gap-3 rounded-lg border p-4 text-left transition-all duration-200 " +
                  (planCode === plan.code ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100" : "border-border hover:border-primary-300")
                }
              >
                <div>
                  <p className="text-sm font-semibold text-ink-900">{plan.name}</p>
                  <p className="text-xs text-ink-400 mt-0.5">{plan.model}</p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-bold text-ink-900">{CURRENCY(plan.price)}</span>
                  <span className={"flex h-5 w-5 items-center justify-center rounded-full border-2 " + (planCode === plan.code ? "border-primary-700 bg-primary-700" : "border-border")}>
                    {planCode === plan.code && <Check size={12} className="text-white" />}
                  </span>
                </div>
              </button>
            ))}
          </div>

          <div className="flex gap-3">
            <Button type="button" variant="secondary" icon={ArrowLeft} onClick={() => setStep(1)}>Back</Button>
            <Button type="button" fullWidth icon={ArrowRight} onClick={confirmPlan}>Continue to payment</Button>
          </div>
        </div>
      )}

      {/* ---------------- STEP 3: Razorpay Payment (student only) ---------------- */}
      {step === 3 && isStudent && (
        <form onSubmit={handleRazorpayPayment} className="mt-1 flex flex-col gap-5" noValidate>
          <div className="rounded-lg border border-border bg-cream-50 p-4 flex flex-col gap-3">
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div>
                <p className="text-xs text-ink-400 font-medium">Selected plan</p>
                <p className="text-sm font-semibold text-ink-900">{selectedPlan?.name}</p>
              </div>
              <div className="text-right">
                {appliedCoupon && selectedPlan && (
                  <p className="text-xs text-ink-400 line-through">{CURRENCY(selectedPlan.price)}</p>
                )}
                <p className="text-base font-bold text-primary-800">{selectedPlan ? CURRENCY(finalPrice) : ""}</p>
              </div>
            </div>

            <div className="text-xs text-ink-600 flex flex-col gap-1">
              <p><span className="font-semibold text-ink-800">Subscriber Name:</span> {values.name}</p>
              <p><span className="font-semibold text-ink-800">Email:</span> {values.email}</p>
              <p><span className="font-semibold text-ink-800">Phone:</span> {values.phone}</p>
            </div>
          </div>

          <div className="rounded-lg border border-border p-4 flex flex-col gap-2">
            <p className="text-xs font-semibold text-ink-800">Have a coupon code?</p>
            {appliedCoupon ? (
              <div className="flex items-center justify-between rounded-md bg-primary-50 border border-primary-100 px-3 py-2">
                <p className="text-xs text-primary-800">
                  <span className="font-semibold">{appliedCoupon.code}</span> applied — {appliedCoupon.discount}
                  {appliedCoupon.expiryDate ? ` (valid till ${appliedCoupon.expiryDate})` : ""}
                </p>
                <button type="button" onClick={removeCoupon} className="text-xs font-medium text-ink-500 hover:text-primary-700">
                  Remove
                </button>
              </div>
            ) : availableCoupons.length > 0 ? (
              <Select
                placeholder="Select a coupon"
                value={selectedCouponCode}
                onChange={(e) => applySelectedCoupon(e.target.value)}
                error={couponError}
                options={availableCoupons.map((c) => ({
                  value: c.code,
                  label: `${c.code} — ${c.discount} — valid till ${c.expiryDate || "—"}`,
                }))}
              />
            ) : (
              <p className="text-xs text-ink-400">No coupons available right now.</p>
            )}
          </div>

          <div className="rounded-lg border border-border p-4 flex flex-col gap-3">
            <p className="text-xs font-semibold text-ink-800">Select Payment Gateway</p>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setGateway("Razorpay")}
                className={`flex flex-col items-center gap-2 p-3 rounded-lg border text-center transition-all ${
                  gateway === "Razorpay"
                    ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100"
                    : "border-border hover:border-primary-300"
                }`}
              >
                <span className="text-sm font-bold text-primary-900 font-display">Razorpay</span>
                <span className="text-[10px] text-ink-400">UPI, Cards, Netbanking</span>
              </button>
              <button
                type="button"
                onClick={() => setGateway("Stripe")}
                className={`flex flex-col items-center gap-2 p-3 rounded-lg border text-center transition-all ${
                  gateway === "Stripe"
                    ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100"
                    : "border-border hover:border-primary-300"
                }`}
              >
                <span className="text-sm font-bold text-primary-950 font-display">Stripe</span>
                <span className="text-[10px] text-ink-400">Card Payment (Test Mode)</span>
              </button>
            </div>
          </div>

          <div className="rounded-lg bg-primary-50/50 border border-primary-100 p-4 text-xs text-ink-600 flex flex-col gap-2">
            <p className="font-semibold text-primary-800 flex items-center gap-1.5">
              <ShieldCheck size={14} className="text-primary-700" />
              Secure Checkout via {gateway}
            </p>
            <p>
              {gateway === "Razorpay"
                ? 'By clicking "Proceed to Pay", you will open the secure Razorpay payment gateway popup. You can complete the transaction using UPI, Cards, Netbanking, or Wallets in test mode.'
                : 'By clicking "Proceed to Pay", you will open the secure Stripe checkout form. You can complete the transaction using Credit/Debit Cards in test mode.'}
            </p>
          </div>

          <div className="flex gap-3 mt-2">
            <Button type="button" variant="secondary" icon={ArrowLeft} onClick={() => setStep(2)}>Back</Button>
            <Button type="submit" fullWidth loading={submitting} icon={CreditCard}>
              {selectedPlan ? `Proceed to Pay ${CURRENCY(finalPrice)}` : "Proceed to Pay"}
            </Button>
          </div>
        </form>
      )}

      <p className="mt-6 text-center text-sm text-ink-500">
        Already have an account?{" "}
        <Link to="/login" className="font-medium text-primary-700 hover:underline">Sign in</Link>
      </p>

      <RazorpayMockModal
        isOpen={showPaymentModal}
        onClose={handlePaymentClose}
        onSuccess={handlePaymentSuccess}
        amount={finalPrice}
        planName={selectedPlan?.name}
        userName={values.name}
        userEmail={values.email}
        userPhone={values.phone}
        gateway={gateway}
      />

      <LegalModal doc={legalDoc} onClose={() => setLegalDoc(null)} />
    </div>
  );
}