import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthTokens, AuthUser, UserRole } from '../types';

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number | null;
  user: AuthUser | null;
  role: UserRole | null;
  isAuthenticated: boolean;
  loginSuccess: (tokens: AuthTokens, user: AuthUser) => void;
  setTokens: (tokens: AuthTokens) => void;
  logout: () => void;
};

export const mapApiRole = (role?: string | null): UserRole | null => {
  switch (role) {
    case 'ADMIN':
      return 'admin';
    case 'INSTRUCTOR':
      return 'instructor';
    case 'STUDENT':
      return 'student';
    default:
      return null;
  }
};

const initialAuthState = {
  accessToken: null,
  refreshToken: null,
  tokenType: 'Bearer',
  expiresIn: null,
  user: null,
  role: null,
  isAuthenticated: false,
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      ...initialAuthState,
      loginSuccess: (tokens, user) =>
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          tokenType: tokens.tokenType || 'Bearer',
          expiresIn: tokens.expiresIn,
          user,
          role: mapApiRole(user.role),
          isAuthenticated: true,
        }),
      setTokens: (tokens) =>
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          tokenType: tokens.tokenType || 'Bearer',
          expiresIn: tokens.expiresIn,
          isAuthenticated: true,
        }),
      logout: () => set(initialAuthState),
    }),
    {
      name: 'rikkei-edu-auth',
    },
  ),
);
