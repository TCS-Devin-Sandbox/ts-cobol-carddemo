import { createContext, ReactNode, useCallback, useContext, useMemo, useState } from 'react';
import { setAuthToken } from '../api/client';

export interface Session {
  token: string;
  userId: string;
  userType: 'A' | 'U';
}

interface AuthValue {
  session: Session | null;
  signIn: (session: Session) => void;
  signOut: () => void;
}

const STORAGE_KEY = 'carddemo.session';
const AuthContext = createContext<AuthValue | null>(null);

function restore(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children, initial }: { children: ReactNode; initial?: Session | null }) {
  const [session, setSession] = useState<Session | null>(() => {
    const s = initial !== undefined ? initial : restore();
    setAuthToken(s?.token ?? null);
    return s;
  });

  const signIn = useCallback((s: Session) => {
    setAuthToken(s.token);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    setSession(s);
  }, []);

  const signOut = useCallback(() => {
    setAuthToken(null);
    sessionStorage.removeItem(STORAGE_KEY);
    setSession(null);
  }, []);

  const value = useMemo(() => ({ session, signIn, signOut }), [session, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
