package project.lms_rikkei_edu.modules.certificate.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminCertificatePageResponse {
    private List<CertificateResponse> items;
    private long totalRecords;
    private int totalPages;
    private int page;
    private int size;
    private long totalIssued;
    private long totalRevoked;
}
