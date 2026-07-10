package project.lms_rikkei_edu.infrastructure.email;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "from", "noreply@rikkei.edu");
        ReflectionTestUtils.setField(emailService, "loginUrl", "https://lms.test/login");
    }

    @Test
    void sendCertificateIssuedMailBuildsMimeMessageWithAttachment() throws Exception {
        MimeMessage message = mimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendCertificateIssuedMail(
                "student@test.com",
                "Student Name",
                "Java Spring Boot",
                "https://lms.test/verify/RKE-1",
                "%PDF".getBytes(),
                "certificate.pdf");

        assertThat(message.getSubject()).isEqualTo("[Rikkei Edu] Chứng chỉ khóa học của bạn");
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@rikkei.edu");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@test.com");
        assertThat(messageBody(message)).contains("Student Name").contains("Java Spring Boot");
        verify(mailSender).send(message);
    }

    @Test
    void sendCertificateRevokedMailBuildsMimeMessage() throws Exception {
        MimeMessage message = mimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendCertificateRevokedMail(
                "student@test.com",
                "Student Name",
                "Java Spring Boot",
                "RKE-1",
                "Invalid completion",
                "https://lms.test/verify/RKE-1");

        assertThat(message.getSubject()).isEqualTo("[Rikkei Edu] Chứng chỉ của bạn đã bị thu hồi");
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@rikkei.edu");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("student@test.com");
        assertThat(messageBody(message))
                .contains("Student Name")
                .contains("Java Spring Boot")
                .contains("RKE-1")
                .contains("Invalid completion");
        verify(mailSender).send(message);
    }

    private static MimeMessage mimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private static String messageBody(MimeMessage message) throws Exception {
        return messageBody(message.getContent());
    }

    private static String messageBody(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            return messageBody(multipart.getBodyPart(0).getContent());
        }
        return content.toString();
    }
}
