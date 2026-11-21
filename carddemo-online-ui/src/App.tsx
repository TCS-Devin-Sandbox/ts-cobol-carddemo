import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin } from './components/RequireAdmin';
import { AdminMenuPage } from './pages/AdminMenuPage';
import { LoginPage } from './pages/LoginPage';
import { TransactionTypeEditPage } from './pages/TransactionTypeEditPage';
import { TransactionTypeListPage } from './pages/TransactionTypeListPage';
import { getSession } from './api/client';
import { CardListPage, CardViewPage } from './pages/CardPages';

function SessionMenu() {
  return getSession() ? <AdminMenuPage main /> : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/menu" element={<SessionMenu />} />
      <Route path="/cards" element={<CardListPage />} />
      <Route path="/cards/view" element={<CardViewPage />} />
      <Route path="/admin" element={<RequireAdmin><AdminMenuPage /></RequireAdmin>} />
      <Route path="/transaction-types" element={<RequireAdmin><TransactionTypeListPage /></RequireAdmin>} />
      <Route path="/transaction-types/maintain" element={<RequireAdmin><TransactionTypeEditPage /></RequireAdmin>} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
