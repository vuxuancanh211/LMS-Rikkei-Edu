package project.lms_rikkei_edu.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAsyncService {

    private final EmailService emailService;

    @Async("emailExecutor")
    public void sendNewAccountMailAsync(String to, String fullName, String temporaryPassword, String courseTitle) {
        try {
            emailService.sendNewAccountMail(to, fullName, temporaryPassword, courseTitle);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async("emailExecutor")
    public void sendEnrolledToCourseMailAsync(String to, String fullName, String courseTitle) {
        try {
            emailService.sendEnrolledToCourseMail(to, fullName, courseTitle);
            log.info("Enrolled-to-course email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send enrolled-to-course email to {}: {}", to, e.getMessage());
        }
    }

}
