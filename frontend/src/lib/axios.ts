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
      if (status === 403 || (status === 401 && /khóa|locked|disabled|vô hiệu/i.test(error.response?.data?.message || ''))) {
        if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
          (window as any).__triggerAccountLockedModal(error.response?.data);
          return Promise.reject(error);
        }
      }
      return Promise.reject(error);
    }

    if (/khóa|locked|disabled|vô hiệu/i.test(error.response?.data?.message || '')) {
      if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
        (window as any).__triggerAccountLockedModal(error.response?.data);
        return Promise.reject(error);
      }
    }

    const { refreshToken: storedRefreshToken, setTokens, logout } = useAuthStore.getState();

    if (!storedRefreshToken) {
      if (status === 403 || /khóa|locked|disabled|vô hiệu/i.test(error.response?.data?.message || '')) {
        if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
          (window as any).__triggerAccountLockedModal(error.response?.data);
          return Promise.reject(error);
        }
      }
      logout();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
      return Promise.reject(error);
    }

    /* Another request is already refreshing — queue this one */
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        waitingQueue.push({
          resolve: (newToken) => {
            originalRequest._retry = true;
            const { tokenType } = useAuthStore.getState();
            originalRequest.headers.Authorization = `${tokenType || 'Bearer'} ${newToken}`;
            resolve(httpClient(originalRequest));
          },
          reject,
        });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data: tokens } = await axios.post(
        `${httpClient.defaults.baseURL || ''}/auth/refresh`,
        { refreshToken: storedRefreshToken },
        { headers: { 'Content-Type': 'application/json' }, timeout: 10000 },
      );
      setTokens(tokens);
      const newToken = tokens.accessToken;
      originalRequest.headers.Authorization = `${tokens.tokenType || 'Bearer'} ${newToken}`;
      flushQueue(newToken);
      return httpClient(originalRequest);
    } catch (refreshError: any) {
      flushQueue(null, refreshError);
      const refreshStatus = refreshError?.response?.status;
      if (refreshStatus === 401 || refreshStatus === 403) {
        const msg = refreshError?.response?.data?.message || '';
        if (refreshStatus === 403 || /khóa|locked|disabled|vô hiệu/i.test(msg)) {
          if (typeof window !== 'undefined' && (window as any).__triggerAccountLockedModal) {
            (window as any).__triggerAccountLockedModal(refreshError?.response?.data);
            return Promise.reject(refreshError);
          }
        }
        logout();
        if (window.location.pathname !== '/login') {
          window.location.assign('/login');
        }
      }
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);
