package project.lms_rikkei_edu.modules.certificate.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import project.lms_rikkei_edu.infrastructure.s3.S3Service;
import project.lms_rikkei_edu.modules.certificate.dto.request.IssueCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.request.RevokeCertificateRequest;
import project.lms_rikkei_edu.modules.certificate.dto.response.AdminCertificatePageResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateDownloadResponse;
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
import project.lms_rikkei_edu.modules.certificate.service.CertificateEmailAsyncService;
import project.lms_rikkei_edu.modules.certificate.service.CertificatePdfService;
import project.lms_rikkei_edu.modules.certificate.service.CertificateService;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.course.exception.CourseNotFoundException;
import project.lms_rikkei_edu.modules.course.repository.CourseRepository;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;
import project.lms_rikkei_edu.modules.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private static final int MAX_PDF_SIZE_BYTES = 2 * 1024 * 1024;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DEFAULT_INSTRUCTOR_NAME = "Rikkei Edu";

    private final CertificateRepository certificateRepository;
    private final CertificateMapper certificateMapper;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final CertificatePdfService certificatePdfService;
    private final CertificateEmailAsyncService certificateEmailAsyncService;

    @Value("${app.certificate.verify-base-url}")
    private String verifyBaseUrl;

    @Override
    @Transactional
    public CertificateResponse issueCertificate(UUID adminId, IssueCertificateRequest request) {
        UserEntity student = userRepository.findByIdAndDeletedAtIsNull(request.getStudentId())
                .orElseThrow(() -> new CertificateStateException("Student not found: " + request.getStudentId()));
        if (student.getRole() != UserRole.STUDENT) {
            throw new CertificateStateException("User is not a student: " + request.getStudentId());
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException(request.getCourseId()));

        if (certificateRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new CertificateAlreadyIssuedException("Certificate already issued for this student and course");
        }

        String credentialId = generateCredentialId();
        String instructorName = resolveInstructorName(course);
        String verifyUrl = buildVerifyUrl(credentialId);
        OffsetDateTime issuedAt = OffsetDateTime.now();
        byte[] pdfBytes = certificatePdfService.generate(
                student.getFullName(),
                course.getTitle(),
                instructorName,
                issuedAt,
                credentialId,
                verifyUrl);
        ensurePdfSize(pdfBytes);

        String s3Key = "certificates/" + credentialId + ".pdf";
        s3Service.putObject(s3Key, pdfBytes, PDF_CONTENT_TYPE);
        String pdfUrl = s3Service.generatePresignedGetUrl(s3Key).url().toString();

        CertificateEntity certificate = new CertificateEntity();
        certificate.setStudentId(student.getId());
        certificate.setCourseId(course.getId());
        certificate.setCredentialId(credentialId);
        certificate.setPdfS3Key(s3Key);
        certificate.setPdfUrl(pdfUrl);
        certificate.setStatus(CertificateStatus.ISSUED);
        certificate.setIssuedAt(issuedAt);
        certificate.setStudent(student);
        certificate.setCourse(course);

        CertificateEntity saved = certificateRepository.save(certificate);
        sendCertificateEmailAfterCommit(
                student.getEmail(),
                student.getFullName(),
                course.getTitle(),
                verifyUrl,
                pdfBytes,
                credentialId + ".pdf");

        return certificateMapper.toResponse(saved, instructorName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> getAllCertificatesForAdmin() {
        return toResponses(certificateRepository.findAllByOrderByIssuedAtDesc());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCertificatePageResponse getAdminCertificates(int page, int size, String search, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "issuedAt"));

        Specification<CertificateEntity> searchSpec = adminCertificateSearchSpec(search);
        Specification<CertificateEntity> pageSpec = searchSpec.and(adminCertificateStatusSpec(status));
        Page<CertificateEntity> certificatePage = certificateRepository.findAll(pageSpec, pageable);
        long totalIssued = certificateRepository.count(searchSpec.and(adminCertificateStatusSpec(CertificateStatus.ISSUED.name())));
        long totalRevoked = certificateRepository.count(searchSpec.and(adminCertificateStatusSpec(CertificateStatus.REVOKED.name())));

        return AdminCertificatePageResponse.builder()
                .items(toResponses(certificatePage.getContent()))
                .totalRecords(certificatePage.getTotalElements())
                .totalPages((int) Math.ceil((double) certificatePage.getTotalElements() / safeSize))
                .page(safePage)
                .size(safeSize)
                .totalIssued(totalIssued)
                .totalRevoked(totalRevoked)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> getMyCertificates(UUID studentId) {
        return toResponses(certificateRepository.findAllByStudentIdOrderByIssuedAtDesc(studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getMyCertificate(UUID studentId, UUID certificateId) {
        CertificateEntity certificate = certificateRepository.findWithRelationsByIdAndStudentId(certificateId, studentId)
                .orElseThrow(() -> new CertificateNotFoundException("Certificate not found: " + certificateId));
        return certificateMapper.toResponse(certificate, resolveInstructorName(certificate.getCourse()));
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateDownloadResponse getStudentDownloadUrl(UUID studentId, UUID certificateId) {
        CertificateEntity certificate = certificateRepository.findByIdAndStudentId(certificateId, studentId)
                .orElseThrow(() -> new CertificateNotFoundException("Certificate not found: " + certificateId));
        if (certificate.getPdfS3Key() == null || certificate.getPdfS3Key().isBlank()) {
            throw new CertificateStateException("Certificate PDF is not available");
        }
        return CertificateDownloadResponse.builder()
                .url(s3Service.generatePresignedGetUrl(certificate.getPdfS3Key()).url().toString())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateVerifyResponse verify(String credentialId) {
        CertificateEntity certificate = certificateRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new CertificateNotFoundException("Certificate not found: " + credentialId));
        return certificateMapper.toVerifyResponse(certificate, resolveInstructorName(certificate.getCourse()));
    }

    @Override
    @Transactional
    public CertificateResponse revoke(UUID adminId, UUID certificateId, RevokeCertificateRequest request) {
        CertificateEntity certificate = certificateRepository.findWithRelationsById(certificateId)
                .orElseThrow(() -> new CertificateNotFoundException("Certificate not found: " + certificateId));
        if (certificate.getStatus() == CertificateStatus.REVOKED) {
            throw new CertificateStateException("Certificate has already been revoked");
        }

        certificate.setStatus(CertificateStatus.REVOKED);
        certificate.setRevokedBy(adminId);
        certificate.setRevokedAt(OffsetDateTime.now());
        certificate.setRevokeReason(request.getReason());

        CertificateEntity saved = certificateRepository.save(certificate);
        sendCertificateRevokedEmailAfterCommit(
                saved.getStudent().getEmail(),
                saved.getStudent().getFullName(),
                saved.getCourse().getTitle(),
                saved.getCredentialId(),
                saved.getRevokeReason(),
                buildVerifyUrl(saved.getCredentialId()));
        return certificateMapper.toResponse(saved, resolveInstructorName(saved.getCourse()));
    }

    private String generateCredentialId() {
        String credentialId;
        do {
            credentialId = "RKE-" + OffsetDateTime.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (certificateRepository.existsByCredentialId(credentialId));
        return credentialId;
    }

    private String resolveInstructorName(Course course) {
        if (course == null || course.getInstructorId() == null) {
            return DEFAULT_INSTRUCTOR_NAME;
        }
        return userRepository.findByIdAndDeletedAtIsNull(course.getInstructorId())
                .map(UserEntity::getFullName)
                .orElse(DEFAULT_INSTRUCTOR_NAME);
    }

    private List<CertificateResponse> toResponses(List<CertificateEntity> certificates) {
        if (certificates == null || certificates.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, String> instructorNames = resolveInstructorNames(certificates);
        return certificates.stream()
                .map(certificate -> certificateMapper.toResponse(
                        certificate,
                        instructorNames.getOrDefault(instructorIdOf(certificate), DEFAULT_INSTRUCTOR_NAME)))
                .toList();
    }

    private Map<UUID, String> resolveInstructorNames(List<CertificateEntity> certificates) {
        Set<UUID> instructorIds = certificates.stream()
                .map(this::instructorIdOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (instructorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return userRepository.findAllByIdInAndDeletedAtIsNull(List.copyOf(instructorIds))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName, (left, right) -> left, HashMap::new));
    }

    private UUID instructorIdOf(CertificateEntity certificate) {
        return certificate.getCourse() != null ? certificate.getCourse().getInstructorId() : null;
    }

    private Specification<CertificateEntity> adminCertificateSearchSpec(String search) {
        return (root, query, criteriaBuilder) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("student", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("course", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("revokedByUser", jakarta.persistence.criteria.JoinType.LEFT);
                query.distinct(true);
            }

            var predicate = criteriaBuilder.conjunction();

            List<String> keywords = extractSearchKeywords(search);
            if (!keywords.isEmpty()) {
                var courseTitle = criteriaBuilder.lower(root.join("course", jakarta.persistence.criteria.JoinType.LEFT).get("title"));
                var keywordPredicate = criteriaBuilder.disjunction();
                for (String keyword : keywords) {
                    keywordPredicate = criteriaBuilder.or(keywordPredicate, criteriaBuilder.like(courseTitle, "%" + keyword + "%"));
                }
                predicate = criteriaBuilder.and(predicate, keywordPredicate);
            }

            return predicate;
        };
    }

    private Specification<CertificateEntity> adminCertificateStatusSpec(String status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), CertificateStatus.valueOf(status.toUpperCase(Locale.ROOT)));
        };
    }

    private List<String> extractSearchKeywords(String search) {
        if (search == null || search.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+")
                .matcher(search.toLowerCase(Locale.ROOT))
                .results()
                .map(java.util.regex.MatchResult::group)
                .toList();
    }

    private String buildVerifyUrl(String credentialId) {
        String baseUrl = verifyBaseUrl.endsWith("/") ? verifyBaseUrl.substring(0, verifyBaseUrl.length() - 1) : verifyBaseUrl;
        return baseUrl + "/" + credentialId;
    }

    private void ensurePdfSize(byte[] pdfBytes) {
        if (pdfBytes.length > MAX_PDF_SIZE_BYTES) {
            throw new CertificatePdfException("Certificate PDF exceeds 2MB limit");
        }
    }

    private void sendCertificateEmailAfterCommit(
            String to,
            String fullName,
            String courseTitle,
            String verifyUrl,
            byte[] pdfBytes,
            String fileName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            certificateEmailAsyncService.sendCertificateIssuedMailAsync(
                    to, fullName, courseTitle, verifyUrl, pdfBytes, fileName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                certificateEmailAsyncService.sendCertificateIssuedMailAsync(
                        to, fullName, courseTitle, verifyUrl, pdfBytes, fileName);
            }
        });
    }

    private void sendCertificateRevokedEmailAfterCommit(
            String to,
            String fullName,
            String courseTitle,
            String credentialId,
            String reason,
            String verifyUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            certificateEmailAsyncService.sendCertificateRevokedMailAsync(
                    to, fullName, courseTitle, credentialId, reason, verifyUrl);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                certificateEmailAsyncService.sendCertificateRevokedMailAsync(
                        to, fullName, courseTitle, credentialId, reason, verifyUrl);
            }
        });
    }
}
