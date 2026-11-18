import { Box, Typography } from '@mui/material';
import { useEffect, useState } from 'react';

/** BMS rows 1-2: Tran:/Prog:, TITLE01/TITLE02, Date: mm/dd/yy, Time: hh:mm:ss. */
export function formatDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}/${p(d.getDate())}/${p(d.getFullYear() % 100)}`;
}

export function formatTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

interface Props {
  tranId: string;
  programName: string;
  title: string;
}

export function ScreenHeader({ tranId, programName, title }: Props) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return (
    <Box component="header" sx={{ fontFamily: 'monospace', mb: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
        <span>
          Tran: <b data-testid="tranid">{tranId}</b>
        </span>
        <Typography component="span" sx={{ color: 'goldenrod', fontFamily: 'monospace' }}>
          AWS Mainframe Modernization
        </Typography>
        <span>Date: {formatDate(now)}</span>
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
        <span>
          Prog: <b data-testid="pgmname">{programName}</b>
        </span>
        <Typography component="span" sx={{ color: 'goldenrod', fontFamily: 'monospace' }}>
          CardDemo
        </Typography>
        <span>Time: {formatTime(now)}</span>
      </Box>
      <Typography variant="h6" align="center" sx={{ mt: 1 }}>
        {title}
      </Typography>
    </Box>
  );
}
