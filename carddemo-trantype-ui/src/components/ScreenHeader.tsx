import { useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';

export const TITLE01 = 'AWS Mainframe Modernization';
export const TITLE02 = 'CardDemo';

const pad = (n: number) => String(n).padStart(2, '0');
export const formatDate = (d: Date) => `${pad(d.getMonth() + 1)}/${pad(d.getDate())}/${pad(d.getFullYear() % 100)}`;
export const formatTime = (d: Date) => `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

interface Props {
  tranId: string;
  programName: string;
  title: string;
}

/** TRNNAME / PGMNAME / TITLE01 / TITLE02 / CURDATE / CURTIME (2100 / 3100-SCREEN-INIT). */
export function ScreenHeader({ tranId, programName, title }: Props) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return (
    <Box mb={2}>
      <Box display="flex" justifyContent="space-between" color="primary.main">
        <Typography variant="body2" data-testid="tran-id">Tran: {tranId}</Typography>
        <Typography variant="body2" color="warning.main" fontWeight={600}>{TITLE01}</Typography>
        <Typography variant="body2">Date: {formatDate(now)}</Typography>
      </Box>
      <Box display="flex" justifyContent="space-between" color="primary.main">
        <Typography variant="body2" data-testid="program-name">Prog: {programName}</Typography>
        <Typography variant="body2" color="warning.main" fontWeight={600}>{TITLE02}</Typography>
        <Typography variant="body2">Time: {formatTime(now)}</Typography>
      </Box>
      <Typography variant="h6" align="center" mt={1}>{title}</Typography>
    </Box>
  );
}
