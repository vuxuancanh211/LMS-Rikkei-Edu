package project.lms_rikkei_edu.modules.certificate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.lms_rikkei_edu.common.exception.BusinessException;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.certificate.dto.request.RevokeCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.response.AdminCertificatePageResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateDownloadResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;
import project.lms_rikkei_edu.modules.certificate.service.CertificateService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/api/certificate/verify/{code}")
    public ResponseEntity<CertificateVerifyResponse> verify(@PathVariable String code) {
        return ResponseEntity.ok(certificateService.verify(code));
    }

    @GetMapping("/api/student/certificates")
    public ResponseEntity<List<CertificateResponse>> getMyCertificates() {
        return ResponseEntity.ok(certificateService.getMyCertificates(currentUserId()));
    }

    @GetMapping("/api/student/certificates/{id}")
    public ResponseEntity<CertificateResponse> getMyCertificate(@PathVariable UUID id) {
        return ResponseEntity.ok(certificateService.getMyCertificate(currentUserId(), id));
    }

    @GetMapping("/api/student/certificates/{id}/download")
    public ResponseEntity<CertificateDownloadResponse> getStudentDownloadUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(certificateService.getStudentDownloadUrl(currentUserId(), id));
    }

    @GetMapping("/api/admin/certificates")
    public ResponseEntity<AdminCertificatePageResponse> getAllCertificatesForAdmin(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(certificateService.getAdminCertificates(page, size, search, status));
    }

    @PostMapping("/api/admin/certificates/{id}/revoke")
    public ResponseEntity<CertificateResponse> revokeCertificate(
            @PathVariable UUID id,
            @Valid @RequestBody RevokeCertificateRequest request) {
        return ResponseEntity.ok(certificateService.revoke(currentUserId(), id, request));
    }

    private UUID currentUserId() {
        return currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new BusinessException("Unauthorized", HttpStatus.UNAUTHORIZED));
    }
}
