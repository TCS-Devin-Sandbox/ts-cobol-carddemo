import { useNavigate } from 'react-router-dom';
import { Box, Button, List, ListItemButton, ListItemText, Paper } from '@mui/material';
import { ScreenHeader } from '../components/ScreenHeader';
import { useAuth } from '../auth/AuthContext';

/** Placeholder for the CardDemo admin menu COADM01C (options from COADM02Y). */
const OPTIONS = [
  { num: 1, name: 'User List (Security)', route: null },
  { num: 2, name: 'User Add (Security)', route: null },
  { num: 3, name: 'User Update (Security)', route: null },
  { num: 4, name: 'User Delete (Security)', route: null },
  { num: 5, name: 'Transaction Type List/Update (Db2)', route: '/transaction-types' },
  { num: 6, name: 'Transaction Type Maintenance (Db2)', route: '/transaction-types/maintain' },
];

export function AdminMenuPage() {
  const navigate = useNavigate();
  const { signOut } = useAuth();
  return (
    <Box p={3} maxWidth={900} mx="auto">
      <ScreenHeader tranId="CA00" programName="COADM01C" title="Admin Menu" />
      <Paper>
        <List>
          {OPTIONS.map((o) => (
            <ListItemButton key={o.num} disabled={!o.route} onClick={() => o.route && navigate(o.route)}>
              <ListItemText primary={`${o.num}. ${o.name}`} secondary={o.route ? null : 'Not migrated'} />
            </ListItemButton>
          ))}
        </List>
      </Paper>
      <Button sx={{ mt: 2 }} onClick={() => { signOut(); navigate('/login'); }}>F3=Sign off</Button>
    </Box>
  );
}
