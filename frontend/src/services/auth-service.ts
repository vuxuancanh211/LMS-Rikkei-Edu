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
  const response = await httpClient.post<LoginResponse>('/auth/login', payload);
  return response.data;
}

export async function forgotPassword(payload: ForgotPasswordFormValues) {
  const response = await httpClient.post<MessageResponse>('/auth/forgot-password', payload);
  return response.data;
}

export async function resetPassword(payload: ResetPasswordFormValues) {
  const response = await httpClient.post<MessageResponse>('/auth/reset-password', payload);
  return response.data;
}

export async function refreshToken(refreshToken: string) {
  const response = await httpClient.post<RefreshTokenResponse>('/auth/refresh', { refreshToken }, { skipAuthRefresh: true });
  return response.data;
}

export async function logoutRequest() {
  const response = await httpClient.post<MessageResponse>('/auth/logout', undefined, { skipAuthRefresh: true });
  return response.data;
}
