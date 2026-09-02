import { useEffect, useState, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { verifyEmail } from "../../services/authService";

// Landed on from the emailed verification link: /verify-email?token=...
export default function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [state, setState] = useState(token ? "verifying" : "missing");
  const [message, setMessage] = useState("");
  const ran = useRef(false);

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
          <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
            Back to sign in
          </Link>
        </>
      )}
    </div>
  );
}
