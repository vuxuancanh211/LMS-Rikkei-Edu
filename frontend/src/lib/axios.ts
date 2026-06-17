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

httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    if (status !== 401 || !originalRequest || originalRequest._retry || originalRequest.skipAuthRefresh) {
      return Promise.reject(error);
    }

    const { refreshToken: storedRefreshToken, setTokens, logout } = useAuthStore.getState();

    if (!storedRefreshToken) {
      logout();
      window.location.assign('/login');
      return Promise.reject(error);
    }

    try {
      originalRequest._retry = true;
      const { data: tokens } = await axios.post(
        `${httpClient.defaults.baseURL || ''}/auth/refresh`,
        { refreshToken: storedRefreshToken },
        { headers: { 'Content-Type': 'application/json' } },
      );
      setTokens(tokens);
      originalRequest.headers.Authorization = `${tokens.tokenType || 'Bearer'} ${tokens.accessToken}`;
      return httpClient(originalRequest);
    } catch (refreshError) {
      logout();
      window.location.assign('/login');
      return Promise.reject(refreshError);
    }
  },
);
