package project.lms_rikkei_edu.modules.certificate.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfServiceTest {

    private final CertificatePdfService certificatePdfService = new CertificatePdfService();

    @Test
    void generateCreatesReadableSinglePagePdf() throws Exception {
        byte[] pdfBytes = certificatePdfService.generate(
                "Nguyen Van A",
                "Java Spring Boot",
                "Teacher One",
                OffsetDateTime.now(),
                "RKE-2026-ABC12345",
                "https://lms.test/verify/RKE-2026-ABC12345");

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }
}
