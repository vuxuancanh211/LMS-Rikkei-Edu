package project.lms_rikkei_edu.modules.certificate.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateExceptionTest {

    @Test
    void accessDeniedExceptionStoresMessage() {
        CertificateAccessDeniedException exception = new CertificateAccessDeniedException("Forbidden");

        assertThat(exception).hasMessage("Forbidden");
    }

    @Test
    void pdfExceptionStoresMessageAndCause() {
        RuntimeException cause = new RuntimeException("PDFBox");
        CertificatePdfException exception = new CertificatePdfException("Failed", cause);

        assertThat(exception).hasMessage("Failed");
        assertThat(exception).hasCause(cause);
    }
}
