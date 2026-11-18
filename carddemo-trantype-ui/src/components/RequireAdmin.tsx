import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { getSession } from '../api/client';

export function RequireAdmin({ children }: { children: ReactNode }) {
  const session = getSession();
  if (!session || !session.roles.includes('ADMIN')) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
