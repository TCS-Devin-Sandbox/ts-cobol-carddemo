import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Paper, TextField, Typography } from '@mui/material';
import { login } from '../api/transactionTypes';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { MessageBanner } from '../components/MessageBanner';

/** Sign-on stand-in for COSGN00C. */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [userId, setUserId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const r = await login(userId, password);
      signIn({ token: r.token, userId: r.userId, userType: r.userType });
      navigate('/admin');
    } catch (err) {
      setError((err as ApiError).message);
    }
  };

  return (
    <Box display="flex" justifyContent="center" mt={8}>
      <Paper sx={{ p: 4, width: 360 }} component="form" onSubmit={submit}>
        <Typography variant="h6" mb={2}>CardDemo Sign-on</Typography>
        <TextField label="User ID" fullWidth margin="dense" value={userId}
          inputProps={{ maxLength: 8 }} onChange={(e) => setUserId(e.target.value)} />
        <TextField label="Password" type="password" fullWidth margin="dense" value={password}
          inputProps={{ maxLength: 8 }} onChange={(e) => setPassword(e.target.value)} />
        <Button type="submit" variant="contained" fullWidth sx={{ mt: 2 }}>Sign on</Button>
        <MessageBanner error={error} />
      </Paper>
    </Box>
  );
}
