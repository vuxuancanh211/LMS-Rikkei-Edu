package project.lms_rikkei_edu.modules.certificate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.lms_rikkei_edu.infrastructure.email.EmailService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CertificateEmailAsyncServiceTest {

    private EmailService emailService;
    private CertificateEmailAsyncService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        service = new CertificateEmailAsyncService(emailService);
    }

    @Test
    void sendCertificateIssuedMailAsyncDelegatesToEmailService() {
        byte[] pdfBytes = "%PDF".getBytes();

        service.sendCertificateIssuedMailAsync(
                "student@test.com", "Student", "Course", "https://verify", pdfBytes, "cert.pdf");

        verify(emailService).sendCertificateIssuedMail(
                "student@test.com", "Student", "Course", "https://verify", pdfBytes, "cert.pdf");
    }

    @Test
    void sendCertificateIssuedMailAsyncSwallowsEmailFailure() {
        byte[] pdfBytes = "%PDF".getBytes();
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendCertificateIssuedMail("student@test.com", "Student", "Course", "https://verify", pdfBytes, "cert.pdf");

        service.sendCertificateIssuedMailAsync(
                "student@test.com", "Student", "Course", "https://verify", pdfBytes, "cert.pdf");

        verify(emailService).sendCertificateIssuedMail(
                "student@test.com", "Student", "Course", "https://verify", pdfBytes, "cert.pdf");
    }

    @Test
    void sendCertificateRevokedMailAsyncDelegatesToEmailService() {
        service.sendCertificateRevokedMailAsync(
                "student@test.com", "Student", "Course", "RKE-1", "Reason", "https://verify");

        verify(emailService).sendCertificateRevokedMail(
                "student@test.com", "Student", "Course", "RKE-1", "Reason", "https://verify");
    }

    @Test
    void sendCertificateRevokedMailAsyncSwallowsEmailFailure() {
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendCertificateRevokedMail("student@test.com", "Student", "Course", "RKE-1", "Reason", "https://verify");

        service.sendCertificateRevokedMailAsync(
                "student@test.com", "Student", "Course", "RKE-1", "Reason", "https://verify");

        verify(emailService).sendCertificateRevokedMail(
                "student@test.com", "Student", "Course", "RKE-1", "Reason", "https://verify");
    }
}
