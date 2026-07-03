import { httpClient } from '../lib';
import type {
  GroupResponse,
  GroupDetailResponse,
  GroupMemberResponse,
  GroupMemberCsvImportConfirmResponse,
  GroupMemberCsvImportPreviewResponse,
  StudentSearchItem,
  CreateGroupPayload,
  UpdateGroupPayload,
  AddMembersPayload,
} from '../types';

type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export async function getGroups(params?: {
  courseId?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) {
  const response = await httpClient.get<SpringPage<GroupResponse>>(
    '/instructor/groups',
    { params: { ...params, size: params?.size ?? 100 } },
  );
  return response.data;
}

export async function getGroupDetail(groupId: string) {
  const response = await httpClient.get<GroupDetailResponse>(
    `/instructor/groups/${groupId}`,
  );
  return response.data;
}

export async function createGroup(payload: CreateGroupPayload) {
  const response = await httpClient.post<GroupResponse>(
    '/instructor/groups',
    payload,
  );
  return response.data;
}

export async function updateGroup(groupId: string, payload: UpdateGroupPayload) {
  const response = await httpClient.put<GroupResponse>(
    `/instructor/groups/${groupId}`,
    payload,
  );
  return response.data;
}

export async function deleteGroup(groupId: string) {
  await httpClient.delete(`/instructor/groups/${groupId}`);
}

export async function searchStudents(email: string) {
  const response = await httpClient.get<StudentSearchItem[]>(
    '/instructor/groups/students/search',
    { params: { email } },
  );
  return response.data;
}

export async function addGroupMembers(groupId: string, payload: AddMembersPayload) {
  const response = await httpClient.post<GroupMemberResponse[]>(
    `/instructor/groups/${groupId}/members`,
    payload,
  );
  return response.data;
}

export async function removeGroupMember(groupId: string, studentId: string) {
  await httpClient.delete(`/instructor/groups/${groupId}/members/${studentId}`);
}

export async function getStudentGroups() {
  const response = await httpClient.get<GroupResponse[]>(
    '/student/groups',
  );
  return response.data;
}

export async function getStudentGroupDetail(groupId: string) {
  const response = await httpClient.get<GroupDetailResponse>(
    `/student/groups/${groupId}`,
  );
  return response.data;
}

export async function previewGroupMembersCsvImport(groupId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await httpClient.post<GroupMemberCsvImportPreviewResponse>(
    `/instructor/groups/${groupId}/members/import-csv/preview`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data;
}

export async function confirmGroupMembersCsvImport(groupId: string, token: string) {
  const response = await httpClient.post<GroupMemberCsvImportConfirmResponse>(
    `/instructor/groups/${groupId}/members/import-csv/confirm`,
    { token },
  );
  return response.data;
}
