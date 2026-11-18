import { Box, CircularProgress, Typography } from '@mui/material';
import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useApi } from './api/ApiContext';
import { UserInfo } from './api/client';

interface AuthState {
  user: UserInfo | null;
  loading: boolean;
  setUser: (u: UserInfo | null) => void;
  signOff: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const api = useApi();
  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, [api]);

  const signOff = useCallback(async () => {
    try {
      await api.csrf();
      await api.logout();
    } finally {
      setUser(null);
    }
  }, [api]);

  return <AuthContext.Provider value={{ user, loading, setUser, signOff }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

/** Admin-only routes (CardDemo user type 'A' is routed to COADM01C). */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <CircularProgress aria-label="loading" />;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.roles.includes('ADMIN')) {
    return (
      <Box p={4}>
        <Typography role="alert" color="error">
          Access restricted to administrators
        </Typography>
      </Box>
    );
  }
  return <>{children}</>;
}
