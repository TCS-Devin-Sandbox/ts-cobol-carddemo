import { Box, Button, List, ListItemButton, ListItemText } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { setSession } from '../api/client';
import { MessageLine } from '../components/MessageLine';
import { ScreenHeader } from '../components/ScreenHeader';

/** Placeholder for the admin menu CA00 / COADM01C (only options 5 and 6 are modernised). */
export function AdminMenuPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const info = (location.state as { info?: string } | null)?.info ?? null;
  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', p: 2 }}>
      <ScreenHeader tranId="CA00" program="COADM01C" title="Admin Menu" />
      <List>
        <ListItemButton onClick={() => navigate('/transaction-types')}>
          <ListItemText primary="5. Transaction Type List/Update (Db2)" />
        </ListItemButton>
        <ListItemButton onClick={() => navigate('/transaction-types/maintain')}>
          <ListItemText primary="6. Transaction Type Maintenance (Db2)" />
        </ListItemButton>
      </List>
      <Button onClick={() => { setSession(null); navigate('/login'); }}>Sign off</Button>
      <MessageLine info={info} />
    </Box>
  );
}
