import { Box, Button } from '@mui/material';
import { useEffect, useRef } from 'react';

export interface FunctionKey {
  key: 'Enter' | 'F2' | 'F3' | 'F4' | 'F5' | 'F7' | 'F8' | 'F10' | 'F12';
  label: string;
  onPress: () => void;
  visible?: boolean;
}

/** PF-key row (BMS row 24). Each key is a button and is bound to the physical key. */
export function FunctionKeys({ keys, onOtherKey }: { keys: FunctionKey[]; onOtherKey?: (key: string) => void }) {
  const keysRef = useRef(keys);
  keysRef.current = keys;
  const otherRef = useRef(onOtherKey);
  otherRef.current = onOtherKey;

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (!/^F\d{1,2}$/.test(e.key) && e.key !== 'Enter') return;
      if (e.key === 'Enter' && e.target instanceof HTMLButtonElement) return;
      const match = keysRef.current.find((k) => k.key === e.key && k.visible !== false);
      if (match) {
        e.preventDefault();
        match.onPress();
      } else if (e.key !== 'Enter' && otherRef.current) {
        e.preventDefault();
        otherRef.current(e.key);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  return (
    <Box component="nav" sx={{ display: 'flex', gap: 1, mt: 2, flexWrap: 'wrap' }}>
      {keys
        .filter((k) => k.visible !== false)
        .map((k) => (
          <Button key={k.key} variant={k.key === 'Enter' ? 'contained' : 'outlined'} size="small" onClick={k.onPress}>
            {k.label}
          </Button>
        ))}
    </Box>
  );
}
