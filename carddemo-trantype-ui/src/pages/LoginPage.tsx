import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, TextField, Typography } from '@mui/material';
import { api, ApiError, setSession } from '../api/client';
import { MessageLine } from '../components/MessageLine';

export function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSession(null);
    try {
      const session = await api.login(username, password);
      if (!session.roles.includes('ADMIN')) {
        setError('Access restricted to administrators');
        return;
      }
      setSession(session);
      navigate('/admin');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Login failed');
    }
  };

  return (
    <Box component="form" onSubmit={submit} sx={{ maxWidth: 360, mx: 'auto', mt: 8, display: 'grid', gap: 2 }}>
      <Typography variant="h5" component="h1">CardDemo sign-on</Typography>
      <TextField label="User ID" value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
      <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <Button type="submit" variant="contained">Sign on</Button>
      <MessageLine error={error} />
    </Box>
  );
}
