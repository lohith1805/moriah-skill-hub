import { useEffect, useState, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CheckCircle2, XCircle, Loader2, Mail, Send } from "lucide-react";
import { verifyEmail, resendVerificationEmail } from "../../services/authService";
import { isEmail } from "../../utils/validators";

// Landed on from the emailed verification link: /verify-email?token=...
export default function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [state, setState] = useState(token ? "verifying" : "missing");
  const [message, setMessage] = useState("");
  const ran = useRef(false);

  // Resend: this page usually has no email in context (it's reached from the
  // emailed link), so ask for one. The backend never reveals whether the address
  // matches an unverified account, so the confirmation copy stays deliberately vague.
  const [email, setEmail] = useState(params.get("email") || "");
  const [resendState, setResendState] = useState("idle"); // idle | sending | sent | error
  const [resendMessage, setResendMessage] = useState("");
  const resendBusy = resendState === "sending" || resendState === "sent";

  useEffect(() => {
    if (!token || ran.current) return;
    ran.current = true;
    verifyEmail(token)
      .then(() => setState("ok"))
      .catch((err) => {
        setState("error");
        setMessage(err.message || "This verification link is invalid or has expired.");
      });
  }, [token]);

  const handleResend = async (e) => {
    e.preventDefault();
    // isEmail() returns an error string when the value is INVALID, "" when valid.
    if (isEmail(email.trim())) {
      setResendState("error");
      setResendMessage("Enter a valid email address.");
      return;
    }
    setResendState("sending");
    setResendMessage("");
    try {
      await resendVerificationEmail(email.trim());
      setResendState("sent");
      setResendMessage(
        "If that account still needs verifying, a fresh link is on its way. It can take a minute to arrive — check your spam folder too."
      );
    } catch (err) {
      setResendState("error");
      setResendMessage(err.message || "Could not send a new link. Please try again in a moment.");
    }
  };

  return (
    <div className="text-center">
      {state === "verifying" && (
        <>
          <Loader2 className="mx-auto animate-spin text-primary-600" size={28} />
          <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Verifying your email…</h2>
        </>
      )}

      {state === "ok" && (
        <>
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-success-50">
            <CheckCircle2 className="text-success-600" size={24} />
          </div>
          <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Email verified</h2>
          <p className="text-sm text-ink-500 mt-2">Your account is ready. You can sign in now.</p>
          <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
            Go to sign in
          </Link>
        </>
      )}

      {(state === "error" || state === "missing") && (
        <>
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-error-50">
            <XCircle className="text-error-600" size={24} />
          </div>
          <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Verification failed</h2>
          <p className="text-sm text-ink-500 mt-2">
            {state === "missing" ? "This link is missing its token." : message}
          </p>

          <form onSubmit={handleResend} className="mx-auto mt-6 max-w-sm text-left">
            <p className="text-sm font-medium text-ink-900">Need a new link?</p>
            <div className="relative mt-2">
              <span className="absolute left-1.5 top-1/2 -translate-y-1/2 flex h-8 w-8 items-center justify-center rounded-md bg-cream-100 text-ink-500">
                <Mail size={15} />
              </span>
              <input
                type="email"
                required
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={resendBusy}
                className="w-full rounded-lg border border-border pl-12 pr-4 py-3 text-sm text-ink-900 outline-none transition-all duration-200 focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-cream-50"
                autoComplete="email"
              />
            </div>
            {resendMessage && (
              <p className={`mt-2 text-xs ${resendState === "error" ? "text-error-500" : "text-success-600"}`}>
                {resendMessage}
              </p>
            )}
            <button
              type="submit"
              disabled={resendBusy}
              className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-primary-700 py-3 text-sm font-semibold text-white transition-all duration-200 hover:bg-primary-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {resendState === "sending" ? (
                <>
                  <Loader2 size={16} className="animate-spin" /> Sending…
                </>
              ) : resendState === "sent" ? (
                <>
                  <CheckCircle2 size={16} /> Link sent
                </>
              ) : (
                <>
                  <Send size={16} /> Resend verification email
                </>
              )}
            </button>
          </form>

          <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
            Back to sign in
          </Link>
        </>
      )}
    </div>
  );
}
