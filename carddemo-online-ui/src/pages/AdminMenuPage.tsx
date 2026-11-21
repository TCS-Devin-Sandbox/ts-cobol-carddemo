import { useEffect, useState } from 'react';
import { Box, Button, TextField } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, request, setSession } from '../api/client';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

interface Menu { tranId: string; program: string; options: string[] }
interface Result { route?: string; message?: string; severity?: string }

export function AdminMenuPage({ main = false }: { main?: boolean }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [menu, setMenu] = useState<Menu | null>(null);
  const [option, setOption] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>((location.state as { info?: string } | null)?.info ?? null);
  const base = `/api/menus/${main ? 'main' : 'admin'}`;
  useEffect(() => {
    request<Menu>('GET', base).then(setMenu).catch((e: Error) => setError(e.message));
  }, [base]);
  const signoff = () => { setSession(null); navigate('/login'); };
  const select = async (value = option) => {
    setError(null); setInfo(null);
    try {
      const result = await request<Result>('POST', `${base}/select`, { option: value });
      if (result.route) navigate(result.route);
      else if (result.severity === 'error') setError(result.message ?? null);
      else setInfo(result.message ?? null);
    } catch (e) { setError(e instanceof ApiError ? e.message : 'Unable to load menu'); }
  };
  useFunctionKeys({ Enter: () => void select(), F3: signoff,
    onInvalid: () => setError('Invalid key pressed. Please see below...') });
  return <Box sx={{ maxWidth: 800, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId={main ? 'CM00' : 'CA00'} program={main ? 'COMEN01C' : 'COADM01C'} title={main ? 'Main Menu' : 'Admin Menu'} />
    {menu?.options.map((text, i) => <Box key={text}><Button onClick={() => void select(String(i + 1))}>{text}</Button></Box>)}
    <TextField label="Option" value={option} inputProps={{ maxLength: 2 }} onChange={e => setOption(e.target.value)} autoFocus />
    <MessageLine error={error} info={info} />
    <FunctionKeyBar keys={[{ label: 'Enter=Select', onClick: () => void select() }, { label: 'F3=Sign off', onClick: signoff }]} />
  </Box>;
}
