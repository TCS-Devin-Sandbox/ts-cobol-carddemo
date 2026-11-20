import { createElement } from 'react';
import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useFunctionKeys, type KeyHandlers } from './useFunctionKeys';

describe('useFunctionKeys', () => {
  it('lets Enter activate a focused button instead of processing the page', async () => {
    const enter = vi.fn();
    const save = vi.fn();
    renderHook(() => useFunctionKeys({ Enter: enter }));
    render(createElement('button', { onClick: save }, 'Save'));
    screen.getByRole('button').focus();
    await userEvent.keyboard('{Enter}');
    expect(save).toHaveBeenCalledOnce();
    expect(enter).not.toHaveBeenCalled();
  });

  it('does not confirm delete when F4 auto-repeats', () => {
    const handler = vi.fn();
    renderHook(() => useFunctionKeys({ F4: handler }));
    const first = new KeyboardEvent('keydown', { key: 'F4', cancelable: true });
    const repeat = new KeyboardEvent('keydown', { key: 'F4', repeat: true, cancelable: true });
    act(() => {
      window.dispatchEvent(first);
      window.dispatchEvent(repeat);
    });
    expect(handler).toHaveBeenCalledOnce();
    expect(first.defaultPrevented).toBe(true);
    expect(repeat.defaultPrevented).toBe(true);
  });

  it.each(['Enter', 'F2', 'F3', 'F4', 'F5', 'F7', 'F8', 'F10', 'F12'] as const)(
    'dispatches %s and prevents the browser action', (key) => {
      const handler = vi.fn();
      renderHook(() => useFunctionKeys({ [key]: handler }));
      const event = new KeyboardEvent('keydown', { key, cancelable: true });
      act(() => { window.dispatchEvent(event); });
      expect(handler).toHaveBeenCalledOnce();
      expect(event.defaultPrevented).toBe(true);
    },
  );

  it('ignores unbound keys and removes its listener', () => {
    const handler = vi.fn();
    const { unmount } = renderHook(() => useFunctionKeys({ F3: handler }));
    const event = new KeyboardEvent('keydown', { key: 'F4', cancelable: true });
    act(() => { window.dispatchEvent(event); });
    expect(event.defaultPrevented).toBe(false);
    unmount();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' }));
    expect(handler).not.toHaveBeenCalled();
  });

  it('uses the latest handlers including removed bindings', () => {
    const old = vi.fn();
    const current = vi.fn();
    const { rerender } = renderHook(({ handlers }: { handlers: KeyHandlers }) => useFunctionKeys(handlers),
      { initialProps: { handlers: { F3: old } as KeyHandlers } });
    rerender({ handlers: { F3: current } });
    act(() => { window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' })); });
    expect(old).not.toHaveBeenCalled();
    expect(current).toHaveBeenCalledOnce();
    rerender({ handlers: {} });
    act(() => { window.dispatchEvent(new KeyboardEvent('keydown', { key: 'F3' })); });
    expect(current).toHaveBeenCalledOnce();
  });
});
