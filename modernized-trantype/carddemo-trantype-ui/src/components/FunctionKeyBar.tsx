import { Box, Button } from '@mui/material';

export interface FunctionKeyButton {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  hidden?: boolean;
}

/** Row 24 PF-key legend rendered as buttons. */
export function FunctionKeyBar({ keys }: { keys: FunctionKeyButton[] }) {
  return (
    <Box display="flex" gap={1} flexWrap="wrap" mt={2}>
      {keys
        .filter((k) => !k.hidden)
        .map((k) => (
          <Button key={k.label} variant="outlined" size="small" onClick={k.onClick} disabled={k.disabled}>
            {k.label}
          </Button>
        ))}
    </Box>
  );
}
