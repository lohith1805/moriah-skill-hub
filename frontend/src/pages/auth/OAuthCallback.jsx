import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2 } from "lucide-react";
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

// Map a backend/provider error code to something a human should read on the
// sign-in screen. The default deliberately reads like a failed password login
// ("no account / try again") rather than exposing an internal code.
function friendlyAuthError(code) {
  switch (code) {
    case "OAUTH_PROFILE_INCOMPLETE":
      return "That provider didn't share enough profile information to sign you in. Try email sign-in instead.";
    case "ACCOUNT_SUSPENDED":
      return "This account is suspended. Contact support if you think that's a mistake.";
    case "TWO_FACTOR_REQUIRED":
      return "This account requires two-factor authentication. Sign in with your email and password to complete it.";
    case "oauth_callback_misrouted":
      return "Social sign-in isn't finishing correctly. The provider's redirect URL needs to be updated — use email sign-in for now.";
    case "access_denied":
      return "You cancelled the social sign-in before it finished.";
    case "MISSING_TOKENS":
    case "SESSION_SETUP_FAILED":
    case "INTERNAL_ERROR":
    case "oauth_failed":
    default:
      return "We couldn't sign you in with that provider. No account was found or the sign-in didn't complete — try again or use your email and password.";
  }
}

export default function OAuthCallback() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const params = useRef(readHashParams()).current;
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const bounceToLogin = (code) =>
      navigate("/login", { replace: true, state: { authError: friendlyAuthError(code) } });

    if (params.get("error")) {
      bounceToLogin(params.get("error"));
      return;
    }

    // A 2FA-mandatory account (admin / hr) can't finish OAuth here — the SPA
    // has no OAuth 2FA screen, so bounce to /login to complete the challenge.
    if (params.get("twoFactorRequired") === "true") {
      bounceToLogin("TWO_FACTOR_REQUIRED");
      return;
    }

    const accessToken = params.get("accessToken") || params.get("token");
    const refreshToken = params.get("refreshToken");
    const expiresIn = Number(params.get("expiresIn") || params.get("expiresInSeconds") || 3600);
    if (!accessToken) {
      bounceToLogin("MISSING_TOKENS");
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
        bounceToLogin("SESSION_SETUP_FAILED");
      }
    })();
  }, [params, navigate, refreshUser]);

  return (
    <div className="text-center">
      <Loader2 className="mx-auto animate-spin text-primary-600" size={28} />
      <h2 className="font-display text-xl font-bold text-ink-900 mt-4">Finishing sign-in…</h2>
    </div>
  );
}
