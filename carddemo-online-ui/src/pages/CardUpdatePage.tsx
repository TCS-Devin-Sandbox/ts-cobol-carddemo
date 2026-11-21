import { useEffect, useState } from 'react';
import { Box, TextField } from '@mui/material';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getSession, request } from '../api/client';
import { ScreenHeader } from '../components/ScreenHeader';
import { MessageLine } from '../components/MessageLine';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Fields { embossedName: string; activeStatus: string; expirationMonth: string; expirationYear: string }
interface Screen { token: string; accountId: string; cardNumber: string; fields: Fields; message: string; confirm: boolean }
export function CardUpdatePage() {
  const navigate = useNavigate();
  const saved = useLocation().state;
  const [accountId, setAccount] = useState<string>(saved?.accountId ?? '');
  const [cardNumber, setCard] = useState<string>(saved?.cardNumber ?? '');
  const [screen, setScreen] = useState<Screen | null>(null);
  const [fields, setFields] = useState<Fields>({ embossedName: '', activeStatus: '', expirationMonth: '', expirationYear: '' });
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>('Please enter Account and Card Number');
  const [busy, setBusy] = useState(false);
  const savedSuccessfully = screen?.message === 'Changes committed to database';
  const accept = (result: Screen) => { setScreen(result); setFields(result.fields); setInfo(result.message); };
  useEffect(() => {
    if (!saved?.cardNumber || !getSession()) return;
    let live = true;
    request<Screen>('GET', `/api/cards/update?${new URLSearchParams({ accountId: saved.accountId, cardNumber: saved.cardNumber })}`)
      .then(result => { if (live) accept(result); }).catch(e => { if (live) setError(e.message); });
    return () => { live = false; };
  }, [saved]);
  const act = async (key = 'ENTER') => {
    if (busy || (key === 'PF5' && !screen?.confirm) || (key === 'PF12' && !screen)) return;
    setBusy(true); setError(null); setInfo(null);
    if (savedSuccessfully) {
      if (saved?.list) navigate('/cards', { state: { ...saved.list, refresh: true } });
      else { setScreen(null); setAccount(''); setCard(''); setFields({ embossedName: '', activeStatus: '', expirationMonth: '', expirationYear: '' }); setInfo('Please enter Account and Card Number'); }
      setBusy(false); return;
    }
    try {
      accept(screen ? await request<Screen>('POST', '/api/cards/update', { token: screen.token, fields, key })
        : await request<Screen>('GET', `/api/cards/update?${new URLSearchParams({ accountId, cardNumber })}`));
    } catch (e) { setError(e instanceof Error ? e.message : 'Error reading Card Data File'); }
    finally { setBusy(false); }
  };
  const back = () => saved?.list ? navigate('/cards', { state: { ...saved.list, refresh: true } }) : navigate('/menu');
  useFunctionKeys({ Enter: () => void act(), F3: back, F5: () => void act(screen?.confirm ? 'PF5' : 'ENTER'),
    F12: () => void act(screen ? 'PF12' : 'ENTER'), onInvalid: () => void act() });
  if (!getSession()) return <Navigate to="/login" replace />;
  return <Box sx={{ maxWidth: 900, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CCUP" program="COCRDUPC" title="Credit Card Update" />
    <Box display="flex" gap={2}>
      <TextField label="Account ID" value={accountId} inputProps={{ maxLength: 11, readOnly: !!screen }} onChange={e => setAccount(e.target.value)} autoFocus />
      <TextField label="Card Number" value={cardNumber} inputProps={{ maxLength: 16, readOnly: !!screen }} onChange={e => setCard(e.target.value)} />
    </Box>
    <Box sx={{ mt: 3, display: 'grid', gap: 2 }}>
      {([['embossedName', 'Name on Card', 50], ['activeStatus', 'Active Status', 1], ['expirationMonth', 'Expiration Month', 2], ['expirationYear', 'Expiration Year', 4]] as const).map(([key, label, maxLength]) =>
        <TextField key={key} label={label} value={fields[key]} inputProps={{ maxLength, readOnly: !screen || screen.confirm || savedSuccessfully }}
          onChange={e => setFields({ ...fields, [key]: e.target.value })} />)}
    </Box>
    <MessageLine info={info} error={error} />
    <FunctionKeyBar keys={[{ label: 'Enter=Process', onClick: () => void act(), disabled: busy }, { label: 'F3=Exit', onClick: back },
      { label: 'F5=Save', onClick: () => void act('PF5'), disabled: busy || !screen?.confirm },
      { label: 'F12=Cancel', onClick: () => void act('PF12'), disabled: busy || !screen }]} />
  </Box>;
}
