import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { Alert, Box } from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import { MSG } from '../logic/messages';

/** Only admin users (type A) reach the admin menu options 5/6 (COADM01C). */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  if (session.userType !== 'A') {
    return (
      <Box p={4}>
        <Alert severity="error" data-testid="admin-only">
          {MSG.ADMIN_ONLY}
        </Alert>
      </Box>
    );
  }
  return <>{children}</>;
}
