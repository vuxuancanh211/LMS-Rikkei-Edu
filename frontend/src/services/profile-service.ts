import { httpClient } from '../lib';
import type { ChangePasswordRequest, ProfileResponse, ProfileUpdateRequest } from '../types';

export async function getProfile() {
  const response = await httpClient.get<ProfileResponse>('/profile');
  return response.data;
}

export async function updateProfile(data: ProfileUpdateRequest) {
  const response = await httpClient.put<ProfileResponse>('/profile', data);
  return response.data;
}

export async function changePassword(data: ChangePasswordRequest) {
  const response = await httpClient.post<ProfileResponse>('/profile/change-password', data);
  return response.data;
}

export async function uploadAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await httpClient.post<ProfileResponse>('/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}
