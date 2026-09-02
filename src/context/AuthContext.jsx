import { createContext, useContext, useMemo, useState, useEffect, useCallback } from "react";
import { useDispatch } from "react-redux";
import { setCredentials, logout as logoutAction } from "../app/authSlice";
import {
  login as loginRequest,
  acceptInvite as acceptInviteRequest,
  verifyTwoFactor as verifyTwoFactorRequest,
  registerStudent,
  registerClient,
  getMe,
  logout as logoutRequest,
  clearSession,
  getPersistedUser,
} from "../services/authService";
import { tokenStore } from "../services/apiClient";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const dispatch = useDispatch();
  const [user, setUser] = useState(getPersistedUser());
  const [loading, setLoading] = useState(false);
  const [hydrating, setHydrating] = useState(tokenStore.hasSession && !getPersistedUser());

  const applyUser = useCallback(
    (u) => {
      setUser(u);
      if (u) dispatch(setCredentials(u));
      else dispatch(logoutAction());
    },
    [dispatch]
  );

  // On load: if there's a token but no cached user (or to refresh a stale one),
  // hydrate from GET /users/me. No more localStorage polling.
  useEffect(() => {
    let cancelled = false;
    if (!tokenStore.hasSession) {
      if (user) applyUser(null);
      setHydrating(false);
      return;
    }
    (async () => {
      try {
        const fresh = await getMe();
        if (!cancelled) applyUser(fresh);
      } catch {
        if (!cancelled) {
          clearSession();
          applyUser(null);
        }
      } finally {
        if (!cancelled) setHydrating(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // run once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Returns { user } on success, or { twoFactorRequired, twoFactorSetupRequired,
  // challengeToken } when the caller must complete a 2FA step first.
  const login = async (credentials) => {
    setLoading(true);
    try {
      const result = await loginRequest(credentials);
      if (result.user) applyUser(result.user);
      return result;
    } finally {
      setLoading(false);
    }
  };

  const completeTwoFactor = async ({ challengeToken, totpCode }) => {
    setLoading(true);
    try {
      const fresh = await verifyTwoFactorRequest({ challengeToken, totpCode });
      applyUser(fresh);
      return fresh;
    } finally {
      setLoading(false);
    }
  };

  // Decision D2 (Hybrid): student register -> verify email -> login -> checkout.
  // No session is created here; the wizard shows a "verify your email" step next.
  const register = async (payload) => {
    setLoading(true);
    try {
      return await registerStudent(payload);
    } finally {
      setLoading(false);
    }
  };

  // Corporate client: PENDING_APPROVAL, cannot log in until an ADMIN approves.
  const registerClientAccount = async (payload) => {
    setLoading(true);
    try {
      return await registerClient(payload);
    } finally {
      setLoading(false);
    }
  };

  // Invited staff finish setting a password. Like login, this may bounce into a
  // 2FA step (mandatory for ADMIN / HR_MANAGER).
  const acceptInvite = async (token, password) => {
    setLoading(true);
    try {
      const result = await acceptInviteRequest(token, password);
      if (result.user) applyUser(result.user);
      return result;
    } finally {
      setLoading(false);
    }
  };

  const refreshUser = useCallback(async () => {
    try {
      const fresh = await getMe();
      applyUser(fresh);
      return fresh;
    } catch {
      return null;
    }
  }, [applyUser]);

  const logout = async () => {
    try {
      await logoutRequest();
    } finally {
      applyUser(null);
    }
  };

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: !!user,
      loading,
      hydrating,
      login,
      completeTwoFactor,
      register,
      registerClientAccount,
      acceptInvite,
      refreshUser,
      logout,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user, loading, hydrating]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
