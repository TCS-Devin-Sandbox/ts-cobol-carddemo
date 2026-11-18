import { Box, List, ListItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { FunctionKeys } from '../components/FunctionKeys';
import { MessageBanner } from '../components/MessageBanner';
import { ScreenHeader } from '../components/ScreenHeader';
import { MSG } from '../messages';

/** CDEMO-ADMIN-OPTIONS (app/cpy/COADM02Y.cpy). Only options 5 and 6 belong to this module. */
export const ADMIN_OPTIONS: { num: number; name: string; path?: string }[] = [
  { num: 1, name: 'User List (Security)' },
  { num: 2, name: 'User Add (Security)' },
  { num: 3, name: 'User Update (Security)' },
  { num: 4, name: 'User Delete (Security)' },
  { num: 5, name: 'Transaction Type List/Update (Db2)', path: '/transaction-types' },
  { num: 6, name: 'Transaction Type Maintenance (Db2)', path: '/transaction-types/maintain' },
];

/** Placeholder for the admin menu CA00 / COADM01C. */
export function AdminMenuPage() {
  const navigate = useNavigate();
  const { signOff } = useAuth();
  const [option, setOption] = useState('');
  const [error, setError] = useState<string | null>(null);

  const enter = () => {
    const num = Number(option.trim());
    const selected = ADMIN_OPTIONS.find((o) => o.num === num);
    if (!/^\d{1,2}$/.test(option.trim()) || !selected) {
      setError(MSG.invalidOption);
      return;
    }
    if (!selected.path) {
      setError(`This option ${selected.name} is not part of the transaction type module`);
      return;
    }
    navigate(selected.path, { state: { from: 'menu' } });
  };

  return (
    <Box component="main" p={3} maxWidth={800}>
      <ScreenHeader tranId="CA00" programName="COADM01C" title="Admin Menu" />
      <List dense>
        {ADMIN_OPTIONS.map((o) => (
          <ListItem key={o.num} sx={{ fontFamily: 'monospace' }}>
            {`${o.num}. ${o.name}`}
          </ListItem>
        ))}
      </List>
      <TextField
        label="Please select an option"
        value={option}
        onChange={(e) => setOption(e.target.value)}
        inputProps={{ maxLength: 2, 'aria-label': 'option' }}
        autoFocus
      />
      <MessageBanner error={error} />
      <FunctionKeys
        keys={[
          { key: 'Enter', label: 'ENTER=Continue', onPress: enter },
          {
            key: 'F3',
            label: 'F3=Exit',
            onPress: () => {
              void signOff().then(() => navigate('/login'));
            },
          },
        ]}
      />
    </Box>
  );
}
