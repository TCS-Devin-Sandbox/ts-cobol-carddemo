import { useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import Alert from '../components/Alert.jsx';
import ConfirmDialog from '../components/ConfirmDialog.jsx';
import TextField from '../components/TextField.jsx';
import { transactionTypesApi } from '../api/transactionTypesApi.js';
import { MAX_DESCRIPTION, normalizeCode, validateDescription, validateTypeCode } from '../validation/transactionType.js';

export default function TransactionTypeAddPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [typeCode, setTypeCode] = useState(params.get('typeCode') ?? '');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState({});
  const [existingCode, setExistingCode] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [alert, setAlert] = useState(null);
  const codeRef = useRef(null);
  const descriptionRef = useRef(null);

  const validate = () => {
    const next = { typeCode: validateTypeCode(typeCode), description: validateDescription(description) };
    setErrors(next);
    if (next.typeCode) codeRef.current?.focus();
    else if (next.description) descriptionRef.current?.focus();
    return !next.typeCode && !next.description;
  };

  const onSubmit = (e) => {
    e.preventDefault();
    setAlert(null);
    setExistingCode(null);
    if (validate()) setConfirming(true);
  };

  const create = async () => {
    setBusy(true);
    try {
      const created = await transactionTypesApi.create(normalizeCode(typeCode), description.trim());
      navigate('/transaction-types', { state: { status: `Transaction type ${created.typeCode} created.` } });
    } catch (err) {
      setConfirming(false);
      if (err.code === 'TYPE_ALREADY_EXISTS') {
        setExistingCode(normalizeCode(typeCode));
        setErrors({ typeCode: err.message });
        codeRef.current?.focus();
      } else if (err.errors && Object.keys(err.errors).length) {
        setErrors({ typeCode: err.errors.typeCode?.[0], description: err.errors.description?.[0] });
      } else {
        setAlert(err.message);
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-sp-5">
      <div>
        <Link className="btn btn-ghost btn-sm px-0" to="/transaction-types">← Back to list</Link>
        <h1 className="m-0 text-xl text-primary-900">Add transaction type</h1>
      </div>
      <form className="card max-w-form" onSubmit={onSubmit} noValidate aria-labelledby="new-heading">
        <div className="card-header">
          <h2 id="new-heading" className="m-0 text-md">New transaction type</h2>
        </div>
        <div className="card-body">
          <Alert variant="danger">{alert}</Alert>
          <TextField
            ref={codeRef}
            id="new-type-code"
            label="Type code"
            required
            value={typeCode}
            onChange={(v) => setTypeCode(v)}
            onBlur={() => setErrors((x) => ({ ...x, typeCode: validateTypeCode(typeCode) }))}
            maxLength={2}
            inputMode="numeric"
            mono
            autoFocus
            error={errors.typeCode}
            hint="Two digits from 01 to 99."
          />
          {existingCode && (
            <p className="mt-0 text-sm">
              <Link to={`/transaction-types/${existingCode}`}>View transaction type {existingCode}</Link>
            </p>
          )}
          <TextField
            ref={descriptionRef}
            id="new-description"
            label="Description"
            required
            value={description}
            onChange={(v) => setDescription(v)}
            onBlur={() => setErrors((x) => ({ ...x, description: validateDescription(description) }))}
            maxLength={MAX_DESCRIPTION}
            error={errors.description}
            hint={`Letters, numbers and spaces. ${description.length}/${MAX_DESCRIPTION}`}
          />
        </div>
        <div className="card-footer flex justify-end gap-sp-3">
          <Link className="btn btn-secondary" to="/transaction-types">Cancel</Link>
          <button type="submit" className="btn btn-primary" disabled={busy}>Create</button>
        </div>
      </form>
      {confirming && (
        <ConfirmDialog
          title="Create transaction type?"
          message={`Create transaction type ${normalizeCode(typeCode)} – ${description.trim()}?`}
          confirmLabel="Create"
          busy={busy}
          onConfirm={create}
          onCancel={() => {
            setConfirming(false);
            setAlert(null);
          }}
        />
      )}
    </div>
  );
}
