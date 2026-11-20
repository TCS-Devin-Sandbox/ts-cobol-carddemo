import { clearSession as clearMg01Session, setAccessToken } from '@mg01/api/transactionTypesApi.js';

/** In-memory session (no web storage). Shares the token with the hosted MG-01 screens. */
let current = null;

export function getSession() {
  return current;
}

export function startSession({ accessToken, user }) {
  current = { accessToken, user };
  setAccessToken(accessToken);
}

export function endSession() {
  current = null;
  clearMg01Session();
}

export const isAdmin = (user) => user?.userType === 'A';
export const homeFor = (user) => (isAdmin(user) ? '/admin' : '/menu');
