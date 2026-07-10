package project.lms_rikkei_edu.modules.certificate.dto.response;

import lombok.Builder;
import lombok.Getter;
import project.lms_rikkei_edu.modules.certificate.enums.CertificateStatus;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CertificateVerifyResponse {
    private String credentialId;
    private CertificateStatus status;
    private String studentName;
    private String courseTitle;
    private String instructorName;
    private OffsetDateTime issuedAt;
    private OffsetDateTime revokedAt;
    private String revokeReason;
}
