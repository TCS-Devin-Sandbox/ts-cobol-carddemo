import { useRef, useState } from 'react';
import { Box, Button, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ApiError, request, setSession } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Account {
  activeStatus: string;
  currentBalance: number;
  creditLimit: number;
  cashCreditLimit: number;
  openDate: string;
  expirationDate: string;
  reissueDate: string;
  currentCycleCredit: number;
  currentCycleDebit: number;
  accountGroupId: string;
}

interface Customer {
  customerId: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  addressLine3: string;
  stateCode: string;
  countryCode: string;
  zipCode: string;
  phoneNumber1: string;
  phoneNumber2: string;
  socialSecurityNumber: string;
  governmentIssuedId: string;
  dateOfBirth: string;
  electronicFundsTransferAccountId: string;
  primaryCardholderIndicator: string;
  ficoCreditScore: string;
}

interface AccountScreen {
  account: Account | null;
  customer: Customer | null;
  error: { code: string; message: string; accountNumberInvalid: boolean } | null;
}

const amounts = new Intl.NumberFormat('en-US', {
  signDisplay: 'always', minimumFractionDigits: 2, maximumFractionDigits: 2,
});
const amount = (value?: number) => value === undefined ? '' : amounts.format(value);
const ssn = (digits?: string) => digits ? `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}` : '';

function Details({ title, fields }: { title: string; fields: [string, string | undefined][] }) {
  return <Box component="section" aria-label={title} sx={{ mt: 2 }}>
    <Typography component="h2" variant="h6">{title}</Typography>
    <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: { sm: '1fr 1fr' }, gap: 1, m: 0 }}>
      {fields.map(([label, value]) => <Box key={label} sx={{ display: 'flex', gap: 1 }}>
        <Typography component="dt" fontWeight="bold">{label}:</Typography>
        <Typography component="dd" sx={{ m: 0 }}>{value}</Typography>
      </Box>)}
    </Box>
  </Box>;
}

export function AccountViewPage() {
  const navigate = useNavigate();
  const [accountId, setAccountId] = useState('');
  const [result, setResult] = useState<AccountScreen | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const pending = useRef(false);
  const input = useRef<HTMLInputElement>(null);
  const exit = () => navigate('/menu', { replace: true });

  const lookup = async () => {
    if (pending.current) return;
    pending.current = true;
    setLoading(true);
    setResult(null);
    setRequestError(null);
    try {
      const screen = await request<AccountScreen>('POST', '/api/accounts/view', { accountId });
      if (screen.error?.code === 'NO_INPUT') setAccountId('');
      setResult(screen);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setSession(null);
        navigate('/login', { replace: true });
      } else {
        setRequestError('Unable to look up account');
      }
    } finally {
      pending.current = false;
      setLoading(false);
      input.current?.focus();
    }
  };

  useFunctionKeys({ Enter: () => void lookup(), F3: exit, onInvalid: () => void lookup() });
  const account = result?.account;
  const customer = result?.customer;
  return <Box sx={{ maxWidth: 900, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CAVW" program="COACTVWC" title="View Account" />
    <Box component="form" onSubmit={e => { e.preventDefault(); void lookup(); }}
      sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
      <TextField autoFocus label="Account Number" value={accountId} inputRef={input} autoComplete="off"
        error={result?.error?.accountNumberInvalid ?? false}
        placeholder={result?.error?.code === 'NO_INPUT' ? '*' : undefined}
        inputProps={{ maxLength: 11, inputMode: 'numeric', readOnly: loading }}
        onChange={e => setAccountId(e.target.value)} />
      <Button type="submit" variant="contained" disabled={loading}>Enter=View</Button>
    </Box>
    <Details title="Account Details" fields={[
      ['Active Y/N', account?.activeStatus], ['Current Balance', amount(account?.currentBalance)],
      ['Opened', account?.openDate], ['Credit Limit', amount(account?.creditLimit)],
      ['Expiry', account?.expirationDate], ['Cash Credit Limit', amount(account?.cashCreditLimit)],
      ['Reissue', account?.reissueDate], ['Current Cycle Credit', amount(account?.currentCycleCredit)],
      ['Account Group', account?.accountGroupId], ['Current Cycle Debit', amount(account?.currentCycleDebit)],
    ]} />
    <Details title="Customer Details" fields={[
      ['Customer ID', customer?.customerId], ['SSN', ssn(customer?.socialSecurityNumber)],
      ['Date of Birth', customer?.dateOfBirth], ['FICO Score', customer?.ficoCreditScore],
      ['First Name', customer?.firstName], ['Middle Name', customer?.middleName], ['Last Name', customer?.lastName],
      ['Address', customer?.addressLine1], ['State', customer?.stateCode],
      ['Address Line 2', customer?.addressLine2], ['ZIP', customer?.zipCode.slice(0, 5)],
      ['City', customer?.addressLine3], ['Country', customer?.countryCode],
      ['Phone 1', customer?.phoneNumber1.slice(0, 13)], ['Government Issued ID', customer?.governmentIssuedId],
      ['Phone 2', customer?.phoneNumber2.slice(0, 13)],
      ['EFT Account ID', customer?.electronicFundsTransferAccountId],
      ['Primary Card Holder Y/N', customer?.primaryCardholderIndicator],
    ]} />
    <MessageLine error={result?.error?.message ?? requestError}
      info="Enter or update id of account to display" />
    <FunctionKeyBar keys={[{ label: 'F3=Exit', onClick: exit }]} />
  </Box>;
}
