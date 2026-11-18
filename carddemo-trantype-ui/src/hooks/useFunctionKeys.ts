import { useEffect, useRef } from 'react';

export type KeyHandlers = Partial<Record<'Enter' | 'F2' | 'F3' | 'F4' | 'F5' | 'F7' | 'F8' | 'F10' | 'F12', () => void>>;

/** Binds 3270-style AID keys (Enter, PFn) to handlers; unbound keys are ignored. */
export function useFunctionKeys(handlers: KeyHandlers) {
  const ref = useRef(handlers);
  ref.current = handlers;
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && e.target instanceof HTMLButtonElement) return;
      const handler = ref.current[e.key as keyof KeyHandlers];
      if (handler) {
        e.preventDefault();
        handler();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
}
