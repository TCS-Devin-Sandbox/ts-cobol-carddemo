import { Typography } from '@mui/material';

/** INFOMSG (protected, centred, 45) and ERRMSG (BRT red, 78) lines of the 3270 maps. */
export function MessageLine({ info, error }: { info?: string | null; error?: string | null }) {
  return (
    <>
      <Typography data-testid="infomsg" align="center" color="success.main" sx={{ minHeight: 24, mt: 2 }}>
        {info ?? ''}
      </Typography>
      <Typography
        data-testid="errmsg"
        role={error ? 'alert' : undefined}
        color="error"
        sx={{ minHeight: 24, fontWeight: 'bold' }}
      >
        {error ?? ''}
      </Typography>
    </>
  );
}
