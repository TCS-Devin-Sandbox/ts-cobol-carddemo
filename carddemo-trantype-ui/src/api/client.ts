import axios, { AxiosError } from 'axios';

export interface TransactionType {
  trType: string;
  description: string;
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

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'NOT_FOUND'
  | 'DUPLICATE_KEY'
  | 'CHILD_RECORDS_EXIST'
  | 'LOCK_ERROR'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'INTERNAL_ERROR';

export interface UserInfo {
  username: string;
  roles: string[];
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ErrorCode,
    message: string,
    public readonly sqlcode?: number,
  ) {
    super(message);
  }
}

export interface ListParams {
  cursor?: string | null;
  direction?: 'forward' | 'backward';
  page?: number;
  size?: number;
  typeFilter?: string;
  descFilter?: string;
}

const http = axios.create({
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

function toApiError(e: unknown): ApiError {
  const err = e as AxiosError<{ code?: ErrorCode; message?: string; sqlcode?: number }>;
  const data = err.response?.data;
  return new ApiError(
    err.response?.status ?? 0,
    data?.code ?? 'INTERNAL_ERROR',
    data?.message ?? err.message ?? 'Unexpected error',
    data?.sqlcode,
  );
}

async function call<T>(fn: () => Promise<{ data: T }>): Promise<T> {
  try {
    return (await fn()).data;
  } catch (e) {
    throw toApiError(e);
  }
}

const BASE = '/api/transaction-types';

export const api = {
  csrf: () => call(() => http.get<{ token: string; headerName: string }>('/api/auth/csrf')),
  login: async (username: string, password: string) => {
    await api.csrf();
    return call(() => http.post<UserInfo>('/api/auth/login', { username, password }));
  },
  logout: () => call(() => http.post<void>('/api/auth/logout')),
  me: () => call(() => http.get<UserInfo>('/api/auth/me')),
  list: (params: ListParams) =>
    call(() =>
      http.get<TransactionTypePage>(BASE, {
        params: {
          cursor: params.cursor ?? undefined,
          direction: params.direction ?? 'forward',
          page: params.page ?? 1,
          size: params.size ?? 7,
          typeFilter: params.typeFilter || undefined,
          descFilter: params.descFilter || undefined,
        },
      }),
    ),
  get: (trType: string) => call(() => http.get<TransactionType>(`${BASE}/${encodeURIComponent(trType)}`)),
  create: (trType: string, description: string) =>
    call(() => http.post<TransactionType>(`${BASE}/${encodeURIComponent(trType)}`, { description })),
  save: (trType: string, description: string, upsert: boolean) =>
    call(() =>
      http.put<TransactionType>(`${BASE}/${encodeURIComponent(trType)}`, { description }, { params: { upsert } }),
    ),
  remove: (trType: string) => call(() => http.delete<void>(`${BASE}/${encodeURIComponent(trType)}`)),
};

export type Api = typeof api;
