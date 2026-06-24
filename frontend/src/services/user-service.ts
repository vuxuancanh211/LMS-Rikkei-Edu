import { httpClient } from '../lib';
import type { AdminUserCreateRequest, AdminUserDetailResponse, AdminUserListRequest, AdminUserUpdateRequest, MessageResponse, PagedResponse, UserResponse } from '../types';

export async function getUsers(params?: AdminUserListRequest) {
  const response = await httpClient.get<PagedResponse<UserResponse>>('/admin/users', { params });
  return response.data;
}

export async function getUserDetail(id: string) {
  const response = await httpClient.get<AdminUserDetailResponse>(`/admin/users/${id}`);
  return response.data;
}

export async function createUser(data: AdminUserCreateRequest) {
  const response = await httpClient.post<UserResponse>('/admin/users', data);
  return response.data;
}

export async function updateUser(id: string, data: AdminUserUpdateRequest) {
  const response = await httpClient.put<UserResponse>(`/admin/users/${id}`, data);
  return response.data;
}

export async function adminResetPassword(id: string, reason?: string) {
  const response = await httpClient.post<MessageResponse>(`/admin/users/${id}/reset-password`, { reason });
  return response.data;
}
