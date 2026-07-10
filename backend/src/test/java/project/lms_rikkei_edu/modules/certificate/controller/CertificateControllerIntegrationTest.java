package project.lms_rikkei_edu.modules.certificate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import project.lms_rikkei_edu.common.exception.GlobalExceptionHandler;
import project.lms_rikkei_edu.common.security.CurrentUserProvider;
import project.lms_rikkei_edu.modules.certificate.dto.request.IssueCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.request.RevokeCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.response.AdminCertificatePageResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateDownloadResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;
import project.lms_rikkei_edu.modules.certificate.enums.CertificateStatus;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateAlreadyIssuedException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateNotFoundException;
import project.lms_rikkei_edu.modules.certificate.service.CertificateService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CertificateControllerIntegrationTest {

    private CertificateService certificateService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID currentUserId;
    private UUID certificateId;
    private UUID studentId;
    private UUID courseId;

    @BeforeEach
    void setUp() {
        certificateService = mock(CertificateService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        currentUserId = UUID.randomUUID();
        certificateId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();

        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.of(currentUserId));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CertificateController(certificateService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void verifyReturnsPublicCertificate() throws Exception {
        when(certificateService.verify("RKE-2026-ABC12345")).thenReturn(verifyResponse(CertificateStatus.ISSUED));

        mockMvc.perform(get("/api/certificate/verify/{code}", "RKE-2026-ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value("RKE-2026-ABC12345"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.studentName").value("Nguyen Van A"));
    }

    @Test
    void verifyReturns404WhenUnknown() throws Exception {
        when(certificateService.verify("missing")).thenThrow(new CertificateNotFoundException("Certificate not found: missing"));

        mockMvc.perform(get("/api/certificate/verify/{code}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Certificate not found: missing"));
    }

    @Test
    void getMyCertificatesUsesCurrentUser() throws Exception {
        when(certificateService.getMyCertificates(currentUserId)).thenReturn(List.of(response(CertificateStatus.ISSUED)));

        mockMvc.perform(get("/api/student/certificates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(certificateId.toString()));

        verify(certificateService).getMyCertificates(currentUserId);
    }

    @Test
    void getMyCertificateReturnsOwnedCertificate() throws Exception {
        when(certificateService.getMyCertificate(currentUserId, certificateId)).thenReturn(response(CertificateStatus.ISSUED));

        mockMvc.perform(get("/api/student/certificates/{id}", certificateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(certificateId.toString()))
                .andExpect(jsonPath("$.courseTitle").value("Java Spring Boot"));
    }

    @Test
    void getMyCertificateReturns404ForOtherStudentOrMissingCertificate() throws Exception {
        when(certificateService.getMyCertificate(currentUserId, certificateId))
                .thenThrow(new CertificateNotFoundException("Certificate not found: " + certificateId));

        mockMvc.perform(get("/api/student/certificates/{id}", certificateId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDownloadUrlReturnsUrl() throws Exception {
        when(certificateService.getStudentDownloadUrl(currentUserId, certificateId))
                .thenReturn(CertificateDownloadResponse.builder().url("https://s3.test/cert.pdf").build());

        mockMvc.perform(get("/api/student/certificates/{id}/download", certificateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.test/cert.pdf"));
    }

    @Test
    void issueCertificateReturns201() throws Exception {
        IssueCertificateRequest request = new IssueCertificateRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        when(certificateService.issueCertificate(eq(currentUserId), any(IssueCertificateRequest.class)))
                .thenReturn(response(CertificateStatus.ISSUED));

        mockMvc.perform(post("/api/admin/certificates/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void issueCertificateValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/admin/certificates/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.studentId").value("Student id is required"))
                .andExpect(jsonPath("$.validationErrors.courseId").value("Course id is required"));
    }

    @Test
    void issueCertificateReturns409WhenDuplicate() throws Exception {
        IssueCertificateRequest request = new IssueCertificateRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        when(certificateService.issueCertificate(eq(currentUserId), any(IssueCertificateRequest.class)))
                .thenThrow(new CertificateAlreadyIssuedException("Certificate already issued for this student and course"));

        mockMvc.perform(post("/api/admin/certificates/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Certificate already issued for this student and course"));
    }

    @Test
    void getAllCertificatesForAdminReturnsPagedList() throws Exception {
        when(certificateService.getAdminCertificates(2, 5, "Java React", "ISSUED"))
                .thenReturn(AdminCertificatePageResponse.builder()
                        .items(List.of(response(CertificateStatus.ISSUED)))
                        .totalRecords(11)
                        .totalPages(3)
                        .page(2)
                        .size(5)
                        .totalIssued(80)
                        .totalRevoked(20)
                        .build());

        mockMvc.perform(get("/api/admin/certificates")
                        .param("page", "2")
                        .param("size", "5")
                        .param("search", "Java React")
                        .param("status", "ISSUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalRecords").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalIssued").value(80))
                .andExpect(jsonPath("$.totalRevoked").value(20));

        verify(certificateService).getAdminCertificates(2, 5, "Java React", "ISSUED");
    }

    @Test
    void revokeCertificateReturnsUpdatedCertificate() throws Exception {
        RevokeCertificateRequest request = new RevokeCertificateRequest();
        request.setReason("Invalid completion");
        when(certificateService.revoke(eq(currentUserId), eq(certificateId), any(RevokeCertificateRequest.class)))
                .thenReturn(response(CertificateStatus.REVOKED));

        mockMvc.perform(post("/api/admin/certificates/{id}/revoke", certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void revokeCertificateValidatesReason() throws Exception {
        RevokeCertificateRequest request = new RevokeCertificateRequest();
        request.setReason(" ");

        mockMvc.perform(post("/api/admin/certificates/{id}/revoke", certificateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.reason").value("Revoke reason is required"));
    }

    @Test
    void protectedEndpointsReturn401WithoutCurrentUser() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/student/certificates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    private CertificateResponse response(CertificateStatus status) {
        return CertificateResponse.builder()
                .id(certificateId)
                .studentId(studentId)
                .courseId(courseId)
                .credentialId("RKE-2026-ABC12345")
                .status(status)
                .studentName("Nguyen Van A")
                .courseTitle("Java Spring Boot")
                .instructorName("Teacher One")
                .issuedAt(OffsetDateTime.now().minusDays(1))
                .revokedAt(status == CertificateStatus.REVOKED ? OffsetDateTime.now() : null)
                .build();
    }

    private CertificateVerifyResponse verifyResponse(CertificateStatus status) {
        return CertificateVerifyResponse.builder()
                .credentialId("RKE-2026-ABC12345")
                .status(status)
                .studentName("Nguyen Van A")
                .courseTitle("Java Spring Boot")
                .instructorName("Teacher One")
                .issuedAt(OffsetDateTime.now().minusDays(1))
                .revokedAt(status == CertificateStatus.REVOKED ? OffsetDateTime.now() : null)
                .revokeReason(status == CertificateStatus.REVOKED ? "Invalid completion" : null)
                .build();
    }
}
