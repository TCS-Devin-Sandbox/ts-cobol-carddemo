import { useEffect, useState } from 'react';
import { Box, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { INVALID_KEY, userAdmin } from '../api/userAdmin';
import type { UserPage } from '../api/userAdmin';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

export function UserListPage() {
  const navigate = useNavigate();
  const [startInput, setStartInput] = useState('');
  const [startId, setStartId] = useState('');
  const [page, setPage] = useState<UserPage>();
  const [number, setNumber] = useState(0);
  const [selections, setSelections] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    userAdmin.list('').then(result => {
      if (!active) return;
      setPage(result);
      setNumber(result.rows.length ? 1 : 0);
      setInfo(result.message);
    }).catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);

  async function load(start: string, target: number, cursor?: { after?: string; before?: string }) {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      const result = await userAdmin.list(start, cursor);
      setInfo(result.message);
      if (cursor && !result.rows.length) {
        if (cursor.after) setPage(p => p && { ...p, hasNext: false });
        return;
      }
      setPage(result);
      setNumber(result.rows.length ? target : 0);
      setStartId(start);
      setStartInput('');
      setSelections({});
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to lookup User...');
    } finally {
      setBusy(false);
    }
  }

  function enter() {
    if (busy) return;
    const row = page?.rows.find(user => selections[user.userId]?.trim());
    if (row) {
      const action = selections[row.userId].toUpperCase();
      if (action === 'U' || action === 'D') {
        navigate(`/admin/users/${action === 'U' ? 'update' : 'delete'}`, { state: { selected: row.userId } });
      } else {
        setInfo(null);
        setError('Invalid selection. Valid values are U and D');
      }
      return;
    }
    void load(startInput, 1);
  }

  function turn(forward: boolean) {
    if (busy) return;
    if (forward && page?.hasNext && page.lastKey) {
      void load(startId, number + 1, { after: page.lastKey });
    } else if (!forward && number > 1 && page?.firstKey) {
      void load(startId, number - 1, { before: page.firstKey });
    } else {
      setError(null);
      setInfo(forward ? 'You are already at the bottom of the page...' : 'You are already at the top of the page...');
    }
  }

  const back = () => navigate('/admin');
  useFunctionKeys({ Enter: enter, F3: back, F7: () => turn(false), F8: () => turn(true),
    onInvalid: () => { setInfo(null); setError(INVALID_KEY); } });

  return <Box sx={{ maxWidth: 850, mx: 'auto', p: 2 }}>
    <ScreenHeader tranId="CU00" program="COUSR00C" title="List Users" page={number} />
    <TextField label="Search User ID" value={startInput} onChange={e => setStartInput(e.target.value)}
      inputProps={{ maxLength: 8 }} autoFocus />
    <Table size="small" aria-label="Users">
      <TableHead><TableRow>{['Selection', 'User ID', 'First Name', 'Last Name', 'User Type'].map(text =>
        <TableCell key={text}>{text}</TableCell>)}</TableRow></TableHead>
      <TableBody>{page?.rows.map(user => <TableRow key={user.userId}>
        <TableCell><TextField size="small" value={selections[user.userId] ?? ''}
          inputProps={{ maxLength: 1, 'aria-label': `Selection for ${user.userId}` }}
          onChange={e => setSelections(values => ({ ...values, [user.userId]: e.target.value }))}
          sx={{ width: '3.5rem' }} /></TableCell>
        <TableCell>{user.userId}</TableCell><TableCell>{user.firstName}</TableCell>
        <TableCell>{user.lastName}</TableCell><TableCell>{user.userType}</TableCell>
      </TableRow>)}</TableBody>
    </Table>
    <Typography sx={{ mt: 1 }}>Type 'U' to Update or 'D' to Delete a User</Typography>
    <MessageLine error={error} info={info} />
    <FunctionKeyBar keys={[
      { label: 'Enter=Continue', onClick: enter, disabled: busy },
      { label: 'F3=Back', onClick: back },
      { label: 'F7=Backward', onClick: () => turn(false), disabled: busy },
      { label: 'F8=Forward', onClick: () => turn(true), disabled: busy },
    ]} />
  </Box>;
}
