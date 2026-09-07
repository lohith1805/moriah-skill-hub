import { useState } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { Lock, CheckCircle2 } from "lucide-react";
import Button from "../../components/ui/Button";
import { Input } from "../../components/ui/FormField";
import { resetPassword } from "../../services/authService";
import { validateForm, required } from "../../utils/validators";

// Landed on from the emailed reset link: /reset-password?token=...
export default function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") || "";

  const [values, setValues] = useState({ password: "", confirm: "" });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const set = (f) => (e) => setValues((v) => ({ ...v, [f]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const v = validateForm(values, { password: [required], confirm: [required] });
    if (!v.password && values.password.length < 8) v.password = "Use at least 8 characters.";
    if (!v.confirm && values.confirm !== values.password) v.confirm = "Passwords do not match.";
    setErrors(v);
    if (Object.keys(v).length) return;

    setLoading(true);
    try {
      await resetPassword(token, values.password);
      setDone(true);
      setTimeout(() => navigate("/login", { replace: true }), 1800);
    } catch (err) {
      setErrors({ password: err.message || "This reset link is invalid or has expired." });
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="text-center">
        <h2 className="font-display text-xl font-bold text-ink-900">Invalid reset link</h2>
        <p className="text-sm text-ink-500 mt-2">This link is missing its token. Request a new one.</p>
        <Link to="/forgot-password" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Request a new reset link
        </Link>
      </div>
    );
  }

  if (done) {
    return (
      <div className="text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-success-50">
          <CheckCircle2 className="text-success-600" size={24} />
        </div>
        <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Password updated</h2>
        <p className="text-sm text-ink-500 mt-2">Taking you to the sign-in page…</p>
      </div>
    );
  }

  return (
    <div>
      <h2 className="font-display text-2xl font-bold text-ink-900">Choose a new password</h2>
      <p className="text-sm text-ink-500 mt-1.5">Enter a new password for your account.</p>

      <form onSubmit={submit} className="mt-7 flex flex-col gap-4" noValidate>
        <Input
          label="New password"
          type="password"
          required
          placeholder="At least 8 characters"
          value={values.password}
          onChange={set("password")}
          error={errors.password}
        />
        <Input
          label="Confirm new password"
          type="password"
          required
          value={values.confirm}
          onChange={set("confirm")}
          error={errors.confirm}
        />
        <Button type="submit" fullWidth loading={loading} icon={Lock}>
          Update password
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        <Link to="/login" className="font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </p>
    </div>
  );
}
