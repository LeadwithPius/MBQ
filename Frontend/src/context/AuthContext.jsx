import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api, clearSession, readSession, saveSession } from "../services/api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession());

  useEffect(() => {
    const expire = () => {
      clearSession();
      setSession(null);
    };
    window.addEventListener("mbq:unauthorized", expire);
    const timer = window.setInterval(() => {
      const current = readSession();
      if (!current) setSession(null);
    }, 15000);
    return () => {
      window.removeEventListener("mbq:unauthorized", expire);
      window.clearInterval(timer);
    };
  }, []);

  async function login(username, password) {
    const result = await api("/auth/login", {
      method: "POST",
      auth: false,
      body: { username, password },
    });
    const next = {
      accessToken: result.accessToken,
      tokenType: result.tokenType,
      expiresAt: Date.now() + result.expiresInMs,
      staff: result.staff,
    };
    saveSession(next);
    setSession(next);
    return next;
  }

  function logout() {
    clearSession();
    setSession(null);
  }

  const value = useMemo(() => ({ session, staff: session?.staff || null, login, logout }), [session]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
