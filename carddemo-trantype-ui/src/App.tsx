import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './pages/LoginPage';
import { AdminMenuPage } from './pages/AdminMenuPage';
import { TransactionTypeListPage } from './pages/TransactionTypeListPage';
import { TransactionTypeEditPage } from './pages/TransactionTypeEditPage';
import { RequireAdmin } from './components/RequireAdmin';

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
