import { render } from '@testing-library/react';
import { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { vi } from 'vitest';
import { ApiProvider } from '../api/ApiContext';
import { Api, ApiError, TransactionType, TransactionTypePage } from '../api/client';
import { AuthProvider } from '../auth';

export function mockApi(overrides: Partial<Record<keyof Api, unknown>> = {}): Api {
  return {
    csrf: vi.fn().mockResolvedValue({ token: 't', headerName: 'X-XSRF-TOKEN' }),
    login: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    me: vi.fn().mockResolvedValue({ username: 'admin', roles: ['ADMIN'] }),
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    save: vi.fn(),
    remove: vi.fn(),
    ...overrides,
  } as unknown as Api;
}

export function apiError(status: number, code: ApiError['code'], message: string, sqlcode?: number) {
  return new ApiError(status, code, message, sqlcode);
}

export const SEED: TransactionType[] = [
  { trType: '01', description: 'PURCHASE' },
  { trType: '02', description: 'PAYMENT' },
  { trType: '03', description: 'CREDIT' },
  { trType: '04', description: 'AUTHORIZATION' },
  { trType: '05', description: 'REFUND' },
  { trType: '06', description: 'REVERAL' },
  { trType: '07', description: 'ADJUSTMENT' },
];

export function page(rows: TransactionType[], extra: Partial<TransactionTypePage> = {}): TransactionTypePage {
  return {
    rows,
    firstKey: rows[0]?.trType ?? null,
    lastKey: rows[rows.length - 1]?.trType ?? null,
    nextCursor: null,
    hasNext: false,
    hasPrev: false,
    pageNumber: 1,
    message: null,
    ...extra,
  };
}

export function LocationProbe() {
  const loc = useLocation();
  return <div data-testid="location">{loc.pathname}</div>;
}

export function renderAt(api: Api, path: string, element: ReactElement, routePath = path, state?: unknown) {
  return render(
    <ApiProvider value={api}>
      <MemoryRouter initialEntries={[{ pathname: path, state }]}>
        <AuthProvider>
          <Routes>
            <Route path={routePath} element={element} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ApiProvider>,
  );
}
