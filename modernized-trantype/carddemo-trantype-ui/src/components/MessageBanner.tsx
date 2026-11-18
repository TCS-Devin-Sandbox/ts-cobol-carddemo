import { Box, Typography } from '@mui/material';

/** INFOMSG (centered, neutral) and ERRMSG (red, bright) lines. */
export function MessageBanner({ info, error }: { info?: string | null; error?: string | null }) {
  return (
    <Box mt={2} minHeight={56}>
      <Typography align="center" data-testid="info-message" sx={{ minHeight: 24 }}>
        {info ?? ''}
      </Typography>
      <Typography color="error" fontWeight={700} role="alert" data-testid="error-message" sx={{ minHeight: 24 }}>
        {error ?? ''}
      </Typography>
    </Box>
  );
}
