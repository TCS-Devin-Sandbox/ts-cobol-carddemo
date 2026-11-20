import { Link, Navigate, Route, Routes } from 'react-router-dom';
import TransactionTypeListPage from './screens/TransactionTypeListPage.jsx';
import TransactionTypeAddPage from './screens/TransactionTypeAddPage.jsx';
import TransactionTypeDetailPage from './screens/TransactionTypeDetailPage.jsx';
import ConfirmDialog from './components/ConfirmDialog.jsx';
import { useIdleTimeout } from './hooks/useIdleTimeout.js';
import { clearSession } from './api/transactionTypesApi.js';

const IDLE_LIMIT_MS = 15 * 60 * 1000;
const WARNING_MS = 60 * 1000;

function AdminPlaceholder() {
  return (
    <div className="card">
      <div className="card-body">
        <h1 className="text-xl m-0 mb-sp-3">Admin menu</h1>
        <p className="text-muted">The admin menu is not available yet.</p>
        <Link className="btn btn-secondary" to="/transaction-types">Go to transaction types</Link>
      </div>
    </div>
  );
}

function SignedOut() {
  return (
    <div className="card">
      <div className="card-body">
        <h1 className="text-xl m-0 mb-sp-3">You have been signed out</h1>
        <p className="text-muted">Your session ended after a period of inactivity.</p>
        <a className="btn btn-primary" href="/transaction-types">Sign in again</a>
      </div>
    </div>
  );
}

export default function App() {
  const { warning, signedOut, stayActive } = useIdleTimeout(IDLE_LIMIT_MS, WARNING_MS, clearSession);

  return (
    <>
      <header className="app-header">
        <span className="brand">
          <span className="brand-mark" aria-hidden="true">C</span>
          CardDemo Administration
        </span>
        <nav aria-label="Main">
          <Link to="/transaction-types">Transaction types</Link>
        </nav>
      </header>
      <main className="container mx-auto max-w-container px-sp-4 py-sp-6">
        {signedOut ? (
          <SignedOut />
        ) : (
          <Routes>
            <Route path="/" element={<Navigate to="/transaction-types" replace />} />
            <Route path="/transaction-types" element={<TransactionTypeListPage />} />
            <Route path="/transaction-types/new" element={<TransactionTypeAddPage />} />
            <Route path="/transaction-types/:typeCode" element={<TransactionTypeDetailPage />} />
            <Route path="/admin" element={<AdminPlaceholder />} />
            <Route path="*" element={<Navigate to="/transaction-types" replace />} />
          </Routes>
        )}
      </main>
      {warning && !signedOut && (
        <ConfirmDialog
          title="Are you still there?"
          message="You will be signed out in one minute because of inactivity."
          confirmLabel="Stay signed in"
          cancelLabel="Sign out now"
          onConfirm={stayActive}
          onCancel={() => stayActive(true)}
        />
      )}
    </>
  );
}
