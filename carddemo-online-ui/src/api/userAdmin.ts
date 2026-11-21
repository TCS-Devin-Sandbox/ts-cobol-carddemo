import { request } from './client';

export interface User {
  userId: string;
  firstName: string;
  lastName: string;
  userType: string;
}

export interface UserPage {
  rows: User[];
  firstKey: string | null;
  lastKey: string | null;
  hasNext: boolean;
  message: string | null;
}

export interface UserEdits extends User {
  password: string;
}

const BASE = '/api/admin/users';
const recordUrl = (userId: string) => `${BASE}/record?${new URLSearchParams({ userId })}`;
type Result = { message: string };

export const userAdmin = {
  list: (startId: string, cursor: { after?: string; before?: string } = {}) =>
    request<UserPage>('GET', `${BASE}?${new URLSearchParams({ startId, ...cursor })}`),
  fetch: (userId: string) => request<User>('GET', recordUrl(userId)),
  add: (user: UserEdits) => request<Result>('POST', BASE, user),
  update: ({ userId, ...edits }: UserEdits) => request<Result>('PUT', recordUrl(userId), edits),
  remove: (userId: string) => request<Result>('DELETE', recordUrl(userId)),
};

export const INVALID_KEY = 'Invalid key pressed. Please see below...';
