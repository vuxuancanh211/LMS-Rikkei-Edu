package project.lms_rikkei_edu.modules.certificate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.certificate.dto.request.IssueCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.request.RevokeCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.response.AdminCertificatePageResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;
import project.lms_rikkei_edu.modules.certificate.entity.CertificateEntity;
import project.lms_rikkei_edu.modules.certificate.enums.CertificateStatus;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateAlreadyIssuedException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateNotFoundException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificatePdfException;
import project.lms_rikkei_edu.modules.certificate.exception.CertificateStateException;
import project.lms_rikkei_edu.modules.certificate.mapper.CertificateMapper;
import project.lms_rikkei_edu.modules.certificate.repository.CertificateRepository;
import project.lms_rikkei_edu.modules.certificate.service.impl.CertificateServiceImpl;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private S3Service s3Service;
    @Mock
    private CertificatePdfService certificatePdfService;
    @Mock
    private CertificateEmailAsyncService certificateEmailAsyncService;

    private CertificateServiceImpl service;

    private UUID adminId;
    private UUID studentId;
    private UUID courseId;
    private UUID instructorId;
    private UserEntity student;
    private UserEntity instructor;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new CertificateServiceImpl(
                certificateRepository,
                certificateMapper,
                courseRepository,
                userRepository,
                s3Service,
                certificatePdfService,
                certificateEmailAsyncService);
        ReflectionTestUtils.setField(service, "verifyBaseUrl", "https://lms.test/verify/");

        adminId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        instructorId = UUID.randomUUID();

        student = user(studentId, "student@test.com", "Nguyen Van A", UserRole.STUDENT);
        instructor = user(instructorId, "teacher@test.com", "Teacher One", UserRole.INSTRUCTOR);
        course = Course.builder()
                .id(courseId)
                .title("Java Spring Boot")
                .slug("java-spring-boot")
                .instructorId(instructorId)
                .build();
    }

    @Test
    void issueCertificateCreatesPdfUploadsSavesAndSendsEmail() {
        IssueCertificateRequest request = issueRequest(studentId, courseId);
        byte[] pdfBytes = "%PDF test".getBytes();

        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(student));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)).thenReturn(false);
        when(certificateRepository.existsByCredentialId(anyString())).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(instructorId)).thenReturn(Optional.of(instructor));
        when(certificatePdfService.generate(eq("Nguyen Van A"), eq("Java Spring Boot"), eq("Teacher One"), any(), anyString(), anyString()))
                .thenReturn(pdfBytes);
        when(s3Service.generatePresignedGetUrl(anyString())).thenReturn(presignedGet("https://s3.test/cert.pdf"));
        when(certificateRepository.save(any(CertificateEntity.class))).thenAnswer(invocation -> {
            CertificateEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(certificateMapper.toResponse(any(CertificateEntity.class), eq("Teacher One"))).thenAnswer(invocation -> {
            CertificateEntity entity = invocation.getArgument(0);
            return response(entity, "Teacher One");
        });

        CertificateResponse result = service.issueCertificate(adminId, request);

        assertThat(result.getStatus()).isEqualTo(CertificateStatus.ISSUED);
        assertThat(result.getStudentName()).isEqualTo("Nguyen Van A");
        assertThat(result.getCourseTitle()).isEqualTo("Java Spring Boot");
        assertThat(result.getInstructorName()).isEqualTo("Teacher One");

        ArgumentCaptor<CertificateEntity> certificateCaptor = ArgumentCaptor.forClass(CertificateEntity.class);
        verify(certificateRepository).save(certificateCaptor.capture());
        CertificateEntity saved = certificateCaptor.getValue();
        assertThat(saved.getCredentialId()).startsWith("RKE-" + OffsetDateTime.now().getYear() + "-");
        assertThat(saved.getPdfS3Key()).isEqualTo("certificates/" + saved.getCredentialId() + ".pdf");
        assertThat(saved.getPdfUrl()).isEqualTo("https://s3.test/cert.pdf");
        assertThat(saved.getStatus()).isEqualTo(CertificateStatus.ISSUED);

        verify(s3Service).putObject(saved.getPdfS3Key(), pdfBytes, "application/pdf");
        verify(certificateEmailAsyncService).sendCertificateIssuedMailAsync(
                eq("student@test.com"),
                eq("Nguyen Van A"),
                eq("Java Spring Boot"),
                eq("https://lms.test/verify/" + saved.getCredentialId()),
                eq(pdfBytes),
                eq(saved.getCredentialId() + ".pdf"));
    }

    @Test
    void issueCertificateRejectsNonStudent() {
        UserEntity admin = user(studentId, "admin@test.com", "Admin", UserRole.ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.issueCertificate(adminId, issueRequest(studentId, courseId)))
                .isInstanceOf(CertificateStateException.class)
                .hasMessageContaining("User is not a student");

        verify(courseRepository, never()).findById(any());
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void issueCertificateRejectsMissingCourse() {
        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(student));
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueCertificate(adminId, issueRequest(studentId, courseId)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void issueCertificateRejectsDuplicateStudentCourse() {
        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(student));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)).thenReturn(true);

        assertThatThrownBy(() -> service.issueCertificate(adminId, issueRequest(studentId, courseId)))
                .isInstanceOf(CertificateAlreadyIssuedException.class);

        verify(certificatePdfService, never()).generate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void issueCertificateRejectsOversizedPdf() {
        when(userRepository.findByIdAndDeletedAtIsNull(studentId)).thenReturn(Optional.of(student));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)).thenReturn(false);
        when(certificateRepository.existsByCredentialId(anyString())).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(instructorId)).thenReturn(Optional.of(instructor));
        when(certificatePdfService.generate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new byte[(2 * 1024 * 1024) + 1]);

        assertThatThrownBy(() -> service.issueCertificate(adminId, issueRequest(studentId, courseId)))
                .isInstanceOf(CertificatePdfException.class)
                .hasMessageContaining("exceeds 2MB");

        verify(s3Service, never()).putObject(any(), any(), any());
    }

    @Test
    void getMyCertificatesReturnsListWithBatchInstructorNames() {
        CertificateEntity certificate = certificate(UUID.randomUUID(), student, course, CertificateStatus.ISSUED);
        when(certificateRepository.findAllByStudentIdOrderByIssuedAtDesc(studentId)).thenReturn(List.of(certificate));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(instructor));
        when(certificateMapper.toResponse(certificate, "Teacher One")).thenReturn(response(certificate, "Teacher One"));

        List<CertificateResponse> result = service.getMyCertificates(studentId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getInstructorName()).isEqualTo("Teacher One");
        verify(userRepository).findAllByIdInAndDeletedAtIsNull(List.of(instructorId));
    }

    @Test
    void getAdminCertificatesReturnsPagedResponseWithSafeBounds() {
        CertificateEntity certificate = certificate(UUID.randomUUID(), student, course, CertificateStatus.ISSUED);
        var pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "issuedAt"));
        when(certificateRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(certificate), pageable, 135));
        when(certificateRepository.count(any(Specification.class))).thenReturn(120L, 15L);
        when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(List.of(instructor));
        when(certificateMapper.toResponse(certificate, "Teacher One")).thenReturn(response(certificate, "Teacher One"));

        AdminCertificatePageResponse result = service.getAdminCertificates(0, 500, "Java @@@ React", "ISSUED");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalRecords()).isEqualTo(135);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getTotalIssued()).isEqualTo(120);
        assertThat(result.getTotalRevoked()).isEqualTo(15);
    }

    @Test
    void getMyCertificateReturnsOwnedCertificate() {
        UUID certificateId = UUID.randomUUID();
        CertificateEntity certificate = certificate(certificateId, student, course, CertificateStatus.ISSUED);
        when(certificateRepository.findWithRelationsByIdAndStudentId(certificateId, studentId)).thenReturn(Optional.of(certificate));
        when(userRepository.findByIdAndDeletedAtIsNull(instructorId)).thenReturn(Optional.of(instructor));
        when(certificateMapper.toResponse(certificate, "Teacher One")).thenReturn(response(certificate, "Teacher One"));

        CertificateResponse result = service.getMyCertificate(studentId, certificateId);

        assertThat(result.getId()).isEqualTo(certificateId);
        assertThat(result.getInstructorName()).isEqualTo("Teacher One");
    }

    @Test
    void getStudentDownloadUrlReturnsPresignedUrlEvenWhenRevoked() {
        UUID certificateId = UUID.randomUUID();
        CertificateEntity certificate = certificate(certificateId, student, course, CertificateStatus.REVOKED);
        certificate.setPdfS3Key("certificates/test.pdf");
        when(certificateRepository.findByIdAndStudentId(certificateId, studentId)).thenReturn(Optional.of(certificate));
        when(s3Service.generatePresignedGetUrl("certificates/test.pdf")).thenReturn(presignedGet("https://s3.test/download.pdf"));

        assertThat(service.getStudentDownloadUrl(studentId, certificateId).getUrl())
                .isEqualTo("https://s3.test/download.pdf");
    }

    @Test
    void getStudentDownloadUrlRejectsMissingPdfKey() {
        UUID certificateId = UUID.randomUUID();
        CertificateEntity certificate = certificate(certificateId, student, course, CertificateStatus.ISSUED);
        certificate.setPdfS3Key(" ");
        when(certificateRepository.findByIdAndStudentId(certificateId, studentId)).thenReturn(Optional.of(certificate));

        assertThatThrownBy(() -> service.getStudentDownloadUrl(studentId, certificateId))
                .isInstanceOf(CertificateStateException.class)
                .hasMessageContaining("PDF is not available");
    }

    @Test
    void verifyReturnsPublicCertificateDetails() {
        CertificateEntity certificate = certificate(UUID.randomUUID(), student, course, CertificateStatus.REVOKED);
        certificate.setRevokeReason("Invalid completion");
        certificate.setRevokedAt(OffsetDateTime.now());
        when(certificateRepository.findByCredentialId("RKE-2026-ABC12345")).thenReturn(Optional.of(certificate));
        when(userRepository.findByIdAndDeletedAtIsNull(instructorId)).thenReturn(Optional.of(instructor));
        when(certificateMapper.toVerifyResponse(certificate, "Teacher One")).thenReturn(verifyResponse(certificate, "Teacher One"));

        CertificateVerifyResponse result = service.verify("RKE-2026-ABC12345");

        assertThat(result.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(result.getRevokeReason()).isEqualTo("Invalid completion");
    }

    @Test
    void revokeUpdatesStatusAndAuditFields() {
        UUID certificateId = UUID.randomUUID();
        CertificateEntity certificate = certificate(certificateId, student, course, CertificateStatus.ISSUED);
        RevokeCertificateRequest request = new RevokeCertificateRequest();
        request.setReason("Manual revoke");

        when(certificateRepository.findWithRelationsById(certificateId)).thenReturn(Optional.of(certificate));
        when(certificateRepository.save(certificate)).thenReturn(certificate);
        when(userRepository.findByIdAndDeletedAtIsNull(instructorId)).thenReturn(Optional.of(instructor));
        when(certificateMapper.toResponse(certificate, "Teacher One")).thenAnswer(invocation -> response(certificate, "Teacher One"));

        CertificateResponse result = service.revoke(adminId, certificateId, request);

        assertThat(result.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(certificate.getRevokedBy()).isEqualTo(adminId);
        assertThat(certificate.getRevokedAt()).isNotNull();
        assertThat(certificate.getRevokeReason()).isEqualTo("Manual revoke");
    }

    @Test
    void revokeRejectsAlreadyRevokedCertificate() {
        UUID certificateId = UUID.randomUUID();
        CertificateEntity certificate = certificate(certificateId, student, course, CertificateStatus.REVOKED);
        when(certificateRepository.findWithRelationsById(certificateId)).thenReturn(Optional.of(certificate));

        assertThatThrownBy(() -> service.revoke(adminId, certificateId, new RevokeCertificateRequest()))
                .isInstanceOf(CertificateStateException.class)
                .hasMessageContaining("already been revoked");

        verify(certificateRepository, never()).save(any());
    }

    @Test
    void verifyRejectsUnknownCredential() {
        when(certificateRepository.findByCredentialId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("missing"))
                .isInstanceOf(CertificateNotFoundException.class);
    }

    private static IssueCertificateRequest issueRequest(UUID studentId, UUID courseId) {
        IssueCertificateRequest request = new IssueCertificateRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        return request;
    }

    private static UserEntity user(UUID id, String email, String fullName, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        return user;
    }

    private static CertificateEntity certificate(UUID id, UserEntity student, Course course, CertificateStatus status) {
        CertificateEntity certificate = new CertificateEntity();
        certificate.setId(id);
        certificate.setStudentId(student.getId());
        certificate.setCourseId(course.getId());
        certificate.setCredentialId("RKE-2026-ABC12345");
        certificate.setPdfS3Key("certificates/RKE-2026-ABC12345.pdf");
        certificate.setStatus(status);
        certificate.setIssuedAt(OffsetDateTime.now().minusDays(1));
        certificate.setStudent(student);
        certificate.setCourse(course);
        return certificate;
    }

    private static CertificateResponse response(CertificateEntity certificate, String instructorName) {
        return CertificateResponse.builder()
                .id(certificate.getId())
                .studentId(certificate.getStudentId())
                .courseId(certificate.getCourseId())
                .credentialId(certificate.getCredentialId())
                .status(certificate.getStatus())
                .studentName(certificate.getStudent().getFullName())
                .courseTitle(certificate.getCourse().getTitle())
                .instructorName(instructorName)
                .issuedAt(certificate.getIssuedAt())
                .revokedAt(certificate.getRevokedAt())
                .build();
    }

    private static CertificateVerifyResponse verifyResponse(CertificateEntity certificate, String instructorName) {
        return CertificateVerifyResponse.builder()
                .credentialId(certificate.getCredentialId())
                .status(certificate.getStatus())
                .studentName(certificate.getStudent().getFullName())
                .courseTitle(certificate.getCourse().getTitle())
                .instructorName(instructorName)
                .issuedAt(certificate.getIssuedAt())
                .revokedAt(certificate.getRevokedAt())
                .revokeReason(certificate.getRevokeReason())
                .build();
    }

    private static PresignedGetObjectRequest presignedGet(String url) {
        return PresignedGetObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(3600))
                .isBrowserExecutable(true)
                .httpRequest(SdkHttpFullRequest.builder()
                        .method(SdkHttpMethod.GET)
                        .uri(URI.create(url))
                        .build())
                .signedHeaders(Map.of("host", List.of("s3.test")))
                .build();
    }
}
