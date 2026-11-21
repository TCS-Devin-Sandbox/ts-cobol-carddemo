import { useEffect, useState } from 'react';
import { Box, Table, TableBody, TableCell, TableHead, TableRow, TextField } from '@mui/material';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getSession, request } from '../api/client';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface CardRow { accountId: string; cardNumber: string; activeStatus: string }
interface CardPage {
  rows: CardRow[]; firstKey: string | null; nextCursor: string | null;
  pageNumber: number; lastPageShown: boolean; message: string | null; info: string | null;
}
interface CardDetail extends CardRow { embossedName: string; expirationMonth: string; expirationYear: string }
interface ListState { accountId: string; cardNumber: string; page: CardPage | null }
interface ViewState { accountId: string; cardNumber: string; list: ListState }
const prompt = 'Please enter Account and Card Number';

function failure(error: unknown) { return error instanceof Error ? error.message : 'Error reading Card Data File'; }

export function CardListPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const saved = location.state as ListState | null;
  const [accountId, setAccount] = useState(saved?.accountId ?? '');
  const [cardNumber, setCard] = useState(saved?.cardNumber ?? '');
  const [page, setPage] = useState<CardPage | null>(saved?.page ?? null);
  const [actions, setActions] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(saved?.page?.info ?? null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (saved?.page || !getSession()) return;
    let live = true;
    request<CardPage>('GET', '/api/cards').then(result => {
      if (live) { setPage(result); setInfo(result.info); setError(result.message); }
    }).catch(e => { if (live) setError(failure(e)); });
    return () => { live = false; };
  }, [saved]);

  const load = async (key = 'ENTER') => {
    if (busy) return;
    setBusy(true); setError(null);
    try {
      const params = new URLSearchParams({ accountId, cardNumber, key, pageNumber: String(page?.pageNumber ?? 1),
        cursor: key === 'PF7' ? page?.firstKey ?? '' : page?.nextCursor ?? '',
        firstKey: page?.firstKey ?? '', lastPageShown: String(page?.lastPageShown ?? false) });
      const result = await request<CardPage>('GET', `/api/cards?${params}`);
      setPage(result); setInfo(result.info); setError(result.message); setActions({});
    } catch (e) { setError(failure(e)); } finally { setBusy(false); }
  };
  const enter = async () => {
    if (busy) return;
    if (!Object.values(actions).some(a => a.trim())) { await load(); return; }
    setBusy(true); setError(null);
    try {
      await request<CardPage>('GET', `/api/cards?${new URLSearchParams({ accountId, cardNumber })}`);
      const result = await request<{ route?: string; message?: string; accountId: string; cardNumber: string }>(
        'POST', '/api/cards/select', { selections: page?.rows.map(row => ({ ...row, action: actions[row.cardNumber] ?? '' })) ?? [] });
      if (result.route) navigate(result.route, { state: { accountId: result.accountId, cardNumber: result.cardNumber,
        list: { accountId, cardNumber, page } } satisfies ViewState });
      else setInfo(result.message ?? null);
    } catch (e) { setError(failure(e)); } finally { setBusy(false); }
  };
  const back = () => navigate('/menu');
  useFunctionKeys({ Enter: () => void enter(), F3: back, F7: () => void load('PF7'), F8: () => void load('PF8'),
    onInvalid: () => void enter() });
  if (!getSession()) return <Navigate to="/login" replace />;
  return <Box sx={{ maxWidth: 900, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CCLI" program="COCRDLIC" title="Credit Card List" page={page?.pageNumber ?? 1} />
    <Box display="flex" gap={2}>
      <TextField label="Account ID" value={accountId} inputProps={{ maxLength: 11 }} onChange={e => setAccount(e.target.value)} autoFocus />
      <TextField label="Card Number" value={cardNumber} inputProps={{ maxLength: 16 }} onChange={e => setCard(e.target.value)} />
    </Box>
    <Table size="small"><TableHead><TableRow><TableCell>Select</TableCell><TableCell>Account ID</TableCell><TableCell>Card Number</TableCell><TableCell>Active Status</TableCell></TableRow></TableHead>
      <TableBody>{page?.rows.map(row => <TableRow key={row.cardNumber}>
        <TableCell><TextField size="small" value={actions[row.cardNumber] ?? ''} inputProps={{ maxLength: 1, 'aria-label': `action ${row.cardNumber}` }}
          onChange={e => setActions({ ...actions, [row.cardNumber]: e.target.value })} /></TableCell>
        <TableCell>{row.accountId}</TableCell><TableCell>{row.cardNumber}</TableCell><TableCell>{row.activeStatus}</TableCell>
      </TableRow>)}</TableBody></Table>
    <MessageLine info={info} error={error} />
    <FunctionKeyBar keys={[{ label: 'Enter=Select', onClick: () => void enter(), disabled: busy },
      { label: 'F3=Back', onClick: back }, { label: 'F7=Page Up', onClick: () => void load('PF7'), disabled: busy },
      { label: 'F8=Page Dn', onClick: () => void load('PF8'), disabled: busy }]} />
  </Box>;
}

export function CardViewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const saved = location.state as ViewState | null;
  const [accountId, setAccount] = useState(saved?.accountId ?? '');
  const [cardNumber, setCard] = useState(saved?.cardNumber ?? '');
  const [detail, setDetail] = useState<CardDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!saved || !getSession()) return;
    let live = true;
    request<CardDetail>('GET', `/api/cards/view?${new URLSearchParams({ accountId: saved.accountId, cardNumber: saved.cardNumber })}`)
      .then(result => { if (live) setDetail(result); }).catch(e => { if (live) setError(failure(e)); });
    return () => { live = false; };
  }, [saved]);
  const search = async () => {
    if (busy) return;
    setBusy(true); setError(null); setDetail(null);
    try { setDetail(await request<CardDetail>('GET', `/api/cards/view?${new URLSearchParams({ accountId, cardNumber })}`)); }
    catch (e) { setError(failure(e)); } finally { setBusy(false); }
  };
  const back = () => saved?.list ? navigate('/cards', { state: saved.list }) : navigate('/menu');
  useFunctionKeys({ Enter: () => void search(), F3: back, onInvalid: () => void search() });
  if (!getSession()) return <Navigate to="/login" replace />;
  return <Box sx={{ maxWidth: 900, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CCDL" program="COCRDSLC" title="Credit Card View" />
    <Box display="flex" gap={2}>
      <TextField label="Account ID" value={accountId} inputProps={{ maxLength: 11, readOnly: !!saved?.list }} onChange={e => setAccount(e.target.value)} autoFocus />
      <TextField label="Card Number" value={cardNumber} inputProps={{ maxLength: 16, readOnly: !!saved?.list }} onChange={e => setCard(e.target.value)} />
    </Box>
    <Box sx={{ mt: 3, display: 'grid', gap: 2 }}>
      <TextField label="Name on Card" value={detail?.embossedName ?? ''} inputProps={{ readOnly: true }} />
      <TextField label="Expiration Month" value={detail?.expirationMonth ?? ''} inputProps={{ readOnly: true }} />
      <TextField label="Expiration Year" value={detail?.expirationYear ?? ''} inputProps={{ readOnly: true }} />
      <TextField label="Active Status" value={detail?.activeStatus ?? ''} inputProps={{ readOnly: true }} />
    </Box>
    <MessageLine info={detail ? '   Displaying requested details' : prompt} error={error} />
    <FunctionKeyBar keys={[{ label: 'Enter=Search', onClick: () => void search(), disabled: busy }, { label: 'F3=Back', onClick: back }]} />
  </Box>;
}
