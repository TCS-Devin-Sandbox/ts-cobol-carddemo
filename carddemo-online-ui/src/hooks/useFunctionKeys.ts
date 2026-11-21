import { useEffect, useRef } from 'react';

export type KeyHandlers = Partial<Record<'Enter' | 'F2' | 'F3' | 'F4' | 'F5' | 'F7' | 'F8' | 'F10' | 'F12' | 'onInvalid', () => void>>;

/** Binds 3270-style AID keys (Enter, PFn) to handlers; unbound keys are ignored. */
export function useFunctionKeys(handlers: KeyHandlers) {
  const ref = useRef(handlers);
  useEffect(() => { ref.current = handlers; });
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && e.target instanceof Element && e.target.closest(
        'button, a[href], textarea, select, summary, input[type="button"], input[type="submit"], '
        + 'input[type="reset"], [contenteditable="true"], [contenteditable=""], [role="button"], [role="link"]',
      )) {
        if (e.repeat) e.preventDefault();
        return;
      }
      const handler = ref.current[e.key as keyof KeyHandlers];
      if (!handler && ref.current.onInvalid && (/^F\d+$/.test(e.key) || e.key === 'Escape')) {
        e.preventDefault();
        ref.current.onInvalid?.();
      }
      if (handler) {
        e.preventDefault();
        if (!e.repeat) handler();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
}
