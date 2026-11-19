import axios, { AxiosError } from 'axios';

export interface ApiError {
  code: string;
  message: string;
  status?: number;
}

let authToken: string | null = null;
export const setAuthToken = (token: string | null) => {
  authToken = token;
};

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  if (authToken && !config.url?.startsWith('/api/auth/')) config.headers.Authorization = `Bearer ${authToken}`;
  return config;
});

/** Normalizes any axios failure to the API's {code, message} error body. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError && error.response?.data && typeof error.response.data === 'object') {
    const data = error.response.data as Partial<ApiError>;
    if (data.message) {
      return { code: data.code ?? 'ERROR', message: data.message, status: error.response.status };
    }
  }
  return { code: 'NETWORK_ERROR', message: 'Unable to reach the transaction type service' };
}
