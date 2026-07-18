import { httpClient } from '../lib';

export type CertificateStatus = 'ISSUED' | 'REVOKED';

export type CertificateVerifyResponse = {
  credentialId: string;
  status: CertificateStatus;
  studentName: string;
  courseTitle: string;
  courseThumbnailUrl?: string | null;
  instructorName: string;
  issuedAt: string;
  revokedAt?: string | null;
  revokeReason?: string | null;
};

export type CertificateResponse = {
  id: string;
  studentId: string;
  courseId: string;
  credentialId: string;
  status: CertificateStatus;
  studentName: string;
  courseTitle: string;
  courseThumbnailUrl?: string | null;
  instructorName: string;
  issuedAt: string;
  revokedAt?: string | null;
};

export type CertificateDownloadResponse = {
  url: string;
};

export type AdminCertificatePageResponse = {
  items: CertificateResponse[];
  totalRecords: number;
  totalPages: number;
  page: number;
  size: number;
  totalIssued: number;
  totalRevoked: number;
};

export async function verifyCertificate(code: string) {
  const response = await httpClient.get<CertificateVerifyResponse>(
    `/certificate/verify/${encodeURIComponent(code)}`,
    { skipAuthRefresh: true },
  );
  return response.data;
}

export async function getMyCertificates() {
  const response = await httpClient.get<CertificateResponse[]>('/student/certificates');
  return response.data;
}

export async function getMyCertificate(id: string) {
  const response = await httpClient.get<CertificateResponse>(
    `/student/certificates/${encodeURIComponent(id)}`,
  );
  return response.data;
}

export async function getCertificateDownloadUrl(id: string) {
  const response = await httpClient.get<CertificateDownloadResponse>(
    `/student/certificates/${encodeURIComponent(id)}/download`,
  );
  return response.data;
}

export async function getAdminCertificates(params?: { page?: number; size?: number; search?: string; status?: string }) {
  const response = await httpClient.get<AdminCertificatePageResponse>('/admin/certificates', { params });
  return response.data;
}

export async function revokeCertificate(id: string, reason: string) {
  const response = await httpClient.post<CertificateResponse>(
    `/admin/certificates/${encodeURIComponent(id)}/revoke`,
    { reason },
  );
  return response.data;
}
