import { useRef, useState } from 'react';
import { Box, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ApiError, request } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Screen {
  accountId: string | null;
  currentBalance: number | null;
  confirm: string | null;
  message: string | null;
  severity: 'success' | 'error' | null;
  focus: 'accountId' | 'confirm';
  transactionId: string | null;
}

const initialForm = { accountId: '', confirm: '', currentBalance: null as number | null };

/** WS-CURR-BAL PIC +9999999999.99. */
export function legacyBalance(value: number | null) {
  if (value === null) return '';
  return (value < 0 ? '-' : '+') + Math.abs(value).toFixed(2).padStart(13, '0');
}

export function BillPayPage() {
  const navigate = useNavigate();
  const formRef = useRef<HTMLFormElement>(null);
  const pending = useRef(false);
  const [working, setWorking] = useState(false);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  function focus(name: string) {
    const input = formRef.current?.elements.namedItem(name);
    if (input instanceof HTMLInputElement) input.focus();
  }

  async function submit() {
    if (pending.current) return;
    pending.current = true;
    setWorking(true);
    setError(null);
    setInfo(null);
    try {
      const result = await request<Screen>('POST', '/api/bill-payments', form);
      setForm({ accountId: result.accountId ?? '', confirm: result.confirm ?? '', currentBalance: result.currentBalance });
      if (result.severity === 'error') setError(result.message);
      if (result.severity === 'success') setInfo(result.message);
      focus(result.focus);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        navigate('/login', { replace: true });
      } else {
        setError(e instanceof ApiError ? e.message : 'Unable to lookup Account...');
        focus('accountId');
      }
    } finally {
      pending.current = false;
      setWorking(false);
    }
  }

  const back = () => navigate('/menu');
  const clear = () => {
    setForm(initialForm);
    setError(null);
    setInfo(null);
    focus('accountId');
  };
  useFunctionKeys({
    Enter: () => void submit(),
    F3: back,
    F4: clear,
    onInvalid: () => {
      setInfo(null);
      setError('Invalid key pressed. Please see below...');
    },
  });

  return (
    <Box component="form" ref={formRef} sx={{ maxWidth: 800, mx: 'auto', p: 2 }}
      onSubmit={e => { e.preventDefault(); void submit(); }}>
      <ScreenHeader tranId="CB00" program="COBIL00C" title="Bill Payment" />
      <TextField label="Enter Acct ID:" name="accountId" size="small" autoFocus sx={{ width: 220 }}
        inputProps={{ maxLength: 11, 'aria-label': 'Enter Acct ID' }} value={form.accountId}
        onChange={e => setForm({ ...form, accountId: e.target.value })} />
      <Box sx={{ borderTop: 1, borderColor: 'divider', my: 2 }} />
      <Typography>
        Your current balance is: <Box component="span" data-testid="curbal" sx={{ fontFamily: 'monospace', color: 'primary.main' }}>
          {legacyBalance(form.currentBalance)}
        </Box>
      </Typography>
      <Typography sx={{ mt: 2 }}>Do you want to pay your balance now. Please confirm: </Typography>
      <TextField label="(Y/N)" name="confirm" size="small" sx={{ mt: 1, width: 100 }}
        inputProps={{ maxLength: 1, 'aria-label': 'Confirm (Y/N)' }} value={form.confirm}
        onChange={e => setForm({ ...form, confirm: e.target.value })} />
      <MessageLine error={error} info={info} />
      <FunctionKeyBar keys={[
        { label: 'ENTER=Continue', onClick: () => void submit(), disabled: working },
        { label: 'F3=Back', onClick: back },
        { label: 'F4=Clear', onClick: clear },
      ]} />
    </Box>
  );
}
