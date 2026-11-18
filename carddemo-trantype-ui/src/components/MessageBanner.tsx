import { Box } from '@mui/material';

/** INFOMSG (centred, neutral; dark when empty) and ERRMSG (red, bright) lines of the 3270 maps. */
export function MessageBanner({ info, error }: { info?: string | null; error?: string | null }) {
  return (
    <Box sx={{ fontFamily: 'monospace', mt: 2 }}>
      <Box role="status" data-testid="infomsg" sx={{ textAlign: 'center', minHeight: '1.5em' }}>
        {info ?? ''}
      </Box>
      <Box role="alert" data-testid="errmsg" sx={{ color: 'error.main', fontWeight: 'bold', minHeight: '1.5em' }}>
        {error ?? ''}
      </Box>
    </Box>
  );
}
