import { Box, Typography } from '@mui/material';

/** Header rows 1-2 of COTRTLI/COTRTUP: TRNNAME, PGMNAME, titles, CURDATE, CURTIME. */
export function ScreenHeader({ tranId, program, title, page }: {
  tranId: string;
  program: string;
  title: string;
  page?: number;
}) {
  const now = new Date();
  return (
    <Box sx={{ mb: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', fontFamily: 'monospace' }}>
        <span>Tran: {tranId}</span>
        <span>AWS Mainframe Modernization — CardDemo</span>
        <span>{now.toLocaleDateString()}</span>
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', fontFamily: 'monospace' }}>
        <span>Prog: {program}</span>
        <span />
        <span>{now.toLocaleTimeString()}</span>
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'baseline', gap: 4, mt: 2 }}>
        <Typography variant="h5" component="h1">{title}</Typography>
        {page !== undefined && <span data-testid="pageno">Page {page}</span>}
      </Box>
    </Box>
  );
}
