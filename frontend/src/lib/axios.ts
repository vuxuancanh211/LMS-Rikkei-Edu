import axios from 'axios';
import { useAuthStore } from '../store';

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRefresh?: boolean;
    _retry?: boolean;
  }
}

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.request.use((config) => {
  const { accessToken, tokenType } = useAuthStore.getState();

  if (accessToken) {
    config.headers.Authorization = `${tokenType || 'Bearer'} ${accessToken}`;
  }

  return config;
});

/* ── Refresh token queue ─────────────────────────────────────────── */
let isRefreshing = false;
type QueueItem = { resolve: (token: string) => void; reject: (err: unknown) => void };
let waitingQueue: QueueItem[] = [];

function flushQueue(token: string | null, error: unknown = null) {
  waitingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token);
    else reject(error);
  });
  waitingQueue = [];
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

function triggerAccountLockedModal(data: unknown) {
  if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
    (window as any).__triggerAccountLockedModal(data);
    return true;
  }
  return false;
}

function isLockedMessage(message: string) {
  return /khóa|locked|disabled|vô hiệu/i.test(message || '');
}

export async function refreshAccessToken(): Promise<string> {
  const { refreshToken: storedRefreshToken, setTokens, logout } = useAuthStore.getState();

  if (!storedRefreshToken) {
    logout();
    redirectToLogin();
    throw new Error('Missing refresh token');
  }

  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      waitingQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;

  try {
    const { data: tokens } = await axios.post(
      `${httpClient.defaults.baseURL || ''}/auth/refresh`,
      { refreshToken: storedRefreshToken },
      { headers: { 'Content-Type': 'application/json' }, timeout: 10000 },
    );
    setTokens(tokens);
    const newToken = tokens.accessToken;
    flushQueue(newToken);
    return newToken;
  } catch (refreshError: any) {
    flushQueue(null, refreshError);
    const refreshStatus = refreshError?.response?.status;
    if (refreshStatus === 401 || refreshStatus === 403) {
      const msg = refreshError?.response?.data?.message || '';
      if (refreshStatus === 403 || isLockedMessage(msg)) {
        if (triggerAccountLockedModal(refreshError?.response?.data)) {
          throw refreshError;
        }
      }
      logout();
      redirectToLogin();
    }
    throw refreshError;
  } finally {
    isRefreshing = false;
  }
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    if (
      status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.skipAuthRefresh
    ) {
      if (status === 403 || (status === 401 && isLockedMessage(error.response?.data?.message || ''))) {
        if (triggerAccountLockedModal(error.response?.data)) {
          return Promise.reject(error);
        }
      }
      return Promise.reject(error);
    }

    if (isLockedMessage(error.response?.data?.message || '')) {
      if (triggerAccountLockedModal(error.response?.data)) {
        return Promise.reject(error);
      }
    }

    originalRequest._retry = true;

    try {
      const newToken = await refreshAccessToken();
      const { tokenType } = useAuthStore.getState();
      originalRequest.headers = originalRequest.headers || {};
      originalRequest.headers.Authorization = `${tokenType || 'Bearer'} ${newToken}`;
      return httpClient(originalRequest);
    } catch (refreshError) {
      return Promise.reject(refreshError);
    }
  },
);
