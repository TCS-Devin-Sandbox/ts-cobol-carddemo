import { useEffect, useState } from 'react';
import { Box, TextField } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { getSession, request } from '../api/client';
import { ScreenHeader } from '../components/ScreenHeader';
import { MessageLine } from '../components/MessageLine';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

type Transaction = Record<string, string | number>;
interface Page { rows: Transaction[]; pageNumber: number; message: string }
interface View { transaction: Transaction | null; message: string }
const invalidKey = 'Invalid key pressed. Please see below...';
const amount = (value: string | number) => `${Number(value) < 0 ? '-' : '+'}${Math.abs(Number(value)).toFixed(2).slice(-11).padStart(11, '0')}`;
const date = (value: string | number) => { const text = String(value); return `${text.slice(5, 7)}-${text.slice(8, 10)}-${text.slice(2, 4)}`; };

export function TransactionListPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState<Page>({ rows: [], pageNumber: 0, message: '' });
  const [selection, setSelection] = useState<string[]>([]);
  const [message, setMessage] = useState('');
  const browse = async () => {
    const index = selection.findIndex(value => Boolean(value?.trim()));
    if (index >= 0 && /^[sS]$/.test(selection[index])) {
      navigate('/transactions/view', { state: { transactionId: String(page.rows[index].transaction_id).trim(), fromList: true } });
      return;
    }
    try {
      const result = await request<Page>('GET', `/api/transactions?startKey=${encodeURIComponent(search)}`);
      if (result.message !== 'Tran ID must be Numeric ...') { setPage(result); setSearch(''); setSelection([]); }
      setMessage(result.message || (index >= 0 ? 'Invalid selection. Valid value is S' : ''));
    } catch (e) { setMessage((e as Error).message); }
  };
  useEffect(() => { if (!getSession()) navigate('/login'); else void browse(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const back = () => navigate('/menu');
  const move = async (direction: 'FORWARD' | 'BACKWARD') => {
    if (direction === 'BACKWARD' && page.pageNumber <= 1) { setMessage('You are already at the top of the page...'); return; }
    if (direction === 'FORWARD' && (page.rows.length < 10 || page.message.includes('bottom'))) { setMessage('You are already at the bottom of the page...'); return; }
    const key = String(page.rows[direction === 'FORWARD' ? page.rows.length - 1 : 0].transaction_id).trim();
    try {
      const result = await request<Page>('GET', `/api/transactions?startKey=${encodeURIComponent(key)}&direction=${direction}&pageNumber=${page.pageNumber}`);
      setPage(result); setMessage(result.message); setSelection([]);
    } catch (e) { setMessage((e as Error).message); }
  };
  const previous = () => { void move('BACKWARD'); };
  const next = () => { void move('FORWARD'); };
  useFunctionKeys({ Enter: () => void browse(), F3: back, F7: previous, F8: next, onInvalid: () => setMessage(invalidKey) });
  return <Box sx={{ maxWidth: 1000, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CT00" program="COTRN00C" title="Transaction List" />
    <TextField label="Search Tran ID" value={search} onChange={e => setSearch(e.target.value)} inputProps={{ maxLength: 16 }} autoFocus />
    <p>Page {page.pageNumber} — Select S to view</p>
    <table><thead><tr><th>Select</th><th>Transaction ID</th><th>Date</th><th>Description</th><th>Amount</th></tr></thead>
      <tbody>{page.rows.map((row, i) => <tr key={String(row.transaction_id)}>
        <td><TextField inputProps={{ maxLength: 1, 'aria-label': `Select row ${i + 1}` }} value={selection[i] ?? ''} onChange={e => setSelection(old => { const values = [...old]; values[i] = e.target.value; return values; })} /></td>
        <td>{row.transaction_id}</td><td>{date(row.origination_timestamp)}</td><td>{String(row.description).slice(0, 26)}</td><td>{amount(row.amount)}</td>
      </tr>)}</tbody></table>
    <MessageLine error={message || null} />
    <FunctionKeyBar keys={[{ label: 'Enter=Select', onClick: () => void browse() }, { label: 'F3=Back', onClick: back }, { label: 'F7=Backward', onClick: previous }, { label: 'F8=Forward', onClick: next }]} />
  </Box>;
}

export function TransactionViewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { transactionId?: string; fromList?: boolean } | null;
  const [id, setId] = useState(state?.transactionId ?? '');
  const [view, setView] = useState<View>({ transaction: null, message: '' });
  const lookup = async () => {
    if (!id.trim()) { setView(old => ({ ...old, message: 'Tran ID can NOT be empty...' })); return; }
    try { setView(await request<View>('GET', `/api/transactions/view?transactionId=${encodeURIComponent(id)}`)); }
    catch (e) { setView({ transaction: null, message: (e as Error).message }); }
  };
  useEffect(() => { if (!getSession()) navigate('/login'); else if (id) void lookup(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const clear = () => { setId(''); setView({ transaction: null, message: '' }); };
  const back = () => navigate(state?.fromList ? '/transactions' : '/menu');
  const browse = () => navigate('/transactions');
  useFunctionKeys({ Enter: () => void lookup(), F3: back, F4: clear, F5: browse, onInvalid: () => setView(old => ({ ...old, message: invalidKey })) });
  return <Box sx={{ maxWidth: 1000, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CT01" program="COTRN01C" title="Transaction View" />
    <TextField label="Tran ID" value={id} onChange={e => setId(e.target.value)} inputProps={{ maxLength: 16 }} autoFocus />
    <dl>{Object.entries(view.transaction ?? {}).filter(([key]) => key !== 'filler').map(([key, value]) => <div key={key}><dt>{key.replace(/_/g, ' ')}</dt><dd>{key === 'amount' ? amount(value) : String(value)}</dd></div>)}</dl>
    <MessageLine error={view.message || null} />
    <FunctionKeyBar keys={[{ label: 'Enter=View', onClick: () => void lookup() }, { label: 'F3=Back', onClick: back }, { label: 'F4=Clear', onClick: clear }, { label: 'F5=Browse', onClick: browse }]} />
  </Box>;
}
