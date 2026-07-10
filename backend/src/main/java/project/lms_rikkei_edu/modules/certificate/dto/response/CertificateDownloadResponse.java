package project.lms_rikkei_edu.modules.certificate.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CertificateDownloadResponse {
    private String url;
}
