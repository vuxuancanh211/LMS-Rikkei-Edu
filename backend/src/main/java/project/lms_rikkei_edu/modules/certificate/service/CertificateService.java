package project.lms_rikkei_edu.modules.certificate.service;

import project.lms_rikkei_edu.modules.certificate.dto.request.RevokeCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.response.AdminCertificatePageResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateDownloadResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    void issueCertificateIfEligible(UUID studentId, UUID courseId);

    List<CertificateResponse> getAllCertificatesForAdmin();

    AdminCertificatePageResponse getAdminCertificates(int page, int size, String search, String status);

    List<CertificateResponse> getMyCertificates(UUID studentId);

    CertificateResponse getMyCertificate(UUID studentId, UUID certificateId);

    CertificateDownloadResponse getStudentDownloadUrl(UUID studentId, UUID certificateId);

    CertificateVerifyResponse verify(String credentialId);

    CertificateResponse revoke(UUID adminId, UUID certificateId, RevokeCertificateRequest request);
}
