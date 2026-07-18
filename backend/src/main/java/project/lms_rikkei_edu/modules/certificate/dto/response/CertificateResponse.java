package project.lms_rikkei_edu.modules.certificate.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.certificate.enums.CertificateStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class CertificateResponse {
    private UUID id;
    private UUID studentId;
    private UUID courseId;
    private String credentialId;
    private CertificateStatus status;
    private String studentName;
    private String courseTitle;
    private String courseThumbnailUrl;
    private String instructorName;
    private OffsetDateTime issuedAt;
    private OffsetDateTime revokedAt;
}
