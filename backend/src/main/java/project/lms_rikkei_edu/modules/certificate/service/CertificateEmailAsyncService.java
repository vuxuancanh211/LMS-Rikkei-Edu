package project.lms_rikkei_edu.modules.certificate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.lms_rikkei_edu.infrastructure.email.EmailService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateEmailAsyncService {

    private final EmailService emailService;

    @Async("emailExecutor")
    public void sendCertificateIssuedMailAsync(
            String to,
            String fullName,
            String courseTitle,
            String verifyUrl,
            byte[] pdfBytes,
            String fileName) {
        try {
            emailService.sendCertificateIssuedMail(to, fullName, courseTitle, verifyUrl, pdfBytes, fileName);
        } catch (Exception e) {
            log.error("Failed to send certificate email to {} for course {}", to, courseTitle, e);
        }
    }
}
