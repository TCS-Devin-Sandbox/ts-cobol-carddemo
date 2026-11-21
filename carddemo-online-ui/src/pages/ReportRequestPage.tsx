import { useRef, useState } from 'react';
import { Box, Checkbox, FormControlLabel, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ApiError, request } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

const blankDate = { month: '', day: '', year: '' };
const initialForm = { monthly: false, yearly: false, custom: false,
  startDate: blankDate, endDate: blankDate, confirmation: '' };
const reportTypes = [
  ['monthly', 'Monthly (Current Month)'],
  ['yearly', 'Yearly (Current Year)'],
  ['custom', 'Custom (Date Range)'],
] as const;
const dateFields = [['month', 'Month', 2], ['day', 'Day', 2], ['year', 'Year', 4]] as const;

interface Result {
  message: string | null;
  severity: 'success' | 'error' | null;
  focus: string;
  reset: boolean;
}

export function ReportRequestPage() {
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
      const result = await request<Result>('POST', '/api/reports', form);
      if (result.reset) setForm(initialForm);
      if (result.severity === 'error') setError(result.message);
      if (result.severity === 'success') setInfo(result.message);
      focus(result.focus);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        navigate('/login', { replace: true });
      } else {
        setError(e instanceof ApiError ? e.message : 'Unable to submit transaction report...');
        focus('monthly');
      }
    } finally {
      pending.current = false;
      setWorking(false);
    }
  }

  const back = () => navigate('/menu');
  useFunctionKeys({
    Enter: () => void submit(),
    F3: back,
    onInvalid: () => {
      setInfo(null);
      setError('Invalid key pressed. Please see below...');
      focus('monthly');
    },
  });

  return (
    <Box component="form" ref={formRef} sx={{ maxWidth: 800, mx: 'auto', p: 2 }}
      onSubmit={e => { e.preventDefault(); void submit(); }}>
      <ScreenHeader tranId="CR00" program="CORPT00C" title="Transaction Reports" />
      {reportTypes.map(([field, label]) => (
        <Box key={field}><FormControlLabel label={label} control={
          <Checkbox name={field} autoFocus={field === 'monthly'} checked={form[field]}
            onChange={e => setForm(prev => ({ ...prev, [field]: e.target.checked }))} />
        } /></Box>
      ))}
      {(['start', 'end'] as const).map(prefix => {
        const label = prefix === 'start' ? 'Start Date' : 'End Date';
        const date = `${prefix}Date` as const;
        return (
          <Box component="fieldset" key={prefix} sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
            <legend>{label} (MM/DD/YYYY)</legend>
            {dateFields.map(([field, part, width]) => (
              <TextField key={field} label={part} size="small" name={prefix + part}
                inputProps={{ maxLength: width, inputMode: 'numeric', 'aria-label': `${label} ${part}` }}
                value={form[date][field]} sx={{ width: 96 }}
                onChange={e => {
                  const value = e.target.value;
                  setForm(prev => ({ ...prev, [date]: { ...prev[date], [field]: value } }));
                }} />
            ))}
          </Box>
        );
      })}
      <Typography>The report will be submitted for printing. Please confirm:</Typography>
      <TextField label="Confirm (Y/N)" name="confirmation" size="small" sx={{ mt: 1, width: 150 }}
        inputProps={{ maxLength: 1 }} value={form.confirmation}
        onChange={e => setForm({ ...form, confirmation: e.target.value })} />
      <MessageLine error={error} info={info} />
      <FunctionKeyBar keys={[
        { label: 'Enter=Continue', onClick: () => void submit(), disabled: working },
        { label: 'F3=Back', onClick: back },
      ]} />
    </Box>
  );
}
