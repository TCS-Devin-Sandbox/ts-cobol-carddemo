export interface TransactionType {
  trType: string;
  trDescription: string;
}

export interface TransactionTypePage {
  rows: TransactionType[];
  firstKey: string | null;
  lastKey: string | null;
  nextCursor: string | null;
  hasNext: boolean;
  hasPrev: boolean;
  pageNumber: number;
  message: string | null;
}

export interface PageQuery {
  cursor?: string | null;
  direction?: 'FORWARD' | 'BACKWARD';
  size?: number;
  page?: number;
  typeFilter?: string;
  descFilter?: string;
}

export interface Session {
  token: string;
  username: string;
  roles: string[];
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

const SESSION_KEY = 'carddemo.session';

export function getSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  return raw ? (JSON.parse(raw) as Session) : null;
}

export function setSession(session: Session | null): void {
  if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  else sessionStorage.removeItem(SESSION_KEY);
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const session = getSession();
  if (session) headers.Authorization = `Bearer ${session.token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'ERROR', data?.message ?? `HTTP ${res.status}`);
  }
  return data as T;
}

const BASE = '/api/transaction-types';

export const api = {
  login: (username: string, password: string) =>
    request<Session>('POST', '/api/auth/login', { username, password }),
  list: (q: PageQuery) => {
    const params = new URLSearchParams();
    Object.entries(q).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
    });
    const qs = params.toString();
    return request<TransactionTypePage>('GET', qs ? `${BASE}?${qs}` : BASE);
  },
  get: (trType: string) => request<TransactionType>('GET', `${BASE}/${encodeURIComponent(trType)}`),
  create: (t: TransactionType) => request<TransactionType>('POST', BASE, t),
  save: (trType: string, trDescription: string) =>
    request<TransactionType>('PUT', `${BASE}/${encodeURIComponent(trType)}`, { trDescription }),
  updateDescription: (trType: string, trDescription: string) =>
    request<TransactionType>('PATCH', `${BASE}/${encodeURIComponent(trType)}`, { trDescription }),
  remove: (trType: string) => request<void>('DELETE', `${BASE}/${encodeURIComponent(trType)}`),
};
