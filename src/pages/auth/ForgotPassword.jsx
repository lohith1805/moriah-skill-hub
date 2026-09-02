import { useState } from "react";
import { Link } from "react-router-dom";
import { Mail, CheckCircle2 } from "lucide-react";
import Button from "../../components/ui/Button";
import { Input } from "../../components/ui/FormField";
import { requestPasswordReset } from "../../services/authService";
import { validateForm, required, isEmail } from "../../utils/validators";

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const validation = validateForm({ email }, { email: [required, isEmail] });
    setError(validation.email || "");
    if (validation.email) return;

    setLoading(true);
    try {
      await requestPasswordReset(email);
      setSent(true);
    } finally {
      setLoading(false);
    }
  };

  if (sent) {
    return (
      <div className="text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-success-50">
          <CheckCircle2 className="text-success-600" size={24} />
        </div>
        <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Check your inbox</h2>
        <p className="text-sm text-ink-500 mt-2">
          If an account exists for <span className="font-medium text-ink-700">{email}</span>, we've sent a password reset link.
        </p>
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h2 className="font-display text-2xl font-bold text-ink-900">Reset your password</h2>
      <p className="text-sm text-ink-500 mt-1.5">Enter the email linked to your account and we'll send you a reset link.</p>

      <form onSubmit={submit} className="mt-7 flex flex-col gap-4" noValidate>
        <Input label="Email address" type="email" required placeholder="you@moriah.io" value={email} onChange={(e) => setEmail(e.target.value)} error={error} />
        <Button type="submit" fullWidth loading={loading} icon={Mail}>
          Send Reset Link
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
