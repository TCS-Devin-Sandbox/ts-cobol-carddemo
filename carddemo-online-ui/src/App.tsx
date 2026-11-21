import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin } from './components/RequireAdmin';
import { AccountViewPage } from './pages/AccountViewPage';
import { AccountUpdatePage } from './pages/AccountUpdatePage';
import { AdminMenuPage } from './pages/AdminMenuPage';
import { LoginPage } from './pages/LoginPage';
import { ReportRequestPage } from './pages/ReportRequestPage';
import { TransactionTypeEditPage } from './pages/TransactionTypeEditPage';
import { TransactionTypeListPage } from './pages/TransactionTypeListPage';
import { UserListPage } from './pages/UserListPage';
import { UserMaintenancePage } from './pages/UserMaintenancePage';
import { getSession } from './api/client';
import { CardListPage, CardViewPage } from './pages/CardPages';
import { CardUpdatePage } from './pages/CardUpdatePage';
import { TransactionListPage, TransactionViewPage } from './pages/TransactionPages';

function SessionMenu() {
  return getSession() ? <AdminMenuPage main /> : <Navigate to="/login" replace />;
}

function SessionAccountView() {
  return getSession() ? <AccountViewPage /> : <Navigate to="/login" replace />;
}

function SessionAccountUpdate() {
  return getSession() ? <AccountUpdatePage /> : <Navigate to="/login" replace />;
}

function SessionReports() {
  return getSession() ? <ReportRequestPage /> : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/menu" element={<SessionMenu />} />
      <Route path="/cards" element={<CardListPage />} />
      <Route path="/cards/view" element={<CardViewPage />} />
      <Route path="/cards/update" element={<CardUpdatePage />} />
      <Route path="/accounts/view" element={<SessionAccountView />} />
      <Route path="/accounts/update" element={<SessionAccountUpdate />} />
      <Route path="/transactions" element={<TransactionListPage />} />
      <Route path="/transactions/view" element={<TransactionViewPage />} />
      <Route path="/reports" element={<SessionReports />} />
      <Route path="/admin" element={<RequireAdmin><AdminMenuPage /></RequireAdmin>} />
      <Route path="/admin/users" element={<RequireAdmin><UserListPage /></RequireAdmin>} />
      <Route path="/admin/users/add" element={<RequireAdmin><UserMaintenancePage key="add" mode="add" /></RequireAdmin>} />
      <Route path="/admin/users/update" element={<RequireAdmin><UserMaintenancePage key="update" mode="update" /></RequireAdmin>} />
      <Route path="/admin/users/delete" element={<RequireAdmin><UserMaintenancePage key="delete" mode="delete" /></RequireAdmin>} />
      <Route path="/transaction-types" element={<RequireAdmin><TransactionTypeListPage /></RequireAdmin>} />
      <Route path="/transaction-types/maintain" element={<RequireAdmin><TransactionTypeEditPage /></RequireAdmin>} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  );
}
