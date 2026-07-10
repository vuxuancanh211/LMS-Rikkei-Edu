package project.lms_rikkei_edu.modules.certificate.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateResponse;
import project.lms_rikkei_edu.modules.certificate.dto.response.CertificateVerifyResponse;
import project.lms_rikkei_edu.modules.certificate.entity.CertificateEntity;
import project.lms_rikkei_edu.modules.certificate.enums.CertificateStatus;
import project.lms_rikkei_edu.modules.course.entity.Course;
import project.lms_rikkei_edu.modules.user.entity.UserEntity;
import project.lms_rikkei_edu.modules.user.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateMapperTest {

    private final CertificateMapper mapper = Mappers.getMapper(CertificateMapper.class);

    @Test
    void toResponseMapsCertificateFieldsAndRelations() {
        CertificateEntity certificate = certificate(CertificateStatus.ISSUED);

        CertificateResponse result = mapper.toResponse(certificate, "Teacher One");

        assertThat(result.getId()).isEqualTo(certificate.getId());
        assertThat(result.getStudentId()).isEqualTo(certificate.getStudentId());
        assertThat(result.getCourseId()).isEqualTo(certificate.getCourseId());
        assertThat(result.getCredentialId()).isEqualTo("RKE-2026-ABC12345");
        assertThat(result.getStatus()).isEqualTo(CertificateStatus.ISSUED);
        assertThat(result.getStudentName()).isEqualTo("Nguyen Van A");
        assertThat(result.getCourseTitle()).isEqualTo("Java Spring Boot");
        assertThat(result.getInstructorName()).isEqualTo("Teacher One");
        assertThat(result.getIssuedAt()).isEqualTo(certificate.getIssuedAt());
        assertThat(result.getRevokedAt()).isEqualTo(certificate.getRevokedAt());
    }

    @Test
    void toVerifyResponseMapsRevocationFields() {
        CertificateEntity certificate = certificate(CertificateStatus.REVOKED);
        certificate.setRevokeReason("Invalid completion");
        certificate.setRevokedAt(OffsetDateTime.now());

        CertificateVerifyResponse result = mapper.toVerifyResponse(certificate, "Teacher One");

        assertThat(result.getCredentialId()).isEqualTo("RKE-2026-ABC12345");
        assertThat(result.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(result.getStudentName()).isEqualTo("Nguyen Van A");
        assertThat(result.getCourseTitle()).isEqualTo("Java Spring Boot");
        assertThat(result.getInstructorName()).isEqualTo("Teacher One");
        assertThat(result.getRevokeReason()).isEqualTo("Invalid completion");
        assertThat(result.getRevokedAt()).isEqualTo(certificate.getRevokedAt());
    }

    @Test
    void mapsNullRelationsWithoutFailing() {
        CertificateEntity certificate = certificate(CertificateStatus.ISSUED);
        certificate.setStudent(null);
        certificate.setCourse(null);

        CertificateResponse response = mapper.toResponse(certificate, "Rikkei Edu");
        CertificateVerifyResponse verifyResponse = mapper.toVerifyResponse(certificate, "Rikkei Edu");

        assertThat(response.getStudentName()).isNull();
        assertThat(response.getCourseTitle()).isNull();
        assertThat(response.getInstructorName()).isEqualTo("Rikkei Edu");
        assertThat(verifyResponse.getStudentName()).isNull();
        assertThat(verifyResponse.getCourseTitle()).isNull();
        assertThat(verifyResponse.getInstructorName()).isEqualTo("Rikkei Edu");
    }

    private static CertificateEntity certificate(CertificateStatus status) {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        UserEntity student = new UserEntity();
        student.setId(studentId);
        student.setFullName("Nguyen Van A");
        student.setRole(UserRole.STUDENT);

        Course course = Course.builder()
                .id(courseId)
                .title("Java Spring Boot")
                .slug("java-spring-boot")
                .instructorId(UUID.randomUUID())
                .build();

        CertificateEntity certificate = new CertificateEntity();
        certificate.setId(UUID.randomUUID());
        certificate.setStudentId(studentId);
        certificate.setCourseId(courseId);
        certificate.setCredentialId("RKE-2026-ABC12345");
        certificate.setStatus(status);
        certificate.setIssuedAt(OffsetDateTime.now().minusDays(1));
        certificate.setStudent(student);
        certificate.setCourse(course);
        return certificate;
    }
}
