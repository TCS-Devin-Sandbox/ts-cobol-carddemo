import { useEffect, useRef, useState } from 'react';
import { Box, TextField } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { getSession, request } from '../api/client';
import { ScreenHeader } from '../components/ScreenHeader';
import { MessageLine } from '../components/MessageLine';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

const fields = [
  ['account_id', 'Account ID', 11], ['card_number', 'Card Number', 16],
  ['transaction_type_code', 'Type CD', 2], ['transaction_category_code', 'Category CD', 4],
  ['source', 'Source', 10], ['description', 'Description', 60], ['amount', 'Amount', 12],
  ['origination_timestamp', 'Orig Date', 10], ['processed_timestamp', 'Proc Date', 10],
  ['merchant_id', 'Merchant ID', 9], ['merchant_name', 'Merchant Name', 30],
  ['merchant_city', 'Merchant City', 25], ['merchant_zip_code', 'Merchant Zip', 10],
  ['confirmation', 'Confirm (Y/N)', 1],
] as const;
interface Result { fields: Record<string, string>; message: string; field: string; added: boolean }

export function TransactionAddPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { cardNumber?: string; from?: string } | null;
  const [values, setValues] = useState<Record<string, string>>(state?.cardNumber ? { card_number: state.cardNumber } : {});
  const [message, setMessage] = useState('');
  const [added, setAdded] = useState(false);
  const [busy, setBusy] = useState(false);
  const inputs = useRef<Record<string, HTMLInputElement | null>>({});
  const focus = useRef('account_id');
  useEffect(() => { if (!busy) inputs.current[focus.current]?.focus(); }, [busy]);
  useEffect(() => { if (!getSession()) navigate('/login'); else if (state?.cardNumber) void submit(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const submit = async (copyLast = false) => {
    if (busy) return;
    setBusy(true);
    try {
      const result = await request<Result>('POST', `/api/transactions/add?copyLast=${copyLast}`, values);
      setValues(result.fields); setMessage(result.message); setAdded(result.added);
      focus.current = result.field;
    } catch (e) { setAdded(false); setMessage((e as Error).message); }
    finally { setBusy(false); }
  };
  const clear = () => { setValues({}); setMessage(''); setAdded(false); inputs.current.account_id?.focus(); };
  const back = () => navigate(state?.from ?? '/menu');
  useFunctionKeys({ Enter: () => void submit(), F3: back, F4: clear, F5: () => void submit(true), onInvalid: () => { setAdded(false); setMessage('Invalid key pressed. Please see below...'); } });
  return <Box sx={{ maxWidth: 900, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CT02" program="COTRN02C" title="Transaction Add" />
    <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
      {fields.map(([key, label, length]) => <TextField key={key} label={label} value={values[key] ?? ''}
        inputRef={node => { inputs.current[key] = node; }} autoFocus={key === 'account_id'}
        inputProps={{ maxLength: length }} disabled={busy} onChange={e => setValues(old => ({ ...old, [key]: e.target.value }))} />)}
    </Box>
    <MessageLine error={added ? null : message || null} info={added ? message : null} />
    <FunctionKeyBar keys={[{ label: 'Enter=Add', onClick: () => void submit() }, { label: 'F3=Back', onClick: back }, { label: 'F4=Clear', onClick: clear }, { label: 'F5=Copy last', onClick: () => void submit(true) }]} />
  </Box>;
}
