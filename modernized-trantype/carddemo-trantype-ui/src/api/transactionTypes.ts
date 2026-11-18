import { http, toApiError } from './client';

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
  size: number;
  infoMessage: string | null;
  errorMessage: string | null;
}

export interface ListParams {
  cursor?: string | null;
  direction?: 'next' | 'prev';
  size?: number;
  typeFilter?: string;
  descFilter?: string;
}

export interface LoginResponse {
  token: string;
  userId: string;
  userType: 'A' | 'U';
  expiresAt: string;
}

const BASE = '/api/transaction-types';

async function call<T>(fn: () => Promise<{ data: T }>): Promise<T> {
  try {
    return (await fn()).data;
  } catch (e) {
    throw toApiError(e);
  }
}

export const PAGE_SIZE = 7;

export function listTransactionTypes(params: ListParams): Promise<TransactionTypePage> {
  const query: Record<string, string | number> = { size: params.size ?? PAGE_SIZE };
  if (params.cursor) query.cursor = params.cursor;
  if (params.direction) query.direction = params.direction;
  if (params.typeFilter?.trim()) query.typeFilter = params.typeFilter.trim();
  if (params.descFilter?.trim()) query.descFilter = params.descFilter.trim();
  return call(() => http.get<TransactionTypePage>(BASE, { params: query }));
}

export const getTransactionType = (trType: string) =>
  call(() => http.get<TransactionType>(`${BASE}/${encodeURIComponent(trType)}`));

export const createTransactionType = (body: TransactionType) =>
  call(() => http.post<TransactionType>(BASE, body));

export const updateTransactionType = (trType: string, trDescription: string, upsert = false) =>
  call(() =>
    http.put<TransactionType>(`${BASE}/${encodeURIComponent(trType)}`, { trDescription }, { params: { upsert } }),
  );

export const deleteTransactionType = (trType: string) =>
  call(() => http.delete<void>(`${BASE}/${encodeURIComponent(trType)}`));

export const login = (userId: string, password: string) =>
  call(() => http.post<LoginResponse>('/api/auth/login', { userId, password }));
