import { Box, Button, TextField } from '@mui/material';
import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApi } from '../api/ApiContext';
import { ApiError } from '../api/client';
import { useAuth } from '../auth';
import { MessageBanner } from '../components/MessageBanner';
import { ScreenHeader } from '../components/ScreenHeader';
import { MSG } from '../messages';

/** Sign-on placeholder (COSGN00C / CC00). */
export function LoginPage() {
  const api = useApi();
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return setError(MSG.enterUserId);
    if (!password.trim()) return setError(MSG.enterPassword);
    try {
      const user = await api.login(username.trim(), password);
      setUser(user);
      navigate('/admin');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err));
    }
  };

  return (
    <Box component="main" p={3} maxWidth={800}>
      <ScreenHeader tranId="CC00" programName="COSGN00C" title="Sign on" />
      <Box component="form" onSubmit={submit} sx={{ display: 'flex', flexDirection: 'column', gap: 2, maxWidth: 300 }}>
        <TextField label="User ID" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <Button type="submit" variant="contained">
          ENTER=Sign-in
        </Button>
      </Box>
      <MessageBanner error={error} />
    </Box>
  );
}
