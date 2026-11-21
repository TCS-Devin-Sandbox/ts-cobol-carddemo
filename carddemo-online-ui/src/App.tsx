import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin } from './components/RequireAdmin';
import { AdminMenuPage } from './pages/AdminMenuPage';
import { LoginPage } from './pages/LoginPage';
import { TransactionTypeEditPage } from './pages/TransactionTypeEditPage';
import { TransactionTypeListPage } from './pages/TransactionTypeListPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/admin" element={<RequireAdmin><AdminMenuPage /></RequireAdmin>} />
      <Route path="/transaction-types" element={<RequireAdmin><TransactionTypeListPage /></RequireAdmin>} />
      <Route path="/transaction-types/maintain" element={<RequireAdmin><TransactionTypeEditPage /></RequireAdmin>} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
