import { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider, Session } from '../auth/AuthContext';

export const adminSession: Session = { token: 't', userId: 'ADMIN001', userType: 'A' };

export function renderAt(path: string, element: ReactElement, session: Session | null = adminSession) {
  return render(
    <AuthProvider initial={session}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={path} element={element} />
          <Route path="/admin" element={<div>ADMIN MENU</div>} />
          <Route path="/login" element={<div>LOGIN PAGE</div>} />
          <Route path="/transaction-types/maintain" element={<div>CTTU PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}
