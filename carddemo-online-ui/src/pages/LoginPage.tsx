import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, TextField } from '@mui/material';
import { api, ApiError, setSession } from '../api/client';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

export function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [thanks, setThanks] = useState(false);
  const signoff = () => { setSession(null); setThanks(true); };
  useFunctionKeys({ F3: signoff, onInvalid: () => setError('Invalid key pressed. Please see below...') });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSession(null);
    try {
      const session = await api.login(username, password);
      setSession(session);
      navigate(session.userType === 'A' ? '/admin' : '/menu');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed');
    }
  };

  if (thanks) return <p>Thank you for using CardDemo application...</p>;
  return (
    <Box component="form" onSubmit={submit} sx={{ maxWidth: 360, mx: 'auto', mt: 8, display: 'grid', gap: 2 }}>
      <ScreenHeader tranId="CC00" program="COSGN00C" title="CardDemo sign-on" />
      <TextField label="User ID" inputProps={{ maxLength: 8 }} value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
      <TextField label="Password" inputProps={{ maxLength: 8 }} type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <Button type="submit" variant="contained">Sign on</Button>
      <MessageLine error={error} />
      <FunctionKeyBar keys={[{ label: 'F3=Exit', onClick: signoff }]} />
    </Box>
  );
}
