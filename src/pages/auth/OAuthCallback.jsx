import { useEffect, useState, useRef } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Loader2, XCircle } from "lucide-react";
import { tokenStore } from "../../services/apiClient";
import { getMe } from "../../services/authService";
import { useAuth } from "../../context/AuthContext";
import { ROLE_HOME } from "../../utils/roleAccess";

// The backend's OAuth2 success/failure handlers redirect here with the result
// in the URL *fragment* (never the query string — a fragment is not sent to any
// server and does not land in access logs / Referer headers):
//   /auth/oauth/callback#accessToken=...&refreshToken=...&expiresIn=3600
//   /auth/oauth/callback#twoFactorRequired=true&challengeToken=...
//   /auth/oauth/callback#error=oauth_failed
function readHashParams() {
  const raw = typeof window !== "undefined" ? window.location.hash.replace(/^#/, "") : "";
  return new URLSearchParams(raw);
}

export default function OAuthCallback() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const params = useRef(readHashParams()).current;
  const [error, setError] = useState(params.get("error"));
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    if (params.get("error")) return;

    // A 2FA-mandatory account (admin / hr) can't finish OAuth here — the SPA
    // has no OAuth 2FA screen, so bounce to /login to complete the challenge.
    if (params.get("twoFactorRequired") === "true") {
      setError("TWO_FACTOR_REQUIRED");
      return;
    }

    const accessToken = params.get("accessToken") || params.get("token");
    const refreshToken = params.get("refreshToken");
    const expiresIn = Number(params.get("expiresIn") || params.get("expiresInSeconds") || 3600);
    if (!accessToken) {
      setError("MISSING_TOKENS");
      return;
    }
    tokenStore.set({ accessToken, refreshToken, expiresInSeconds: expiresIn });
    // Scrub the tokens out of the address bar / history now that they're stored.
    if (typeof window !== "undefined" && window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    (async () => {
      try {
        const user = (await refreshUser()) || (await getMe());
        navigate(ROLE_HOME[user.role] || "/", { replace: true });
      } catch {
        setError("SESSION_SETUP_FAILED");
      }
    })();
  }, [params, navigate, refreshUser]);

  if (error) {
    return (
      <div className="text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-error-50">
          <XCircle className="text-error-600" size={24} />
        </div>
        <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Could not complete sign-in</h2>
        <p className="text-sm text-ink-500 mt-2">
          {error === "OAUTH_PROFILE_INCOMPLETE"
            ? "That provider did not share enough profile information. Try email sign-in."
            : error === "TWO_FACTOR_REQUIRED"
            ? "This account needs two-factor authentication. Sign in with your email and password to complete it."
            : "Something went wrong finishing the OAuth sign-in."}
        </p>
        <Link to="/login" className="inline-block mt-6 text-sm font-medium text-primary-700 hover:underline">
          Back to sign in
        </Link>
      </div>
    );
  }

  return (
    <div className="text-center">
      <Loader2 className="mx-auto animate-spin text-primary-600" size={28} />
      <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Finishing sign-in…</h2>
    </div>
  );
}
