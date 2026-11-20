import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Alert from '../components/Alert.jsx';
import ConfirmDialog from '../components/ConfirmDialog.jsx';
import TextField from '../components/TextField.jsx';
import { useTransactionTypes } from '../hooks/useTransactionTypes.js';
import { transactionTypesApi } from '../api/transactionTypesApi.js';
import { isUnchanged, MAX_DESCRIPTION, validateDescription, validateTypeFilter } from '../validation/transactionType.js';

const NO_MATCH = 'No transaction types match these filters.';

export default function TransactionTypeListPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { page, pageNo, filters, loading, error, setError, search, next, previous, reloadFirst, replaceItem } =
    useTransactionTypes();

  const [draft, setDraft] = useState({ typeCode: '', description: '' });
  const [filterError, setFilterError] = useState(null);
  const [status, setStatus] = useState(location.state?.status ?? null);
  const [editing, setEditing] = useState(null); // { typeCode, original, value, error }
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [busy, setBusy] = useState(false);
  const editInputRef = useRef(null);
  const editButtonRefs = useRef({});

  useEffect(() => {
    search({ typeCode: '', description: '' });
    if (location.state?.status) navigate(location.pathname, { replace: true, state: null });
    // initial load only
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (editing) editInputRef.current?.focus();
  }, [editing?.typeCode]); // eslint-disable-line react-hooks/exhaustive-deps

  const noMatch = !loading && !error && page.filtered && page.items.length === 0;
  const empty = !loading && !error && !page.filtered && page.items.length === 0;

  const onSearch = (e) => {
    e.preventDefault();
    const err = validateTypeFilter(draft.typeCode);
    setFilterError(err);
    setStatus(null);
    setEditing(null);
    if (!err) search({ typeCode: draft.typeCode.trim(), description: draft.description.trim() });
  };

  const onClear = () => {
    setDraft({ typeCode: '', description: '' });
    setFilterError(null);
    setStatus(null);
    setEditing(null);
    search({ typeCode: '', description: '' });
  };

  const startEdit = (item) => {
    setStatus(null);
    setError(null);
    setEditing({ typeCode: item.typeCode, original: item.description, value: item.description, error: null });
  };

  const cancelEdit = () => {
    const code = editing?.typeCode;
    setEditing(null);
    setTimeout(() => editButtonRefs.current[code]?.focus(), 0);
  };

  const saveEdit = async () => {
    const err = validateDescription(editing.value);
    if (err) {
      setEditing({ ...editing, error: err });
      editInputRef.current?.focus();
      return;
    }
    setBusy(true);
    try {
      const updated = await transactionTypesApi.update(editing.typeCode, editing.value.trim());
      replaceItem(updated);
      setStatus(`Transaction type ${updated.typeCode} updated.`);
      cancelEdit();
    } catch (e) {
      if (e.errors?.description) setEditing({ ...editing, error: e.errors.description[0] });
      else setError(e);
    } finally {
      setBusy(false);
    }
  };

  const doDelete = async () => {
    setBusy(true);
    try {
      await transactionTypesApi.remove(confirmDelete.typeCode);
      setStatus(`Transaction type ${confirmDelete.typeCode} deleted.`);
      setConfirmDelete(null);
      reloadFirst();
    } catch (e) {
      setConfirmDelete(null);
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const filterInvalid = Boolean(filterError) || noMatch;
  const disableRowActions = Boolean(editing) || busy;

  return (
    <div className="stack flex flex-col gap-sp-5">
      <div className="flex flex-wrap items-center justify-between gap-sp-3">
        <div>
          <Link className="btn btn-ghost btn-sm px-0" to="/admin">← Back to admin menu</Link>
          <h1 className="m-0 text-xl text-primary-900">Transaction types</h1>
        </div>
        <Link className="btn btn-primary" to="/transaction-types/new">Add transaction type</Link>
      </div>

      <section className="card" aria-labelledby="filters-heading">
        <div className="card-header">
          <h2 id="filters-heading" className="m-0 text-md">Find transaction types</h2>
        </div>
        <form className="card-body" onSubmit={onSearch} noValidate>
          <div className="grid grid-cols-1 gap-x-sp-4 md:grid-cols-12">
            <div className="md:col-span-3">
              <TextField
                id="filter-type-code"
                label="Type code"
                value={draft.typeCode}
                onChange={(v) => setDraft({ ...draft, typeCode: v })}
                maxLength={2}
                inputMode="numeric"
                mono
                error={filterError ?? (noMatch && filters.typeCode ? ' ' : null)}
                hint="Two digits, for example 05."
              />
            </div>
            <div className="md:col-span-6">
              <TextField
                id="filter-description"
                label="Description contains"
                value={draft.description}
                onChange={(v) => setDraft({ ...draft, description: v })}
                maxLength={MAX_DESCRIPTION}
                error={noMatch && filters.description ? ' ' : null}
                hint="Matches exact letter case."
              />
            </div>
            <div className="flex items-start gap-sp-2 md:col-span-3 md:pt-[1.85rem]">
              <button type="submit" className="btn btn-primary" disabled={loading}>Search</button>
              <button type="button" className="btn btn-secondary" onClick={onClear} disabled={loading}>Clear</button>
            </div>
          </div>
          {filterInvalid && noMatch && <Alert variant="danger">{NO_MATCH}</Alert>}
        </form>
      </section>

      <section className="card" aria-labelledby="table-heading">
        <div className="card-header">
          <h2 id="table-heading" className="m-0 text-md">Transaction types</h2>
          <span className="text-muted text-sm">Page {pageNo}</span>
        </div>
        <div className="card-body flex flex-col gap-sp-4">
          <Alert variant="success">{status}</Alert>
          {error && <Alert variant="danger">{error.message}</Alert>}
          {!status && !error && !noMatch && page.items.length > 0 && (
            <Alert variant="info">Select Edit or Delete on a row to change it.</Alert>
          )}

          <div className="table-wrap">
            <table className="data-table">
              <caption className="visually-hidden">Transaction types, page {pageNo}</caption>
              <thead>
                <tr>
                  <th scope="col" className="w-[6rem]">Code</th>
                  <th scope="col">Description</th>
                  <th scope="col" className="w-[12rem] text-right">Actions</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {loading &&
                  Array.from({ length: 7 }, (_, i) => (
                    <tr key={`s${i}`} aria-hidden="true">
                      <td colSpan={3}><div className="h-4 w-full animate-pulse rounded-md bg-surface-alt" /></td>
                    </tr>
                  ))}
                {!loading &&
                  page.items.map((item) => {
                    const isEditing = editing?.typeCode === item.typeCode;
                    return (
                      <tr key={item.typeCode} className={isEditing ? 'outline outline-2 outline-primary-300' : undefined}>
                        <td>
                          <Link className="font-mono" to={`/transaction-types/${item.typeCode}`}>{item.typeCode}</Link>
                        </td>
                        <td>
                          {isEditing ? (
                            <TextField
                              ref={editInputRef}
                              id={`edit-${item.typeCode}`}
                              label={`Description for type ${item.typeCode}`}
                              srOnlyLabel
                              value={editing.value}
                              onChange={(v) => setEditing({ ...editing, value: v, error: null })}
                              maxLength={MAX_DESCRIPTION}
                              error={editing.error}
                              hint={isUnchanged(editing.value, editing.original) ? 'No changes to save.' : undefined}
                            />
                          ) : (
                            item.description
                          )}
                        </td>
                        <td className="text-right">
                          {isEditing ? (
                            <span className="inline-flex gap-sp-2">
                              <button
                                type="button"
                                className="btn btn-primary btn-sm"
                                onClick={saveEdit}
                                disabled={busy || isUnchanged(editing.value, editing.original)}
                              >
                                {busy ? 'Saving…' : 'Save'}
                              </button>
                              <button type="button" className="btn btn-secondary btn-sm" onClick={cancelEdit} disabled={busy}>
                                Cancel
                              </button>
                            </span>
                          ) : (
                            <span className="inline-flex gap-sp-2">
                              <button
                                ref={(el) => (editButtonRefs.current[item.typeCode] = el)}
                                type="button"
                                className="btn btn-ghost btn-sm"
                                onClick={() => startEdit(item)}
                                disabled={disableRowActions}
                                aria-label={`Edit transaction type ${item.typeCode}`}
                              >
                                Edit
                              </button>
                              <button
                                type="button"
                                className="btn btn-ghost btn-sm text-danger-700"
                                onClick={() => {
                                  setStatus(null);
                                  setConfirmDelete(item);
                                }}
                                disabled={disableRowActions}
                                aria-label={`Delete transaction type ${item.typeCode}`}
                              >
                                Delete
                              </button>
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                {empty && (
                  <tr>
                    <td colSpan={3} className="text-center text-muted">
                      No transaction types yet. <Link to="/transaction-types/new">Add transaction type</Link>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between">
            <button type="button" className="btn btn-secondary btn-sm" onClick={previous} disabled={loading || !page.hasPrevious || Boolean(editing)}>
              Previous
            </button>
            <span className="text-muted text-sm">Page {pageNo}</span>
            <button type="button" className="btn btn-secondary btn-sm" onClick={next} disabled={loading || !page.hasNext || Boolean(editing)}>
              Next
            </button>
          </div>
        </div>
      </section>

      {confirmDelete && (
        <ConfirmDialog
          title="Delete transaction type?"
          message={`Delete transaction type ${confirmDelete.typeCode} – ${confirmDelete.description}? This cannot be undone.`}
          confirmLabel="Delete"
          danger
          busy={busy}
          onConfirm={doDelete}
          onCancel={() => {
            setConfirmDelete(null);
            setStatus('Nothing was changed.');
          }}
        />
      )}
    </div>
  );
}
