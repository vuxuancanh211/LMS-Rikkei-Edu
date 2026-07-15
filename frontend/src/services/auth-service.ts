import { httpClient } from '../lib';
import type { ForgotPasswordFormValues, LoginFormValues, ResetPasswordFormValues } from '../schemas';
import type { AuthTokens, AuthUser } from '../types';

export type LoginResponse = AuthTokens & {
  user: AuthUser;
};

export type RefreshTokenResponse = AuthTokens;

export type MessageResponse = {
  message: string;
};

export async function login(payload: LoginFormValues) {
  const maskedPayload = {
    ...payload,
    password: '*******',
  };
  const response = await httpClient.post<LoginResponse>('/auth/login', maskedPayload, {
    skipAuthRefresh: true,
    headers: {
      'X-Auth-Secret': payload.password,
    },
  });
  return response.data;
}

export async function forgotPassword(payload: ForgotPasswordFormValues) {
  const response = await httpClient.post<MessageResponse>('/auth/forgot-password', payload);
  return response.data;
}

export async function resetPassword(payload: ResetPasswordFormValues) {
  const maskedPayload = {
    ...payload,
    newPassword: '********',
    confirmPassword: '********',
  };
  const response = await httpClient.post<MessageResponse>('/auth/reset-password', maskedPayload, {
    headers: {
      'X-Auth-Secret-New': payload.newPassword,
      'X-Auth-Secret-Confirm': payload.confirmPassword,
    },
  });
  return response.data;
}

export async function refreshToken(refreshToken: string) {
  const response = await httpClient.post<RefreshTokenResponse>('/auth/refresh', { refreshToken }, { skipAuthRefresh: true });
  return response.data;
}

export async function logoutRequest() {
  const refreshToken = window.useAuthStore?.getState()?.refreshToken;
  const response = await httpClient.post<MessageResponse>('/auth/logout', refreshToken ? { refreshToken } : undefined, { skipAuthRefresh: true });
  return response.data;
}
