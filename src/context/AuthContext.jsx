import { createContext, useContext, useMemo, useState, useEffect } from "react";
import { useDispatch } from "react-redux";
import { setCredentials, logout as logoutAction } from "../app/authSlice";
import { login as loginRequest, registerStudent, registerClient, acceptInvite as acceptInviteRequest, persistSession, clearSession, getPersistedUser } from "../services/authService";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const dispatch = useDispatch();
  const [user, setUser] = useState(getPersistedUser());
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const interval = setInterval(() => {
      const latest = getPersistedUser();
      if (latest) {
        if (latest.batch !== user?.batch || latest.accountStatus !== user?.accountStatus || latest.name !== user?.name) {
          setUser(latest);
          dispatch(setCredentials(latest));
        }
      } else if (user) {
        setUser(null);
        dispatch(logoutAction());
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [user, dispatch]);

  const login = async (credentials) => {
    setLoading(true);
    try {
      const { user: loggedInUser, token } = await loginRequest(credentials);
      persistSession(loggedInUser, token);
      setUser(loggedInUser);
      dispatch(setCredentials(loggedInUser));
      return loggedInUser;
    } finally {
      setLoading(false);
    }
  };

  // Student flow: payment already succeeded in the wizard, so the account is
  // active immediately — log them straight in.
  const register = async (payload) => {
    setLoading(true);
    try {
      const { user: newUser, token } = await registerStudent(payload);
      persistSession(newUser, token);
      setUser(newUser);
      dispatch(setCredentials(newUser));
      return newUser;
    } finally {
      setLoading(false);
    }
  };

  // Client flow: account is created but locked pending Admin/BA approval —
  // do NOT log them in or persist a session.
  const registerClientAccount = async (payload) => {
    setLoading(true);
    try {
      const { user: newUser } = await registerClient(payload);
      return newUser;
    } finally {
      setLoading(false);
    }
  };

  // Invited-staff flow: they finish setting a password on /accept-invite,
  // which activates their account and logs them straight in.
  const acceptInvite = async (token, password) => {
    setLoading(true);
    try {
      const { user: activatedUser, token: sessionToken } = await acceptInviteRequest(token, password);
      persistSession(activatedUser, sessionToken);
      setUser(activatedUser);
      dispatch(setCredentials(activatedUser));
      return activatedUser;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    clearSession();
    setUser(null);
    dispatch(logoutAction());
  };

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, loading, login, register, registerClientAccount, acceptInvite, logout }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
