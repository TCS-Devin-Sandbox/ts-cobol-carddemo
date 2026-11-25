import { useEffect, useRef, useState } from 'react';
import {
  Box, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, request, setSession } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Account {
  customerId: string | null;
  name: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  phoneNumber1: string | null;
  creditLimit: number | null;
  cashCreditLimit: number | null;
}

interface Summary {
  found: boolean;
  approvedCount: number;
  declinedCount: number;
  creditBalance: number;
  cashBalance: number;
  approvedAmount: number;
  declinedAmount: number;
}

export interface AuthorizationRow {
  authorizationKey: string;
  transactionId: string;
  date: string;
  time: string;
  type: string;
  approval: string;
  matchStatus: string;
  approvedAmount: number;
}

interface CpvsState {
  accountId: string | null;
  pageNumber: number;
  previousPageKeys: string[];
  lastKey: string | null;
  nextPage: boolean;
  rowKeys: string[];
}

interface CpvsScreen {
  account: Account | null;
  summary: Summary | null;
  rows: AuthorizationRow[] | null;
  message: string | null;
  accountIdInvalid: boolean;
  transfer: { program: string; tranId: string; accountId: string; authorizationKey: string } | null;
  state: CpvsState;
}

type Action = 'ENTER' | 'PF7' | 'PF8';
const INVALID_KEY = 'Invalid key pressed. Please see below...';
const amounts = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2, useGrouping: false });
const amount = (value?: number | null) => value === undefined || value === null ? '' : amounts.format(value);
const count = (value?: number) => value === undefined ? '' : String(value).padStart(3, '0');

function Field({ label, value }: { label: string; value?: string | null }) {
  return <Box sx={{ display: 'flex', gap: 1 }}>
    <Typography component="dt" fontWeight="bold">{label}</Typography>
    <Typography component="dd" sx={{ m: 0 }}>{value ?? ''}</Typography>
  </Box>;
}

/** COPAUS0C / COPAU0A: account authorization summary with a five-row pending authorization browser. */
export function AuthorizationSummaryPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [accountId, setAccountId] = useState('');
  const [account, setAccount] = useState<Account | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [rows, setRows] = useState<AuthorizationRow[]>([]);
  const [selections, setSelections] = useState<string[]>(['', '', '', '', '']);
  const [message, setMessage] = useState<string | null>(null);
  const [invalid, setInvalid] = useState(false);
  const [loading, setLoading] = useState(false);
  const state = useRef<CpvsState | null>(null);
  const pending = useRef(false);
  const input = useRef<HTMLInputElement>(null);
  const exit = () => navigate('/menu', { replace: true });

  const send = async (action: Action, id = accountId) => {
    if (pending.current) return;
    pending.current = true;
    setLoading(true);
    try {
      const screen = await request<CpvsScreen>('POST', '/api/authorizations/summary', {
        action, accountId: id, selections, state: state.current,
      });
      state.current = screen.state;
      setMessage(screen.message);
      setInvalid(screen.accountIdInvalid);
      if (screen.transfer) {
        setMessage(`${screen.transfer.tranId} (${screen.transfer.program}) is not installed yet...`);
        return;
      }
      if (action === 'ENTER') {
        setAccount(screen.account);
        setSummary(screen.summary);
        if (screen.accountIdInvalid) setAccountId('');
      }
      if (screen.rows) {
        setRows(screen.rows);
        setSelections(['', '', '', '', '']);
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setSession(null);
        navigate('/login', { replace: true });
      } else {
        setMessage('Unable to read pending authorizations');
      }
    } finally {
      pending.current = false;
      setLoading(false);
      input.current?.focus();
    }
  };

  const initial = (location.state as { accountId?: string } | null)?.accountId;
  useEffect(() => {
    if (initial && /^[0-9]{11}$/.test(initial)) {
      setAccountId(initial);
      void send('ENTER', initial);
    }
    // First entry only (MAIN-PARA with CDEMO-ACCT-ID numeric).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const invalidKey = () => { setMessage(INVALID_KEY); input.current?.focus(); };
  useFunctionKeys({
    Enter: () => void send('ENTER'), F3: exit, F7: () => void send('PF7'), F8: () => void send('PF8'),
    onInvalid: invalidKey,
  });

  return <Box sx={{ maxWidth: 1000, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CPVS" program="COPAUS0C" title="View Authorizations" />
    <Box component="form" onSubmit={e => { e.preventDefault(); void send('ENTER'); }}
      sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
      <TextField autoFocus label="Search Acct Id" value={accountId} inputRef={input} autoComplete="off"
        error={invalid} inputProps={{ maxLength: 11, inputMode: 'numeric', readOnly: loading }}
        onChange={e => setAccountId(e.target.value)} />
    </Box>
    <Box component="dl" aria-label="Account Summary"
      sx={{ display: 'grid', gridTemplateColumns: { sm: '1fr 1fr 1fr' }, gap: 1, mt: 2 }}>
      <Field label="Name:" value={account?.name} />
      <Field label="Customer Id:" value={account?.customerId} />
      <Field label="Acct Status:" value="" />
      <Field label="Address:" value={account?.addressLine1} />
      <Field label="" value={account?.addressLine2} />
      <Field label="PH:" value={account?.phoneNumber1} />
      <Field label="Approval # :" value={count(summary?.approvedCount)} />
      <Field label="Decline #:" value={count(summary?.declinedCount)} />
      <Field label="Credit Lim:" value={amount(account?.creditLimit)} />
      <Field label="Cash Lim:" value={amount(account?.cashCreditLimit)} />
      <Field label="Appr Amt:" value={amount(summary?.approvedAmount)} />
      <Field label="Credit Bal:" value={amount(summary?.creditBalance)} />
      <Field label="Cash Bal:" value={amount(summary?.cashBalance)} />
      <Field label="Decl Amt:" value={amount(summary?.declinedAmount)} />
    </Box>
    <Table size="small" aria-label="Pending Authorizations" sx={{ mt: 2 }}>
      <TableHead>
        <TableRow>
          {['Sel', 'Transaction ID', 'Date', 'Time', 'Type', 'A/D', 'STS', 'Amount']
            .map(h => <TableCell key={h}>{h}</TableCell>)}
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row, i) => <TableRow key={row.authorizationKey}>
          <TableCell>
            <TextField size="small" value={selections[i]} inputProps={{
              maxLength: 1, 'aria-label': `Select ${row.transactionId}`, readOnly: loading,
            }} onChange={e => setSelections(s => s.map((v, j) => j === i ? e.target.value : v))} />
          </TableCell>
          <TableCell>{row.transactionId}</TableCell>
          <TableCell>{row.date}</TableCell>
          <TableCell>{row.time}</TableCell>
          <TableCell>{row.type}</TableCell>
          <TableCell>{row.approval}</TableCell>
          <TableCell>{row.matchStatus}</TableCell>
          <TableCell align="right">{amount(row.approvedAmount)}</TableCell>
        </TableRow>)}
      </TableBody>
    </Table>
    <Typography sx={{ mt: 1 }}>Type &apos;S&apos; to View Authorization details from the list</Typography>
    <MessageLine error={message} />
    <FunctionKeyBar keys={[
      { label: 'ENTER=Continue', onClick: () => void send('ENTER'), disabled: loading },
      { label: 'F3=Back', onClick: exit },
      { label: 'F7=Backward', onClick: () => void send('PF7'), disabled: loading },
      { label: 'F8=Forward', onClick: () => void send('PF8'), disabled: loading },
    ]} />
  </Box>;
}
