import { httpClient } from '../lib';
import type { CsvImportConfirmResponse, CsvImportPreviewResponse } from '../types';

export async function previewCsvImport(file: File, defaultRole: string, courseId?: string, groupIds?: string[]) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('defaultRole', defaultRole);
  if (courseId) formData.append('courseId', courseId);
  if (groupIds && groupIds.length > 0) {
    groupIds.forEach(id => formData.append('groupIds', id));
  }
  const response = await httpClient.post<CsvImportPreviewResponse>(
    '/admin/users/import-csv/preview',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data;
}

export async function confirmCsvImport(token: string, courseId?: string, groupIds?: string[]) {
  const response = await httpClient.post<CsvImportConfirmResponse>(
    '/admin/users/import-csv/confirm',
    { token, courseId, groupIds },
  );
  return response.data;
}
