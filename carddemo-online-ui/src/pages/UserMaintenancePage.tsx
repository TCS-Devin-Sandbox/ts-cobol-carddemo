import { useEffect, useRef, useState } from 'react';
import { Box, TextField } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { INVALID_KEY, userAdmin } from '../api/userAdmin';
import type { UserEdits } from '../api/userAdmin';
import { FunctionKeyBar } from '../components/FunctionKeyBar';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';
import { useFunctionKeys } from '../hooks/useFunctionKeys';

const screen = {
  add: { tranId: 'CU01', program: 'COUSR01C', title: 'Add User' },
  update: { tranId: 'CU02', program: 'COUSR02C', title: 'Update User' },
  delete: { tranId: 'CU03', program: 'COUSR03C', title: 'Delete User' },
};
const blank: UserEdits = { userId: '', firstName: '', lastName: '', password: '', userType: '' };
const fetchHint = { add: '', update: 'Press PF5 key to save your updates ...', delete: 'Press PF5 key to delete this user ...' };
const fields = [
  { key: 'userId', label: 'User ID', width: 8 },
  { key: 'firstName', label: 'First Name', width: 20 },
  { key: 'lastName', label: 'Last Name', width: 20 },
  { key: 'password', label: 'Password', width: 8 },
  { key: 'userType', label: 'User Type', width: 1 },
] as const;

export function UserMaintenancePage({ mode }: { mode: keyof typeof screen }) {
  const navigate = useNavigate();
  const location = useLocation();
  const selected = (location.state as { selected?: string } | null)?.selected ?? '';
  const [form, setForm] = useState<UserEdits>({ ...blank, userId: selected });
  const [busy, setBusy] = useState(Boolean(selected));
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const initialFocus = useRef<HTMLInputElement>(null);
  const adding = mode === 'add';
  const deleting = mode === 'delete';

  useEffect(() => {
    if (!selected) return;
    let active = true;
    userAdmin.fetch(selected).then(user => {
      if (!active) return;
      setForm({ ...user, password: '' });
      setInfo(fetchHint[mode]);
    }).catch((e: Error) => { if (active) setError(e.message); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [mode, selected]);

  function reset() {
    setForm(blank);
    setError(null);
    setInfo(null);
    initialFocus.current?.focus();
  }

  async function enter() {
    if (busy) return;
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      if (adding) {
        const result = await userAdmin.add(form);
        reset();
        setInfo(result.message);
      } else {
        setForm({ ...blank, userId: form.userId });
        const user = await userAdmin.fetch(form.userId);
        setForm({ ...user, password: '' });
        setInfo(fetchHint[mode]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to lookup User...');
    } finally {
      setBusy(false);
    }
  }

  const back = () => navigate(selected ? '/admin/users' : '/admin');
  const exit = () => navigate('/admin');
  async function write(leave = false) {
    if (busy) return;
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      const result = deleting ? await userAdmin.remove(form.userId) : await userAdmin.update(form);
      if (leave) {
        back();
        return;
      }
      if (deleting) reset();
      else setForm({ ...form, password: '' });
      setInfo(result.message);
    } catch (e) {
      if (leave && e instanceof ApiError && e.code === 'NO_CHANGE') back();
      else setError(e instanceof Error ? e.message : 'Unable to Update User...');
    } finally {
      setBusy(false);
    }
  }

  const goBack = mode === 'update' ? () => { if (form.userId) void write(true); else back(); } : back;
  const clear = () => { if (!busy) reset(); };
  const save = () => void write();
  useFunctionKeys({ Enter: () => void enter(), F3: goBack, F4: clear,
    F5: adding ? undefined : save, F12: adding ? undefined : exit,
    onInvalid: () => { setInfo(null); setError(INVALID_KEY); } });

  return <Box sx={{ maxWidth: 800, mx: 'auto', p: 2 }}>
    <ScreenHeader {...screen[mode]} />
    <Box sx={{ display: 'grid', gap: 2, maxWidth: 400 }}>
      {(adding ? [fields[1], fields[2], fields[0], fields[3], fields[4]] : fields)
        .filter(field => !deleting || field.key !== 'password').map(field => {
          const focused = field.key === (adding ? 'firstName' : 'userId');
          const readOnly = deleting && field.key !== 'userId';
          const label = field.key === 'userId' && !adding ? 'Enter User ID' : field.label;
          return <TextField key={field.key} label={label} autoFocus={focused}
            inputRef={focused ? initialFocus : undefined} value={form[field.key]}
            type={field.key === 'password' ? 'password' : 'text'}
            InputProps={{ readOnly: readOnly || busy }}
            inputProps={{ maxLength: field.width, autoComplete: field.key === 'password' ? 'new-password' : 'off' }}
            helperText={field.key === 'userType' ? 'A=Admin, U=User'
              : mode === 'update' && field.key === 'password' ? 'Leave blank to keep the existing password' : undefined}
            onChange={e => {
              const value = e.target.value;
              setForm(current => ({ ...(field.key === 'userId' && !adding ? blank : current), [field.key]: value }));
              if (field.key === 'userId' && !adding) { setInfo(null); setError(null); }
            }} />;
        })}
    </Box>
    <MessageLine error={error} info={info} />
    <FunctionKeyBar keys={[
      { label: adding ? 'Enter=Add User' : 'Enter=Fetch', onClick: () => void enter(), disabled: busy },
      { label: mode === 'update' ? 'F3=Save & Exit' : 'F3=Back', onClick: goBack, disabled: busy },
      { label: 'F4=Clear', onClick: clear, disabled: busy },
      { label: deleting ? 'F5=Delete' : 'F5=Save', onClick: save, disabled: busy, hidden: adding },
      { label: 'F12=Exit', onClick: exit, disabled: busy, hidden: adding },
    ]} />
  </Box>;
}
