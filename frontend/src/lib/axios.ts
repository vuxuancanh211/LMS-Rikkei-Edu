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
      return Promise.reject(error);
    }

    const { refreshToken: storedRefreshToken, setTokens, logout } = useAuthStore.getState();

    if (!storedRefreshToken) {
      logout();
      window.location.assign('/login');
      return Promise.reject(error);
    }

    /* Another request is already refreshing — queue this one */
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        waitingQueue.push({
          resolve: (newToken) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
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
        { headers: { 'Content-Type': 'application/json' } },
      );
      setTokens(tokens);
      const newToken = tokens.accessToken;
      originalRequest.headers.Authorization = `${tokens.tokenType || 'Bearer'} ${newToken}`;
      flushQueue(newToken);
      return httpClient(originalRequest);
    } catch (refreshError) {
      flushQueue(null, refreshError);
      logout();
      window.location.assign('/login');
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);
