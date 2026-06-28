import { httpClient } from '../lib';
import type { CsvImportConfirmResponse, CsvImportPreviewResponse } from '../types';

export async function previewCsvImport(file: File, defaultRole: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('defaultRole', defaultRole);
  const response = await httpClient.post<CsvImportPreviewResponse>(
    '/admin/users/import-csv/preview',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return response.data;
}

export async function confirmCsvImport(token: string) {
  const response = await httpClient.post<CsvImportConfirmResponse>(
    '/admin/users/import-csv/confirm',
    { token },
  );
  return response.data;
}
