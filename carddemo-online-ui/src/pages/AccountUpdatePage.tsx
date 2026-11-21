import { useRef, useState } from 'react';
import { Box, Button, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ApiError, getSession, request, setSession } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Screen {
  accountId: string;
  customerId: string | null;
  version: string | null;
  fields: Record<string, string>;
  state: 'SEARCH' | 'EDIT' | 'CONFIRM' | 'SAVED';
  message: string;
  invalidField: string | null;
}

const accountFields: [string, string, number][] = [
  ['activeStatus', 'Account Status', 1], ['openDate', 'Open Date', 10],
  ['creditLimit', 'Credit Limit', 15], ['expirationDate', 'Expiry Date', 10],
  ['cashCreditLimit', 'Cash Credit Limit', 15], ['reissueDate', 'Reissue Date', 10],
  ['currentBalance', 'Current Balance', 15], ['currentCycleCredit', 'Current Cycle Credit', 15],
  ['currentCycleDebit', 'Current Cycle Debit', 15], ['accountGroupId', 'Account Group', 10],
];
const customerFields: [string, string, number][] = [
  ['socialSecurityNumber', 'SSN (9 digits)', 9], ['dateOfBirth', 'Date of Birth', 10],
  ['ficoCreditScore', 'FICO Score', 3], ['firstName', 'First Name', 25],
  ['middleName', 'Middle Name', 25], ['lastName', 'Last Name', 25],
  ['addressLine1', 'Address Line 1', 50], ['stateCode', 'State', 2],
  ['zipCode', 'ZIP', 5], ['addressLine2', 'Address Line 2', 50],
  ['addressLine3', 'City', 50], ['countryCode', 'Country', 3],
  ['phoneNumber1', 'Phone Number 1', 13], ['phoneNumber2', 'Phone Number 2', 13],
  ['governmentIssuedId', 'Government Issued ID', 20],
  ['electronicFundsTransferAccountId', 'EFT Account ID', 10],
  ['primaryCardholderIndicator', 'Primary Card Holder', 1],
];

export function AccountUpdatePage() {
  const navigate = useNavigate();
  const [accountId, setAccountId] = useState('');
  const [screen, setScreen] = useState<Screen | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);
  const pending = useRef(false);
  const confirm = screen?.state === 'CONFIRM';
  const exit = () => navigate('/menu', { replace: true });

  const submit = async (action: 'read' | 'review' | 'save') => {
    if (pending.current || (action === 'save' && (!confirm || conflict))) return;
    pending.current = true;
    setBusy(true);
    setError(null);
    try {
      const result = await request<Screen>('POST', action === 'read' ? '/api/accounts/update/read' : '/api/accounts/update',
        action === 'read' ? { accountId } : { accountId: screen?.accountId, version: screen?.version, fields, confirmed: action === 'save' });
      setScreen(result);
      setFields(result.fields);
      setConflict(false);
      if (result.invalidField) setError(result.message);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setSession(null);
        navigate('/login', { replace: true });
      } else {
        setError(e instanceof ApiError ? e.message : 'Changes unsuccessful. Please try again');
        if (e instanceof ApiError && e.status === 409) setConflict(true);
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const enter = () => {
    if (confirm || conflict) return;
    void submit(screen?.version ? 'review' : 'read');
  };
  const cancel = () => { if (screen?.version) void submit('read'); };
  useFunctionKeys({ Enter: enter, F3: exit, F5: () => void submit('save'), F12: cancel,
    onInvalid: () => setError('Invalid key pressed. Please see below...') });

  if (!getSession()) return null;
  return <Box sx={{ maxWidth: 1000, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CAUP" program="COACTUPC" title="Update Account" />
    <Box component="form" autoComplete="off" onSubmit={e => { e.preventDefault(); enter(); }}>
      <TextField autoFocus label="Account Number" value={accountId} disabled={busy || !!screen?.version}
        error={screen?.invalidField === 'accountId'} inputProps={{ maxLength: 11 }}
        onChange={e => setAccountId(e.target.value)} />
      {screen?.customerId && <Typography>Customer ID: {screen.customerId}</Typography>}
      {screen?.version && [['Account Details', accountFields], ['Customer Details', customerFields]].map(([title, definitions]) =>
        <Box component="section" aria-label={title as string} key={title as string} sx={{ mt: 2 }}>
          <Typography component="h2" variant="h6">{title as string}</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, mt: 1 }}>
            {(definitions as [string, string, number][]).map(([key, label, maxLength]) =>
              <TextField key={key} label={label} value={fields[key] ?? ''} disabled={busy || confirm || conflict || key === 'countryCode'}
                inputProps={{ maxLength }} error={screen.invalidField === key}
                helperText={key.endsWith('Date') || key === 'dateOfBirth' ? 'YYYY-MM-DD' : key.startsWith('phoneNumber') ? '(999)999-9999' : undefined}
                onChange={e => setFields(previous => ({ ...previous, [key]: e.target.value }))} />)}
          </Box>
        </Box>)}
      <Box sx={{ mt: 2 }}><Button type="submit" variant="contained" disabled={busy || confirm || conflict}>
        {screen?.version ? 'Enter=Review changes' : 'Enter=Fetch'}
      </Button></Box>
    </Box>
    <MessageLine error={error} info={error ? null : screen?.message ?? 'Enter or update id of account to update'} />
    <FunctionKeyBar keys={[
      { label: 'F3=Exit', onClick: exit },
      ...(confirm && !conflict ? [{ label: 'F5=Save', disabled: busy, onClick: () => void submit('save') }] : []),
      ...(screen?.version ? [{ label: 'F12=Cancel / Reload', disabled: busy, onClick: cancel }] : []),
      ...(screen?.version ? [{ label: 'New account', disabled: busy, onClick: () => { setScreen(null); setFields({}); setError(null); setAccountId(''); setConflict(false); } }] : []),
    ]} />
  </Box>;
}
