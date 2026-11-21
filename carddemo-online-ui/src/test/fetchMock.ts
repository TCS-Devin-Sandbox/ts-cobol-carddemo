import { vi } from 'vitest';
import { setSession } from '../api/client';

export type Handler = (method: string, url: string, body: unknown) => { status: number; body?: unknown };

export function mockFetch(handler: Handler) {
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    const res = handler(method, url, body);
    return new Response(res.status === 204 ? null : JSON.stringify(res.body ?? {}), {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

export function loginAsAdmin() {
  setSession({ token: 't', userId: 'ADMIN', userType: 'A', roles: ['ADMIN'] });
}

export const rows = (from: number, count: number) =>
  Array.from({ length: count }, (_, i) => {
    const code = String(from + i).padStart(2, '0');
    return { trType: code, trDescription: `DESC ${code}` };
  });
